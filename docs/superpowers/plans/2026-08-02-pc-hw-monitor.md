# PC HW Monitor Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a Kotlin/Compose Android app + Python FastAPI bridge server that streams PC hardware stats (CPU/GPU temp, usage, clocks, RAM/VRAM) over WebSocket from LibreHardwareMonitor to the phone dashboard.

**Architecture:** Python server on the PC polls LibreHardwareMonitor's `/data.json` every 1s, normalizes it into a fixed JSON schema, broadcasts over WebSocket. Android connects via OkHttp WebSocket, exposes a Flow of `SystemStatus`, renders Compose dashboard/history/settings, persists 1-hour history in Room. Simulator mode (`--simulate`) generates realistic fake data for development without a real PC.

**Tech Stack:** Kotlin 2.2.10, Jetpack Compose BOM 2025.12.00, AGP 9.3.1, OkHttp 4.12, kotlinx-serialization-json, Room, DataStore, Navigation-Compose, coroutines. Python 3.14, FastAPI, Uvicorn, httpx, pydantic, pytest.

**Spec:** `docs/superpowers/specs/2026-08-02-pc-hw-monitor-design.md`

## Global Constraints

- Android package: `com.example.pchwmonitor`, namespace `com.example.pchwmonitor`, minSdk 24, targetSdk 37, compileSdk 37.
- Kotlin code: no comments in code unless asked; official code style; DO NOT use experimental Compose APIs.
- No git repository — skip all `git commit` steps in this session (repo is not git-initialized).
- Protocol field names are FIXED (defined in Task 1): `type, timestamp, pc, cpu{name,usagePct,tempC,clockMhz,powerW,loads[]}, gpu{name,usagePct,tempC,hotspotC,vramUsedMb,vramTotalMb,coreClockMhz,memClockMhz,powerW,fps}, ram{usedGb,totalGb,usagePct,clockMhz}`, plus `welcome{type,intervalMs,serverName,source,pcName}`. Missing values are `null`, never absent keys.
- All UI strings must exist in all 13 locales: default EN + fr, de, es, it, pt, pt-rBR, ru, tr, pl, nl, zh, zh-rTW, ja.
- Charts are custom Canvas composables — no chart library.
- `server/` is standalone Python (FastAPI), run with `python3 server/main.py --simulate`.

---

### Task 1: Server — Pydantic schema + Simulator

**Files:**
- Create: `server/schema.py`
- Create: `server/adapters/__init__.py`
- Create: `server/adapters/simulator.py`
- Create: `server/tests/__init__.py`
- Create: `server/tests/test_schema.py`
- Create: `server/tests/test_simulator.py`
- Create: `server/requirements.txt`
- Create: `server/requirements-dev.txt`

**Interfaces:**
- Consumes: nothing.
- Produces:
  - `schema.py`: `PcInfo(name: str, os: str, source: str)`, `CpuInfo(name: str|None, usagePct: float|None, tempC: float|None, clockMhz: float|None, powerW: float|None, loads: list[float]|None)`, `GpuInfo(...same shape + hotspotC, vramUsedMb, vramTotalMb, coreClockMhz, memClockMhz, fps: float|None)`, `RamInfo(usedGb, totalGb, usagePct, clockMhz)`, `StatusMessage(type: str = "status", timestamp: int, available: bool, error: str|None, cpu, gpu, ram, pc)`, `WelcomeMessage(type: str = "welcome", intervalMs: int, serverName: str, source: str, pcName: str)`.
  - `simulator.py`: `class Simulator` with `pc_name: str` and `sample() -> tuple[CpuInfo, GpuInfo, RamInfo, PcInfo]` producing realistic, ever-changing values (sine waves + noise, temps 30–90, usage 5–100, clocks 800–5100 CPU / 400–2800 core / 5000–14000 mem GPU).

