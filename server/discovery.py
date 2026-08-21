"""
UDP broadcast module for LAN server discovery.
Broadcasts server info (name, IP, port) to the local network every 2 seconds.
"""

import json
import logging
import platform
import socket
import threading
import time

logger = logging.getLogger("pchw.discovery")

BROADCAST_PORT = 8766
BROADCAST_INTERVAL = 2.0
MAGIC = "PCHW"


def get_local_ip() -> str:
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.connect(("255.255.255.255", 1))
        ip = s.getsockname()[0]
        s.close()
        return ip
    except Exception:
        return "127.0.0.1"


def start_broadcast(server_port: int, server_name: str | None = None) -> threading.Thread:
    if server_name is None:
        server_name = platform.node() or "PC-HW-Monitor"

    local_ip = get_local_ip()
    stop_event = threading.Event()

    def broadcast_loop():
        sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        sock.setsockopt(socket.SOL_SOCKET, socket.SO_BROADCAST, 1)
        sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)

        payload = json.dumps({
            "magic": MAGIC,
            "name": server_name,
            "ip": local_ip,
            "port": server_port,
            "version": "1.5",
        }).encode("utf-8")

        logger.info("discovery broadcast started on %s:%d", "0.0.0.0", BROADCAST_PORT)

        while not stop_event.is_set():
            try:
                sock.sendto(payload, ("<broadcast>", BROADCAST_PORT))
            except Exception as e:
                logger.debug("broadcast send failed: %s", e)
            stop_event.wait(BROADCAST_INTERVAL)

        sock.close()

    thread = threading.Thread(target=broadcast_loop, daemon=True, name="discovery-broadcast")
    thread.start()
    return thread
