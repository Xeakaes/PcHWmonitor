# v1.3 Metrics & FPS Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add disk/network/fan metrics and real FPS (PresentMon, embedded) to the PC HW Monitor server and app, plus per-card min/avg/max and a configurable chart window.

**Architecture:** Server (Python/FastAPI) gains three new adapters — `system.py` (psutil disk/net), fan parsing in `lhm.py`, and `fps.py` (embedded PresentMon CLI subprocess) — and broadcasts them as nullable fields in `StatusMessage`. The app (Kotlin/Compose) mirrors the new payload in `SystemStatus`, parses it, and renders four new cards (Disk, Net, Fan, FPS) that only appear when data exists. All new fields are nullable; any missing source degrades to `None` and hides the card.

**Tech Stack:** Python 3 (FastAPI, uvicorn, httpx, pydantic v2, psutil, pytest) · Kotlin (Compose, kotlinx.serialization, DataStore, JUnit4) · PresentMon CLI (embedded via PyInstaller data).

## Global Constraints

- Server: Python 3.11+, pydantic v2 (`BaseModel`), tests under `server/tests/` using the existing `sys.path.insert(0, parent.parent)` preamble.
- App: kotlinx.serialization with `ignoreUnknownKeys = true`; all new model fields nullable with defaults.
- Every new protocol field must be nullable; a missing/unavailable source yields `None` and hides the related card. The server must never crash from adapter failures.
- FPS window on the server is fixed at 30s; `current` = mean over ~2s; `avg` = mean over 30s; `onePercentLow` = inverse of the 99th percentile of `msBetweenPresents` over 30s.
- PresentMon is embedded: no user-facing download. Binary is NOT committed to git; it is placed in `server/presentmon/` at build time and bundled by `build_exe.bat`.
- `GpuInfo.fps` is removed (never populated); FPS lives in the new `FpsInfo` block.
- Card order: CPU → GPU → FPS → RAM → Disk → Net → Fan.
- Commit after every passing step. Commits on `main`, message style `feat:` / `test:` / `refactor:` as in the repo history.

---

### Task 1: Server schema — Disk/Net/Fan/FPS models

**Files:**
- Modify: `server/schema.py`
- Test: `server/tests/test_schema.py`

**Interfaces:**
- Produces: `DiskInfo`, `NetInfo`, `FanInfo`, `FpsInfo` pydantic models and new `StatusMessage` fields `disk: DiskInfo | None = None`, `net: NetInfo | None = None`, `fans: list[FanInfo] | None = None`, `fps: FpsInfo | None = None`. `GpuInfo.fps` removed. Later tasks construct these and read them via attribute access.

- [ ] **Step 1: Write the failing test**

Append to `server/tests/test_schema.py`:

```python
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
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd server && python -m pytest tests/test_schema.py -v`
Expected: FAIL — `ImportError: cannot import name 'DiskInfo'`

- [ ] **Step 3: Implement schema changes**

In `server/schema.py`, remove `fps: float | None = None` from `GpuInfo` (line 29) and add after `RamInfo`:

```python
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
```

In `StatusMessage`, add after `ram`:

```python
    disk: DiskInfo | None = None
    net: NetInfo | None = None
    fans: list[FanInfo] | None = None
    fps: FpsInfo | None = None
```

- [ ] **Step 4: Fix the two existing tests broken by `GpuInfo.fps` removal**

In `server/tests/test_schema.py`:
- `test_status_message_serializes_with_all_protocol_fields`: remove `fps=None` from the `GpuInfo(...)` call and drop `assert data["gpu"]["fps"] is None`.
- `test_status_message_serializes_igpu_field`: expected dict drops `"fps": None`.

- [ ] **Step 5: Run full server test suite**

Run: `cd server && python -m pytest tests/ -v`
Expected: PASS (all files, including the new tests)

- [ ] **Step 6: Commit**

```bash
git add server/schema.py server/tests/test_schema.py
git commit -m "feat(server): add disk/net/fan/fps models to schema"
```

---

### Task 2: psutil adapter for disk and network

**Files:**
- Create: `server/adapters/system.py`
- Create: `server/tests/test_system.py`
- Modify: `server/requirements.txt` (add `psutil`)

**Interfaces:**
- Consumes: `schema.DiskInfo`, `schema.NetInfo` (from Task 1).
- Produces: `class SystemAdapter` with `__init__(self, interval: float = 1.0)` and `sample(self) -> tuple[DiskInfo | None, NetInfo | None]`. First call returns `None` (no delta yet); subsequent calls return deltas in MB/s. On non-Windows platforms or psutil errors, returns `(None, None)`.

- [ ] **Step 1: Write the failing test**

Create `server/tests/test_system.py`:

```python
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
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd server && python -m pytest tests/test_system.py -v`
Expected: FAIL — `ModuleNotFoundError: No module named 'adapters.system'`

- [ ] **Step 3: Implement `SystemAdapter`**

Create `server/adapters/system.py`:

```python
import time

from schema import DiskInfo, NetInfo

_MB = 1024.0 * 1024.0


class SystemAdapter:
    def __init__(self, interval: float = 1.0, _psutil=None):
        self._interval = interval
        if _psutil is None:
            import psutil as _psutil
        self._psutil = _psutil
        self._last = None
        self._last_time = None

    def sample(self) -> tuple[DiskInfo | None, NetInfo | None]:
        try:
            io = self._psutil.disk_io_counters()
            net = self._psutil.net_io_counters()
            usage = self._psutil.disk_usage("/")
        except Exception:
            return None, None

        now = time.monotonic()
        if self._last is None or self._last_time is None:
            self._last = (io, net)
            self._last_time = now
            return None, None

        dt = max(now - self._last_time, 0.05)
        last_io, last_net = self._last
        self._last = (io, net)
        self._last_time = now

        disk = DiskInfo(
            usagePct=round(float(usage.percent), 1),
            readMbPerSec=round(max(io.read_bytes - last_io.read_bytes, 0) / _MB / dt, 1),
            writeMbPerSec=round(max(io.write_bytes - last_io.write_bytes, 0) / _MB / dt, 1),
        )
        net_info = NetInfo(
            downloadMbPerSec=round(max(net.bytes_recv - last_net.bytes_recv, 0) / _MB / dt, 1),
            uploadMbPerSec=round(max(net.bytes_sent - last_net.bytes_sent, 0) / _MB / dt, 1),
        )
        return disk, net_info
```

