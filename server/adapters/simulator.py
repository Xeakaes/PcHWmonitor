import math
import platform
import random
import time

from schema import CpuInfo, DiskInfo, FanInfo, FpsInfo, GpuInfo, NetInfo, PcInfo, RamInfo, StatusMessage

CPU_NAMES = ["Intel Core i7-13700K", "AMD Ryzen 7 7800X3D", "Intel Core i5-13600K"]
GPU_NAMES = ["NVIDIA GeForce RTX 4070", "NVIDIA GeForce RTX 3060 Ti", "AMD Radeon RX 7800 XT"]


class Simulator:
    def __init__(self, seed: int | None = None, base_temp: float = 40.0):
        self._rng = random.Random(seed)
        self._base_temp = base_temp
        self._start = time.time()
        self.pc_name = platform.node() or "SIM-PC"

    def sample(self) -> StatusMessage:
        t = time.time() - self._start
        r = self._rng

        cpu_usage = clamp(18 + 45 * (0.5 + 0.5 * math.sin(t / 40.0)) + r.uniform(-8, 8), 2, 100)
        gpu_usage = clamp(12 + 55 * (0.5 + 0.5 * math.sin(t / 55.0 + 1.3)) + r.uniform(-10, 10), 0, 100)
        ram_usage = clamp(38 + 18 * (0.5 + 0.5 * math.sin(t / 130.0)) + r.uniform(-2, 2), 4, 98)

        cpu_temp = clamp(self._base_temp + cpu_usage * 0.45 + r.uniform(-1.5, 1.5), 25, 100)
        gpu_temp = clamp(35 + gpu_usage * 0.42 + r.uniform(-1.5, 1.5), 25, 100)
        hotspot = clamp(gpu_temp + 10 + r.uniform(-1, 3), 30, 105)

        vram_total = 12288.0
        vram_used = vram_total * (0.25 + 0.6 * (gpu_usage / 100.0)) + r.uniform(-200, 200)
        vram_used = clamp(vram_used, 0, vram_total)

        cpu = CpuInfo(
            name=r.choice(CPU_NAMES),
            usagePct=round(cpu_usage, 1),
            tempC=round(cpu_temp, 1),
            clockMhz=round(clamp(800 + (cpu_usage / 100.0) * 4300 + r.uniform(-150, 150), 800, 5200), 0),
            powerW=round(clamp(12 + cpu_usage * 1.1 + r.uniform(-5, 5), 5, 260), 1),
            loads=[round(clamp(cpu_usage + r.uniform(-25, 25), 0, 100), 1) for _ in range(8)],
        )
        gpu = GpuInfo(
            name=r.choice(GPU_NAMES),
            usagePct=round(gpu_usage, 1),
            tempC=round(gpu_temp, 1),
            hotspotC=round(hotspot, 1),
            vramUsedMb=round(vram_used, 0),
            vramTotalMb=vram_total,
            coreClockMhz=round(clamp(400 + (gpu_usage / 100.0) * 2300 + r.uniform(-60, 60), 300, 2900), 0),
            memClockMhz=round(r.choice([10000.0, 10500.0, 11250.0, 14000.0]) + r.uniform(-30, 30), 0),
            powerW=round(clamp(15 + gpu_usage * 1.9 + r.uniform(-8, 8), 5, 220), 1),
        )
        igpu = GpuInfo(
            name="Intel UHD Graphics",
            usagePct=round(clamp(2 + cpu_usage * 0.35 + r.uniform(-4, 4), 0, 100), 1),
            tempC=None,
            coreClockMhz=round(clamp(300 + (cpu_usage / 100.0) * 1100 + r.uniform(-40, 40), 300, 1500), 0),
            powerW=round(clamp(0.2 + cpu_usage * 0.02 + r.uniform(-0.2, 0.2), 0, 15), 1),
        )
        ram = RamInfo(
            usedGb=round(ram_usage * 0.32, 1),
            totalGb=32.0,
            usagePct=round(ram_usage, 1),
            clockMhz=3600.0,
        )
        disk = DiskInfo(
            usagePct=round(clamp(20 + 40 * (0.5 + 0.5 * math.sin(t / 70.0)) + r.uniform(-5, 5), 2, 100), 1),
            readMbPerSec=round(clamp(80 + 160 * (0.5 + 0.5 * math.sin(t / 45.0)) + r.uniform(-20, 20), 0, 500), 1),
            writeMbPerSec=round(clamp(20 + 80 * (0.5 + 0.5 * math.sin(t / 60.0)) + r.uniform(-10, 10), 0, 300), 1),
        )
        net = NetInfo(
            downloadMbPerSec=round(clamp(4 + 40 * (0.5 + 0.5 * math.sin(t / 30.0)) + r.uniform(-5, 5), 0, 200), 1),
            uploadMbPerSec=round(clamp(1 + 10 * (0.5 + 0.5 * math.sin(t / 35.0)) + r.uniform(-2, 2), 0, 60), 1),
        )
        fans = [
            FanInfo(label="CPU Fan", rpm=round(clamp(900 + cpu_usage * 8 + r.uniform(-50, 50), 600, 2200), 0)),
            FanInfo(label="Case Fan", rpm=round(clamp(700 + cpu_usage * 4 + r.uniform(-40, 40), 500, 1600), 0)),
        ]
        fps = FpsInfo(
            name="game.exe",
            current=round(clamp(60 + 70 * (0.5 + 0.5 * math.sin(t / 25.0)) + r.uniform(-8, 8), 30, 240), 1),
            avg=round(clamp(85 + r.uniform(-5, 5), 30, 240), 1),
            onePercentLow=round(clamp(60 + r.uniform(-10, 10), 30, 200), 1),
        )
        pc = PcInfo(name=self.pc_name, os=platform.system(), source="simulator")
        return StatusMessage(
            timestamp=int(time.time()), pc=pc, cpu=cpu, gpu=gpu, igpu=igpu, ram=ram, disk=disk, net=net, fans=fans, fps=fps
        )


def clamp(v: float, lo: float, hi: float) -> float:
    return max(lo, min(hi, v))
