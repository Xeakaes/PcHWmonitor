import logging
import subprocess
import sys
import threading
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
        self.cert_path = cert_path
        self.bootstrap_timeout = bootstrap_timeout
        self._on_state = on_state
        self._on_url = on_url
        self._lock = threading.Lock()
        self._process: subprocess.Popen | None = None
        self._state = TunnelState.OFF
        self._url: str | None = None
        self._tunnel_name: str | None = None
        self._last_error: str | None = None

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
        if self._on_state is None:
            return
        try:
            self._on_state(state)
        except Exception:
            logger.exception("on_state subscriber raised")

    def _set_url(self, url: str) -> None:
        with self._lock:
            self._url = url
        if self._on_url is None:
            return
        try:
            self._on_url(url)
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
