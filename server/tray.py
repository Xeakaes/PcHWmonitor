import asyncio
import logging
import subprocess
import threading
import time

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


class TunnelManager:
    """Manages a cloudflared tunnel subprocess."""

    def __init__(self):
        self._process: subprocess.Popen | None = None
        self._url: str | None = None
        self._name: str | None = None
        self._reader_thread: threading.Thread | None = None
        self._on_url_callback = None

    @property
    def is_running(self) -> bool:
        return self._process is not None and self._process.poll() is None

    @property
    def url(self) -> str | None:
        return self._url

    @property
    def name(self) -> str | None:
        return self._name

    def set_on_url_callback(self, callback):
        self._on_url_callback = callback

    def start_quick(self, port: int) -> bool:
        """Start a temporary quick tunnel."""
        if self.is_running:
            return False
        cloudflared = _find_cloudflared()
        if cloudflared is None:
            return False
        cmd = [str(cloudflared), "tunnel", "--url", f"http://localhost:{port}"]
        return self._start(cmd, "quick")

    def start_named(self, tunnel_name: str, port: int) -> bool:
        """Start a named (persistent) tunnel."""
        if self.is_running:
            return False
        cloudflared = _find_cloudflared()
        if cloudflared is None:
            return False
        cmd = [str(cloudflared), "tunnel", "run", tunnel_name]
        return self._start(cmd, tunnel_name)

    def _start(self, cmd: list[str], name: str) -> bool:
        try:
            logger.info("starting cloudflare tunnel: %s", " ".join(cmd))
            self._process = subprocess.Popen(
                cmd,
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,
                text=True,
            )
            self._name = name
            self._url = None
            self._reader_thread = threading.Thread(
                target=self._read_output, daemon=True, name="tunnel-reader"
            )
            self._reader_thread.start()
            return True
        except Exception as e:
            logger.error("failed to start tunnel: %s", e)
            return False

    def _read_output(self):
        proc = self._process
        if proc is None or proc.stdout is None:
            return
        for line in proc.stdout:
            line = line.strip()
            logger.info("cloudflared: %s", line)
            if "trycloudflare.com" in line or "https://" in line:
                for word in line.split():
                    if word.startswith("https://"):
                        self._url = word.rstrip(",/.;")
                        logger.info("tunnel URL: %s", self._url)
                        if self._on_url_callback:
                            self._on_url_callback(self._url)
                        break

    def stop(self):
        if self._process is not None:
            try:
                self._process.terminate()
                self._process.wait(timeout=5)
            except Exception:
                try:
                    self._process.kill()
                except Exception:
                    pass
            self._process = None
            self._url = None
            self._name = None
            logger.info("tunnel stopped")


def _find_cloudflared():
    """Find cloudflared.exe - check multiple locations."""
    import sys
    from pathlib import Path
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


