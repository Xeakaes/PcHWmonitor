import json
from pathlib import Path

import httpx

from adapters.lhm import LhmAdapter

FIXTURE = None


def _load_fixture() -> dict:
    global FIXTURE
    if FIXTURE is None:
        FIXTURE = json.loads((Path(__file__).resolve().parent / "fixtures" / "lhm_sample_v2.json").read_text())
    return FIXTURE


class _FakeTransport(httpx.BaseTransport):
    def __init__(self, payload: dict):
        self.payload = payload

    def handle_request(self, request):
        return httpx.Response(200, json=self.payload, request=request)


def _adapter(payload=None) -> LhmAdapter:
    client = httpx.Client(transport=_FakeTransport(payload), base_url="http://lhm")
    return LhmAdapter(lhm_url="http://lhm/data.json", http_client=client)


def test_lhm_v2_maps_real_device():
    msg = _adapter(_load_fixture()).fetch()

    assert msg.available is True
    assert msg.error is None

    assert msg.cpu is not None
    assert msg.cpu.name == "13th Gen Intel Core i7-13620H"
    assert msg.cpu.usagePct == 11.8
    assert msg.cpu.tempC == 71.0
    assert msg.cpu.clockMhz == 4729.8
    assert msg.cpu.powerW == 32.8
    assert msg.cpu.loads == [20.0, 0.0, 11.3, 0.0, 0.0, 40.0, 40.0, 0.0, 0.0, 27.7, 21.5, 0.0, 0.0, 15.6, 6.4, 6.4]

    assert msg.gpu is not None
    assert msg.gpu.name == "NVIDIA GeForce RTX 4060 Laptop GPU"
    assert msg.gpu.usagePct == 7.0
    assert msg.gpu.tempC == 55.0
    assert msg.gpu.hotspotC == 62.9
    assert msg.gpu.vramUsedMb == 1146.0
    assert msg.gpu.vramTotalMb == 8188.0
    assert msg.gpu.coreClockMhz == 1470.0
    assert msg.gpu.memClockMhz == 7001.0
    assert msg.gpu.powerW == 9.0
    assert msg.gpu.fps is None

    assert msg.ram is not None
    assert msg.ram.usedGb == 14.7
    assert msg.ram.totalGb == 15.7
    assert msg.ram.usagePct == 93.8
    assert msg.ram.clockMhz is None

    assert msg.pc is not None
    assert msg.pc.source == "librehardwaremonitor"


def test_lhm_v2_prefers_nvidia_over_integrated_gpu():
    msg = _adapter(_load_fixture()).fetch()
    assert msg.gpu.name == "NVIDIA GeForce RTX 4060 Laptop GPU"


def test_lhm_v2_uses_total_memory_not_virtual_memory():
    msg = _adapter(_load_fixture()).fetch()
    assert msg.ram.totalGb == 15.7
    assert msg.ram.usagePct == 93.8


def test_lhm_v2_parses_integrated_gpu():
    msg = _adapter(_load_fixture()).fetch()
    assert msg.igpu is not None
    assert msg.igpu.name == "Intel(R) UHD Graphics"
