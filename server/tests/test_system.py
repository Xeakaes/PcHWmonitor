import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from adapters.system import SystemAdapter  # noqa: E402


class _FakeCounters:
    def __init__(self, read_bytes, write_bytes, bytes_sent, bytes_recv):
        self.read_bytes = read_bytes
        self.write_bytes = write_bytes
        self.bytes_sent = bytes_sent
        self.bytes_recv = bytes_recv


class _FakePsutil:
    def __init__(self, io, net, partitions, usages):
        self.io = io
        self.net = net
        self.partitions = partitions
        self.usages = usages

    def disk_io_counters(self):
        return self.io

    def net_io_counters(self):
        return self.net

    def disk_partitions(self, all=False):
        return self.partitions

    def disk_usage(self, path):
        return self.usages[path]


def test_first_sample_returns_none_none():
    adapter = SystemAdapter(interval=1.0)
    disk, net = adapter.sample()
    assert disk is None and net is None


def test_second_sample_computes_mbps():
    fake = _FakePsutil(
        io=_FakeCounters(0, 0, 0, 0),
        net=_FakeCounters(0, 0, 0, 0),
        partitions=[type("P", (), {"mountpoint": "/", "fstype": "ext4"})()],
        usages={"/": type("U", (), {"total": 100.0, "used": 40.0, "percent": 40.0})},
    )
    adapter = SystemAdapter(interval=1.0, _psutil=fake)
    adapter.sample()
    fake.io = _FakeCounters(read_bytes=200 * 1024 * 1024, write_bytes=50 * 1024 * 1024,
                            bytes_sent=10 * 1024 * 1024, bytes_recv=30 * 1024 * 1024)
    fake.net = _FakeCounters(0, 0, 10 * 1024 * 1024, 30 * 1024 * 1024)
    disk, net = adapter.sample()
    assert disk is not None and net is not None
    assert abs(disk.readMbPerSec - 200.0) < 0.5
    assert abs(disk.writeMbPerSec - 50.0) < 0.5
    assert abs(disk.usagePct - 40.0) < 0.01
    assert abs(net.downloadMbPerSec - 30.0) < 0.5
    assert abs(net.uploadMbPerSec - 10.0) < 0.5


def test_usage_averages_all_partitions_weighted_by_capacity():
    fake = _FakePsutil(
        io=_FakeCounters(0, 0, 0, 0),
        net=_FakeCounters(0, 0, 0, 0),
        partitions=[
            type("P", (), {"mountpoint": "/", "fstype": "ext4"})(),
            type("P", (), {"mountpoint": "/data", "fstype": "ext4"})(),
        ],
        usages={
            "/": type("U", (), {"total": 100.0, "used": 50.0, "percent": 50.0}),
            "/data": type("U", (), {"total": 100.0, "used": 0.0, "percent": 0.0}),
        },
    )
    adapter = SystemAdapter(interval=1.0, _psutil=fake)
    adapter.sample()
    fake.io = _FakeCounters(0, 0, 0, 0)
    fake.net = _FakeCounters(0, 0, 0, 0)
    disk, net = adapter.sample()
    assert disk is not None
    assert abs(disk.usagePct - 25.0) < 0.01


def test_usage_skips_pseudo_filesystems():
    fake = _FakePsutil(
        io=_FakeCounters(0, 0, 0, 0),
        net=_FakeCounters(0, 0, 0, 0),
        partitions=[
            type("P", (), {"mountpoint": "/", "fstype": "ext4"})(),
            type("P", (), {"mountpoint": "/dev/shm", "fstype": "tmpfs"})(),
            type("P", (), {"mountpoint": "/snap/x", "fstype": "squashfs"})(),
        ],
        usages={
            "/": type("U", (), {"total": 100.0, "used": 50.0, "percent": 50.0}),
            "/dev/shm": type("U", (), {"total": 100.0, "used": 100.0, "percent": 100.0}),
            "/snap/x": type("U", (), {"total": 100.0, "used": 100.0, "percent": 100.0}),
        },
    )
    adapter = SystemAdapter(interval=1.0, _psutil=fake)
    adapter.sample()
    fake.io = _FakeCounters(0, 0, 0, 0)
    fake.net = _FakeCounters(0, 0, 0, 0)
    disk, net = adapter.sample()
    assert disk is not None
    assert abs(disk.usagePct - 50.0) < 0.01


def test_usage_none_when_all_partitions_fail():
    fake = _FakePsutil(
        io=_FakeCounters(0, 0, 0, 0),
        net=_FakeCounters(0, 0, 0, 0),
        partitions=[type("P", (), {"mountpoint": "/", "fstype": "ext4"})()],
        usages={},
    )
    adapter = SystemAdapter(interval=1.0, _psutil=fake)
    adapter.sample()
    fake.io = _FakeCounters(0, 0, 0, 0)
    fake.net = _FakeCounters(0, 0, 0, 0)
    disk, net = adapter.sample()
    assert disk is not None
    assert disk.usagePct is None


def test_psutil_error_yields_none_none():
    class _Boom:
        def disk_io_counters(self):
            raise RuntimeError("boom")

        def net_io_counters(self):
            raise RuntimeError("boom")

        def disk_usage(self, path):
            raise RuntimeError("boom")

    adapter = SystemAdapter(interval=1.0, _psutil=_Boom())
    disk, net = adapter.sample()
    assert disk is None and net is None