- [ ] **Step 1: Write failing tests** (`server/tests/test_schema.py`, `test_simulator.py`): schema serializes to exact protocol JSON (nulls present), simulator values stay in documented ranges and change between calls.
- [ ] **Step 2: Run tests → fail** (`python3 -m pytest server/tests -v`; note pytest missing → install `pip install -r server/requirements-dev.txt` first).
- [ ] **Step 3: Implement** `schema.py` (pydantic v2 models with `field_serializer` where needed) and `simulator.py` (time-driven sine + random walk).
- [ ] **Step 4: Run tests → pass.**

### Task 2: Server — LHM adapter

**Files:**
- Create: `server/adapters/lhm.py`
- Create: `server/tests/fixtures/lhm_sample.json`
- Create: `server/tests/test_lhm.py`

**Interfaces:**
- Consumes: `schema.py` models (CpuInfo, GpuInfo, RamInfo, PcInfo), `StatusMessage`.
- Produces:
  - `class LhmAdapter`: `__init__(self, lhm_url: str = "http://127.0.0.1:8085/data.json", http_client=None)`; `fetch() -> StatusMessage` — GETs the URL (sync httpx, 3s timeout), walks the JSON tree matching by `HardwareType` (Cpu / GpuNvidia / Memory), `SensorType` (Temperature/Load/Clock/Power/Data/SmallData), and name substrings (case-insensitive): "cpu package"/"cpu core max"→tempC, "cpu total"→usagePct, "core max"→clockMhz, "cpu package power"/"cpu total power"→powerW, "cpu core 0..n"/"cpu core #n" loads→loads[], "gpu core" temp, "gpu hot spot"→hotspotC, "gpu memory used"→vramUsedMb (SmallData→MB), "gpu memory total"→vramTotalMb, "gpu core clock"→coreClockMhz, "gpu memory clock"→memClockMhz, "gpu total power"/"gpu power"→powerW, "memory used" (Data→GB)→usedGb, "memory total"→totalGb, "memory utilization"→usagePct, "memory clock"→clockMhz. On HTTP/schema failure returns `StatusMessage(available=False, error=...)`.
- [ ] **Step 1: Write fixture** `lhm_sample.json` — realistic 2-level tree with CPU/GPU/Memory hardware, all needed sensors incl. hot spot, some extras that must be ignored.
- [ ] **Step 2: Write failing tests** (`test_lhm.py`) — mock httpx transport returning fixture; assert every field mapped correctly; assert unavailable on transport error.
- [ ] **Step 3: Implement** `adapters/lhm.py` with `_walk`, `_find_sensor(hardware, sensor_types, *name_parts)` helpers.
- [ ] **Step 4: Run tests → pass.**

### Task 3: Server — Hub + FastAPI app

**Files:**
- Create: `server/hub.py`
- Create: `server/main.py`
- Create: `server/smoke_test.py`

**Interfaces:**
- Consumes: `LhmAdapter.fetch()`, `Simulator.sample()`, schema models.
- Produces:
  - `hub.py`: `class Hub` — `register(ws)`, `unregister(ws)`, `async tick_forever()` loop: every 1s build `StatusMessage` (adapter or simulator), `json.dumps(..., default=pydantic_encoder)`, broadcast to all connected `websockets`; if fetch raises, broadcast `available=False` (5s retry grace).
  - `main.py`: FastAPI app; `GET /health` → `{"ok": true}`; `WS /ws` → send `WelcomeMessage` first, then register client; CLI: `--port 8765`, `--simulate`, `--lhm-url`, `--interval`; `uvicorn`-independent (uses `app.run()` via uvicorn programmatically).
  - `smoke_test.py`: stdlib-only test client — connects `ws://127.0.0.1:8765/ws` with the `websockets` lib, receives welcome + 2 status messages, asserts JSON schema, exits 0.
- [ ] **Step 1: Write hub + main + smoke_test.**
- [ ] **Step 2: Run smoke test against `--simulate` server** (`python3 server/main.py --simulate &`, then `python3 server/smoke_test.py`).
- [ ] **Step 3: Verify** `curl http://127.0.0.1:8765/health` returns ok.

### Task 4: Android — build config, manifest, Application

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`
- Modify: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/java/com/example/pchwmonitor/PcHwMonitorApp.kt`
- Modify: `app/src/main/res/values/strings.xml` (add only `app_name` = "PC HW Monitor")

