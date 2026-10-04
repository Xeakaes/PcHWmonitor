import json
import logging
import os
import re
import subprocess
import sys
import threading
from dataclasses import dataclass
from enum import Enum
from pathlib import Path
from typing import Callable

logger = logging.getLogger("pchw.tunnel")


def _find_cloudflared():
    """Find cloudflared.exe - check multiple locations."""
    candidates = []
    if getattr(sys, "frozen", False):
        base = Path(sys.executable).parent
        meipass = Path(sys._MEIPASS)
        candidates = [
            meipass / "cloudflared.exe",
            meipass / "vendor" / "cloudflared.exe",
            base / "cloudflared.exe",
            base / "vendor" / "cloudflared.exe",
        ]
    else:
        base = Path(__file__).resolve().parent
        candidates = [
            base / "cloudflared.exe",
            base / "vendor" / "cloudflared.exe",
            base.parent / "cloudflared.exe",
            base.parent / "vendor" / "cloudflared.exe",
        ]
    for c in candidates:
        if c.exists():
            return c
    return None


def find_cloudflared() -> Path | None:
    """Public wrapper around the module's cloudflared lookup."""
    return _find_cloudflared()


class TunnelState(Enum):
    OFF = "off"
    STARTING = "starting"
    RUNNING = "running"
    ERROR = "error"


@dataclass
class TunnelInfo:
    id: str
    name: str


