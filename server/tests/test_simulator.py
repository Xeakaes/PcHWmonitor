import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from adapters.simulator import Simulator  # noqa: E402


def test_simulator_values_in_ranges_and_change():
    sim = Simulator(seed=42)
    a = sim.sample()
    b = sim.sample()

    assert a.available is True
    assert a.cpu is not None and a.gpu is not None and a.ram is not None
    assert 0 <= a.cpu.usagePct <= 100
    assert 25 <= a.cpu.tempC <= 100
    assert 800 <= a.cpu.clockMhz <= 5200
    assert a.gpu.hotspotC > a.gpu.tempC
    assert 0 <= a.gpu.vramUsedMb <= a.gpu.vramTotalMb
    assert a.ram.usedGb <= a.ram.totalGb
    assert a.fps is not None and a.fps.current is not None
    assert 30 <= a.fps.current <= 240
    assert a.disk is not None and 0 <= a.disk.usagePct <= 100
    assert a.net is not None and a.net.downloadMbPerSec >= 0
    assert a.fans is not None and len(a.fans) == 2

    values = [sim.sample().cpu.tempC for _ in range(20)]
    assert len(set(values)) > 5, "simulator must produce changing values"


def test_simulator_loads_length():
    sim = Simulator(seed=1)
    loads = sim.sample().cpu.loads
    assert loads is not None and len(loads) == 8
    assert all(0 <= x <= 100 for x in loads)


def test_simulator_igpu_values():
    msg = Simulator(seed=7).sample()
    assert msg.igpu is not None
    assert msg.igpu.name == "Intel UHD Graphics"
    assert 0 <= msg.igpu.usagePct <= 100
    assert msg.igpu.tempC is None