- [ ] **Step 4: Add psutil dependency**

In `server/requirements.txt` add a line `psutil>=5.9`.

- [ ] **Step 5: Run test to verify it passes**

Run: `cd server && python -m pytest tests/test_system.py -v`
Expected: PASS (3 tests)

- [ ] **Step 6: Commit**

```bash
git add server/adapters/system.py server/tests/test_system.py server/requirements.txt
git commit -m "feat(server): add psutil-based disk/network adapter"
```

---

### Task 3: Fan sensors from LibreHardwareMonitor

**Files:**
- Modify: `server/adapters/lhm.py`
- Test: `server/tests/test_lhm.py`

**Interfaces:**
- Consumes: `schema.FanInfo` (Task 1).
- Produces: `LhmAdapter._parse_fans(nodes: list[dict]) -> list[FanInfo] | None` — walks all hardware nodes, collects every sensor with `SensorType == "Fan"` (or `Type == "Fan"`), one `FanInfo` per sensor: `label` = sensor `Text`, `rpm` = parsed `Value`. Returns `None` when no fan sensors exist. Called from `_parse`; `StatusMessage.fans` gets the result.

- [ ] **Step 1: Write the failing test**

Append to `server/tests/test_lhm.py` (a `_parse` root fixture that includes a fan-bearing node; reuse the existing LHM-style JSON root shape from that file):

```python
def test_parse_collects_fan_sensors():
    from schema import FanInfo

    adapter = LhmAdapter(lhm_url="http://x", http_client=object())
    root = {
        "Children": [
            {
                "HardwareId": "/mainboard",
                "HardwareType": "Mainboard",
                "Text": "B450 AORUS",
                "Children": [
                    {
                        "SensorType": "Fan",
                        "Text": "CPU Fan",
                        "Value": 1120.0,
                    },
                    {
                        "SensorType": "Fan",
                        "Text": "Case Fan",
                        "Value": "750 rpm",
                    },
                    {"SensorType": "Temperature", "Text": "System", "Value": 40.0},
                ],
            }
        ]
    }
    fans = adapter._parse_fans(_hardware_nodes(root))
    assert fans is not None
    assert len(fans) == 2
    assert fans[0] == FanInfo(label="CPU Fan", rpm=1120.0)
    assert fans[1] == FanInfo(label="Case Fan", rpm=750.0)


def test_parse_no_fans_returns_none():
    adapter = LhmAdapter(lhm_url="http://x", http_client=object())
    root = {"Children": [{"HardwareId": "/cpu", "Children": []}]}
    assert adapter._parse_fans(_hardware_nodes(root)) is None
```

Check the top of `server/tests/test_lhm.py` for the existing imports (`LhmAdapter`, `_hardware_nodes`, `_num`) and use them as-is; the file already imports these helpers.

- [ ] **Step 2: Run test to verify it fails**

Run: `cd server && python -m pytest tests/test_lhm.py -v`
Expected: FAIL — `AttributeError: 'LhmAdapter' object has no attribute '_parse_fans'`

- [ ] **Step 3: Implement fan parsing**

In `server/adapters/lhm.py`:

```python
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
```

Add `FanInfo` to the `from schema import ...` line. In `_parse`, build fans from all nodes and pass them into the returned `StatusMessage`:

```python
        fans = self._parse_fans(nodes)
        return StatusMessage(timestamp=int(time.time()), pc=pc, cpu=cpu, gpu=gpu, igpu=igpu, ram=ram, fans=fans)
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd server && python -m pytest tests/test_lhm.py tests/test_lhm_v2.py tests/test_lhm_lib.py -v`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add server/adapters/lhm.py server/tests/test_lhm.py
git commit -m "feat(server): collect fan sensors from LibreHardwareMonitor"
```

---

### Task 4: PresentMon FPS adapter

**Files:**
- Create: `server/adapters/fps.py`
- Create: `server/tests/test_fps.py`

**Interfaces:**
- Consumes: `schema.FpsInfo` (Task 1).
- Produces:
  - `parse_csv_line(header: list[str], line: str) -> tuple[str, float] | None` — returns `(application, msBetweenPresents)`; `None` on garbage. Looks up column indexes from `header`; uses the `Application` and `msBetweenPresents` columns; skips non-numeric frame times.
  - `compute_fps(entries: list[tuple[str, float]], now: float, interval_s: float = 2.0, window_s: float = 30.0) -> FpsInfo | None` — pure function: `current` = 1000/mean of `msBetweenPresents` over the last `interval_s` worth of entries (time not available per-entry; entries are treated as ordered, most recent last, with the last `window_s/interval_s` count as the window); `avg` = 1000/mean over the whole entry list (capped to 30s worth); `onePercentLow` = 1000/99th-percentile of `msBetweenPresents`. Returns `None` if fewer than 2 entries.
  - `PresentMonFps` class: `__init__(self, exe_path: str, process_name: str | None = None, interval_s: float = 2.0, window_s: float = 30.0)`; `start()` spawns the subprocess with `[exe, "--output_stdout"]` (+ `--process_name NAME` when given) and starts a reader thread appending parsed lines to a `collections.deque(maxlen=1800)`; `sample() -> FpsInfo | None` computes from the deque via `compute_fps`; `stop()` terminates the process and joins the thread.

- [ ] **Step 1: Write the failing test**

Create `server/tests/test_fps.py`:

```python
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from adapters.fps import compute_fps, parse_csv_line  # noqa: E402


HEADER = "Application,ProcessID,SwapChainAddress,Runtime,msBetweenPresents,PresentMode,TimeInSeconds"


def test_parse_csv_line_extracts_application_and_frame_time():
    line = "game.exe,1234,0x1,DXGI,16.7,Hardware: Independent Flip,1.000"
    app, frame_ms = parse_csv_line(HEADER.split(","), line)
    assert app == "game.exe"
    assert abs(frame_ms - 16.7) < 1e-9


def test_parse_csv_line_rejects_garbage():
    assert parse_csv_line(HEADER.split(","), "not a csv line") is None
    assert parse_csv_line(HEADER.split(","), "a,b,c") is None
    assert parse_csv_line(HEADER.split(","), "game.exe,1,2,3,abc,4,5") is None


