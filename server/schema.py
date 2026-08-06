from pydantic import BaseModel


class PcInfo(BaseModel):
    name: str | None = None
    os: str | None = None
    source: str | None = None


class CpuInfo(BaseModel):
    name: str | None = None
    usagePct: float | None = None
    tempC: float | None = None
    clockMhz: float | None = None
    powerW: float | None = None
    loads: list[float] | None = None


class GpuInfo(BaseModel):
    name: str | None = None
    usagePct: float | None = None
    tempC: float | None = None
    hotspotC: float | None = None
    vramUsedMb: float | None = None
    vramTotalMb: float | None = None
    coreClockMhz: float | None = None
    memClockMhz: float | None = None
    powerW: float | None = None


class RamInfo(BaseModel):
    usedGb: float | None = None
    totalGb: float | None = None
    usagePct: float | None = None
    clockMhz: float | None = None


class FanInfo(BaseModel):
    label: str | None = None
    rpm: float | None = None


class DiskInfo(BaseModel):
    usagePct: float | None = None
    readMbPerSec: float | None = None
    writeMbPerSec: float | None = None


class NetInfo(BaseModel):
    downloadMbPerSec: float | None = None
    uploadMbPerSec: float | None = None


class FpsInfo(BaseModel):
    name: str | None = None
    current: float | None = None
    avg: float | None = None
    onePercentLow: float | None = None


class StatusMessage(BaseModel):
    type: str = "status"
    timestamp: int
    available: bool = True
    error: str | None = None
    pc: PcInfo | None = None
    cpu: CpuInfo | None = None
    gpu: GpuInfo | None = None
    igpu: GpuInfo | None = None
    ram: RamInfo | None = None
    disk: DiskInfo | None = None
    net: NetInfo | None = None
    fans: list[FanInfo] | None = None
    fps: FpsInfo | None = None


class WelcomeMessage(BaseModel):
    type: str = "welcome"
    intervalMs: int
    serverName: str
    source: str
    pcName: str | None = None
