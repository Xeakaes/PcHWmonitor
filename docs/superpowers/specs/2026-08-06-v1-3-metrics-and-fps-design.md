# PC HW Monitor v1.3 — New Metrics & FPS — Design

Date: 2026-08-06

## Problem Statement

PC HW Monitor v1.2 monitors CPU, GPU, iGPU and RAM through a Python bridge
server on the user's Windows PC. Two gaps:

1. The dashboard shows no storage, network, or fan data.
2. Users want in-game FPS while playing, with frametime realism (avg + 1% low).

The F-Droid review of v1.2 is pending. Releases keep flowing to GitHub; only the
final release later gets submitted to F-Droid. So v1.3 must stay observable and
safe, keeping backward/forward compatibility between app and server.

## Goals (v1.3)

1. Add disk, network, and fan metrics to the server and the dashboard.
2. Add real FPS measurement via PresentMon embedded in the server, shown on its
   own new card (an FPS card, separate from the GPU card).
3. Show avg FPS and 1% low on the FPS card (not hidden behind a menu).
4. Card improvements: per-card min/avg/max summary line and a configurable chart
   window (30s / 1m / 5m).

## Non-Goals (deferred to v1.4)

- Landscape / tablet layouts (current cards reflow but are not yet a first-class
  responsive layout).
- Theme options beyond the existing dark/light pair.
- Connection screen improvements.
These are captured for v1.4 in a later spec. Nothing in v1.3 precludes them.

## Architecture Overview

Two deployables, one WebSocket protocol:

- **Server** (Python/FastAPI, PyInstaller single .exe on Windows): reads
  hardware, broadcasts a `StatusMessage` JSON every interval.
- **App** (Kotlin + Compose): displays cards; charts from `RingBuffer` history.

### Server

| Concern        | Module                          | Change                              |
|----------------|---------------------------------|-------------------------------------|
| schema         | `server/schema.py`              | `DiskInfo`, `NetInfo`, `FanInfo[]`, `FpsInfo` added to `StatusMessage` |
| LHM fan        | `server/adapters/lhm.py`        | parse Fan sensors into `FanInfo[]`  |
| psutil disk/net| new `server/adapters/system.py` | via `psutil` on Windows; MB/s computed from counter deltas |
| FPS            | new `server/adapters/fps.py`    | PresentMon embedded CLI, subprocess, frametime buffer |
| wiring        | `server/main.py`                | new adapter attached; `--fps-process` **CLI arg only** (no config file) |
| simulator     | `server/adapters/simulator.py`  | generates disk/fan/net/FPS samples for tests & demo |
| deps          | `server/requirements.txt`       | add `psutil` |

#### FPS measurement — PresentMon, embedded

A separate download by the end user is not acceptable; the PresentMon CLI is
bundled into the PyInstaller server with a single `.exe`:

- Build-time: `PresentMon64.exe` (from its official release; license verified
  at build time, expected MIT) sits in `server/presentmon/PresentMon64.exe`;
  PyInstaller adds it as a data file.
- Runtime: the server locates it via `sys._MEIPASS` (or `server/presentmon`
  when running from source), runs it as a subprocess streaming CSV lines
  (`--output_stdout`).
- Process target:
  - `--fps-process` CLI arg set → PresentMon is restricted to that process
    (`--process_name`), deterministic.
  - arg empty → PresentMon captures all processes and the server tracks the
    latest ~2s of present frames per process (from the `Process` CSV column);
    the process with the highest present rate becomes the measured one and is
    reported as `FpsInfo.name`. If no process presents in the window, FPS is
    `null`.
- Frametime buffer: last ~30s of frame intervals (a deque capped at ~1800
  entries max). `current` = inverse of mean over last 2s; `avg` = inverse of
  mean over the 30s buffer; `onePercentLow` = inverse of the 99th percentile
  frame-time (msBetweenPresents). Missing/garbled lines are skipped.
- The upstream PresentMon 2.x CSV header uses `msBetweenPresents`; the adapter
  parses by header names — if a column is absent it maps to `None`.
- The subprocess is terminated (`proc.terminate()`) on server shutdown and
  reaped; a watchdog restarts it if idle is lost (only if the user asked for
  FPS). Any failure degrades to `fps=null`; the server never crashes.