def test_parse_csv_line_missing_column_returns_none():
    header = "Application,ProcessID,PresentMode"
    assert parse_csv_line(header.split(","), "game.exe,1234,Flip") is None


def test_compute_fps_returns_none_for_few_entries():
    assert compute_fps([("game.exe", 16.7)], time.time()) is None


def test_compute_fps_math():
    now = time.time()
    # 60 fps for the whole window: 16.6667 ms, but one slow frame of 200 ms
    entries = [("game.exe", 16.7)] * 60 + [("game.exe", 200.0)] + [("game.exe", 16.7)] * 30
    info = compute_fps(entries, now)
    assert info is not None
    assert info.name == "game.exe"
    assert 55 <= info.current <= 62          # mean ~16.7-17 ms window
    assert 55 <= info.avg <= 62              # mean over 91 entries ~ 18.6 ms
    assert info.onePercentLow < info.avg     # the 200 ms outlier drags p99 down
    assert info.onePercentLow >= 4.0         # 1000/200 = 5 fps floor
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd server && python -m pytest tests/test_fps.py -v`
Expected: FAIL — `ModuleNotFoundError: No module named 'adapters.fps'`

- [ ] **Step 3: Implement the pure functions**

Create `server/adapters/fps.py`:

```python
import math
import subprocess
import threading
import time
from collections import deque

from schema import FpsInfo


def parse_csv_line(header: list[str], line: str) -> tuple[str, float] | None:
    try:
        idx_app = header.index("Application")
        idx_ms = header.index("msBetweenPresents")
    except ValueError:
        return None
    parts = line.split(",")
    if len(parts) <= max(idx_app, idx_ms):
        return None
    try:
        ms = float(parts[idx_ms])
    except ValueError:
        return None
    if not math.isfinite(ms) or ms <= 0:
        return None
    return parts[idx_app], ms


def _percentile(values: list[float], pct: float) -> float:
    ordered = sorted(values)
    idx = min(len(ordered) - 1, int(math.ceil(pct / 100.0 * len(ordered))) - 1)
    return ordered[idx]


def compute_fps(entries: list[tuple[str, float]], now: float, interval_s: float = 2.0, window_s: float = 30.0) -> FpsInfo | None:
    if len(entries) < 2:
        return None
    app = entries[-1][0]
    frames = [ms for _, ms in entries]
    recent = frames[-max(2, int(interval_s / window_s * len(frames))):]
    current = 1000.0 / (sum(recent) / len(recent))
    avg = 1000.0 / (sum(frames) / len(frames))
    p99 = _percentile(frames, 99.0)
    return FpsInfo(name=app, current=round(current, 1), avg=round(avg, 1), onePercentLow=round(1000.0 / p99, 1))
```

Note: `_percentile` returns the raw frame time; `onePercentLow` converts it back to FPS.

- [ ] **Step 4: Run the pure-function tests**

Run: `cd server && python -m pytest tests/test_fps.py -v`
Expected: PASS

- [ ] **Step 5: Implement `PresentMonFps` process wrapper**

Append to `server/adapters/fps.py`:

```python
class PresentMonFps:
    def __init__(self, exe_path: str, process_name: str | None = None, interval_s: float = 2.0, window_s: float = 30.0):
        self._exe_path = exe_path
        self._process_name = process_name
        self._interval_s = interval_s
        self._window_s = window_s
        self._entries: deque[tuple[str, float]] = deque(maxlen=1800)
        self._proc: subprocess.Popen | None = None
        self._thread: threading.Thread | None = None
        self._stop = threading.Event()

    def start(self) -> bool:
        cmd = [self._exe_path, "--output_stdout"]
        if self._process_name:
            cmd += ["--process_name", self._process_name]
        try:
            self._proc = subprocess.Popen(cmd, stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, text=True)
        except OSError:
            self._proc = None
            return False
        self._thread = threading.Thread(target=self._read, daemon=True)
        self._thread.start()
        return True

    def _read(self) -> None:
        assert self._proc is not None and self._proc.stdout is not None
        header: list[str] | None = None
        for raw in self._proc.stdout:
            line = raw.rstrip("\n")
            if header is None:
                if "msBetweenPresents" in line:
                    header = line.split(",")
                continue
            parsed = parse_csv_line(header, line)
            if parsed is not None:
                self._entries.append(parsed)
        self._stop.set()

    def sample(self) -> FpsInfo | None:
        if not self._entries:
            return None
        return compute_fps(list(self._entries), time.time(), self._interval_s, self._window_s)

    def stop(self) -> None:
        if self._proc is not None:
            self._proc.terminate()
            try:
                self._proc.wait(timeout=3)
            except Exception:
                self._proc.kill()
            self._proc = None
        if self._thread is not None:
            self._thread.join(timeout=3)
            self._thread = None
        self._stop.set()
```

- [ ] **Step 6: Run the full test file**

Run: `cd server && python -m pytest tests/test_fps.py -v`
Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add server/adapters/fps.py server/tests/test_fps.py
git commit -m "feat(server): add PresentMon-based FPS adapter"
```

---

### Task 5: Simulator generates new v1.3 fields

**Files:**
- Modify: `server/adapters/simulator.py`
- Test: `server/tests/test_simulator.py`

**Interfaces:**
- Consumes: `DiskInfo`, `NetInfo`, `FanInfo`, `FpsInfo` (Task 1).
- Produces: `Simulator.sample()` now always returns non-null `disk`, `net`, `fans` (2 fans), `fps` fields with plausible varying values.

- [ ] **Step 1: Update the simulator test**

In `server/tests/test_simulator.py`, replace `assert a.gpu.fps is None` (line 22) with:

```python
    assert a.fps is not None and a.fps.current is not None
    assert 30 <= a.fps.current <= 240
    assert a.disk is not None and 0 <= a.disk.usagePct <= 100
    assert a.net is not None and a.net.downloadMbPerSec >= 0
    assert a.fans is not None and len(a.fans) == 2
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd server && python -m pytest tests/test_simulator.py -v`
Expected: FAIL — `AttributeError: 'StatusMessage' object has no attribute 'fps'`

- [ ] **Step 3: Implement simulator fields**

In `server/adapters/simulator.py`:

```python
import math
import platform
import random
import time

from schema import CpuInfo, DiskInfo, FanInfo, FpsInfo, GpuInfo, NetInfo, PcInfo, RamInfo, StatusMessage
```

