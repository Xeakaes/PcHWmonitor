import asyncio
import logging
import threading

from fastapi import FastAPI

from app import _run_forever

logger = logging.getLogger("pchw.tray")


def _tray_image():
    from PIL import Image, ImageDraw
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.rounded_rectangle([8, 6, 56, 46], radius=6, outline="#FFFFFF", width=5)
    d.line([(32, 46), (32, 54)], fill="#FFFFFF", width=5)
    d.line([(22, 54), (42, 54)], fill="#FFFFFF", width=5)
    d.line([(20, 30), (26, 30), (30, 22), (36, 38), (41, 30), (46, 30)], fill="#4DD0E1", width=4, joint="curve")
    return img


def _run_tray(stop_event: threading.Event, token: str | None = None, port: int = 8765) -> None:
    import pystray

    active_token = token or "(restart required)"

    def _connection_payload() -> str:
        from discovery import best_lan_ip
        return f"pchw://connect?ip={best_lan_ip()}&port={port}&token={active_token}"

    def _copy_payload(icon, item):
        payload = _connection_payload()
        try:
            import subprocess
            subprocess.run("clip", input=payload.encode("utf-16-le"), check=True)
            icon.notify("Connection info copied to clipboard.", "PC HW Monitor")
        except Exception:
            icon.notify(f"Copy failed. Payload:\n{payload}", "PC HW Monitor")

    def _show_qr(icon, item):
        # Run Tk in its own daemon thread so the tray stays responsive and the
        # window can always be closed with its [X] button.
        threading.Thread(target=_open_qr_window, args=(_connection_payload(),), daemon=True).start()

    def _open_qr_window(payload: str) -> None:
        import io
        import tkinter as tk
        import qrcode
        try:
            qr = qrcode.QRCode(box_size=6, border=2)
            qr.add_data(payload)
            qr.make(fit=True)
            img = qr.make_image(fill_color="black", back_color="white")
            buf = io.BytesIO()
            img.save(buf, format="PNG")
            root = tk.Tk()
            root.title("PC HW Monitor — Scan to connect")
            photo = tk.PhotoImage(data=buf.getvalue())
            tk.Label(root, image=photo).pack(padx=12, pady=(12, 4))
            tk.Label(root, text=payload, wraplength=340, justify="center",
                     fg="#555555").pack(padx=12, pady=4)
            tk.Label(root, text='Scan with the app ("Fill via QR"), then tap Connect.',
                     justify="center").pack(pady=(2, 12))
            root.protocol("WM_DELETE_WINDOW", root.destroy)
            root.eval("tk::PlaceWindow . center")
            root.mainloop()
        except Exception as e:
            logger.error("QR window failed: %s — payload: %s", e, payload)

    def _show_info(icon, item):
        info_text = (
            f"Port: {port}\n"
            f"Token: {active_token}\n\n"
            f"Enter this token in the Android app's\n"
            f"Access Key field, then tap Connect.\n"
            f"(Or use the app's 'Fill via QR' button.)"
        )
        # Use pystray notification — no dialog window, always dismissable
        icon.notify(info_text, "PC HW Monitor")

    def _show_exit(icon, item):
        stop_event.set()
        icon.stop()

    menu = pystray.Menu(
        pystray.MenuItem("Show QR", _show_qr, default=True),
        pystray.MenuItem("Copy connection info", _copy_payload),
        pystray.Menu.SEPARATOR,
        pystray.MenuItem("Info", _show_info),
        pystray.Menu.SEPARATOR,
        pystray.MenuItem("Exit", _show_exit),
    )
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
    _run_tray(stop_event, token=app.state.token, port=port)
    thread.join(timeout=5)
    if app.state.fps_adapter is not None:
        app.state.fps_adapter.stop()
