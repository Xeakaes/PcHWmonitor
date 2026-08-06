import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from schema import StatusMessage, WelcomeMessage  # noqa: E402


def test_status_message_serializes_with_all_protocol_fields():
    from schema import CpuInfo, GpuInfo, PcInfo, RamInfo

    msg = StatusMessage(
        timestamp=1754150000,
        pc=PcInfo(name="DESKTOP-ABC", os="Windows 11", source="librehardwaremonitor"),
        cpu=CpuInfo(name="Intel Core i7-13700K", usagePct=34.5, tempC=61.2, clockMhz=5100.0, powerW=125.0, loads=[12.0, 45.0]),
        gpu=GpuInfo(name="RTX 4070", usagePct=78.3, tempC=71.4, hotspotC=84.1, vramUsedMb=6112.0,
                    vramTotalMb=12288.0, coreClockMhz=2745.0, memClockMhz=10500.0, powerW=182.0),
        ram=RamInfo(usedGb=11.2, totalGb=32.0, usagePct=35.0, clockMhz=3600.0),
    )
    data = json.loads(msg.model_dump_json())
    assert data["type"] == "status"
    assert data["timestamp"] == 1754150000
    assert data["available"] is True
    assert data["error"] is None
    assert data["pc"]["name"] == "DESKTOP-ABC"
    assert data["cpu"]["tempC"] == 61.2
    assert data["cpu"]["loads"] == [12.0, 45.0]
    assert data["gpu"]["hotspotC"] == 84.1
    assert data["ram"]["usedGb"] == 11.2


def test_status_message_keeps_missing_keys_as_null():
    data = json.loads(StatusMessage(timestamp=1).model_dump_json())
    for section in ("cpu", "gpu", "ram", "pc"):
        assert section in data, f"missing section {section}"
    assert data["cpu"] is None


def test_welcome_message_shape():
    data = json.loads(WelcomeMessage(intervalMs=1000, serverName="DESKTOP-ABC", source="librehardwaremonitor", pcName="DESKTOP-ABC").model_dump_json())
    assert data["type"] == "welcome"
    assert data["intervalMs"] == 1000
    assert data["source"] == "librehardwaremonitor"


def test_status_message_serializes_igpu_field():
    from schema import GpuInfo

    msg = StatusMessage(timestamp=1, igpu=GpuInfo(name="Intel UHD", usagePct=12.5, tempC=None))
    data = json.loads(msg.model_dump_json())
    assert data["igpu"] == {"name": "Intel UHD", "usagePct": 12.5, "tempC": None, "hotspotC": None,
                            "vramUsedMb": None, "vramTotalMb": None, "coreClockMhz": None,
                            "memClockMhz": None, "powerW": None}


def test_status_message_serializes_new_v13_fields():
    from schema import DiskInfo, FanInfo, FpsInfo, NetInfo

    msg = StatusMessage(
        timestamp=1754150000,
        disk=DiskInfo(usagePct=42.5, readMbPerSec=180.2, writeMbPerSec=64.1),
        net=NetInfo(downloadMbPerSec=12.4, uploadMbPerSec=3.2),
        fans=[FanInfo(label="cpu fan", rpm=1150.0), FanInfo(label="case fan", rpm=800.0)],
        fps=FpsInfo(name="game.exe", current=120.0, avg=117.3, onePercentLow=92.0),
    )
    data = json.loads(msg.model_dump_json())
    assert data["disk"] == {"usagePct": 42.5, "readMbPerSec": 180.2, "writeMbPerSec": 64.1}
    assert data["net"] == {"downloadMbPerSec": 12.4, "uploadMbPerSec": 3.2}
    assert data["fans"][1]["rpm"] == 800.0
    assert data["fps"]["onePercentLow"] == 92.0


def test_status_message_v13_sections_null_by_default():
    data = json.loads(StatusMessage(timestamp=1).model_dump_json())
    assert data["disk"] is None
    assert data["net"] is None
    assert data["fans"] is None
    assert data["fps"] is None