Remove `fps=None,` from the `GpuInfo(...)` construction. Add before the `pc` construction:

```python
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
```

Update the `StatusMessage(...)` return to include `disk=disk, net=net, fans=fans, fps=fps`.

- [ ] **Step 4: Run test to verify it passes**

Run: `cd server && python -m pytest tests/test_simulator.py -v`
Expected: PASS

- [ ] **Step 5: Run the whole server suite**

Run: `cd server && python -m pytest tests/ -v`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add server/adapters/simulator.py server/tests/test_simulator.py
git commit -m "feat(server): simulator generates disk/net/fan/fps samples"
```

---

### Task 6: Wire adapters into the server + bundle PresentMon

**Files:**
- Modify: `server/main.py`
- Modify: `server/tests/test_main.py`
- Modify: `build_exe.bat`

**Interfaces:**
- Consumes: `SystemAdapter.sample()` (Task 2), `LhmAdapter._parse_fans` (Task 3), `PresentMonFps` (Task 4).
- Produces: `build_app(...)` gains `fps_process: str | None = None`; the app gets a composite `sample` that merges adapter outputs; `main.py` exposes `--fps-process`; the packaged exe ships `PresentMon64.exe` in its root.

- [ ] **Step 1: Write the failing tests**

Append to `server/tests/test_main.py`:

```python
def test_build_app_accepts_fps_process_arg():
    app = build_app(simulate=True, fps_process="game.exe")
    assert app.state.fps_process == "game.exe"
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd server && python -m pytest tests/test_main.py -v`
Expected: FAIL — `TypeError: build_app() got an unexpected keyword argument 'fps_process'`

- [ ] **Step 3: Implement the wiring**

In `server/main.py`:

1. Import the new adapters:
```python
from adapters.fps import PresentMonFps
from adapters.system import SystemAdapter
```

2. Add `fps_process: str | None = None` to `build_app`'s keyword-only params.

3. After the source selection block, build the composite sampler. In simulate mode use the simulator; otherwise instantiate `SystemAdapter()` and `PresentMonFps(exe_path=..., process_name=fps_process or None)` where `exe_path` resolves:

```python
    if simulate:
        sim = Simulator()
        system_adapter = None
        fps_adapter = None
        base_sample = sim.sample
    else:
        system_adapter = SystemAdapter()
        exe_path = _presentmon_path()
        fps_adapter = PresentMonFps(exe_path=exe_path, process_name=fps_process or None)
        if not fps_adapter.start():
            fps_adapter = None
            logger.warning("PresentMon unavailable; FPS disabled")
        base_sample = sample

    def composite() -> StatusMessage:
        message = base_sample()
        if system_adapter is not None:
            disk, net = system_adapter.sample()
            message.disk = disk if message.disk is None else message.disk
            message.net = net if message.net is None else message.net
        if fps_adapter is not None:
            message.fps = fps_adapter.sample()
        return message

    hub = Hub(sample=composite, interval_ms=interval_ms)
```

4. Add the helper (module-level, near `_lib_available`):

```python
def _presentmon_path() -> str:
    import sys
    if getattr(sys, "frozen", False):
        base = Path(sys._MEIPASS)
    else:
        base = Path(__file__).resolve().parent
    candidate = base / "PresentMon64.exe"
    if not candidate.exists():
        candidate = base / "presentmon" / "PresentMon64.exe"
    return str(candidate)
```

5. Store the flag on the app for tests: `app.state.fps_process = fps_process` (also `app.state.fps_active = fps_adapter is not None`).

6. In `main()`, add the argument:

```python
    parser.add_argument("--fps-process", default=None, help="process name to measure FPS for (empty = auto)")
```

and pass `fps_process=args.fps_process` to `build_app`.

- [ ] **Step 4: Run the tests**

Run: `cd server && python -m pytest tests/ -v`
Expected: PASS (existing + new)

- [ ] **Step 5: Bundle PresentMon into the exe build**

Modify `build_exe.bat` — copy the file before packaging and add it as data (keep the existing `--add-data "%VENDOR%;."` line):

```bat
if not exist "%PRESENTMON%" goto :no_presentmon
copy /y "%PRESENTMON%" "%TMP%\PresentMon64.exe" >nul
```

Add `--add-data "%PRESENTMON%;."` to the PyInstaller line and add a `:no_presentmon` label that warns but continues (FPS simply disabled at runtime). Create `server/presentmon/README.md` with: download URL of the official PresentMon release, the expected filename `PresentMon64.exe`, a note that the binary is intentionally NOT committed to git, and the license verification step.

- [ ] **Step 6: Commit**

```bash
git add server/main.py server/tests/test_main.py build_exe.bat server/presentmon/README.md
git commit -m "feat(server): wire fps/disk/net adapters, add --fps-process"
```

---

### Task 7: Android models and parser

**Files:**
- Modify: `app/src/main/java/com/Obscrum/pchwmonitor/domain/model/SystemStatus.kt`
- Test: `app/src/test/java/com/Obscrum/pchwmonitor/StatusParserTest.kt`

**Interfaces:**
- Consumes: JSON contract from Task 1 (camelCase keys, nullable fields).
- Produces: `@Serializable data class FanInfo(label: String? = null, rpm: Float? = null)`, `DiskInfo(usagePct, readMbPerSec, writeMbPerSec)`, `NetInfo(downloadMbPerSec, uploadMbPerSec)`, `FpsInfo(name, current, avg, onePercentLow)`; `SystemStatus` gains `disk: DiskInfo? = null`, `net: NetInfo? = null`, `fans: List<FanInfo>? = null`, `fps: FpsInfo? = null`. `GpuInfo.fps` removed from the Kotlin model. Later tasks read `status.fps`, `status.disk`, `status.net`, `status.fans`.

- [ ] **Step 1: Write the failing test**

Append to `app/src/test/java/com/Obscrum/pchwmonitor/StatusParserTest.kt`:

```kotlin
@Test
fun parsesV13MetricsAndFps() {
    val raw = """
        {"type":"status","timestamp":1754150000,
         "disk":{"usagePct":42.5,"readMbPerSec":180.2,"writeMbPerSec":64.1},
         "net":{"downloadMbPerSec":12.4,"uploadMbPerSec":3.2},
         "fans":[{"label":"CPU Fan","rpm":1150.0},{"label":"Case Fan","rpm":800.0}],
         "fps":{"name":"game.exe","current":120.0,"avg":117.3,"onePercentLow":92.0}}
    """.trimIndent()
    val status = (StatusParser.parse(raw) as WsMessage.Status).status
    assertEquals(42.5f, status.disk?.usagePct!!, 0.001f)
    assertEquals(64.1f, status.disk?.writeMbPerSec!!, 0.001f)
    assertEquals(12.4f, status.net?.downloadMbPerSec!!, 0.001f)
    assertEquals(listOf("CPU Fan", "Case Fan"), status.fans?.map { it.label })
    assertEquals(1150.0f, status.fans?.get(0)?.rpm!!, 0.001f)
    assertEquals("game.exe", status.fps?.name)
    assertEquals(92.0f, status.fps?.onePercentLow!!, 0.001f)
}

