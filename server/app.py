import asyncio
import json
import logging
import platform
import sys
import threading
import time
from pathlib import Path

import uvicorn
from fastapi import FastAPI, WebSocket, WebSocketDisconnect

from adapters.fps import PresentMonFps
from adapters.lhm import LhmAdapter
from adapters.lhm_lib import LhmLibAdapter
from adapters.simulator import Simulator
from adapters.system import SystemAdapter
from hub import Hub
from schema import StatusMessage, WelcomeMessage

logger = logging.getLogger("pchw.app")


def _unavailable_sample() -> StatusMessage:
    return StatusMessage(timestamp=int(time.time()), available=False, error="LibreHardwareMonitorLib.dll not found")


def _lib_available() -> bool:
    try:
        adapter = LhmLibAdapter()
        adapter.close()
        return True
    except Exception:
        return False


def _presentmon_path() -> str:
    if getattr(sys, "frozen", False):
        base = Path(sys._MEIPASS)
    else:
        base = Path(__file__).resolve().parent
    candidate = base / "PresentMon64.exe"
    if not candidate.exists():
        candidate = base / "presentmon" / "PresentMon64.exe"
    return str(candidate)


def build_app(
    *,
    simulate: bool = False,
    lhm_url: str = "http://127.0.0.1:8085/data.json",
    interval_ms: int = 1000,
    source: str = "auto",
    fps_process: str | None = None,
    token: str | None = None,
    auth_timeout: float = 5.0,
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

    if simulate:
        sim = Simulator()
        system_adapter = None
        fps_adapter = None
        base_sample = sim.sample
    else:
        system_adapter = SystemAdapter()
        exe_path = _presentmon_path()
        fps_adapter = PresentMonFps(exe_path=exe_path, process_name=fps_process or None)
        if not fps_adapter.start():
            fps_adapter = None
            logger.warning("PresentMon unavailable; FPS disabled")
        base_sample = sample

    def composite() -> StatusMessage:
        message = base_sample()
        if system_adapter is not None:
            disk, net = system_adapter.sample()
            message.disk = disk if message.disk is None else message.disk
            message.net = net if message.net is None else message.net
        if fps_adapter is not None:
            message.fps = fps_adapter.sample()
        return message

    hub = Hub(sample=composite, interval_ms=interval_ms)
    app = FastAPI(title="PC HW Monitor bridge")
    app.state.hub = hub
    app.state.fps_process = fps_process
    app.state.fps_adapter = fps_adapter
    app.state.fps_active = fps_adapter is not None
    app.state.welcome = WelcomeMessage(intervalMs=interval_ms, serverName=pc_name, source=source_name, pcName=pc_name)
    app.state.token = token
    app.state.auth_timeout = auth_timeout

    @app.get("/health")
    async def health():
        return {"ok": True, "source": source_name, "clients": hub.client_count}

    async def _authenticated(ws: WebSocket) -> bool:
        try:
            raw = await asyncio.wait_for(ws.receive_text(), timeout=app.state.auth_timeout)
        except (asyncio.TimeoutError, WebSocketDisconnect):
            return False
        try:
            payload = json.loads(raw)
        except json.JSONDecodeError:
            return False
        return payload.get("type") == "auth" and payload.get("token") == app.state.token

    @app.websocket("/ws")
    async def ws_endpoint(ws: WebSocket):
        if ws.headers.get("origin"):
            # Browser-based clients always send an Origin header; the Android
            # app and CLI tools do not. Reject browser origins to block CSWSH.
            await ws.close(code=1008)
            return
        await ws.accept()
        if app.state.token is not None and not await _authenticated(ws):
            logger.warning("client rejected: missing or invalid auth token")
            await ws.close(code=1008)
            return
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


async def _run_forever(app: FastAPI, port: int, stop_event: threading.Event | None = None) -> None:
    task = asyncio.create_task(app.state.hub.tick_forever())
    monitor = None
    try:
        config = uvicorn.Config(app, host="0.0.0.0", port=port, log_level="info", log_config=None, access_log=False)
        server = uvicorn.Server(config)
        if stop_event is not None:
            monitor = asyncio.create_task(_wait_for_stop(server, stop_event))
        await server.serve()
    finally:
        if monitor is not None:
            monitor.cancel()
        task.cancel()
        if app.state.fps_adapter is not None:
            app.state.fps_adapter.stop()


async def _wait_for_stop(server: uvicorn.Server, stop_event: threading.Event) -> None:
    while not stop_event.is_set():
        await asyncio.sleep(0.2)
    server.should_exit = True