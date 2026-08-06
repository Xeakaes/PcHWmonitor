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
    def __init__(self, io, net, usage):
        self.io = io
        self.net = net
        self.usage = usage

    def disk_io_counters(self):
        return self.io

    def net_io_counters(self):
        return self.net

    def disk_usage(self, path):
        return self.usage


def test_first_sample_returns_none_none():
    adapter = SystemAdapter(interval=1.0)
    disk, net = adapter.sample()
    assert disk is None and net is None


def test_second_sample_computes_mbps():
    fake = _FakePsutil(
        io=_FakeCounters(0, 0, 0, 0),
        net=_FakeCounters(0, 0, 0, 0),
        usage=type("U", (), {"percent": 40.0}),
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