**Interfaces:**
- Consumes: nothing.
- Produces: `PcHwMonitorApp : Application` registering nothing yet (DI created in later tasks); manifest with `INTERNET` + `ACCESS_NETWORK_STATE` permissions and `android:usesCleartextTraffic="true"`.
- [ ] **Step 1: Update `libs.versions.toml`** — add: `okhttp=4.12.0`, `kotlinxSerializationJson=1.7.3`, `room=2.7.1`, `ksp=<kotlin version>-2.0.2`, `datastore=1.1.1`, `lifecycleViewmodelCompose=2.9.4`, `navigationCompose=2.9.0`, `coroutines=1.10.1`, kotlin-serialization plugin `org.jetbrains.kotlin.plugin.serialization` version `2.2.10`.
- [ ] **Step 2: Update `app/build.gradle.kts`** — plugins: `kotlin-serialization`, `ksp`; deps: okhttp, kotlinx-serialization-json, room-runtime/room-ktx + ksp room-compiler, datastore-preferences, lifecycle-viewmodel-compose, navigation-compose, kotlinx-coroutines-android.
- [ ] **Step 3: Manifest** — permissions + cleartext flag; Application `android:name=".PcHwMonitorApp"`.
- [ ] **Step 4: Verify** — `./gradlew :app:assembleDebug` builds (JDK auto-provisioned via foojay; Android SDK must be installed — see Task 18 for SDK setup).

### Task 5: Android — domain models + StatusParser

**Files:**
- Create: `app/src/main/java/com/example/pchwmonitor/domain/model/SystemStatus.kt`
- Create: `app/src/main/java/com/example/pchwmonitor/data/network/StatusParser.kt`
- Create: `app/src/test/java/com/example/pchwmonitor/StatusParserTest.kt`

**Interfaces:**
- Consumes: nothing.
- Produces:
  - `data class SystemStatus(timestamp: Long, pc: PcInfo?, cpu: CpuInfo?, gpu: GpuInfo?, ram: RamInfo?, available: Boolean, error: String?)` with nested `CpuInfo/GpuInfo/RamInfo/PcInfo` matching protocol (all nullable fields, `loads: List<Float>?`).
  - `data class WelcomeInfo(intervalMs: Int, serverName: String, source: String, pcName: String)`
  - `sealed class WsMessage { data class Welcome(..), data class Status(..), object Error(String) }`
  - `object StatusParser { fun parseWelcome(json: String): WsMessage.Welcome; fun parseStatus(json: String): WsMessage.Status; }` — kotlinx-serialization `Json { ignoreUnknownKeys = true }`, tolerates missing keys as null via default values.
- [ ] **Step 1: Write failing tests** — full message, missing fields (all null), unknown extra keys, malformed JSON → `JsonDecodingException` or graceful error.
- [ ] **Step 2: Implement** models + parser.
- [ ] **Step 3: Run `./gradlew :app:testDebugUnitTest` → pass.**

### Task 6: Android — WebSocketClient

**Files:**
- Create: `app/src/main/java/com/example/pchwmonitor/data/network/WebSocketClient.kt`

**Interfaces:**
- Consumes: `WsMessage`/`StatusParser` from Task 5.
- Produces:
  - `class WebSocketClient(private val parser: StatusParser)`:
    - `val messages: SharedFlow<WsMessage>`
    - `val connectionState: StateFlow<ConnectionState>` (`enum: Disconnected, Connecting, Connected`)
    - `suspend fun connect(url: String)` — closes old socket, opens OkHttp `Request.Builder().url(url).build()` with ping interval 20s; on failure schedules reconnect with exponential backoff 1s→30s inside `connect()`'s loop; `fun disconnect()`.
- [ ] **Step 1: Write implementation** (no unit test infra for OkHttp needed; keep logic thin).
- [ ] **Step 2: Verify compile** via Task 4 build.

### Task 7: Android — Room DB + HistoryRepository

