import os
import platform
import sys
import time
from pathlib import Path

from schema import CpuInfo, GpuInfo, PcInfo, RamInfo, StatusMessage
from adapters.lhm import _clock_max, _find, _loads


def _default_lib_dir() -> str:
    meipass = getattr(sys, "_MEIPASS", None)
    if meipass:
        return meipass
    if os.environ.get("LHMDIR"):
        return os.environ["LHMDIR"]
    return str(Path(__file__).resolve().parent.parent)


# -- pythonnet + LibreHardwareMonitorLib in-process reading --

def _sensor_dicts(hardware) -> list[dict]:
    result = []
    for sensor in hardware.Sensors:
        result.append({
            "Text": sensor.Name,
            "Type": sensor.SensorType.ToString(),
            "Value": sensor.Value,
        })
    return result


def _hardware_map(computer) -> dict:
    found = {}
    for hw in computer.Hardware:
        hw.Update()
        found.setdefault(hw.HardwareType.ToString(), []).append(hw)
        for sub in hw.SubHardware:
            sub.Update()
            found.setdefault(sub.HardwareType.ToString(), []).append(sub)
    return found


def _pick(nodes: list, name: str):
    for node in nodes:
        if node.Name == name:
            return node
    return nodes[0] if nodes else None


class LhmLibAdapter:
    def __init__(self, lib_dir: str | None = None):
        self._lib_dir = lib_dir
        self._computer = None
        self._load()

    # -- assembly load + computer setup --

    def _load(self) -> None:
        import clr  # pythonnet; lazy import keeps this module importable on Linux
        lib_dir = self._lib_dir or _default_lib_dir()
        clr.AddReference(str(Path(lib_dir) / "LibreHardwareMonitorLib.dll"))
        from LibreHardwareMonitor.Hardware import Computer

        computer = Computer()
        computer.IsCpuEnabled = True
        computer.IsGpuEnabled = True
        computer.IsMemoryEnabled = True
        computer.Open()
        self._computer = computer

    def close(self) -> None:
        if self._computer is not None:
            try:
                self._computer.Close()
            except Exception:
                pass

    def fetch(self) -> StatusMessage:
        try:
            hw = _hardware_map(self._computer)
            cpu_node = _pick(hw.get("Cpu", []), "CPU")
            gpu_node = _pick(hw.get("GpuNvidia", []), "") or _pick(hw.get("GpuAmd", []), "")
            igpu_node = _pick(hw.get("GpuIntel", []), "")
            mem_node = _pick(hw.get("Memory", []), "Total Memory")

            cpu = self._parse_cpu(cpu_node) if cpu_node else None
            gpu = self._parse_gpu(gpu_node) if gpu_node else None
            igpu = self._parse_gpu(igpu_node) if igpu_node else None
            ram = self._parse_ram(mem_node) if mem_node else None

            pc = PcInfo(name=platform.node(), os="Windows", source="lhm-lib")
            return StatusMessage(timestamp=int(time.time()), pc=pc, cpu=cpu, gpu=gpu, igpu=igpu, ram=ram)
        except Exception as exc:
            return StatusMessage(timestamp=int(time.time()), available=False, error=str(exc))

    # -- sensor mapping (shared helpers from adapters.lhm) --

    def _parse_cpu(self, node) -> CpuInfo:
        sensors = _sensor_dicts(node)
        return CpuInfo(
            name=node.Name,
            usagePct=_find(sensors, ("Load",), "cpu total"),
            tempC=_find(sensors, ("Temperature",), "cpu package") or _find(sensors, ("Temperature",), "core max"),
            clockMhz=_find(sensors, ("Clock",), "core max") or _clock_max(sensors),
            powerW=_find(sensors, ("Power",), "cpu package") or _find(sensors, ("Power",), "cpu total power"),
            loads=_loads(sensors),
        )

    def _parse_gpu(self, node) -> GpuInfo:
        sensors = _sensor_dicts(node)
        return GpuInfo(
            name=node.Name,
            usagePct=_find(sensors, ("Load",), "gpu core"),
            tempC=_find(sensors, ("Temperature",), "gpu core"),
            hotspotC=_find(sensors, ("Temperature",), "gpu hot spot"),
            vramUsedMb=_find(sensors, ("SmallData",), "gpu memory used"),
            vramTotalMb=_find(sensors, ("SmallData",), "gpu memory total"),
            coreClockMhz=_find(sensors, ("Clock",), "gpu core"),
            memClockMhz=_find(sensors, ("Clock",), "gpu memory"),
            powerW=(
                _find(sensors, ("Power",), "gpu total power")
                or _find(sensors, ("Power",), "gpu power")
                or _find(sensors, ("Power",), "gpu package")
            ),
            fps=None,
        )

    def _parse_ram(self, node) -> RamInfo:
        sensors = _sensor_dicts(node)
        used = _find(sensors, ("Data",), "memory used")
        total = _find(sensors, ("Data",), "memory total")
        if total is None:
            available = _find(sensors, ("Data",), "memory available")
            if used is not None and available is not None:
                total = used + available
        return RamInfo(
            usedGb=used,
            totalGb=total,
            usagePct=_find(sensors, ("Load",), "memory utilization") or _find(sensors, ("Load",), "memory"),
            clockMhz=_find(sensors, ("Clock",), "memory clock"),
        )
