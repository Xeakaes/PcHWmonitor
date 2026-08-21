import argparse
import asyncio
import logging
import os
import secrets
import sys
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
    args = parser.parse_args()

    # Always require auth: auto-generate if not provided
    token = args.token if args.token else secrets.token_urlsafe(16)

    app = build_app(simulate=args.simulate, lhm_url=args.lhm_url, interval_ms=args.interval, source=args.source, fps_process=args.fps_process, token=token)
    if args.simulate:
        logger.info("running in SIMULATION mode on 0.0.0.0:%d", args.port)
    else:
        logger.info("running with source=%s on 0.0.0.0:%d", args.source, args.port)

    if getattr(sys, "frozen", False) and not args.simulate:
        _run_with_tray(app, args.port)
    else:
        logger.info("token: %s", token)
        asyncio.run(_run_forever(app, args.port))


if __name__ == "__main__":
    main()