class TunnelManager:
    """Owns one cloudflared subprocess: quick tunnel, state, lifecycle."""

    def __init__(
        self,
        *,
        cloudflared_finder: Callable[[], Path | None] | None = None,
        config_dir: Path | None = None,
        cert_path: Path | None = None,
        on_state: Callable[[TunnelState], None] | None = None,
        on_url: Callable[[str], None] | None = None,
        bootstrap_timeout: float = 30.0,
    ) -> None:
        self._finder = cloudflared_finder if cloudflared_finder is not None else _find_cloudflared
        self.config_dir = config_dir
        self._cert_path = cert_path
        self.bootstrap_timeout = bootstrap_timeout
        self._state_listeners: list[Callable[[TunnelState], None]] = []
        self._url_listeners: list[Callable[[str], None]] = []
        if on_state is not None:
            self._state_listeners.append(on_state)
        if on_url is not None:
            self._url_listeners.append(on_url)
        self._lock = threading.Lock()
        self._process: subprocess.Popen | None = None
        self._state = TunnelState.OFF
        self._url: str | None = None
        self._tunnel_name: str | None = None
        self._last_error: str | None = None

    @property
    def cert_path(self) -> Path:
        if self._cert_path is None:
            return Path.home() / ".cloudflared" / "cert.pem"
        return self._cert_path

    @property
    def state(self) -> TunnelState:
        return self._state

    @property
    def url(self) -> str | None:
        return self._url

    @property
    def tunnel_name(self) -> str | None:
        return self._tunnel_name

    @property
    def last_error(self) -> str | None:
        return self._last_error

    @property
    def is_running(self) -> bool:
        process = self._process
        return process is not None and process.poll() is None

    def has_cloudflared(self) -> bool:
        return self._finder() is not None

    def add_state_listener(self, listener: Callable[[TunnelState], None]) -> None:
        """Register an additional on_state subscriber (ctor callbacks are the first)."""
        self._state_listeners.append(listener)

    def add_url_listener(self, listener: Callable[[str], None]) -> None:
        """Register an additional on_url subscriber (ctor callbacks are the first)."""
        self._url_listeners.append(listener)

    def start_quick(self, port: int) -> bool:
        if self.is_running:
            return False
        cloudflared = self._finder()
        if cloudflared is None:
            self._set_state(TunnelState.ERROR, "cloudflared not found")
            return False
        cmd = [str(cloudflared), "tunnel", "--url", f"http://localhost:{port}"]
        logger.info("starting cloudflare tunnel: %s", " ".join(cmd))
        try:
            process = subprocess.Popen(
                cmd,
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,
                text=True,
            )
        except OSError as e:
            self._set_state(TunnelState.ERROR, str(e))
            return False
        with self._lock:
            self._process = process
            self._tunnel_name = "quick"
            self._url = None
            self._last_error = None
            self._state = TunnelState.RUNNING
        self._fire_state(TunnelState.RUNNING)
        threading.Thread(
            target=self._read_output,
            args=(process,),
            daemon=True,
            name="tunnel-reader",
        ).start()
        return True

    def list_tunnels(self, timeout: float | None = None) -> list[TunnelInfo]:
        cloudflared = self._finder()
        if cloudflared is None:
            return []
        limit = self.bootstrap_timeout if timeout is None else timeout
        try:
            result = subprocess.run(
                [str(cloudflared), "tunnel", "list"],
                capture_output=True,
                text=True,
                timeout=limit,
            )
        except (subprocess.TimeoutExpired, OSError) as e:
            logger.debug("tunnel list failed: %s", e)
            return []
        if result.returncode != 0:
            return []
        tunnels: list[TunnelInfo] = []
        for line in (result.stdout or "").splitlines():
            if line.startswith("ID") or line.startswith("---") or not line.strip():
                continue
            parts = line.split()
            if len(parts) >= 2:
                tunnels.append(TunnelInfo(id=parts[0], name=parts[1]))
        return tunnels

    def start_named(self, name: str, hostname: str, port: int) -> bool:
        # Single-start design: only the caller thread and the output reader mutate state.
        if self.is_running:
            return False
        cloudflared = self._finder()
        if cloudflared is None:
            self._set_state(TunnelState.ERROR, "cloudflared not found")
            return False
        if not Path(self.cert_path).exists():
            self._set_state(TunnelState.ERROR, "Cloudflare login required")
            return False
        cfg_dir = self._resolved_config_dir()
        process: subprocess.Popen | None = None
        try:
            tunnel_id = self._ensure_tunnel(cloudflared, name)
            if tunnel_id is None:
                return False
            cfg_path = write_ingress_config(cfg_dir, tunnel_id, hostname, port)
            if not route_dns(
                cloudflared, name, hostname, timeout=self.bootstrap_timeout
            ):
                self._set_state(
                    TunnelState.ERROR, f"DNS route failed for {hostname}"
                )
                return False
            process = subprocess.Popen(
                [str(cloudflared), "tunnel", "run", "--config", str(cfg_path), name],
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,
                text=True,
            )
        except Exception as e:
            if process is not None and process.poll() is None:
                process.terminate()
                try:
                    process.wait(timeout=5)
                except Exception:
                    process.kill()
            self._set_state(TunnelState.ERROR, str(e))
            return False
        with self._lock:
            self._process = process
            self._tunnel_name = name
            self._url = None
            self._last_error = None
            self._state = TunnelState.RUNNING
        self._fire_state(TunnelState.RUNNING)
        self._set_url(f"https://{hostname}")
        previous = load_tunnel_config(cfg_dir)
        save_tunnel_config(
            cfg_dir,
            TunnelConfig(tunnel_id, name, hostname, previous.auto_start),
        )
        threading.Thread(
            target=self._read_output,
            args=(process,),
            daemon=True,
            name="tunnel-reader",
        ).start()
        return True

    def apply_auto_start(self, port: int) -> bool:
        # Synchronous (caller threads it); an already-running tunnel wins (spec 4.3).
        if self.is_running:
            return False
        cfg = self.get_config()
        if not (cfg.auto_start and cfg.tunnel_name and cfg.hostname):
            return False
        return self.start_named(cfg.tunnel_name, cfg.hostname, port)

    def login(self) -> bool:
        cloudflared = self._finder()
        if cloudflared is None:
            return False
        try:
            import webbrowser

            process = subprocess.Popen(
                [str(cloudflared), "tunnel", "login"],
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,
                text=True,
            )
            url_opened = False
            for raw_line in process.stdout:
                line = raw_line.strip()
                logger.info("cloudflared login: %s", line)
                if url_opened or "https://" not in line:
                    continue
                for word in line.split():
                    word = word.rstrip(",/.;")
                    if word.startswith("https://") and "cloudflare" in word:
                        webbrowser.open(word)
                        url_opened = True
                        logger.info("opened login URL: %s", word)
                        break
            process.wait()
        except Exception as e:
            logger.error("cloudflared login failed: %s", e)
            return False
        return Path(self.cert_path).exists()

    def get_config(self) -> "TunnelConfig":
        return load_tunnel_config(self._resolved_config_dir())

    def set_auto_start(self, enabled: bool) -> None:
        cfg = self.get_config()
        cfg.auto_start = enabled
        save_tunnel_config(self._resolved_config_dir(), cfg)

    def _resolved_config_dir(self) -> Path:
        return self.config_dir if self.config_dir is not None else default_config_dir()

    def _ensure_tunnel(self, cloudflared: Path, name: str) -> str | None:
        for info in self.list_tunnels(timeout=self.bootstrap_timeout):
            if info.name == name:
                return info.id
        result = subprocess.run(
            [str(cloudflared), "tunnel", "create", name],
            capture_output=True,
            text=True,
            timeout=self.bootstrap_timeout,
        )
        output = ((result.stdout or "") + (result.stderr or "")).strip()
        if result.returncode != 0:
            self._set_state(
                TunnelState.ERROR,
                output[-200:] or f"tunnel create failed ({result.returncode})",
            )
            return None
        tunnel_id = parse_tunnel_id(output)
        if tunnel_id is None:
            self._set_state(
                TunnelState.ERROR, output[-200:] or "could not parse tunnel id"
            )
            return None
        return tunnel_id

    def stop(self) -> None:
        process = self._process
        if process is not None and process.poll() is None:
            process.terminate()
            try:
                process.wait(timeout=5)
            except Exception:
                try:
                    process.kill()
                    process.wait(timeout=5)
                except Exception:
                    pass
        with self._lock:
            self._process = None
            self._url = None
            self._tunnel_name = None
            self._last_error = None
            previous = self._state
            self._state = TunnelState.OFF
        if previous is not TunnelState.OFF:
            self._fire_state(TunnelState.OFF)

    def _set_state(self, state: TunnelState, error: str | None = None) -> None:
        with self._lock:
            self._state = state
            self._last_error = error
        self._fire_state(state)

    def _fire_state(self, state: TunnelState) -> None:
        for listener in list(self._state_listeners):
            try:
                listener(state)
            except Exception:
                logger.exception("on_state subscriber raised")

    def _set_url(self, url: str) -> None:
        with self._lock:
            if self._url is not None:
                return
            self._url = url
        for listener in list(self._url_listeners):
            try:
                listener(url)
            except Exception:
                logger.exception("on_url subscriber raised")

    def _read_output(self, process: subprocess.Popen) -> None:
        for raw_line in process.stdout:
            line = raw_line.strip()
            logger.info("cloudflared: %s", line)
            if "https://" not in line:
                continue
            for word in line.split():
                if word.startswith("https://"):
                    self._set_url(word.rstrip(",/.;"))
                    break
        process.wait()
        code = process.returncode
        if code in (0, None):
            return
        with self._lock:
            if self._state is not TunnelState.RUNNING or self._process is not process:
                return
            self._state = TunnelState.ERROR
            self._last_error = f"cloudflared exited ({code})"
        self._fire_state(TunnelState.ERROR)


