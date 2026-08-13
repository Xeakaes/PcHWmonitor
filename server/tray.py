import asyncio
import threading

from fastapi import FastAPI

from app import _run_forever


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
        asyncio.run(_run_forever(app, port, stop_event))
    except asyncio.CancelledError:
        pass
    finally:
        stop_event.set()


def _run_with_tray(app: FastAPI, port: int) -> None:
    stop_event = threading.Event()
    thread = threading.Thread(target=_serve_in_thread, args=(app, port, stop_event), daemon=True)
    thread.start()
    _run_tray(stop_event)
    thread.join(timeout=5)
    if app.state.fps_adapter is not None:
        app.state.fps_adapter.stop()