**Files:**
- Create: `app/src/main/java/com/example/pchwmonitor/data/local/HistorySample.kt`
- Create: `app/src/main/java/com/example/pchwmonitor/data/local/HistoryDao.kt`
- Create: `app/src/main/java/com/example/pchwmonitor/data/local/HistoryDb.kt`
- Create: `app/src/main/java/com/example/pchwmonitor/data/local/HistoryRepository.kt`
- Create: `app/src/test/java/com/example/pchwmonitor/HistoryRepositoryTest.kt`

**Interfaces:**
- Consumes: `SystemStatus` (Task 5).
- Produces:
  - `@Entity(tableName="history_samples") data class HistorySample(timestamp: Long, cpuTempC: Float?, cpuUsagePct: Float?, gpuTempC: Float?, gpuUsagePct: Float?, gpuHotspotC: Float?, ramUsagePct: Float?)`
  - `@Dao interface HistoryDao { @Insert suspend fun insert(sample: HistorySample); @Query("DELETE FROM history_samples WHERE timestamp < :cutoff") suspend fun pruneOlderThan(cutoff: Long); @Query("SELECT * FROM history_samples WHERE timestamp >= :start ORDER BY timestamp ASC") suspend fun getBetween(start: Long): List<HistorySample> }`
  - `class HistoryRepository(private val dao: HistoryDao, private val retentionMs: Long = 3600_000) { suspend fun record(s: SystemStatus); suspend fun history(start: Long): List<HistorySample>; }` — records only when `s.available`, prunes on insert.
- [ ] **Step 1: Write failing tests with fake DAO** — record inserts + prunes (assert delete called with cutoff), history filters.
- [ ] **Step 2: Implement.**
- [ ] **Step 3: Run unit tests → pass.**

### Task 8: Android — SettingsStore

**Files:**
- Create: `app/src/main/java/com/example/pchwmonitor/data/SettingsStore.kt`
- Create: `app/src/test/java/com/example/pchwmonitor/SettingsStoreTest.kt`

**Interfaces:**
- Consumes: nothing.
- Produces:
  - `enum class ThemeMode { SYSTEM, LIGHT, DARK }`
  - `class SettingsStore(private val dataStore: DataStore<Preferences>)`:
    - `val settings: Flow<AppSettings>` (`data class AppSettings(serverIp: String = "192.168.1.100", serverPort: Int = 8765, theme: ThemeMode = ThemeMode.SYSTEM)`)
    - `suspend fun setServerIp(v: String)`, `setServerPort(v: Int)`, `setTheme(v: ThemeMode)`
- [ ] **Step 1: Write failing test with in-memory DataStore (`PreferenceDataStoreFactory.create` with `tmpFile`)** — defaults, round-trip writes.
- [ ] **Step 2: Implement.**
- [ ] **Step 3: Run tests → pass.**

### Task 9: Android — MonitorRepository + ViewModel

**Files:**
- Create: `app/src/main/java/com/example/pchwmonitor/data/repository/MonitorRepository.kt`
- Create: `app/src/main/java/com/example/pchwmonitor/MonitorViewModel.kt`
- Create: `app/src/test/java/com/example/pchwmonitor/MonitorViewModelTest.kt`

**Interfaces:**
- Consumes: `WebSocketClient` (Task 6), `HistoryRepository` (Task 7), `SettingsStore` (Task 8).
- Produces:
  - `class MonitorRepository(client: WebSocketClient, history: HistoryRepository, scope: CoroutineScope)`: `fun start(url: String)`, `fun stop()`; exposes `client.messages`/`connectionState`; internally writes every status to history (5s throttle).
  - `class MonitorViewModel(app: Application) : AndroidViewModel` — builds real `WebSocketClient`, `HistoryRepository(Room.databaseBuilder(..).build().historyDao())`, `SettingsStore` via `PreferenceDataStoreFactory` singleton; exposes `status: StateFlow<SystemStatus?>`, `connection: StateFlow<ConnectionState>`, `lastError: StateFlow<String?>`, `fun connect(ip: String, port: Int)`, `fun disconnect()`, `suspend fun historySamples(start: Long): List<HistorySample>`; starts with persisted settings on `init`.