_UUID_RE = re.compile(r"^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")


@dataclass
class TunnelConfig:
    tunnel_id: str | None = None
    tunnel_name: str | None = None
    hostname: str | None = None
    auto_start: bool = False


def default_config_dir() -> Path:
    if sys.platform == "win32":
        appdata = os.environ.get("APPDATA")
        if appdata:
            return Path(appdata) / "PcHwMonitor"
        return Path.home() / "AppData/Roaming/PcHwMonitor"
    xdg = os.environ.get("XDG_CONFIG_HOME")
    if xdg:
        return Path(xdg) / "pchwmonitor"
    return Path.home() / ".config/pchwmonitor"


def load_tunnel_config(config_dir: Path) -> TunnelConfig:
    path = config_dir / "tunnel.json"
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, ValueError):
        return TunnelConfig()
    if not isinstance(data, dict):
        return TunnelConfig()
    return TunnelConfig(
        tunnel_id=data.get("tunnel_id"),
        tunnel_name=data.get("tunnel_name"),
        hostname=data.get("hostname"),
        auto_start=bool(data.get("auto_start", False)),
    )


def save_tunnel_config(config_dir: Path, cfg: TunnelConfig) -> None:
    payload = {
        "tunnel_id": cfg.tunnel_id,
        "tunnel_name": cfg.tunnel_name,
        "hostname": cfg.hostname,
        "auto_start": cfg.auto_start,
    }
    try:
        config_dir.mkdir(parents=True, exist_ok=True)
        (config_dir / "tunnel.json").write_text(
            json.dumps(payload, indent=2), encoding="utf-8"
        )
    except OSError as e:
        logger.warning("could not persist tunnel config: %s", e)


def parse_tunnel_id(output: str) -> str | None:
    tokens = output.split()
    for token in tokens:
        if token.startswith("id="):
            return token.split("=", 1)[1]
    for i, token in enumerate(tokens):
        if token == "id" and i + 1 < len(tokens):
            return tokens[i + 1]
    for line in reversed(output.splitlines()):
        stripped = line.strip()
        if stripped:
            if _UUID_RE.match(stripped):
                return stripped
            return None
    return None


def write_ingress_config(config_dir: Path, tunnel_id: str, hostname: str, port: int) -> Path:
    path = config_dir / "cloudflared" / "config.yml"
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(
        f"tunnel: {tunnel_id}\n"
        f"ingress:\n"
        f"  - hostname: {hostname}\n"
        f"    service: http://localhost:{port}\n"
        f"  - service: http_status:404\n",
        encoding="utf-8",
    )
    return path


def route_dns(cloudflared: Path, tunnel_name: str, hostname: str, timeout: float = 30.0) -> bool:
    cmd = [str(cloudflared), "tunnel", "route", "dns", tunnel_name, hostname]
    try:
        result = subprocess.run(cmd, capture_output=True, text=True, timeout=timeout)
    except (subprocess.CalledProcessError, subprocess.TimeoutExpired, OSError) as e:
        logger.error("tunnel route dns failed: %s", e)
        return False
    combined = (result.stdout or "") + (result.stderr or "")
    if result.returncode == 0:
        return True
    if "already exists" in combined.lower():
        return True
    logger.error(
        "tunnel route dns exited %s: %s", result.returncode, combined.strip()
    )
    return False
