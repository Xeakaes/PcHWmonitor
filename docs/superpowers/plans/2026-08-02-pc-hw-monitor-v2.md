# PC HW Monitor v2 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Sıfır-kurulum tek EXE (gömülü LibreHardwareMonitorLib okuma), iGPU kartı, dil seçici ve özel logo.

**Architecture:** Sunucu tarafında yeni `LhmLibAdapter` (pythonnet + DLL, işlem içi sensör okuma) mevcut HTTP adaptörüyle aynı şemayı üretir; `main.py --source auto/http/lib` ile seçilir. `StatusMessage`'a isteğe bağlı `igpu` alanı eklenir (Dashboard'da ikinci kart; geçmişe girmez). Android'de `SettingsStore.language` + `AppCompatDelegate.setApplicationLocales` ile dil seçici; PIL ile üretilmiş adaptif logo.

**Tech Stack:** Python 3.14 + pythonnet 3.1 + PyInstaller (Windows'ta), FastAPI/uvicorn; Kotlin/Jetpack Compose + DataStore + kotlinx.serialization.

## Global Constraints

- **Yorum kuralı:** Satır satır yorum YOK. Yalnızca kod bölümü başlarında kısa İNGİLİZCE bölüm başlığı yorumları (`# -- sensor lookup --`). Kullanıcı isteği, spec §7.
- Dil etiketleri BCP-47: `fr, de, es, it, pt, pt-BR, ru, tr, pl, nl, zh, zh-TW, ja`; `null` = sistem. (Spec §3.5)
- Git repo yok → "commit" adımı yok; her görev kendi doğrulamasıyla biter.
- Geriye dönük uyumluluk: HTTP adaptörü ve eski fixture'lar aynen çalışmalı; `igpu` her iki yönde isteğe bağlı (spec §4).
- Sunucu test komutu: `server/.venv/bin/python -m pytest server/tests -v` (Linux).
- Android test komutu: `./gradlew :app:testDebugUnitTest` ve `./gradlew :app:assembleDebug` (Linux, `JAVA_HOME=~/jdk21`).
- Windows komutları: `/mnt/c/Windows/System32/cmd.exe /c "..."` üzerinden; Windows venv: `C:\Users\msi\PcHWmonitor\server\.venv`.
- Windows kopyası senkron: `rsync -a --exclude .venv --exclude __pycache__ server/ /mnt/c/Users/msi/PcHWmonitor/server/` (uygulama tarafı: `app/` + `gradle/` + `build.gradle.kts` + `settings.gradle.kts` + `gradle.properties` + `gradlew*`).

---

### Task 1: Sunucu — `igpu` alanı (schema + HTTP adaptör + simülatör + testler)

**Files:**
- Modify: `server/schema.py`
- Modify: `server/adapters/lhm.py`
- Modify: `server/adapters/simulator.py`
- Modify: `server/tests/test_lhm_v2.py`, `server/tests/test_schema.py`, `server/tests/test_simulator.py`

**Interfaces:**
- Consumes: mevcut `GpuInfo`, `StatusMessage`, `_hardware_nodes`, `_pick`, `_sensors`, `_find`, `_loads`, `_clock_max`, `_num` (adapters/lhm.py).
- Produces: `StatusMessage.igpu: GpuInfo | None`; `LhmAdapter._parse` artık `gpu`=Nvidia/AMD, `igpu`=Intel döndürür; `Simulator.sample()` `igpu` üretir.

- [ ] **Step 1: Failing test — schema'da `igpu`**

`server/tests/test_schema.py`'ye ekle (mevcut dosyanın sonuna):

```python
def test_status_message_serializes_igpu_field():
    gpu = {"name": "Intel UHD", "usagePct": 12.5, "tempC": None}
    msg = StatusMessage(timestamp=1, igpu=GpuInfo(**gpu))
    data = json.loads(msg.model_dump_json())
    assert data["igpu"] == gpu
```

`server/tests/test_schema.py` zaten `json` ve `StatusMessage` import ediyorsa ekleme yapma; `GpuInfo` importunu da kontrol et. Çalıştır:

```
server/.venv/bin/python -m pytest server/tests/test_schema.py::test_status_message_serializes_igpu_field -v
```

Beklenen: FAIL (`igpu` alanı yok).

- [ ] **Step 2: Failing test — HTTP adaptörü Intel iGPU'yu ayrıştırır**

`server/tests/test_lhm_v2.py`'ye ekle (fixture'da `/gpu-intel-integrated/...` düğümü zaten var):

```python
def test_lhm_v2_parses_integrated_gpu():
    msg = _adapter(_load_fixture()).fetch()
    assert msg.igpu is not None
    assert msg.igpu.name == "Intel(R) UHD Graphics"
```

