import time

from schema import DiskInfo, NetInfo

_MB = 1024.0 * 1024.0

_PSEUDO_FS = {
    "proc", "sysfs", "devtmpfs", "tmpfs", "devpts", "cgroup", "cgroup2",
    "overlay", "squashfs", "fusectl", "securityfs", "debugfs", "pstore",
    "configfs", "autofs", "binfmt_misc", "mqueue", "hugetlbfs", "tracefs",
    "bpf", "ramfs", "efivarfs",
}


class SystemAdapter:
    def __init__(self, interval: float = 1.0, _psutil=None):
        self._interval = interval
        if _psutil is None:
            import psutil as _psutil
        self._psutil = _psutil
        self._last = None
        self._last_time = None

    def _usage_percent(self) -> float | None:
        total = 0
        used = 0
        for part in self._psutil.disk_partitions(all=False):
            if part.fstype in _PSEUDO_FS:
                continue
            try:
                usage = self._psutil.disk_usage(part.mountpoint)
            except Exception:
                continue
            total += usage.total
            used += usage.used
        if total <= 0:
            return None
        return round(used / total * 100.0, 1)

    def sample(self) -> tuple[DiskInfo | None, NetInfo | None]:
        try:
            io = self._psutil.disk_io_counters()
            net = self._psutil.net_io_counters()
            usage_pct = self._usage_percent()
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
            usagePct=usage_pct,
            readMbPerSec=round(max(io.read_bytes - last_io.read_bytes, 0) / _MB / dt, 1),
            writeMbPerSec=round(max(io.write_bytes - last_io.write_bytes, 0) / _MB / dt, 1),
        )
        net_info = NetInfo(
            downloadMbPerSec=round(max(net.bytes_recv - last_net.bytes_recv, 0) / _MB / dt, 1),
            uploadMbPerSec=round(max(net.bytes_sent - last_net.bytes_sent, 0) / _MB / dt, 1),
        )
        return disk, net_info