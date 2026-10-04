import asyncio
import logging
import threading
import time
from pathlib import Path

from fastapi import FastAPI

from app import _run_forever
from tunnel import TunnelManager, TunnelState

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


def _run_tray(stop_event: threading.Event, token: str | None, port: int, tunnel: TunnelManager) -> None:
    import pystray

    active_token = token or "(restart required)"

    def _connection_payload() -> str:
        from discovery import best_lan_ip
        tunnel_url = tunnel.url
        if tunnel_url:
            # Extract hostname from https://xxx.trycloudflare.com
            from urllib.parse import urlparse
            host = urlparse(tunnel_url).hostname
            if host:
                return f"pchw://connect?hostname={host}&token={active_token}"
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
        if not tunnel.has_cloudflared():
            icon.notify("cloudflared.exe not found.\nPlace it next to PcHwMonitor.exe.", "PC HW Monitor")
            return
        tunnel.start_quick(port)

    def _start_named_tunnel(icon, item):
        if tunnel.is_running:
            icon.notify("Tunnel already running.", "PC HW Monitor")
            return
        if not tunnel.has_cloudflared():
            icon.notify("cloudflared.exe not found.\nPlace it next to PcHwMonitor.exe or in vendor/.", "PC HW Monitor")
            return
        # Check if authenticated
        if not _is_cloudflared_authenticated():
            icon.notify("Not authenticated with Cloudflare.\nPlease run 'Cloudflare Login' first.", "PC HW Monitor")
            return
        # Check if there are existing tunnels to offer as choices
        existing = tunnel.list_tunnels()
        if existing:
            threading.Thread(target=_pick_tunnel, args=(existing,), daemon=True).start()
        else:
            threading.Thread(target=_ask_new_tunnel_name, daemon=True).start()

    def _is_cloudflared_authenticated():
        """Check if cloudflared has a cert.pem (means user has logged in)."""
        cert_path = Path.home() / ".cloudflared" / "cert.pem"
        return cert_path.exists()

    def _resolve_hostname(name: str) -> str | None:
        """Reuse saved hostname for this tunnel, else prompt; blank/None aborts."""
        cfg = tunnel.get_config()
        if cfg.tunnel_name == name and cfg.hostname:
            return cfg.hostname
        import tkinter as tk
        from tkinter import simpledialog
        root = tk.Tk()
        root.withdraw()
        hostname = simpledialog.askstring("Hostname", "Public hostname (e.g. pc.example.com):", parent=root)
        root.destroy()
        if not hostname or not hostname.strip():
            return None
        return hostname.strip()

    def _pick_tunnel(existing):
        """Show dialog to pick existing tunnel or create new one."""
        import tkinter as tk
        from tkinter import simpledialog
        root = tk.Tk()
        root.withdraw()
        # Build choices
        choices = [info.name for info in existing]
        choices.append("+ Create new tunnel")
        # Simple selection dialog
        from tkinter import ttk
        dialog = tk.Toplevel(root)
        dialog.title("Cloudflare Tunnel")
        dialog.geometry("350x200")
        dialog.resizable(False, False)
        tk.Label(dialog, text="Select a tunnel to run:", font=("Segoe UI", 10)).pack(padx=10, pady=(10, 5))
        listbox = tk.Listbox(dialog, font=("Consolas", 10))
        for c in choices:
            listbox.insert(tk.END, c)
        listbox.pack(fill=tk.BOTH, expand=True, padx=10, pady=5)
        selected = [None]
        def on_ok(event=None):
            sel = listbox.curselection()
            if sel:
                selected[0] = choices[sel[0]]
            dialog.destroy()
        def on_cancel():
            dialog.destroy()
        btn_frame = tk.Frame(dialog)
        btn_frame.pack(fill=tk.X, padx=10, pady=(0, 10))
        tk.Button(btn_frame, text="Run", width=8, command=on_ok).pack(side=tk.RIGHT, padx=(5, 0))
        tk.Button(btn_frame, text="Cancel", width=8, command=on_cancel).pack(side=tk.RIGHT)
        listbox.bind("<Double-Button-1>", on_ok)
        listbox.focus_set()
        dialog.protocol("WM_DELETE_WINDOW", on_cancel)
        dialog.eval("tk::PlaceWindow . center")
        root.destroy()
        dialog.mainloop()
        if selected[0] is None:
            return
        if selected[0] == "+ Create new tunnel":
            threading.Thread(target=_ask_new_tunnel_name, daemon=True).start()
        else:
            # Run existing tunnel
            name = selected[0]
            hostname = _resolve_hostname(name)
            if hostname is None:
                return
            threading.Thread(target=tunnel.start_named, args=(name, hostname, port), daemon=True).start()

    def _ask_new_tunnel_name():
        """Ask for a new tunnel name + hostname, then start it."""
        import tkinter as tk
        from tkinter import simpledialog
        root = tk.Tk()
        root.withdraw()
        name = simpledialog.askstring("Create Cloudflare Tunnel", "Enter tunnel name:", parent=root)
        root.destroy()
        if not name or not name.strip():
            return
        tunnel_name = name.strip()
        hostname = _resolve_hostname(tunnel_name)
        if hostname is None:
            return
        threading.Thread(target=tunnel.start_named, args=(tunnel_name, hostname, port), daemon=True).start()

    def _cloudflare_login(icon, item):
        if not tunnel.has_cloudflared():
            icon.notify("cloudflared.exe not found.", "PC HW Monitor")
            return
        icon.notify("Opening Cloudflare login page...\nAuthenticate in your browser.", "PC HW Monitor")
        def _run_login():
            ok = tunnel.login()
            icon.notify(
                "Cloudflare login successful." if ok else "Login failed — check cloudflared output.",
                "PC HW Monitor",
            )
        threading.Thread(target=_run_login, daemon=True).start()

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
        if tunnel.state is TunnelState.STARTING:
            return "Tunnel: starting..."
        if tunnel.state is TunnelState.RUNNING:
            return f"Tunnel: {tunnel.url or tunnel.tunnel_name}"
        if tunnel.state is TunnelState.ERROR:
            return f"Tunnel: error — {tunnel.last_error}"
        return "Tunnel: off"

    def _toggle_auto_start(icon, item):
        current = tunnel.get_config().auto_start
        tunnel.set_auto_start(not current)
        enabled = tunnel.get_config().auto_start
        icon.notify(
            f"Auto-start named tunnel {'enabled' if enabled else 'disabled'}.", "PC HW Monitor"
        )

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
                pystray.MenuItem(
                    "Auto-start named tunnel",
                    _toggle_auto_start,
                    checked=lambda item: tunnel.get_config().auto_start,
                ),
            ),
        ),
        pystray.Menu.SEPARATOR,
        pystray.MenuItem("Exit", _show_exit),
    )
    icon = pystray.Icon("PcHwMonitor", _tray_image(), "PC HW Monitor", menu)
    tunnel.add_url_listener(lambda u: icon.notify(f"Tunnel ready:\n{u}", "PC HW Monitor"))
    tunnel.add_state_listener(
        lambda s: icon.notify(f"Tunnel error:\n{tunnel.last_error}", "PC HW Monitor")
        if s is TunnelState.ERROR
        else None
    )
    threading.Thread(target=tunnel.apply_auto_start, args=(port,), daemon=True).start()
    icon.run()


def _serve_in_thread(app: FastAPI, port: int, stop_event: threading.Event, ssl_cert: str | None = None, ssl_key: str | None = None) -> None:
    try:
        asyncio.run(_run_forever(app, port, stop_event, ssl_cert=ssl_cert, ssl_key=ssl_key))
    except asyncio.CancelledError:
        pass
    finally:
        stop_event.set()


def _run_with_tray(
    app: FastAPI,
    port: int,
    ssl_cert: str | None = None,
    ssl_key: str | None = None,
    tunnel_manager: TunnelManager | None = None,
) -> None:
    stop_event = threading.Event()
    thread = threading.Thread(target=_serve_in_thread, args=(app, port, stop_event, ssl_cert, ssl_key), daemon=True)
    thread.start()
    tunnel = tunnel_manager if tunnel_manager is not None else TunnelManager()
    _run_tray(stop_event, token=app.state.token, port=port, tunnel=tunnel)
    tunnel.stop()
    thread.join(timeout=5)
    if app.state.fps_adapter is not None:
        app.state.fps_adapter.stop()