Beklenen: FAIL (`igpu` attr yok).

- [ ] **Step 3: Implement — schema + adaptör + simülatör**

`server/schema.py`: `StatusMessage`'a ekle:

```python
    igpu: GpuInfo | None = None
```

`server/adapters/lhm.py`:

```python
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

        pc = PcInfo(name=platform.node(), os=_os_name(), source="librehardwaremonitor")
        return StatusMessage(timestamp=int(time.time()), pc=pc, cpu=cpu, gpu=gpu, igpu=igpu, ram=ram)
```

`_pick_gpu`'dan Intel'i ayır, `_pick_igpu` ekle:

```python
def _pick_gpu(nodes: list[dict]) -> dict | None:
    for hardware_type, id_prefix in (("GpuNvidia", "/gpu-nvidia"), ("GpuAmd", "/gpu-amd")):
        node = _pick(nodes, hardware_type, (id_prefix,))
        if node is not None:
            return node
    return None


def _pick_igpu(nodes: list[dict]) -> dict | None:
    return _pick(nodes, "GpuIntel", ("/gpu-intel",))
```

`_parse` içindeki `os_name` bloklarını değiştirme; mevcut yapıyı koru (sadece yukarıdaki gibi `_os_name()` kullanıyorsan her ikisini de değiştir, değilse mevcut satırları aynen bırakıp `igpu` ekle).

`server/adapters/simulator.py`: `sample()` sonunda `igpu` ekle (CPU ile korelasyonlu, düşük değerler):

```python
        igpu = GpuInfo(
            name="Intel UHD Graphics",
            usagePct=round(clamp(2 + cpu_usage * 0.35 + r.uniform(-4, 4), 0, 100), 1),
            tempC=None,
            coreClockMhz=round(clamp(300 + (cpu_usage / 100.0) * 1100 + r.uniform(-40, 40), 300, 1500), 0),
            powerW=round(clamp(0.2 + cpu_usage * 0.02 + r.uniform(-0.2, 0.2), 0, 15), 1),
        )
```

ve `StatusMessage(...)` çağrısına `igpu=igpu,` ekle.

- [ ] **Step 4: Tüm sunucu testleri**

```
server/.venv/bin/python -m pytest server/tests -v
```