def _run_tray(stop_event: threading.Event, token: str | None = None, port: int = 8765) -> None:
    import pystray

    active_token = token or "(restart required)"
    tunnel = TunnelManager()

    def _connection_payload() -> str:
        from discovery import best_lan_ip
        return f"pchw://connect?ip={best_lan_ip()}&port={port}&token={active_token}"

    def _copy_payload(icon, item):
        payload = _connection_payload()
        try:
            import subprocess
            import shutil
            if shutil.which("xclip"):
                subprocess.run(
                    ["xclip", "-selection", "clipboard"],
                    input=payload.encode("utf-8"), check=True,
                )
            else:
                subprocess.run(
                    ["clip"], input=payload.encode("utf-16-le"), check=True,
                )
            icon.notify("Connection info copied to clipboard.", "PC HW Monitor")
        except Exception:
            icon.notify(f"Copy failed. Payload:\n{payload}", "PC HW Monitor")

    def _show_qr(icon, item):
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
        if tunnel.is_running and tunnel.url:
            info_text += f"\n\nTunnel: {tunnel.url}"
        icon.notify(info_text, "PC HW Monitor")

    # --- Tunnel actions ---
    def _start_quick_tunnel(icon, item):
        if tunnel.is_running:
            icon.notify("Tunnel already running.", "PC HW Monitor")
            return
        if not _find_cloudflared():
            icon.notify("cloudflared.exe not found.\nPlace it next to PcHwMonitor.exe.", "PC HW Monitor")
            return
        def _on_url(url):
            icon.notify(f"Quick tunnel started:\n{url}", "PC HW Monitor")
        tunnel.set_on_url_callback(_on_url)
        ok = tunnel.start_quick(port)
        if not ok:
            icon.notify("Failed to start tunnel.", "PC HW Monitor")

    def _start_named_tunnel(icon, item):
        if tunnel.is_running:
            icon.notify("Tunnel already running.", "PC HW Monitor")
            return
        cf = _find_cloudflared()
        if not cf:
            icon.notify("cloudflared.exe not found.\nPlace it next to PcHwMonitor.exe or in vendor/.", "PC HW Monitor")
            return
        # Check if authenticated
        if not _is_cloudflared_authenticated(cf):
            icon.notify("Not authenticated with Cloudflare.\nPlease run 'Cloudflare Login' first.", "PC HW Monitor")
            return
        # Open a simple Tk dialog to ask for tunnel name
        threading.Thread(target=_ask_tunnel_name, daemon=True).start()

    def _is_cloudflared_authenticated(cf_path):
        """Check if cloudflared has a cert.pem (means user has logged in)."""
        import sys
        from pathlib import Path
        if getattr(sys, "frozen", False):
            base = Path(sys.executable).parent
        else:
            base = Path(__file__).resolve().parent
        # cloudflared stores cert in ~/.cloudflared/cert.pem
        cert_path = Path.home() / ".cloudflared" / "cert.pem"
        return cert_path.exists()

    def _cloudflare_login(icon, item):
        cf = _find_cloudflared()
        if not cf:
            icon.notify("cloudflared.exe not found.", "PC HW Monitor")
            return
        icon.notify("Opening Cloudflare login page...\nAuthenticate in your browser.", "PC HW Monitor")
        def _run_login():
            try:
                proc = subprocess.Popen(
                    [str(cf), "tunnel", "login"],
                    stdout=subprocess.PIPE,
                    stderr=subprocess.STDOUT,
                    text=True,
                )
                for line in proc.stdout:
                    line = line.strip()
                    logger.info("cloudflared login: %s", line)
                    if "https://" in line and "cloudflareaccess" in line:
                        for word in line.split():
                            if word.startswith("https://"):
                                import webbrowser
                                webbrowser.open(word.rstrip(",/.;"))
                                break
                proc.wait()
                if _is_cloudflared_authenticated(cf):
                    logger.info("cloudflared authentication successful")
                else:
                    logger.warning("cloudflared authentication may have failed")
            except Exception as e:
                logger.error("cloudflared login failed: %s", e)
        threading.Thread(target=_run_login, daemon=True).start()

    def _ask_tunnel_name():
        import tkinter as tk
        from tkinter import simpledialog
        root = tk.Tk()
        root.withdraw()
        name = simpledialog.askstring("Cloudflare Tunnel", "Enter tunnel name:", parent=root)
        root.destroy()
        if not name or not name.strip():
            return
        def _on_url(url):
            # Can't notify from here since icon isn't accessible; logger handles it
            pass
        tunnel.set_on_url_callback(_on_url)
        ok = tunnel.start_named(name.strip(), port)
        if not ok:
            logger.error("failed to start named tunnel: %s", name)

    def _stop_tunnel(icon, item):
        if not tunnel.is_running:
            icon.notify("No tunnel running.", "PC HW Monitor")
            return
        tunnel.stop()
        icon.notify("Tunnel stopped.", "PC HW Monitor")

    def _copy_tunnel_url(icon, item):
        if not tunnel.is_running or not tunnel.url:
            icon.notify("No active tunnel URL to copy.", "PC HW Monitor")
            return
        try:
            import subprocess
            import shutil
            if shutil.which("xclip"):
                subprocess.run(
                    ["xclip", "-selection", "clipboard"],
                    input=tunnel.url.encode("utf-8"), check=True,
                )
            else:
                subprocess.run(
                    ["clip"], input=tunnel.url.encode("utf-16-le"), check=True,
                )
            icon.notify(f"Tunnel URL copied:\n{tunnel.url}", "PC HW Monitor")
        except Exception:
            icon.notify(f"Tunnel URL:\n{tunnel.url}", "PC HW Monitor")

    def _show_tunnel_url(icon, item):
        if not tunnel.is_running or not tunnel.url:
            icon.notify("No active tunnel.", "PC HW Monitor")
            return
        icon.notify(f"Tunnel URL:\n{tunnel.url}", "PC HW Monitor")

    def _show_exit(icon, item):
        tunnel.stop()
        stop_event.set()
        icon.stop()

    # Dynamic menu: show tunnel status
    def _tunnel_status_text(item=None):
        if tunnel.is_running:
            return f"Tunnel: {tunnel.url or tunnel.name or 'starting...'}"
        return "Tunnel: off"

    menu = pystray.Menu(
        pystray.MenuItem("Show QR", _show_qr, default=True),
        pystray.MenuItem("Copy connection info", _copy_payload),
        pystray.Menu.SEPARATOR,
        pystray.MenuItem("Info", _show_info),
        pystray.Menu.SEPARATOR,
        pystray.MenuItem(
            _tunnel_status_text,
            pystray.Menu(
                pystray.MenuItem("Start Quick Tunnel", _start_quick_tunnel),
                pystray.MenuItem("Start Named Tunnel...", _start_named_tunnel),
                pystray.MenuItem("Stop Tunnel", _stop_tunnel),
                pystray.Menu.SEPARATOR,
                pystray.MenuItem("Copy Tunnel URL", _copy_tunnel_url),
                pystray.MenuItem("Show Tunnel URL", _show_tunnel_url),
                pystray.Menu.SEPARATOR,
                pystray.MenuItem("Cloudflare Login", _cloudflare_login),
            ),
        ),
        pystray.Menu.SEPARATOR,
        pystray.MenuItem("Exit", _show_exit),
    )
    icon = pystray.Icon("PcHwMonitor", _tray_image(), "PC HW Monitor", menu)
    icon.run()


def _serve_in_thread(app: FastAPI, port: int, stop_event: threading.Event, ssl_cert: str | None = None, ssl_key: str | None = None) -> None:
    try:
        asyncio.run(_run_forever(app, port, stop_event, ssl_cert=ssl_cert, ssl_key=ssl_key))
    except asyncio.CancelledError:
        pass
    finally:
        stop_event.set()


def _run_with_tray(app: FastAPI, port: int, ssl_cert: str | None = None, ssl_key: str | None = None) -> None:
    stop_event = threading.Event()
    thread = threading.Thread(target=_serve_in_thread, args=(app, port, stop_event, ssl_cert, ssl_key), daemon=True)
    thread.start()
    _run_tray(stop_event, token=app.state.token, port=port)
    thread.join(timeout=5)
    if app.state.fps_adapter is not None:
        app.state.fps_adapter.stop()
