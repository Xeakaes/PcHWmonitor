import time

from schema import DiskInfo, NetInfo

_MB = 1024.0 * 1024.0


class SystemAdapter:
    def __init__(self, interval: float = 1.0, _psutil=None):
        self._interval = interval
        if _psutil is None:
            import psutil as _psutil
        self._psutil = _psutil
        self._last = None
        self._last_time = None

    def sample(self) -> tuple[DiskInfo | None, NetInfo | None]:
        try:
            io = self._psutil.disk_io_counters()
            net = self._psutil.net_io_counters()
            usage = self._psutil.disk_usage("/")
        except Exception:
            return None, None

        now = time.monotonic()
        if self._last is None or self._last_time is None:
            self._last = (io, net)
            self._last_time = now
            return None, None

        dt = max(now - self._last_time, self._interval)
        last_io, last_net = self._last
        self._last = (io, net)
        self._last_time = now

        disk = DiskInfo(
            usagePct=round(float(usage.percent), 1),
            readMbPerSec=round(max(io.read_bytes - last_io.read_bytes, 0) / _MB / dt, 1),
            writeMbPerSec=round(max(io.write_bytes - last_io.write_bytes, 0) / _MB / dt, 1),
        )
        net_info = NetInfo(
            downloadMbPerSec=round(max(net.bytes_recv - last_net.bytes_recv, 0) / _MB / dt, 1),
            uploadMbPerSec=round(max(net.bytes_sent - last_net.bytes_sent, 0) / _MB / dt, 1),
        )
        return disk, net_info