- [ ] **Step 1: Write failing ViewModel test** — fake WebSocketClient (MutableSharedFlow-driven), fake HistoryRepository, fake SettingsStore; connect() → status flows, 5s throttle recorded once.
- [ ] **Step 2: Implement.**
- [ ] **Step 3: Run tests → pass.**

### Task 10: Android — theme + shared components

**Files:**
- Modify: `app/src/main/java/com/example/pchwmonitor/ui/theme/Color.kt`, `Theme.kt`
- Create: `app/src/main/java/com/example/pchwmonitor/ui/theme/ThemeMode.kt` (re-export of data ThemeMode — actually consume from data package)
- Create: `app/src/main/java/com/example/pchwmonitor/ui/components/RadialGauge.kt`
- Create: `app/src/main/java/com/example/pchwmonitor/ui/components/LineChart.kt`
- Create: `app/src/main/java/com/example/pchwmonitor/ui/components/FilledBar.kt`
- Create: `app/src/main/java/com/example/pchwmonitor/ui/components/MetricCard.kt`
- Create: `app/src/main/java/com/example/pchwmonitor/ui/components/ConnectionBar.kt`
- Create: `app/src/main/java/com/example/pchwmonitor/ui/components/TemperatureColor.kt`

**Interfaces:**
- Consumes: domain models.
- Produces:
  - `TemperatureColor.tempColor(tempC: Float): Color` — green <60, yellow 60–75, orange 75–85, red >85.
  - `RadialGauge(value: Float, max: Float, color: Color, label: String, modifier)` — 270° arc Canvas + big value text.
  - `LineChart(points: List<Float>, color: Color, min: Float, max: Float, modifier)` — smooth-ish polyline Canvas with gradient fill; empty state draws "—".
  - `FilledBar(valuePct: Float, color: Color, modifier)` — rounded progress bar.
  - `MetricCard(title: String, content: @Composable () -> Unit, modifier)` — Material3 `Card` with title.
  - `ConnectionBar(state: ConnectionState, pcName: String?, modifier)` — colored dot + text, 3 states.
  - Theme: extend `Color.kt` with dashboard semantic colors (tempYellow, tempOrange, tempRed, chartBlue); `Theme.kt` gains `fun PcHwMonitorTheme(themeMode: ThemeMode, content)` forcing light/dark.
- [ ] **Step 1: Implement all components.**
- [ ] **Step 2: Verify compile via assembleDebug.**

### Task 11: Android — Dashboard screen

**Files:**
- Create: `app/src/main/java/com/example/pchwmonitor/ui/dashboard/DashboardScreen.kt`
- Create: `app/src/main/java/com/example/pchwmonitor/ui/dashboard/CpuCard.kt`
- Create: `app/src/main/java/com/example/pchwmonitor/ui/dashboard/GpuCard.kt`
- Create: `app/src/main/java/com/example/pchwmonitor/ui/dashboard/RamCard.kt`
- Create: `app/src/main/java/com/example/pchwmonitor/ui/dashboard/RingBuffer.kt`

**Interfaces:**
- Consumes: components (Task 10), `SystemStatus`, `ConnectionState`.
- Produces:
  - `class RingBuffer(capacity: Int = 60)` — thread-safe append of `SystemStatus`-derived float; `snapshot(): List<Float>`.
  - `DashboardScreen(status: SystemStatus?, connection: ConnectionState, modifier)` — LazyColumn: ConnectionBar, CPU/GPU/RAM cards; each card: big radial gauge (temp), usage bar, clock/power rows, 60-point sparkline fed from a `remember` RingBuffer updated in `LaunchedEffect(status)`.
  - CpuCard: temp gauge + hotspot n/a; loads strip (small horizontal bars from `cpu.loads`).
  - GpuCard: temp gauge + hotspot gauge, VRAM FilledBar with used/total MB, clocks row.
  - RamCard: usage FilledBar with GB text, clock row.
- [ ] **Step 1: Implement RingBuffer + cards + screen.**
- [ ] **Step 2: Verify compile.**

