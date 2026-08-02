import httpx

from schema import StatusMessage
from adapters.lhm import LhmAdapter

FIXTURE = None


def _load_fixture() -> dict:
    global FIXTURE
    if FIXTURE is None:
        import json
        from pathlib import Path

        FIXTURE = json.loads((Path(__file__).resolve().parent / "fixtures" / "lhm_sample.json").read_text())
    return FIXTURE


class _FakeTransport(httpx.BaseTransport):
    def __init__(self, payload: dict | None, error: bool = False):
        self.payload = payload
        self.error = error

    def handle_request(self, request):
        if self.error:
            raise httpx.ConnectError("connection refused")
        return httpx.Response(200, json=self.payload, request=request)


def _adapter(payload=None, error=False) -> LhmAdapter:
    client = httpx.Client(transport=_FakeTransport(payload, error), base_url="http://lhm")
    return LhmAdapter(lhm_url="http://lhm/data.json", http_client=client)


def test_lhm_maps_all_sensors():
    msg: StatusMessage = _adapter(_load_fixture()).fetch()

    assert msg.available is True
    assert msg.error is None
    assert msg.cpu is not None
    assert msg.cpu.name == "Intel Core i7-13700K"
    assert msg.cpu.tempC == 61.2
    assert msg.cpu.usagePct == 34.5
    assert msg.cpu.clockMhz == 5100.0
    assert msg.cpu.powerW == 125.0
    assert msg.cpu.loads == [12.3, 45.2, 33.1, 67.4]

    assert msg.gpu is not None
    assert msg.gpu.name == "NVIDIA GeForce RTX 4070"
    assert msg.gpu.tempC == 71.4
    assert msg.gpu.hotspotC == 84.1
    assert msg.gpu.usagePct == 78.3
    assert msg.gpu.vramUsedMb == 6112.0
    assert msg.gpu.vramTotalMb == 12288.0
    assert msg.gpu.coreClockMhz == 2745.0
    assert msg.gpu.memClockMhz == 10500.0
    assert msg.gpu.powerW == 182.0
    assert msg.gpu.fps is None

    assert msg.ram is not None
    assert msg.ram.usedGb == 11.2
    assert msg.ram.totalGb == 32.0
    assert msg.ram.usagePct == 35.0
    assert msg.ram.clockMhz == 3600.0

    assert msg.pc is not None
    assert msg.pc.name is not None
    assert msg.pc.source == "librehardwaremonitor"


def test_lhm_unavailable_on_transport_error():
    msg = _adapter(error=True).fetch()
    assert msg.available is False
    assert msg.error is not None
    assert msg.cpu is None and msg.gpu is None and msg.ram is None


def test_lhm_handles_missing_sensors_gracefully():
    payload = _load_fixture()
    cpu_node = payload["Children"][0]
    cpu_node["Children"] = [c for c in cpu_node["Children"] if c["Text"] != "CPU Package"]
    msg = _adapter(payload).fetch()
    assert msg.available is True
    assert msg.cpu is not None
    assert msg.cpu.tempC == 63.8
    assert msg.cpu.usagePct == 34.5