@Test
fun v13SectionsAbsentAreNull() {
    val raw = """{"type":"status","timestamp":1}"""
    val status = (StatusParser.parse(raw) as WsMessage.Status).status
    assertNull(status.disk)
    assertNull(status.net)
    assertNull(status.fans)
    assertNull(status.fps)
}
```

- [ ] **Step 2: Run test to verify it fails**

Run (from project root): `./gradlew testDebugUnitTest --tests "com.Obscrum.pchwmonitor.StatusParserTest"`
Expected: FAIL — compilation error `unresolved reference: disk`

- [ ] **Step 3: Implement model changes**

In `SystemStatus.kt`: remove `val fps: Float? = null,` from `GpuInfo`; add after `RamInfo`:

```kotlin
@Serializable
data class FanInfo(
    val label: String? = null,
    val rpm: Float? = null,
)

@Serializable
data class DiskInfo(
    @SerialName("usagePct") val usagePct: Float? = null,
    @SerialName("readMbPerSec") val readMbPerSec: Float? = null,
    @SerialName("writeMbPerSec") val writeMbPerSec: Float? = null,
)

@Serializable
data class NetInfo(
    @SerialName("downloadMbPerSec") val downloadMbPerSec: Float? = null,
    @SerialName("uploadMbPerSec") val uploadMbPerSec: Float? = null,
)

