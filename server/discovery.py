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
BROADCAST_ADDRESS = "255.255.255.255"


def get_local_ip() -> str:
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.connect(("255.255.255.255", 1))
        ip = s.getsockname()[0]
        s.close()
        return ip
    except Exception:
        return "127.0.0.1"


def best_lan_ip() -> str:
    """Pick the most likely real-LAN IPv4 address.

    Machines with virtual switches (WSL, Hyper-V, Docker) expose several
    private addresses; the UDP-routing trick above often lands on one of
    them, which phones then reject. Enumerate every interface instead and
    prefer classic home-LAN ranges: 192.168.x.x > 10.x.x.x > 172.16-31.x.x,
    skipping loopback and link-local (169.254.x.x).
    """
    try:
        import psutil

        virtual_hints = ("vethernet", "wsl", "docker", "hyper-v", "virtualbox",
                         "vmware", "loopback", "tailscale", "zerotier", "hamachi",
                         "libvirt", "virbr")
        scored: list[tuple[int, str]] = []
        for name, addrs in psutil.net_if_addrs().items():
            lname = name.lower()
            if lname == "lo":
                continue
            penalty = 0
            if any(h in lname for h in virtual_hints):
                penalty = -10
            for addr in addrs:
                if addr.family != socket.AF_INET:
                    continue
                ip = addr.address
                octets = ip.split(".")
                if len(octets) != 4 or ip.startswith(("127.", "169.254.")):
                    continue
                a, b = int(octets[0]), int(octets[1])
                if a == 192 and b == 168:
                    scored.append((3 + penalty, ip))
                elif a == 10:
                    scored.append((2 + penalty, ip))
                elif a == 172 and 16 <= b <= 31:
                    scored.append((1 + penalty, ip))
        if scored:
            scored.sort(reverse=True)
            return scored[0][1]
    except Exception as e:
        logger.debug("best_lan_ip fallback: %s", e)
    return get_local_ip()


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
            targets = [BROADCAST_ADDRESS]
            # Subnet-directed broadcast (e.g. 192.168.1.255) survives routers
            # that swallow the global 255.255.255.255 packet.
            octets = local_ip.split(".")
            if len(octets) == 4 and local_ip != "127.0.0.1":
                targets.append(f"{octets[0]}.{octets[1]}.{octets[2]}.255")
            for target in targets:
                try:
                    sock.sendto(payload, (target, BROADCAST_PORT))
                except Exception as e:
                    logger.debug("broadcast to %s failed: %s", target, e)
            stop_event.wait(BROADCAST_INTERVAL)

        sock.close()

    thread = threading.Thread(target=broadcast_loop, daemon=True, name="discovery-broadcast")
    thread.start()
    return thread
