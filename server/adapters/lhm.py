import platform
import re
import time

import httpx

from schema import CpuInfo, FanInfo, GpuInfo, PcInfo, RamInfo, StatusMessage

_NUMBER = re.compile(r"[-+]?\d+(?:[.,]\d+)?")


def _num(value) -> float | None:
    """Accept numeric values or localized strings like '11,8 %' / '4729,8 MHz'."""
    if isinstance(value, (int, float)):
        return float(value)
    if isinstance(value, str):
        match = _NUMBER.search(value)
        if match:
            return float(match.group().replace(",", "."))
    return None


def _hardware_nodes(root: dict) -> list[dict]:
    """Nodes identified by HardwareType (legacy LHM) or HardwareId (LHM >= 0.9.6)."""
    nodes = []

    def walk(node: dict) -> None:
        if node.get("HardwareId") is not None or node.get("HardwareType") is not None:
            nodes.append(node)
        for child in node.get("Children", []):
            walk(child)

    walk(root)
    return nodes


def _pick(nodes: list[dict], hardware_type: str, id_prefixes: tuple[str, ...]) -> dict | None:
    for node in nodes:
        if node.get("HardwareType") == hardware_type:
            return node
    for node in nodes:
        hardware_id = node.get("HardwareId") or ""
        if any(hardware_id.startswith(prefix) for prefix in id_prefixes):
            return node
    return None


def _pick_gpu(nodes: list[dict]) -> dict | None:
    for hardware_type, id_prefix in (("GpuNvidia", "/gpu-nvidia"), ("GpuAmd", "/gpu-amd")):
        node = _pick(nodes, hardware_type, (id_prefix,))
        if node is not None:
            return node
    return None


def _pick_igpu(nodes: list[dict]) -> dict | None:
    return _pick(nodes, "GpuIntel", ("/gpu-intel",))


def _sensors(node: dict) -> list[dict]:
    """Legacy: hardware children are sensors. New: children are groups containing sensors."""
    sensors = []
    for child in node.get("Children", []):
        if child.get("SensorType") is not None or child.get("Type") is not None:
            sensors.append(child)
        else:
            sensors.extend(child.get("Children", []))
    return sensors


def _sensor_type(sensor: dict) -> str | None:
    return sensor.get("SensorType") or sensor.get("Type")


def _find(sensors: list[dict], sensor_types: tuple[str, ...], name_part: str) -> float | None:
    for sensor in sensors:
        if _sensor_type(sensor) not in sensor_types:
            continue
        if name_part in str(sensor.get("Text", "")).lower():
            value = _num(sensor.get("Value"))
            if value is not None:
                return value
    return None


def _clock_max(sensors: list[dict]) -> float | None:
    """Fallback for CPUs whose max core clock sensor is missing (LHM >= 0.9.6)."""
    values = []
    for sensor in sensors:
        if _sensor_type(sensor) != "Clock":
            continue
        text = str(sensor.get("Text", "")).lower()
        if "p-core" in text or "e-core" in text:
            value = _num(sensor.get("Value"))
            if value is not None:
                values.append(value)
    return max(values) if values else None


def _loads(sensors: list[dict]) -> list[float] | None:
    per_core = []
    for sensor in sensors:
        if _sensor_type(sensor) != "Load":
            continue
        if "core #" in str(sensor.get("Text", "")).lower():
            value = _num(sensor.get("Value"))
            if value is not None:
                per_core.append(value)
    return per_core or None


class LhmAdapter:
    def __init__(self, lhm_url: str = "http://127.0.0.1:8085/data.json", http_client: httpx.Client | None = None):
        self._url = lhm_url
        self._client = http_client or httpx.Client(timeout=3.0)

    def fetch(self) -> StatusMessage:
        try:
            response = self._client.get(self._url)
            response.raise_for_status()
            root = response.json()
            return self._parse(root)
        except Exception as exc:
            return StatusMessage(timestamp=int(time.time()), available=False, error=str(exc))

    def _parse(self, root: dict) -> StatusMessage:
        nodes = _hardware_nodes(root)

        cpu_node = _pick(nodes, "Cpu", ("/intelcpu", "/amdcpu", "/cpu"))
        gpu_node = _pick_gpu(nodes)
        igpu_node = _pick_igpu(nodes)
        mem_node = _pick(nodes, "Memory", ("/ram",))

        cpu = self._parse_cpu(cpu_node) if cpu_node else None
        gpu = self._parse_gpu(gpu_node) if gpu_node else None
        igpu = self._parse_gpu(igpu_node) if igpu_node else None
        ram = self._parse_ram(mem_node) if mem_node else None

        os_name = platform.system()
        if os_name == "Windows":
            os_name = "Windows"
        elif os_name == "Darwin":
            os_name = "macOS"

        pc = PcInfo(name=platform.node(), os=os_name, source="librehardwaremonitor")
        fans = self._parse_fans(nodes)
        return StatusMessage(timestamp=int(time.time()), pc=pc, cpu=cpu, gpu=gpu, igpu=igpu, ram=ram, fans=fans)

    def _parse_cpu(self, node: dict) -> CpuInfo:
        sensors = _sensors(node)
        return CpuInfo(
            name=node.get("Text"),
            usagePct=_find(sensors, ("Load",), "cpu total"),
            tempC=_find(sensors, ("Temperature",), "cpu package") or _find(sensors, ("Temperature",), "core max"),
            clockMhz=_find(sensors, ("Clock",), "core max") or _clock_max(sensors),
            powerW=_find(sensors, ("Power",), "cpu package") or _find(sensors, ("Power",), "cpu total power"),
            loads=_loads(sensors),
        )

    def _parse_gpu(self, node: dict) -> GpuInfo:
        sensors = _sensors(node)
        return GpuInfo(
            name=node.get("Text"),
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
        )

    def _parse_ram(self, node: dict) -> RamInfo:
        sensors = _sensors(node)
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

    def _parse_fans(self, nodes: list[dict]) -> list[FanInfo] | None:
        fans: list[FanInfo] = []
        for node in nodes:
            for sensor in _sensors(node):
                if _sensor_type(sensor) != "Fan":
                    continue
                rpm = _num(sensor.get("Value"))
                if rpm is not None:
                    fans.append(FanInfo(label=sensor.get("Text"), rpm=round(rpm, 1)))
        return fans or None