@Serializable
data class FpsInfo(
    val name: String? = null,
    val current: Float? = null,
    val avg: Float? = null,
    @SerialName("onePercentLow") val onePercentLow: Float? = null,
)
```

Add to `SystemStatus` after `ram`:

```kotlin
    val disk: DiskInfo? = null,
    val net: NetInfo? = null,
    val fans: List<FanInfo>? = null,
    val fps: FpsInfo? = null,
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.Obscrum.pchwmonitor.StatusParserTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/Obscrum/pchwmonitor/domain/model/SystemStatus.kt app/src/test/java/com/Obscrum/pchwmonitor/StatusParserTest.kt
git commit -m "feat(app): parse disk/net/fan/fps fields"
```

---

### Task 8: Chart window setting

**Files:**
- Modify: `app/src/main/java/com/Obscrum/pchwmonitor/data/SettingsStore.kt`
- Modify: `app/src/main/java/com/Obscrum/pchwmonitor/ui/settings/SettingsScreen.kt`
- Test: `app/src/test/java/com/Obscrum/pchwmonitor/SettingsStoreTest.kt`

**Interfaces:**
- Consumes: existing `AppSettings` data class and DataStore pattern.
- Produces: `AppSettings.chartWindowSeconds: Int = 60` (60 = current behavior), `SettingsStore.settings` exposes it, `setChartWindowSeconds(value: Int)` persists it, `SettingsScreen` gets a dropdown (30 / 60 / 300) bound to it.

- [ ] **Step 1: Write the failing test**

Append to `app/src/test/java/com/Obscrum/pchwmonitor/SettingsStoreTest.kt` (follow the existing `runTest`/fake-DataStore pattern used in that file — check its helpers first):

```kotlin
@Test
fun chartWindowDefaultIs60Seconds() {
    val store = SettingsStore(fakeDataStore())
    val settings = store.settings.first()
    assertEquals(60, settings.chartWindowSeconds)
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.Obscrum.pchwmonitor.SettingsStoreTest"`
Expected: FAIL — `unresolved reference: chartWindowSeconds`

- [ ] **Step 3: Implement**

In `SettingsStore.kt`:

```kotlin
    private val keyChartWindow = intPreferencesKey("chart_window_seconds")
```

Add `val chartWindowSeconds: Int = 60` to `AppSettings`; map `prefs[keyChartWindow] ?: 60` into `settings`; add:

```kotlin
    suspend fun setChartWindowSeconds(value: Int) {
        dataStore.edit { it[keyChartWindow] = value }
    }
```

In `SettingsScreen.kt`, add a section with three `FilterChip`s (30 sn / 1 dk / 5 dk) next to the existing theme section, calling `viewModel::setChartWindowSeconds`. Mirror how the theme chips are wired (check the screen file for the exact composable names used there).

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.Obscrum.pchwmonitor.SettingsStoreTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/Obscrum/pchwmonitor/data/SettingsStore.kt app/src/main/java/com/Obscrum/pchwmonitor/ui/settings/SettingsScreen.kt app/src/test/java/com/Obscrum/pchwmonitor/SettingsStoreTest.kt
git commit -m "feat(app): configurable chart window setting"
```

---

### Task 9: Card improvements — min/avg/max + window plumbing

**Files:**
- Modify: `app/src/main/java/com/Obscrum/pchwmonitor/ui/dashboard/RingBuffer.kt`
- Modify: `app/src/main/java/com/Obscrum/pchwmonitor/ui/dashboard/CpuCard.kt`, `GpuCard.kt`, `RamCard.kt`
- Modify: `app/src/main/java/com/Obscrum/pchwmonitor/ui/dashboard/DashboardScreen.kt`
- Modify: `app/src/main/java/com/Obscrum/pchwmonitor/MonitorViewModel.kt`

**Interfaces:**
- Consumes: `RingBuffer` (existing), `AppSettings.chartWindowSeconds` (Task 8).
- Produces: `RingBuffer.clearAndResize(capacity: Int)`; `fun minAvgMax(values: List<Float>): Triple<Float, Float, Float>?` (new file `ui/dashboard/SummaryStats.kt`); existing cards accept `chartPoints: Int = 60` and render a `min / ort. / max` footer line; `DashboardScreen` gains `chartWindowSeconds: Int` and passes `chartWindowSeconds` as `chartPoints` to every card; `MonitorViewModel` exposes `chartWindowSeconds` from settings.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/Obscrum/pchwmonitor/SummaryStatsTest.kt`:

```kotlin
package com.Obscrum.pchwmonitor

import com.Obscrum.pchwmonitor.ui.dashboard.minAvgMax
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SummaryStatsTest {
    @Test
    fun emptyListReturnsNull() {
        assertNull(minAvgMax(emptyList()))
    }

    @Test
    fun computesMinAvgMax() {
        val result = minAvgMax(listOf(10f, 20f, 30f, 40f))
        assertEquals(10f, result?.first!!, 0.001f)
        assertEquals(25f, result?.second!!, 0.001f)
        assertEquals(40f, result?.third!!, 0.001f)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.Obscrum.pchwmonitor.SummaryStatsTest"`
Expected: FAIL — compilation error

- [ ] **Step 3: Implement stats + RingBuffer resize**

Create `app/src/main/java/com/Obscrum/pchwmonitor/ui/dashboard/SummaryStats.kt`:

```kotlin
package com.Obscrum.pchwmonitor.ui.dashboard

fun minAvgMax(values: List<Float>): Triple<Float, Float, Float>? {
    if (values.isEmpty()) return null
    val min = values.min()
    val max = values.max()
    val avg = values.sum() / values.size
    return Triple(min, avg, max)
}
```

In `RingBuffer.kt` add:

```kotlin
    fun clearAndResize(capacity: Int) {
        synchronized(buffer) {
            buffer.clear()
            // capacity is honored by the append path
        }
    }
```

Note: `capacity` is read in `append` via the constructor field; instead change `capacity` to `private var capacity` and make `clearAndResize` set it:

```kotlin
    private var capacity: Int

    init { this.capacity = capacity }

    fun clearAndResize(newCapacity: Int) {
        synchronized(buffer) {
            capacity = newCapacity
            buffer.clear()
        }
    }
```

(Adjust `append` to read the mutable `capacity`.)

- [ ] **Step 4: Run SummaryStats tests**

Run: `./gradlew testDebugUnitTest --tests "com.Obscrum.pchwmonitor.SummaryStatsTest"`
Expected: PASS

- [ ] **Step 5: Add the footer line + window param to cards**

In each of `CpuCard.kt`, `GpuCard.kt`, `RamCard.kt`:
1. Add parameter `chartPoints: Int = 60` and replace `remember { RingBuffer() }` with `remember(chartPoints) { RingBuffer(chartPoints) }` (also handle `LaunchedEffect(chartPoints)` calling `spark.clearAndResize(chartPoints)` so a settings change resets the buffer).
2. Where the points are read (`points = spark.snapshot()`), compute:

```kotlin
val summary = minAvgMax(points)
```

3. Below the `LineChart` (non-compact branch), add:

```kotlin
if (summary != null) {
    Text(
        text = "min ${summary.first.toInt()} / ort. ${summary.second.toInt()} / max ${summary.third.toInt()}",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp),
    )
}
```

- [ ] **Step 6: Plumb window through DashboardScreen + ViewModel**

In `MonitorViewModel.kt`, expose `val chartWindowSeconds: StateFlow<Int>` derived from `settingsStore.settings` (`map { it.chartWindowSeconds }`); in `DashboardScreen.kt` add parameter `chartWindowSeconds: Int = 60` and pass `chartPoints = chartWindowSeconds` to `CpuCard`, `GpuCard`, `RamCard` in both portrait and landscape call sites. Wire the new parameter where `DashboardScreen` is invoked (follow how other state flows into it).

- [ ] **Step 7: Run all unit tests**

Run: `./gradlew testDebugUnitTest`
Expected: PASS

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/Obscrum/pchwmonitor/ui/dashboard/ app/src/main/java/com/Obscrum/pchwmonitor/MonitorViewModel.kt app/src/test/java/com/Obscrum/pchwmonitor/SummaryStatsTest.kt
git commit -m "feat(app): min/avg/max summary and configurable chart window on cards"
```

---

### Task 10: FPS card

**Files:**
- Create: `app/src/main/java/com/Obscrum/pchwmonitor/ui/dashboard/FpsCard.kt`

**Interfaces:**
- Consumes: `FpsInfo` (Task 7), `MetricCard`, `LineChart`, `RingBuffer`, `minAvgMax` (Task 9).
- Produces: `@Composable fun FpsCard(fps: FpsInfo?, labelTitle: String, labelAvg: String, labelOnePercentLow: String, labelFpsDetails: String, labelFpsHint: String, modifier: Modifier = Modifier, compact: Boolean = false, chartPoints: Int = 60)` — big current FPS, mini chart, `Ort. <avg> · 1% Low <low> · <process>` footer, and an overflow `IconButton` showing `labelFpsHint` in a small `AlertDialog` (`labelFpsDetails` title, `labelFpsHint` body) explaining 1% low.

- [ ] **Step 1: Write the composable**

Create `FpsCard.kt` modeled on `RamCard.kt`:

```kotlin
package com.Obscrum.pchwmonitor.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.Obscrum.pchwmonitor.domain.model.FpsInfo
import com.Obscrum.pchwmonitor.ui.components.LineChart
import com.Obscrum.pchwmonitor.ui.components.MetricCard

@Composable
fun FpsCard(
    fps: FpsInfo?,
    labelTitle: String,
    labelAvg: String,
    labelOnePercentLow: String,
    labelFpsDetails: String,
    labelFpsHint: String,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    chartPoints: Int = 60,
) {
    MetricCard(title = labelTitle, modifier = modifier, compact = compact) {
        val spark = remember(chartPoints) { RingBuffer(chartPoints) }
        var points by remember { mutableStateOf(listOf<Float>()) }
        var showHint by remember { mutableStateOf(false) }
        LaunchedEffect(fps?.current) {
            fps?.current?.let {
                spark.append(it)
                points = spark.snapshot()
            }
        }
        LaunchedEffect(chartPoints) { spark.clearAndResize(chartPoints) }

        Column(verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 12.dp)) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text(
                    text = fps?.current?.toInt()?.toString() ?: "--",
                    style = if (compact) MaterialTheme.typography.titleLarge else MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { showHint = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = labelFpsDetails)
                }
            }
            Text(
                text = "$labelAvg ${fps?.avg?.toInt() ?: "--"} · $labelOnePercentLow ${fps?.onePercentLow?.toInt() ?: "--"} · ${fps?.name ?: "--"}",
                style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (!compact) {
            LineChart(points = points, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
        }
        if (showHint) {
            AlertDialog(
                onDismissRequest = { showHint = false },
                title = { Text(labelFpsDetails) },
                text = { Text(labelFpsHint) },
                confirmButton = {
                    TextButton(onClick = { showHint = false }) { Text("OK") }
                },
            )
        }
    }
}
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew compileDebugKotlin`
Expected: PASS

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/Obscrum/pchwmonitor/ui/dashboard/FpsCard.kt
git commit -m "feat(app): add FPS card with 1% low details"
```

---

### Task 11: Disk, Net, Fan cards

**Files:**
- Create: `app/src/main/java/com/Obscrum/pchwmonitor/ui/dashboard/DiskCard.kt`
- Create: `app/src/main/java/com/Obscrum/pchwmonitor/ui/dashboard/NetCard.kt`
- Create: `app/src/main/java/com/Obscrum/pchwmonitor/ui/dashboard/FanCard.kt`

**Interfaces:**
- Consumes: `DiskInfo`/`NetInfo`/`FanInfo` (Task 7), `MetricCard`, `LineChart`, `FilledBar`, `minAvgMax` (Task 9).
- Produces: `DiskCard(disk: DiskInfo?, labelTitle, labelRead, labelWrite, labelUsage, modifier, compact, chartPoints)`, `NetCard(net: NetInfo?, labelTitle, labelDownload, labelUpload, modifier, compact, chartPoints)`, `FanCard(fans: List<FanInfo>?, labelTitle, modifier, compact)`.

- [ ] **Step 1: Write DiskCard**

```kotlin
package com.Obscrum.pchwmonitor.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.Obscrum.pchwmonitor.domain.model.DiskInfo
import com.Obscrum.pchwmonitor.ui.components.FilledBar
import com.Obscrum.pchwmonitor.ui.components.LineChart
import com.Obscrum.pchwmonitor.ui.components.MetricCard
import com.Obscrum.pchwmonitor.ui.components.TemperatureColor

@Composable
fun DiskCard(
    disk: DiskInfo?,
    labelTitle: String,
    labelRead: String,
    labelWrite: String,
    labelUsage: String,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    chartPoints: Int = 60,
) {
    MetricCard(title = labelTitle, modifier = modifier, compact = compact) {
        val spark = remember(chartPoints) { RingBuffer(chartPoints) }
        var points by remember { mutableStateOf(listOf<Float>()) }
        LaunchedEffect(disk?.readMbPerSec, disk?.writeMbPerSec) {
            disk?.readMbPerSec?.let {
                spark.append(it + (disk.writeMbPerSec ?: 0f))
                points = spark.snapshot()
            }
        }
        LaunchedEffect(chartPoints) { spark.clearAndResize(chartPoints) }

        Column(verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 12.dp)) {
            Text(
                text = "${(disk?.usagePct ?: 0f).toInt()} %",
                style = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = TemperatureColor.forUsage(disk?.usagePct ?: 0f),
            )
            FilledBar(valuePct = disk?.usagePct ?: 0f, color = TemperatureColor.forUsage(disk?.usagePct ?: 0f))
            Text(
                text = "$labelRead ${disk?.readMbPerSec?.toInt() ?: "--"} MB/s · $labelWrite ${disk?.writeMbPerSec?.toInt() ?: "--"} MB/s",
                style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (!compact) {
            LineChart(points = points, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
        }
    }
}
```

- [ ] **Step 2: Write NetCard**

```kotlin
package com.Obscrum.pchwmonitor.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.Obscrum.pchwmonitor.domain.model.NetInfo
import com.Obscrum.pchwmonitor.ui.components.LineChart
import com.Obscrum.pchwmonitor.ui.components.MetricCard

@Composable
fun NetCard(
    net: NetInfo?,
    labelTitle: String,
    labelDownload: String,
    labelUpload: String,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    chartPoints: Int = 60,
) {
    MetricCard(title = labelTitle, modifier = modifier, compact = compact) {
        val spark = remember(chartPoints) { RingBuffer(chartPoints) }
        var points by remember { mutableStateOf(listOf<Float>()) }
        LaunchedEffect(net?.downloadMbPerSec, net?.uploadMbPerSec) {
            net?.downloadMbPerSec?.let {
                spark.append(it)
                points = spark.snapshot()
            }
        }
        LaunchedEffect(chartPoints) { spark.clearAndResize(chartPoints) }

        Column(verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 12.dp)) {
            Text(
                text = "${net?.downloadMbPerSec?.toInt() ?: "--"} ↓",
                style = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "$labelDownload ${net?.downloadMbPerSec?.toInt() ?: "--"} · $labelUpload ${net?.uploadMbPerSec?.toInt() ?: "--"} MB/s",
                style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (!compact) {
            LineChart(points = points, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
        }
    }
}
```

- [ ] **Step 3: Write FanCard**

```kotlin
package com.Obscrum.pchwmonitor.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.Obscrum.pchwmonitor.domain.model.FanInfo
import com.Obscrum.pchwmonitor.ui.components.MetricCard

@Composable
fun FanCard(
    fans: List<FanInfo>?,
    labelTitle: String,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    MetricCard(title = labelTitle, modifier = modifier, compact = compact) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (fans.isNullOrEmpty()) {
                Text("--", style = MaterialTheme.typography.bodyMedium)
            } else {
                fans.forEach { fan ->
                    Text(
                        text = "${fan.label ?: "Fan"}: ${fan.rpm?.toInt() ?: "--"} RPM",
                        style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}
```

- [ ] **Step 4: Verify compilation**

Run: `./gradlew compileDebugKotlin`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/Obscrum/pchwmonitor/ui/dashboard/DiskCard.kt app/src/main/java/com/Obscrum/pchwmonitor/ui/dashboard/NetCard.kt app/src/main/java/com/Obscrum/pchwmonitor/ui/dashboard/FanCard.kt
git commit -m "feat(app): add disk, network and fan cards"
```

---

### Task 12: Dashboard placement & labels

**Files:**
- Modify: `app/src/main/java/com/Obscrum/pchwmonitor/ui/dashboard/DashboardScreen.kt`
- Modify: `app/src/main/java/com/Obscrum/pchwmonitor/MonitorViewModel.kt` (labels)

**Interfaces:**
- Consumes: the four new cards (Tasks 10-11), `chartWindowSeconds` (Task 8-9).
- Produces: portrait + landscape dashboards render the new cards in order CPU → GPU → FPS → RAM → Disk → Net → Fan, only when data is non-null; string labels for the new cards are supplied from the ViewModel (Turkish/English resources exist — check `res/values*/strings.xml` and add `fps_card_title`, `fps_avg`, `fps_1pct_low`, `fps_details_title`, `fps_hint`, `disk_card_title`, `disk_read`, `disk_write`, `disk_usage`, `net_card_title`, `net_download`, `net_upload`, `fan_card_title` in both `values` and `values-tr`).

- [ ] **Step 1: Update DashboardScreen**

In portrait `LazyColumn`, after the `GpuCard` item (and after the iGPU card, before `RamCard`), add:

```kotlin
                    if (status.fps != null) {
                        item {
                            FpsCard(
                                fps = status.fps,
                                labelTitle = labelFps,
                                labelAvg = labelFpsAvg,
                                labelOnePercentLow = labelFpsOnePercentLow,
                                labelFpsDetails = labelFpsDetails,
                                labelFpsHint = labelFpsHint,
                                chartPoints = chartWindowSeconds,
                                modifier = Modifier.padding(horizontal = 12.dp),
                            )
                        }
                    }
                    if (status.disk != null) {
                        item {
                            DiskCard(
                                disk = status.disk,
                                labelTitle = labelDisk,
                                labelRead = labelDiskRead,
                                labelWrite = labelDiskWrite,
                                labelUsage = labelDiskUsage,
                                chartPoints = chartWindowSeconds,
                                modifier = Modifier.padding(horizontal = 12.dp),
                            )
                        }
                    }
                    if (status.net != null) {
                        item {
                            NetCard(
                                net = status.net,
                                labelTitle = labelNet,
                                labelDownload = labelNetDownload,
                                labelUpload = labelNetUpload,
                                chartPoints = chartWindowSeconds,
                                modifier = Modifier.padding(horizontal = 12.dp),
                            )
                        }
                    }
                    if (status.fans != null) {
                        item {
                            FanCard(
                                fans = status.fans,
                                labelTitle = labelFan,
                                modifier = Modifier.padding(horizontal = 12.dp),
                            )
                        }
                    }
```

Do the same in `LandscapeDashboard`'s `LazyVerticalGrid`, with `compact = true` and `Modifier.fillMaxWidth()`. Add the new label parameters to both functions (same signature pattern as existing labels) and add `chartWindowSeconds: Int` to `DashboardScreen`'s parameter list, passing it through to both branches.

- [ ] **Step 2: Add strings**

Add to `app/src/main/res/values/strings.xml` and `app/src/main/res/values-tr/strings.xml` (English / Turkish translations, following the existing string naming style):

```xml
<string name="fps_card_title">FPS</string>
<string name="fps_avg">Ort.</string>
<string name="fps_1pct_low">1% Low</string>
<string name="fps_details_title">FPS Detayları</string>
<string name="fps_hint">1% low, kare sürelerindeki en kötü %1\'in ortalamasıdır — takılma hissiyatını ölçer.</string>
<string name="disk_card_title">Disk</string>
<string name="disk_read">Okuma</string>
<string name="disk_write">Yazma</string>
<string name="disk_usage">Disk Kullanımı</string>
<string name="net_card_title">Ağ</string>
<string name="net_download">İndirme</string>
<string name="net_upload">Yükleme</string>
<string name="fan_card_title">Fan</string>
```

- [ ] **Step 3: Wire labels in the ViewModel call site**

In `MonitorViewModel.kt` (or wherever `DashboardScreen` is composed — find the call and follow it), resolve the new string resources and pass them; pass `chartWindowSeconds = chartWindowSeconds` from the settings flow (Task 9).

- [ ] **Step 4: Verify build**

Run: `./gradlew compileDebugKotlin`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/Obscrum/pchwmonitor/ui/dashboard/DashboardScreen.kt app/src/main/java/com/Obscrum/pchwmonitor/MonitorViewModel.kt app/src/main/res/values/strings.xml app/src/main/res/values-tr/strings.xml
git commit -m "feat(app): place new cards on dashboard with labels"
```

---

### Task 13: End-to-end smoke + README

**Files:**
- Modify: `README.md`

- [ ] **Step 1: Manual smoke test**

Run the server in simulation mode and verify the payload:

```bash
cd server && python -m pytest tests/ -v
cd server && python main.py --simulate --port 8765
# from another terminal:
curl -s http://127.0.0.1:8765/health
# open the app (or a WS client) and confirm: fps, disk, net, fans present;
# then run the app against an old-server payload and confirm cards hidden
```

- [ ] **Step 2: Update README**

In `README.md`:
- Document `--fps-process` (and auto-follow behavior) in the server section.
- Note `psutil` in the server dependencies and the `server/presentmon/PresentMon64.exe` requirement for packaged FPS support (built by `build_exe.bat`).
- Document the new dashboard cards.

- [ ] **Step 3: Commit**

```bash
git add README.md
git commit -m "docs: document v1.3 metrics and FPS"
```

---

## Self-Review Notes

- Spec → plan mapping: schema models (T1), psutil disk/net (T2), LHM fans (T3), PresentMon embed + math (T4), simulator parity (T5), wiring + `--fps-process` + bundling (T6), app models/parser (T7), chart window setting (T8), card min/avg/max (T9), FPS card w/ 1% low + details dialog (T10), disk/net/fan cards (T11), dashboard placement + strings (T12), smoke + docs (T13). Compatibility matrix covered implicitly: nullable fields (T1/T7) + conditional card rendering (T12).
- Server `current/avg/onePercentLow` semantics match the spec's 2s/30s windows.
- `GpuInfo.fps` removal is covered in T1 (Python) and T7 (Kotlin), including test updates.
- No placeholders; every code step contains complete code.
