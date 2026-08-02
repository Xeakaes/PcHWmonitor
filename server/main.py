import argparse
import asyncio
import logging
import os
import platform
import sys
import threading
import time
from pathlib import Path

import uvicorn
from fastapi import FastAPI, WebSocket, WebSocketDisconnect

from adapters.lhm import LhmAdapter
from adapters.lhm_lib import LhmLibAdapter
from adapters.simulator import Simulator
from hub import Hub
from schema import StatusMessage, WelcomeMessage

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s: %(message)s")
logger = logging.getLogger("pchw.main")


def _unavailable_sample() -> StatusMessage:
    return StatusMessage(timestamp=int(time.time()), available=False, error="LibreHardwareMonitorLib.dll not found")


def _lib_available() -> bool:
    try:
        adapter = LhmLibAdapter()
        adapter.close()
        return True
    except Exception:
        return False


def build_app(
    *,
    simulate: bool = False,
    lhm_url: str = "http://127.0.0.1:8085/data.json",
    interval_ms: int = 1000,
    source: str = "auto",
) -> FastAPI:
    if simulate:
        sample = Simulator().sample
        source_name = "simulator"
        pc_name = platform.node() or "SIM-PC"
    else:
        chosen = source
        if chosen == "auto":
            chosen = "lib" if _lib_available() else "http"
        if chosen == "lib":
            try:
                adapter = LhmLibAdapter()
                sample = adapter.fetch
                source_name = "lhm-lib"
            except Exception as exc:
                sample = _unavailable_sample
                source_name = "lhm-lib"
                logger.warning("lhm-lib init failed (%s); reporting unavailable", exc)
        else:
            adapter = LhmAdapter(lhm_url=lhm_url)
            sample = adapter.fetch
            source_name = "librehardwaremonitor"
        pc_name = platform.node()

    hub = Hub(sample=sample, interval_ms=interval_ms)
    app = FastAPI(title="PC HW Monitor bridge")
    app.state.hub = hub
    app.state.welcome = WelcomeMessage(intervalMs=interval_ms, serverName=pc_name, source=source_name, pcName=pc_name)

    @app.get("/health")
    async def health():
        return {"ok": True, "source": source_name, "clients": hub.client_count}

    @app.websocket("/ws")
    async def ws_endpoint(ws: WebSocket):
        await ws.accept()
        await ws.send_text(app.state.welcome.model_dump_json())
        hub.register(ws)
        logger.info("client connected (%d total)", hub.client_count)
        try:
            while True:
                await ws.receive_text()
        except WebSocketDisconnect:
            hub.unregister(ws)
            logger.info("client disconnected (%d total)", hub.client_count)

    return app


# -- system tray (packaged mode only) --

def _tray_image():
    from PIL import Image, ImageDraw
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.rounded_rectangle([8, 6, 56, 46], radius=6, outline="#FFFFFF", width=5)
    d.line([(32, 46), (32, 54)], fill="#FFFFFF", width=5)
    d.line([(22, 54), (42, 54)], fill="#FFFFFF", width=5)
    d.line([(20, 30), (26, 30), (30, 22), (36, 38), (41, 30), (46, 30)], fill="#4DD0E1", width=4, joint="curve")
    return img


def _run_tray(stop_event: threading.Event) -> None:
    import pystray
    menu = pystray.Menu(pystray.MenuItem("Kapat", lambda icon, item: (stop_event.set(), icon.stop())))
    icon = pystray.Icon("PcHwMonitor", _tray_image(), "PC HW Monitor", menu)
    icon.run()


def _serve_in_thread(app: FastAPI, port: int, stop_event: threading.Event) -> None:
    try:
        asyncio.run(_run_forever(app, port))
    except asyncio.CancelledError:
        pass
    finally:
        stop_event.set()


def _run_with_tray(app: FastAPI, port: int) -> None:
    stop_event = threading.Event()
    thread = threading.Thread(target=_serve_in_thread, args=(app, port, stop_event), daemon=True)
    thread.start()
    _run_tray(stop_event)
    os._exit(0)


async def _run_forever(app: FastAPI, port: int) -> None:
    task = asyncio.create_task(app.state.hub.tick_forever())
    try:
        config = uvicorn.Config(app, host="0.0.0.0", port=port, log_level="info", log_config=None, access_log=False)
        await uvicorn.Server(config).serve()
    finally:
        task.cancel()


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
    args = parser.parse_args()

    app = build_app(simulate=args.simulate, lhm_url=args.lhm_url, interval_ms=args.interval, source=args.source)
    if args.simulate:
        logger.info("running in SIMULATION mode on 0.0.0.0:%d", args.port)
    else:
        logger.info("running with source=%s on 0.0.0.0:%d", args.source, args.port)

    if getattr(sys, "frozen", False) and not args.simulate:
        _run_with_tray(app, args.port)
    else:
        asyncio.run(_run_forever(app, args.port))


if __name__ == "__main__":
    main()