### Task 12: Android — History + Settings screens, Navigation, MainActivity

**Files:**
- Create: `app/src/main/java/com/example/pchwmonitor/ui/history/HistoryScreen.kt`
- Create: `app/src/main/java/com/example/pchwmonitor/ui/settings/SettingsScreen.kt`
- Create: `app/src/main/java/com/example/pchwmonitor/ui/navigation/AppNavHost.kt`
- Modify: `app/src/main/java/com/example/pchwmonitor/MainActivity.kt`

**Interfaces:**
- Consumes: `MonitorViewModel`, `LineChart`, `SettingsStore.AppSettings`.
- Produces:
  - `HistoryScreen(viewModel, modifier)` — metric chips row (6 metrics), loads `history(now-1h)` via repository scope in VM (`fun historySamples(): List<HistorySample>`), draws `LineChart` + min/max labels.
  - `SettingsScreen(settings: AppSettings, onSave: (ip, port, theme) -> Unit, onConnect: () -> Unit, connection: ConnectionState, modifier)` — OutlinedTextFields for IP/port, theme radio row, Save + Connect buttons.
  - `AppNavHost(viewModel, modifier)` — `Scaffold` with `NavigationBar` 3 items (Dashboard/History/Settings), `NavHost` with `popUpTo(startDestination){saveState=true}` + `launchSingleTop` patterns.
  - `MainActivity` — collects settings, wraps `AppNavHost` in `PcHwMonitorTheme(themeMode)`.
- [ ] **Step 1: Implement screens + nav + activity.**
- [ ] **Step 2: Verify compile.**

### Task 13: Android — Localization (13 locales)

**Files:**
- Create: `app/src/main/res/values-fr/strings.xml`, `values-de/`, `values-es/`, `values-it/`, `values-pt/`, `values-pt-rBR/`, `values-ru/`, `values-tr/`, `values-pl/`, `values-nl/`, `values-zh/`, `values-zh-rTW/`, `values-ja/`
- Modify: `app/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes: string keys referenced in Tasks 11-12 code.
- Produces: complete key parity across 13 locales + EN default.
- Key set: `app_name, tab_dashboard, tab_history, tab_settings, cpu, gpu, ram, cpu_temp, gpu_temp, gpu_hotspot, usage, core_clock, mem_clock, power, vram, vram_used, ram_used, available, ram_total, connected, connecting, disconnected, reconnecting, server, server_ip, port, theme, theme_system, theme_light, theme_dark, save, connect, cancel, no_data, history, last_hour, min, max, metric_cpu_temp, metric_cpu_usage, metric_gpu_temp, metric_gpu_usage, metric_gpu_hotspot, metric_ram_usage, source, pc, unknown, error_connect, loading, cores`.
- [ ] **Step 1: Write EN strings + TR strings.**
- [ ] **Step 2: Write remaining 11 locales** (translated by hand, verified term-by-term).
- [ ] **Step 3: Verify** — no missing keys (grep each file for the full key set; each locale file must contain every key).

### Task 14: Android SDK setup + End-to-End verification + README

**Files:**
- Modify: `local.properties` (point `sdk.dir` to Linux SDK path)
- Create: `README.md`

- [ ] **Step 1: Install Android SDK** (cmdline-tools → `sdkmanager "platforms;android-37" "build-tools;37.0.0" "platform-tools"`; accept licenses).
- [ ] **Step 2: `./gradlew :app:assembleDebug`** — must succeed (JDK auto-provisioned; AGP 9.3.1 needs JDK 17+; fix any version conflicts found).
- [ ] **Step 3: `./gradlew :app:testDebugUnitTest`** — all unit tests pass.
- [ ] **Step 4: Server verification** — `pip install -r server/requirements-dev.txt`; `pytest server/tests -v` pass; run `python3 server/main.py --simulate` + `smoke_test.py` pass.
- [ ] **Step 5: Write README.md** — setup (Windows: LHM enable Remote Web Server 8085 + run server; Linux/macOS: `--simulate`), Android build/install, protocol summary, troubleshooting.