#### New payload models

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
    name: str | None = None        # measured process
    current: float | None = None   # fps over last ~2s
    avg: float | None = None       # mean over ~30s
    onePercentLow: float | None = None
```

`StatusMessage` gains `disk: DiskInfo | None`, `net: NetInfo | None`,
`fans: list[FanInfo] | None`, `fps: FpsInfo | None`. `GpuInfo.fps` is removed
(it was never populated; removing it keeps the GPU domain clean for the
dedicated FPS card).

#### Unavailable-source semantics

Every new field is `None` when its source is unavailable (no psutil, no fan
sensor in the LHM tree, PresentMon missing/unable to start). This keeps the
server resilient and lets the app hide cards.

### App (Android)

- `domain/model/SystemStatus.kt` gains `DiskInfo`, `NetInfo`, `FanInfo`,
  `FpsInfo` mirrored data classes with nullable fields.
- `data/network/StatusParser.kt` parses `disk`, `net`, `fans`, `fps` (unknown
  / missing stays null → this is how v1.2 app ignores new fields and v1.3 app
  still works against an old server).
- New composables under `ui/dashboard/`:
  - `FpsCard.kt` — distinct card (not part of GPU card): big current FPS,
    a mini FPS line chart (own ring buffer, ~60 points), bottom line
    `Ort. <avg> · 1% Low <onePercentLow> · <process name>`. Open a small
    explanation of 1% low via the card's overflow menu ("FPS detayları").
  - `DiskCard.kt` — usage ` filled bar + read/write MB/s per second,
    mini chart for read+write.
  - `NetCard.kt` — download/upload MB/s, mini chart.
  - `FanCard.kt` — list of label + RPM rows.
- `ui/dashboard/DashboardScreen.kt` + `LayoutHelper.kt` order:
  CPU → GPU → FPS → RAM → Disk → Net → Fan.
  Cards whose data is `null` are not rendered (old server ⇒ FPS/Disk/Net/Fan
  cards simply do not appear).
- Card improvements (all cards):
  - summary line `min / ort. / max` computed from the ring buffer,
  - chart window configurable (30s default) via `Settings` (existing
    `SettingsScreen` + `SettingsStore`).

### Data flow / timing

- Server Hy snapshot → WebSocket `status` JSON 1/s.
- App keeps per-card fluorescent ring buffers sized by chart window setting.
- 1% low explanation lives only in the app UI; the metric is computed on the
  server from the 30s frametime buffer.

## Error Handling

- psutil missing/failing on Windows → disk/net `None`.
- LHM tree without Fan sensors → `fans=None`.
- PresentMon fails to start / dies / PID unknown → `fps=None` (with log).
- Any server adapter exception caught by the adapter, sample continues with
  missing fields; server never raises through the hub.

## Testing

- **Server (pytest, existing layout in `server/tests/`)**:
  - `test_simulator.py` — simulator emits plausible disk/net/fan/fps and
    `StatusMessage` schema round-trip.
  - new `test_fps.py` — feed a fake PresentMon CSV stream (incl. header
    missing columns, garbage lines) and assert `current/avg/onePercentLow`
    math.
  - `test_lhm.py`/`test_lhm_v2.py` — new fan parsing from LHM v1-style and v2
    sensor groups.
- **App**: existing unit test pattern extended for `StatusParser` new fields
  (missing → null, present → parsed). Manual smoke of the 4 new cards in both
  simulator mode (server `--simulate`) and real LHM mode.
- **Compatibility matrix**:
  - v1.2 app + v1.3 server → no new cards shown, app behavior unchanged.
  - v1.3 app + v1.2 server → no new cards shown, app behavior unchanged.

## Known Decisions / Trade-offs

- KISS over speed: FPS via PresentMon CLI subprocess (embedded) rather than
  PresentMon C#/package; keeping average + 1% low server-side; `GpuInfo.fps`
  removed in favor of a standalone `FpsInfo`.
- 0.1% low deferred. Not needed for the primary goal.
- FPS window fixed on the server at 30s; window chart setting is an app-side
  ring-buffer concern only.