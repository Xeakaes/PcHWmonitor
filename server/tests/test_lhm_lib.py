import sys

from adapters.lhm_lib import _default_lib_dir, _hardware_map, _pick


class FakeType:
    def __init__(self, name):
        self._name = name

    def ToString(self):
        return self._name


class FakeHW:
    def __init__(self, name, type_name):
        self.Name = name
        self.HardwareType = FakeType(type_name)
        self.SubHardware = []

    def Update(self):
        pass


def test_hardware_map_groups_and_prefers_total_memory():
    nodes = [
        FakeHW("Virtual Memory", "Memory"),
        FakeHW("Total Memory", "Memory"),
        FakeHW("NVIDIA GeForce RTX 4060 Laptop GPU", "GpuNvidia"),
    ]
    map = _hardware_map(FakeComputer(nodes))
    assert [n.Name for n in map["Memory"]] == ["Virtual Memory", "Total Memory"]
    assert _pick(map["Memory"], "Total Memory").Name == "Total Memory"
    assert _pick(map["Memory"], "missing").Name == "Virtual Memory"
    assert _pick(map["GpuNvidia"], "").Name == "NVIDIA GeForce RTX 4060 Laptop GPU"
    assert _pick(map.get("Cpu", []), "") is None


class FakeComputer:
    def __init__(self, hardware):
        self.Hardware = hardware


def test_default_lib_dir_prefers_bundle_then_env(monkeypatch):
    monkeypatch.setattr(sys, "_MEIPASS", r"C:\bundle", raising=False)
    assert _default_lib_dir() == r"C:\bundle"
    monkeypatch.delattr(sys, "_MEIPASS", raising=False)
    monkeypatch.setenv("LHMDIR", r"C:\lhm")
    assert _default_lib_dir() == r"C:\lhm"
