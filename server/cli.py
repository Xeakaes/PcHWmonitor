import argparse
import asyncio
import logging
import os
import secrets
import subprocess
import sys
import tempfile
from pathlib import Path

from app import _run_forever, build_app
from tray import _run_with_tray

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s: %(message)s")
logger = logging.getLogger("pchw.cli")


def _redirect_noconsole_streams() -> None:
    if sys.stdout is None:
        sys.stdout = open(os.devnull, "w", encoding="utf-8")
    if sys.stderr is None:
        sys.stderr = open(os.devnull, "w", encoding="utf-8")
    if getattr(sys, "frozen", False):
        try:
            handler = logging.FileHandler(Path(sys.executable).parent / "pchw.log", encoding="utf-8")
            handler.setFormatter(logging.Formatter("%(asctime)s %(levelname)s %(name)s: %(message)s"))
            logging.getLogger().addHandler(handler)
        except Exception:
            pass


def main() -> None:
    _redirect_noconsole_streams()
    parser = argparse.ArgumentParser(description="PC HW Monitor bridge server")
    parser.add_argument("--port", type=int, default=8765)
    parser.add_argument("--simulate", action="store_true", help="generate fake data instead of reading hardware")
    parser.add_argument("--source", choices=["auto", "http", "lib"], default="auto", help="data source: auto | http (LibreHardwareMonitor remote web server) | lib (embedded LibreHardwareMonitorLib)")
    parser.add_argument("--lhm-url", default="http://127.0.0.1:8085/data.json")
    parser.add_argument("--interval", type=int, default=1000, help="broadcast interval in ms")
    parser.add_argument("--fps-process", default=None, help="process name to measure FPS for (empty = auto)")
    parser.add_argument("--token", default=None, help="require clients to authenticate with this token")
    parser.add_argument("--ssl-cert", default=None, help="path to PEM certificate file for WSS, or 'auto' for self-signed")
    parser.add_argument("--ssl-key", default=None, help="path to PEM private key file for WSS")
    parser.add_argument("--tunnel", default=None, nargs="?", const="quick",
                        help="start a Cloudflare Tunnel. Use 'quick' for temporary URL, or provide a tunnel name for persistent URL")
    args = parser.parse_args()

    # Always require auth: auto-generate if not provided
    token = args.token if args.token else secrets.token_urlsafe(16)

    # SSL setup
    ssl_cert = args.ssl_cert
    ssl_key = args.ssl_key
    if ssl_cert == "auto":
        import platform
        import subprocess
        cert_path = Path(tempfile.mktemp(suffix=".pem"))
        key_path = Path(tempfile.mktemp(suffix=".pem"))
        subprocess.run([
            "openssl", "req", "-x509", "-newkey", "rsa:2048",
            "-keyout", str(key_path), "-out", str(cert_path),
            "-days", "365", "-nodes",
            "-subj", f"/CN={platform.node()}",
        ], check=True)
        ssl_cert = str(cert_path)
        ssl_key = str(key_path)
        logger.info("auto-generated self-signed cert: %s", ssl_cert)

    app = build_app(simulate=args.simulate, lhm_url=args.lhm_url, interval_ms=args.interval, source=args.source, fps_process=args.fps_process, token=token)
    if args.simulate:
        logger.info("running in SIMULATION mode on 0.0.0.0:%d", args.port)
    else:
        logger.info("running with source=%s on 0.0.0.0:%d", args.source, args.port)

    # Cloudflare Tunnel
    tunnel_process = None
    tunnel_url = None
    if args.tunnel is not None:
        cloudflared = _find_cloudflared()
        if cloudflared is None:
            logger.error("cloudflared.exe not found — cannot start tunnel")
            sys.exit(1)
        if args.tunnel == "quick":
            cmd = [str(cloudflared), "tunnel", "--url", f"http://localhost:{args.port}"]
        else:
            cmd = [str(cloudflared), "tunnel", "run", args.tunnel]
        logger.info("starting cloudflare tunnel: %s", " ".join(cmd))
        tunnel_process = subprocess.Popen(
            cmd,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=True,
        )
        # Read output to find the tunnel URL
        import threading
        def _read_tunnel_output(proc):
            nonlocal tunnel_url
            for line in proc.stdout:
                line = line.strip()
                logger.info("cloudflared: %s", line)
                if "trycloudflare.com" in line or "https://" in line:
                    # Extract URL from line
                    for word in line.split():
                        if word.startswith("https://"):
                            tunnel_url = word.rstrip(",/.;")
                            break
        reader = threading.Thread(target=_read_tunnel_output, args=(tunnel_process,), daemon=True)
        reader.start()
        # Give it a moment to connect
        import time
        time.sleep(3)

    if getattr(sys, "frozen", False) and not args.simulate:
        _run_with_tray(app, args.port, ssl_cert=ssl_cert, ssl_key=ssl_key)
    else:
        scheme = "wss" if ssl_cert else "ws"
        logger.info("token: %s", token)
        print(f"\n{'='*50}")
        print(f"  ACCESS TOKEN: {token}")
        print(f"  Enter this in the Android app's Access Key field")
        if ssl_cert:
            print(f"  SSL: enabled ({scheme}://)")
        if tunnel_url:
            print(f"  TUNNEL: {tunnel_url}")
        elif args.tunnel is not None:
            print(f"  TUNNEL: starting... check tray icon or logs")
        print(f"{'='*50}\n")
        asyncio.run(_run_forever(app, args.port, ssl_cert=ssl_cert, ssl_key=ssl_key))

    # Cleanup tunnel
    if tunnel_process is not None:
        tunnel_process.terminate()
        tunnel_process.wait(timeout=5)


def _find_cloudflared():
    """Find cloudflared.exe - check next to exe, then in vendor dir."""
    if getattr(sys, "frozen", False):
        base = Path(sys.executable).parent
    else:
        base = Path(__file__).resolve().parent
    # Check same directory
    candidate = base / "cloudflared.exe"
    if candidate.exists():
        return candidate
    # Check vendor directory
    candidate = base / "vendor" / "cloudflared.exe"
    if candidate.exists():
        return candidate
    # Check _MEIPASS (PyInstaller temp dir)
    if getattr(sys, "frozen", False):
        candidate = Path(sys._MEIPASS) / "cloudflared.exe"
        if candidate.exists():
            return candidate
    return None


if __name__ == "__main__":
    main()