Beklenen: 11 mevcut + 2 yeni = 13 PASS. (`test_lhm.py` eski fixture'da GpuIntel yok → `igpu` None; değişmemiş.)

- [ ] **Step 5: Simülatör testi**

`server/tests/test_simulator.py`'ye ekle:

```python
def test_simulator_igpu_values():
    msg = Simulator(seed=7).sample()
    assert msg.igpu is not None
    assert msg.igpu.name == "Intel UHD Graphics"
    assert 0 <= msg.igpu.usagePct <= 100
    assert msg.igpu.tempC is None
```

`test_simulator.py` içindeki importları kontrol et (`Simulator` zaten import ediliyor olmalı). Çalıştır: `server/.venv/bin/python -m pytest server/tests/test_simulator.py -v` → PASS.

---

### Task 2: Sunucu — gömülü LHM okuma (`adapters/lhm_lib.py`) + kaynak seçimi (`main.py`)

**Files:**
- Create: `server/adapters/lhm_lib.py`
- Modify: `server/main.py`
- Create: `server/tests/test_main.py`

**Interfaces:**
- Produces: `LhmLibAdapter(lib_dir: str | None = None)` → `fetch() -> StatusMessage`; `main.py build_app(source: str = "auto", ...)`.
- Consumes: `StatusMessage`, `CpuInfo`, `GpuInfo`, `RamInfo`, `PcInfo`; eşleştirme için `adapters.lhm` içindeki `_find`, `_loads`, `_clock_max`, `_num`.

- [ ] **Step 1: Implement — `lhm_lib.py`**

pythonnet nesneleri sözlüğe çevrilir, eşleştirme `adapters.lhm` içindeki ortak yardımcılarla yapılır (bölüm başlığı yorumları İngilizce):

```python
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
        htype = hw.HardwareType.ToString()
        found.setdefault(htype, hw)
        for sub in hw.SubHardware:
            sub.Update()
            found.setdefault(sub.HardwareType.ToString(), sub)
    return found


class LhmLibAdapter:
    def __init__(self, lib_dir: str | None = None):
        self._lib_dir = lib_dir
        self._computer = None
        self._load()

    # -- assembly load + computer setup --

    def _load(self) -> None:
        import clr  # pythonnet; imported lazily so Linux can import this module
        lib_dir = self._lib_dir or str(Path(__file__).resolve().parent.parent)
        clr.AddReference(str(Path(lib_dir) / "LibreHardwareMonitorLib.dll"))
        from LibreHardwareMonitor.Hardware import Computer

        computer = Computer()
        computer.IsCpuEnabled = True
        computer.IsGpuEnabled = True
        computer.IsMemoryEnabled = True
        computer.Open()
        self._computer = computer

    def fetch(self) -> StatusMessage:
        try:
            hw = _hardware_map(self._computer)
            cpu_node = hw.get("Cpu")
            gpu_node = hw.get("GpuNvidia") or hw.get("GpuAmd")
            igpu_node = hw.get("GpuIntel")
            mem_node = hw.get("Memory")

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
            powerW=(_find(sensors, ("Power",), "gpu total power")
                    or _find(sensors, ("Power",), "gpu power")
                    or _find(sensors, ("Power",), "gpu package")),
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
```

Not: `sensor.Name` yerine LHM lib'de `sensor.Name` doğrudur (`Text` değil). `node.Name` de aynı şekilde. Yorumlar bölüm başlığı olarak İngilizce, satır satır değil.

- [ ] **Step 2: Failing test — kaynak seçimi (`server/tests/test_main.py`)**

```python
from main import build_app


def test_build_app_http_source_uses_http_adapter():
    app = build_app(source="http")
    assert app.state.welcome.source == "librehardwaremonitor"


def test_build_app_auto_falls_back_to_http_without_dll():
    app = build_app(source="auto")
    assert app.state.welcome.source in ("lhm-lib", "librehardwaremonitor")


def test_build_app_lib_source_requests_dll():
    app = build_app(source="lib")
    assert app.state.welcome.source == "lhm-lib"
```

Beklenen: FAIL (`build_app` `source` parametresi yok). Not: Linux'ta `source="lib"` — `LhmLibAdapter.__init__` çağrısı `clr` import eder ve DLL'i bulamayınca exception atar → `build_app`'in lib modunda hatayı yutup `available:false` modunda mı, yoksa hata mı üreteceği tasarım kararı. Basit tut: `build_app(source="lib")` `LhmLibAdapter` kurulum hatasını yakalar, `sample` her çağrıda `available:false` döndüren bir fallback üretir:

```python
def _unavailable_sample() -> StatusMessage:
    return StatusMessage(timestamp=int(time.time()), available=False, error="LibreHardwareMonitorLib.dll not found")
```

- [ ] **Step 3: Implement — `main.py`**

`build_app` imzasına `source: str = "auto"` ekle; seçim:

```python
def build_app(*, simulate: bool = False, lhm_url: str = "http://127.0.0.1:8085/data.json",
              interval_ms: int = 1000, source: str = "auto") -> FastAPI:
    if simulate:
        sample = Simulator().sample
        source_name = "simulator"
        pc_name = platform.node() or "SIM-PC"
    else:
        chosen = source
        if chosen == "auto":
            chosen = "lib" if _lib_available() else "http"
        if chosen == "lib":
            try:
                adapter = LhmLibAdapter()
                sample = adapter.fetch
                source_name = "lhm-lib"
            except Exception as exc:
                sample = _unavailable_sample
                source_name = "lhm-lib"
                logger.warning("lhm-lib init failed (%s); will report unavailable", exc)
        else:
            adapter = LhmAdapter(lhm_url=lhm_url)
            sample = adapter.fetch
            source_name = "librehardwaremonitor"
        pc_name = platform.node()

    hub = Hub(sample=sample, interval_ms=interval_ms)
    app = FastAPI(title="PC HW Monitor bridge")
    app.state.hub = hub
    app.state.welcome = WelcomeMessage(intervalMs=interval_ms, serverName=pc_name, source=source_name, pcName=pc_name)
    # ... mevcut /health ve /ws endpoint'leri aynen kalır ...
    return app
```

`_lib_available()`:

```python
def _lib_available() -> bool:
    try:
        LhmLibAdapter()
        return True
    except Exception:
        return False
```

`main()` argparse'a ekle: `parser.add_argument("--source", choices=["auto", "http", "lib"], default="auto")` ve `build_app(..., source=args.source)`. İçe aktarmalar: `import time`, `from schema import StatusMessage`, `from adapters.lhm_lib import LhmLibAdapter`.

Not: `_lib_available()` her çağrıda yeni `Computer` açıyor → `build_app`'in başında bir kez çağrılır (auto modda), ardından açılan computer'ı kapatmak için `LhmLibAdapter`'a `close()` ekle:

```python
    def close(self) -> None:
        if self._computer is not None:
            try:
                self._computer.Close()
            except Exception:
                pass
```

ve `_lib_available` içinde kullan:

```python
def _lib_available() -> bool:
    try:
        adapter = LhmLibAdapter()
        adapter.close()
        return True
    except Exception:
        return False
```

- [ ] **Step 4: Testleri çalıştır**

```
server/.venv/bin/python -m pytest server/tests -v
```

Beklenen: 3 yeni test dahil hepsi PASS. (`auto` Linux'ta `_lib_available()` False → http.)

- [ ] **Step 5: Smoke test** (sunucu arka planda çalışırken)

```bash
server/.venv/bin/python server/main.py --simulate --port 8765 &
sleep 2
server/.venv/bin/python server/smoke_test.py
kill %1
```

Beklenen: `SMOKE TEST PASSED`.

---

### Task 3: Android — `igpu` model + Dashboard kartı

**Files:**
- Modify: `app/src/main/java/com/example/pchwmonitor/domain/model/SystemStatus.kt`
- Modify: `app/src/main/java/com/example/pchwmonitor/ui/dashboard/DashboardScreen.kt`
- Modify: `app/src/main/java/com/example/pchwmonitor/ui/dashboard/GpuCard.kt`
- Modify: `app/src/test/java/com/example/pchwmonitor/StatusParserTest.kt`
- Modify: `app/src/main/res/values/strings.xml` + 13 dil dosyası (`values-*/strings.xml`)

**Interfaces:**
- Produces: `SystemStatus.igpu: GpuInfo? = null`; `DashboardScreen` yeni parametre `labelIntegratedGpu: String`; `GpuCard` yeni parametre `titleFallback: String = "GPU"`.
- Consumes: mevcut `SystemStatus`, `GpuCard`, `DashboardScreen` yapısı.

- [ ] **Step 1: Failing test — parser `igpu` ayrıştırır**

`StatusParserTest.kt`'ye ekle (dosya sonuna; mevcut yardımcı fonksiyonları kullan):

```kotlin
@Test
fun parse_status_with_igpu() {
    val raw = """{"type":"status","timestamp":1,"igpu":{"name":"Intel UHD Graphics","usagePct":12.5}}"""
    val message = StatusParser.parse(raw)
    val status = (message as WsMessage.Status).status
    assertEquals("Intel UHD Graphics", status.igpu?.name)
    assertEquals(12.5f, status.igpu?.usagePct)
}

@Test
fun parse_status_without_igpu_keeps_null() {
    val raw = """{"type":"status","timestamp":1,"gpu":{"name":"RTX"}}"""
    val status = (StatusParser.parse(raw) as WsMessage.Status).status
    assertNull(status.igpu)
}
```

Beklenen: FAIL (`igpu` modelde yok).

- [ ] **Step 2: Implement — model**

`SystemStatus.kt`: `val igpu: GpuInfo? = null,` (gpu'dan sonra) ekle.

- [ ] **Step 3: Implement — GpuCard başlık fallback**

`GpuCard.kt` imzasına `titleFallback: String = "GPU"` ekle; `MetricCard(title = gpu?.name ?: titleFallback, ...)`.

- [ ] **Step 4: Implement — DashboardScreen iGPU kartı**

`DashboardScreen.kt` imzasına `labelIntegratedGpu: String` ekle; GPU kartı item'ından sonra yeni item:

```kotlin
if (status.igpu != null) {
    item {
        GpuCard(
            gpu = status.igpu,
            titleFallback = labelIntegratedGpu,
            labelTemp = labelGpuTemp,
            labelHotspot = labelHotspot,
            labelUsage = labelUsage,
            labelVram = labelVram,
            labelCoreClock = labelCoreClock,
            labelMemClock = labelMemClock,
            labelPower = labelPower,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
    }
}
```

- [ ] **Step 5: Strings — 14 dosyaya `label_integrated_gpu`**

Tüm `values*/strings.xml` dosyalarına (`values`, `values-fr`, `values-de`, `values-es`, `values-it`, `values-pt`, `values-pt-rBR`, `values-ru`, `values-tr`, `values-pl`, `values-nl`, `values-zh`, `values-zh-rTW`, `values-ja`) ekle:

```xml
<string name="label_integrated_gpu">İntegre GPU</string>
```

Her dilde kendi çevirisi (ör. `values-de` → `Integrierte GPU`, `values-ja` → `内蔵GPU`). En azından base (`values`) `İntegre GPU` olur. Anahtarlar 14 dosyada da aynı adla bulunmalı.

- [ ] **Step 6: Doğrula**

```
./gradlew :app:testDebugUnitTest
```

Beklenen: tüm testler PASS (yeni 2 dahil).

---

### Task 4: Android — dil seçici

**Files:**
- Modify: `app/src/main/java/com/example/pchwmonitor/data/SettingsStore.kt`
- Modify: `app/src/main/java/com/example/pchwmonitor/MainActivity.kt`
- Modify: `app/src/main/java/com/example/pchwmonitor/MonitorViewModel.kt`
- Modify: `app/src/main/java/com/example/pchwmonitor/ui/settings/SettingsScreen.kt`
- Modify: `app/src/test/java/com/example/pchwmonitor/SettingsStoreTest.kt`
- Modify: `gradle/libs.versions.toml`, `app/build.gradle.kts` (appcompat)
- Modify: `app/src/main/res/values*/strings.xml` (14 dosya)

**Interfaces:**
- Produces: `AppSettings.language: String? = null`; `SettingsStore.setLanguage(String?)`; `MonitorViewModel.setLanguage(String?)`; `SettingsScreen(onSave: (ip, port, theme, language) -> Unit)`; `MainActivity` locale uygular.
- Consumes: `AppCompatDelegate.setApplicationLocales`, `LocaleListCompat.forLanguageTags`.

- [ ] **Step 1: Failing test — SettingsStore dil**

`SettingsStoreTest.kt`'ye ekle (mevcut DataStore test kalıbıyla aynı kurulum):

```kotlin
@Test
fun language_roundtrip() = runTest {
    val store = SettingsStore(testDataStore)
    store.setLanguage("tr")
    assertEquals("tr", store.settings.first().language)
    store.setLanguage(null)
    assertNull(store.settings.first().language)
}
```

`testDataStore` yardımcısı mevcut test dosyasında nasıl kurulduysa aynen kullan. Beklenen: FAIL (`language` yok).

- [ ] **Step 2: Implement — SettingsStore**

```kotlin
data class AppSettings(
    val serverIp: String = "192.168.1.100",
    val serverPort: Int = 8765,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val language: String? = null,
)
```

Store'a:

```kotlin
private val keyLanguage = stringPreferencesKey("language")

// settings flow'a: language = prefs[keyLanguage]
suspend fun setLanguage(value: String?) {
    dataStore.edit { prefs ->
        if (value == null) prefs.remove(keyLanguage) else prefs[keyLanguage] = value
    }
}
```

- [ ] **Step 3: Implement — ViewModel**

```kotlin
suspend fun setLanguage(language: String?) = settingsStore.setLanguage(language)
```

`saveSettings(ip, port, theme, language: String?)` imzasını genişlet; `settingsStore.setLanguage(language)` çağır.

- [ ] **Step 4: Implement — MainActivity (locale uygula)**

`app/build.gradle.kts` + `gradle/libs.versions.toml`: `androidx.appcompat:appcompat` ekle (toml'a `appcompat = "1.7.0"` versiyon ve `libs.androidx.appcompat` referansı; sürümü toml'daki mevcut kalıpla uyumlu seç). MainActivity:

```kotlin
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: MonitorViewModel = viewModel()
            val settings by viewModel.settings.collectAsState()
            androidx.compose.runtime.LaunchedEffect(settings.language) {
                applyLanguage(settings.language)
            }
            PcHWMonitorTheme(themeMode = settings.theme) {
                AppNavHost(viewModel = viewModel)
            }
        }
    }

    private fun applyLanguage(language: String?) {
        val current = AppCompatDelegate.getApplicationLocales().toLanguageTags()
        val target = language ?: ""
        if (target != current) {
            if (target.isEmpty()) {
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
            } else {
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(target))
            }
        }
    }
}
```

İmportlar: `androidx.appcompat.app.AppCompatActivity`, `androidx.appcompat.app.AppCompatDelegate`, `androidx.core.os.LocaleListCompat`, `androidx.compose.runtime.LaunchedEffect`, `androidx.lifecycle.viewmodel.compose.viewModel`.

- [ ] **Step 5: Implement — SettingsScreen dil menüsü**

Yeni parametreler: `labelLanguage: String`, `labelLanguageSystem: String`, `languages: List<Pair<String?, String>>` (kod→etiket, başta `null to labelLanguageSystem`), `onSave: (ip: String, port: Int, theme: ThemeMode, language: String?) -> Unit`.

Theme radyolarının altına (Kaydet butonundan önce):

```kotlin
Text(
    text = labelLanguage,
    style = MaterialTheme.typography.titleMedium,
    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
)
Spacer(modifier = Modifier.height(8.dp))
var expanded by remember { mutableStateOf(false) }
var language by remember { mutableStateOf(settings.language) }
ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
    OutlinedTextField(
        value = languages.firstOrNull { it.first == language }?.second ?: labelLanguageSystem,
        onValueChange = {},
        readOnly = true,
        label = { Text(labelLanguage) },
        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
        modifier = Modifier.fillMaxWidth().menuAnchor(),
    )
    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        languages.forEach { (code, label) ->
            DropdownMenuItem(
                text = { Text(label) },
                onClick = { language = code; expanded = false; saved = false },
            )
        }
    }
}
```

Kaydet butonunda `onSave(ip.trim(), portInt, theme, language)` çağır. İmportlar: `androidx.compose.material3.ExposedDropdownMenuBox`, `ExposedDropdownMenu`, `DropdownMenuItem`, `ExposedDropdownMenuDefaults`, `androidx.compose.foundation.layout.menuAnchor` (veya `androidx.compose.material3.menuAnchor` — hangisi mevcut Compose BOM'da derleniyorsa onu kullan).

- [ ] **Step 6: Strings — 14 dosya, 15 yeni anahtar**

Her `values*/strings.xml`'e şunları ekle (her dil kendi dilindeki çevirileriyle):

```xml
<string name="settings_language">Dil</string>
<string name="settings_language_system">Sistem (varsayılan)</string>
<string name="language_fr">Français</string>
<string name="language_de">Deutsch</string>
<string name="language_es">Español</string>
<string name="language_it">Italiano</string>
<string name="language_pt">Português</string>
<string name="language_pt_br">Português (Brasil)</string>
<string name="language_ru">Русский</string>
<string name="language_tr">Türkçe</string>
<string name="language_pl">Polski</string>
<string name="language_nl">Nederlands</string>
<string name="language_zh">简体中文</string>
<string name="language_zh_tw">繁體中文</string>
<string name="language_ja">日本語</string>
```

Dil adları tüm dosyalarda AYNI kalır (kendi dilinde); `settings_language` ve `settings_language_system` her dilde çevrilir. Tüm 14 dosyada aynı anahtar seti bulunmalı.

- [ ] **Step 7: Doğrula**

```
./gradlew :app:testDebugUnitTest
```

Beklenen: PASS. Anahtar eşitlik kontrolü: her `values-*/strings.xml` içindeki `name=` anahtarlarının `values/strings.xml` ile küme eşitliği bash ile doğrulanır (Task 6'da komut var).

---

### Task 5: Android — Logo

**Files:**
- Delete: `app/src/main/res/mipmap-*/ic_launcher.webp`, `ic_launcher_round.webp` (5 yoğunluk)
- Create: `app/src/main/res/mipmap-*/ic_launcher.png`, `ic_launcher_round.png` (PIL ile)
- Modify: `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`, `ic_launcher_round.xml`
- Create: `app/src/main/res/drawable/ic_launcher_foreground.xml`

**Interfaces:** — (tek başına teslim edilebilir görsel)

- [ ] **Step 1: Vektör ön plan**

`app/src/main/res/drawable/ic_launcher_foreground.xml` — 108dp kanvas, 72dp görünür alan, termometre motifi:

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <group android:scaleX="0.5" android:scaleY="0.5" android:pivotX="54" android:pivotY="54">
        <path
            android:fillColor="#4DD0E1"
            android:pathData="M54,36 a10,10 0 1,0 0.01,0 z" />
        <path
            android:strokeColor="#FFFFFF"
            android:strokeWidth="6"
            android:strokeLineCap="round"
            android:fillColor="#00000000"
            android:pathData="M54,46 v34" />
        <path
            android:strokeColor="#FFFFFF"
            android:strokeWidth="6"
            android:strokeLineCap="round"
            android:fillColor="#00000000"
            android:pathData="M54,80 a18,18 0 1,1 0,-0.01 z" />
    </group>
</vector>
```

(İnce ayar: termometre gövdesi + hazne; derleme sırasında görsel olarak kontrol edilir, sadece çizim mantığı önemli.)

- [ ] **Step 2: Adaptif XML**

`mipmap-anydpi-v26/ic_launcher.xml`:

```xml
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
</adaptive-icon>
```

`ic_launcher_round.xml` aynısı. `values/colors.xml` (yoksa `values/ic_launcher_background.xml` olarak) ekle:

```xml
<color name="ic_launcher_background">#0B1220</color>
```

(colors.xml yoksa `values/colors.xml` oluştur; varsa ekle.)

- [ ] **Step 3: PIL ile legacy PNG'ler**

WSL'de `python3` ve pillow yoksa: `pip install pillow` (sistem pip'i veya `server/.venv/bin/pip`). Ardından bir defalık script (geçici dosya, repo'ya girmez):

```python
from PIL import Image, ImageDraw

SIZE = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
BG = (11, 18, 32, 255)
TEAL = (77, 208, 225, 255)
WHITE = (255, 255, 255, 255)

def draw_icon(px: int) -> Image.Image:
    img = Image.new("RGBA", (px, px), BG)
    d = ImageDraw.Draw(img)
    # rounded square corners are provided by the launcher; keep a full-square draw
    bulb_r = px * 0.10
    cx, cy = px / 2, px * 0.42
    d.ellipse([cx - bulb_r, cy - bulb_r, cx + bulb_r, cy + bulb_r], fill=TEAL)
    lw = max(2, int(px * 0.06))
    x0, x1 = cx, cx
    d.line([x0, cy + bulb_r, x1, px * 0.86], fill=WHITE, width=lw)
    ring_r = px * 0.22
    d.ellipse([cx - ring_r, px * 0.86 - ring_r, cx + ring_r, px * 0.86 + ring_r], outline=WHITE, width=lw)
    # inner fill line (mercury)
    d.line([x0, px * 0.80, x1, px * 0.86], fill=TEAL, width=lw)
    return img

for name, px in SIZE.items():
    draw_icon(px).save(f"/home/xeakaes/PcHWmonitor/app/src/main/res/mipmap-{name}/ic_launcher.png")
    draw_icon(px).save(f"/home/xeakaes/PcHWmonitor/app/src/main/res/mipmap-{name}/ic_launcher_round.png")
```

Webp dosyalarını sil: `rm app/src/main/res/mipmap-*/ic_launcher.webp app/src/main/res/mipmap-*/ic_launcher_round.webp`.

- [ ] **Step 4: Doğrula**

```
./gradlew :app:assembleDebug
```

Beklenen: BUILD SUCCESSFUL (aapt ikonları kabul eder).

---

### Task 6: Android — tam doğrulama + locale anahtar kontrolü + senkron

**Files:** (değişiklik yok — doğrulama)

- [ ] **Step 1: Birim testler + build**

```
./gradlew :app:testDebugUnitTest && ./gradlew :app:assembleDebug
```

Beklenen: tüm testler PASS, `app-debug.apk` üretilir.

- [ ] **Step 2: Locale anahtar eşitliği**

```bash
for d in app/src/main/res/values-*/; do
  comm -3 <(grep -oP 'name="\K[^"]+' app/src/main/res/values/strings.xml | sort) \
          <(grep -oP 'name="\K[^"]+' "$d/strings.xml" | sort) | diff - /dev/null || echo "FARK: $d";
done
```

Beklenen: hiçbir "FARK" çıkmaz.

- [ ] **Step 3: Windows kopyasına senkron**

```bash
rsync -a --exclude server/.venv --exclude app/build --exclude build --exclude .gradle --exclude .kotlin \
  /home/xeakaes/PcHWmonitor/app /home/xeakaes/PcHWmonitor/gradle /home/xeakaes/PcHWmonitor/build.gradle.kts \
  /home/xeakaes/PcHWmonitor/settings.gradle.kts /home/xeakaes/PcHWmonitor/gradle.properties /home/xeakaes/PcHWmonitor/gradlew \
  /home/xeakaes/PcHWmonitor/gradlew.bat /home/xeakaes/PcHWmonitor/local.properties \
  /mnt/c/Users/msi/PcHWmonitor/
rsync -a --exclude .venv --exclude __pycache__ server/ /mnt/c/Users/msi/PcHWmonitor/server/
```

(local.properties Windows SDK yolunu korur — mevcut dosyayı ezme.)

---

### Task 7: Windows — pythonnet smoke testi + EXE derleme + çalışma doğrulaması

**Files:**
- Create: `server/build_exe.bat` (repo + Windows kopyası)
- Modify: `server/requirements-dev.txt` (pythonnet, pyinstaller)
- Modify: `README.md` (EXE kullanımı, iGPU, dil, AGPL notu)

**Interfaces:**
- Produces: `C:\Users\msi\PcHWmonitor\server\dist\PcHwMonitor.exe` (tek dosya, konsolsuz).
- Consumes: `C:\Users\msi\LibreHardwareMonitor\LibreHardwareMonitorLib.dll` (kaynak DLL seti).

- [ ] **Step 1: Windows venv'e bağımlılıklar**

```bash
/mnt/c/Windows/System32/cmd.exe /c "cd /d C:\Users\msi\PcHWmonitor\server && .venv\Scripts\python -m pip install pythonnet pyinstaller"
```

`requirements-dev.txt`'e ekle:

```
pythonnet>=3.1.0
pyinstaller>=6.10
```

- [ ] **Step 2: pythonnet + DLL smoke testi (kaynak modunda)**

```bash
/mnt/c/Windows/System32/cmd.exe /c "cd /d C:\Users\msi\PcHWmonitor\server && .venv\Scripts\python -c \"import clr; clr.AddReference(r'C:\Users\msi\LibreHardwareMonitor\LibreHardwareMonitorLib.dll'); from LibreHardwareMonitor.Hardware import Computer; c=Computer(); c.IsCpuEnabled=True; c.IsGpuEnabled=True; c.IsMemoryEnabled=True; c.Open(); hw=next(iter(c.Hardware)); hw.Update(); print('OK', hw.HardwareType)\""
```

Beklenen: `OK Cpu` benzeri çıktı. **Karar noktası:** Çıktıda CPU sensörleri (sıcaklık) geliyor mu diye yönetici olmayan kabukta kontrol et. Eğer CPU sıcaklığı null ise (Ring0 sürücüsü admin ister) → EXE'ye UAC manifest ekle (Step 4'te `--manifest`), README'ye "yönetici olarak çalıştırın" notu. Sensörler geliyorsa manifest gerekmez.

- [ ] **Step 3: `build_exe.bat`**

`server/build_exe.bat` (Windows kopyasında çalışır):

```bat
@echo off
setlocal
cd /d %~dp0
set VENV=.venv
set LHMDIR=C:\Users\msi\LibreHardwareMonitor

if not exist "%LHMDIR%\LibreHardwareMonitorLib.dll" (
  echo LibreHardwareMonitorLib.dll bulunamadi: %LHMDIR%
  exit /b 1
)

rmdir /s /q dist build 2>nul

"%VENV%\Scripts\python.exe" -m PyInstaller ^
  --onefile --noconsole --name PcHwMonitor ^
  --add-data "%LHMDIR%\LibreHardwareMonitorLib.dll;." ^
  main.py

echo.
echo Bitti: dist\PcHwMonitor.exe
```

Not: Sadece ana DLL ile başla. PyInstaller çıktısında ya da çalıştırma sırasında `FileNotFoundException` (bağımlı assembly) olursa `--add-data` satırlarına ek DLL'ler ekle: `HidSharp.dll`, `System.Text.Json.dll` vb. (`%LHMDIR%` altından).

- [ ] **Step 4: EXE derle ve çalıştır**

```bash
/mnt/c/Windows/System32/cmd.exe /c "cd /d C:\Users\msi\PcHWmonitor\server && build_exe.bat"
```

Sonra:

```bash
/mnt/c/Windows/System32/cmd.exe /c "start /b C:\Users\msi\PcHWmonitor\server\dist\PcHwMonitor.exe"
sleep 5
/mnt/c/Windows/System32/curl.exe -s http://127.0.0.1:8765/health
/mnt/c/Windows/System32/cmd.exe /c "cd /d C:\Users\msi\PcHWmonitor\server && .venv\Scripts\python smoke_test.py"
/mnt/c/Windows/System32/cmd.exe /c "taskkill /f /im PcHwMonitor.exe"
```

Beklenen: `/health` → `{"ok":true,"source":"lhm-lib",...}`, smoke_test PASS (welcome + status mesajları). `source` `lhm-lib` ise gömülü okuma devrede; `/health` yanıtındaki `source` alanı `"lhm-lib"` olmalı. Status verisi gerçek sensörlerden geldiğini doğrula (smoke_test çıktısında cpu/gpu alanları dolu).

- [ ] **Step 5: README güncelle**

`README.md`'ye:
- "Sıfır kurulum" bölümü: `dist\PcHwMonitor.exe` çift tıkla (veya yönetici), LHM kurulumu gerekmez; `--source http` ile eski akış.
- iGPU kartı ve dil seçici notları.
- AGPL notu: "LibreHardwareMonitorLib AGPL-3.0; kamuya dağıtımda proje açık kaynak olmalıdır."

- [ ] **Step 6: Senkron**

`build_exe.bat`, `requirements-dev.txt`, `README.md` değişikliklerini Windows kopyasına rsync'le (Task 6 Step 3 komutlarıyla).

---

### Task 8: Final doğrulama

- [ ] **Step 1: Sunucu tam test**

```
server/.venv/bin/python -m pytest server/tests -v && server/.venv/bin/python server/smoke_test.py
```

- [ ] **Step 2: Android tam doğrulama**

```
./gradlew :app:testDebugUnitTest && ./gradlew :app:assembleDebug
```

- [ ] **Step 3: Özet**

Kullanıcıya rapor: EXE yolu, Android'in Studio'da derlenecek durumda olduğu, iGPU kartı, dil seçici ve logo teslim edildiği.
