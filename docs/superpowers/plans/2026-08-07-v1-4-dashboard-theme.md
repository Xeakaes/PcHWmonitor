# v1.4 Dashboard Layout & Theme Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rebuild the v1.4 dashboard with user card hide/reorder (card menu + edit mode), a landscape/tablet layout engine that keeps pinned metrics on the first screen, and 5 selectable palettes including black-gold.

**Architecture:** Pure `DashboardLayout` (list of `LayoutEntry(card, visible, pinned)`) persisted as JSON in `SettingsStore`. Pure `LayoutEngine` maps (`layout`, width, height) → `RenderPlan` (columns, firstScreen, rest, isGrid) + `buildRows`. Cards get a shared `MetricCard.menu` header slot for hide/pin. `Palette.kt` provides 5 light/dark `ColorScheme` pairs keyed by `themePaletteId`. No server or `MonitorController` change.

**Tech Stack:** Kotlin 2.2, Compose BOM 2025.12 (Material3), kotlinx-serialization-json 1.7.3, DataStore, JUnit4 unit tests.

## Global Constraints

- Package root: `com.Obscrum.pchwmonitor`
- Card ids: `cpu`, `gpu`, `igpu`, `fps`, `ram`, `disk`, `net`, `fan`
- Palette ids: `default`, `ocean`, `ember`, `forest`, `gold`
- Default: all 8 visible; pinned = `cpu`,`gpu`,`fps`,`ram`
- Do NOT touch `MonitorController`/`WebSocketClient`
- Keep `isLandscapeLayout(maxWidth: Dp, maxHeight: Dp): Boolean` signature
- Existing tests stay green (31 app + 37 server)
- New strings → BOTH `values/strings.xml` and `values-tr/strings.xml`
- Card chart windows / chartMax / card internals unchanged
- No third-party drag lib

---

### Task 1: `DashboardLayout` model + serialization

**Files:** Create `app/src/main/java/com/obscrum/pchwmonitor/ui/dashboard/DashboardLayout.kt`; test `app/src/test/java/com/obscrum/pchwmonitor/DashboardLayoutTest.kt`

**Interfaces (produced):**
- `enum class CardId(val storageId: String) { CPU("cpu"), GPU("gpu"), IGPU("igpu"), FPS("fps"), RAM("ram"), DISK("disk"), NET("net"), FAN("fan") }` + `companion fun fromStorage(s): CardId?`
- `data class LayoutEntry(val card: CardId, val visible: Boolean = true, val pinned: Boolean = false)`
- `enum class CardPriority { PINNED, NORMAL }`; `fun LayoutEntry.priority(): CardPriority`
- `data class DashboardLayout(val entries: List<LayoutEntry> = emptyList())` with `visibleEntries()`, `toJson()`, `fromJson()` (tolerant: unknown cards dropped, failure→default), `companion default()`
- DTO pattern: serialize `card` as its `storageId` via a private DTO (avoid custom serializer)

- [ ] **Step 1: Write failing test** — `DashboardLayoutTest.kt` (assert): default has 8 entries, pinned order `[CPU,GPU,FPS,RAM]`, all visible; `fromJson(roundTrip toJson)==layout`; unknown `"bogus"` card skipped (only CPU remains); garbage JSON → default fallback; `visibleEntries()` filters hidden.
- [ ] **Step 2: Run** `cd app && ./gradlew testDebugUnitTest --tests "*DashboardLayoutTest"` → FAIL (unresolved).
- [ ] **Step 3: Implement** `DashboardLayout.kt`:

```kotlin
package com.obscrum.pchwmonitor.ui.dashboard

enum class CardId(val storageId: String) { CPU("cpu"), GPU("gpu"), IGPU("igpu"), FPS("fps"),
    RAM("ram"), DISK("disk"), NET("net"), FAN("fan");
    companion object { fun fromStorage(s: String): CardId? = entries.firstOrNull { it.storageId == s } }
}
enum class CardPriority { PINNED, NORMAL }
fun LayoutEntry.priority(): CardPriority = if (pinned) CardPriority.PINNED else CardPriority.NORMAL
data class LayoutEntry(val card: CardId, val visible: Boolean = true, val pinned: Boolean = false) {
    fun toDto() = LayoutEntryDto(card.storageId, visible, pinned)
    companion object { fun fromDto(d: LayoutEntryDto): LayoutEntry? =
        CardId.fromStorage(d.card)?.let { LayoutEntry(it, d.visible, d.pinned) } }
}
data class DashboardLayout(val entries: List<LayoutEntry> = emptyList()) {
    fun visibleEntries() = entries.filter { it.visible }
    fun toJson(): String = Json.encodeToString(entries.map { it.toDto() })
    fun fromJson(s: String): DashboardLayout = runCatching {
        DashboardLayout(Json.decodeFromString<List<LayoutEntryDto>>(s).mapNotNull(LayoutEntry::fromDto))
    }.getOrDefault(default())
    companion object {
        private val Json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        fun default() = DashboardLayout(listOf(
            LayoutEntry(CardId.CPU, pinned = true), LayoutEntry(CardId.GPU, pinned = true),
            LayoutEntry(CardId.IGPU), LayoutEntry(CardId.FPS, pinned = true),
            LayoutEntry(CardId.RAM, pinned = true), LayoutEntry(CardId.DISK),
            LayoutEntry(CardId.NET), LayoutEntry(CardId.FAN)))
    }
}
@kotlinx.serialization.Serializable
private data class LayoutEntryDto(val card: String, val visible: Boolean, val pinned: Boolean)
```
Note: implement in `DashboardLayout.kt` with imports `kotlinx.serialization.Serializable`, `kotlinx.serialization.builtins.ListSerializer`, `kotlinx.serialization.json.Json`; adjust to actual access. The public API (CardId/LayoutEntry/DashboardLayout + methods) must match.
- [ ] **Step 4: Run** → PASS (5/5)
- [ ] **Step 5: Commit** `feat(app): add DashboardLayout model with tolerant JSON serialization`

---

### Task 2: Persist layout + palette in SettingsStore

**Files:** Modify `app/src/main/java/com/obscrum/pchwmonitor/data/SettingsStore.kt`, `app/src/main/java/com/obscrum/pchwmonitor/MonitorViewModel.kt`; extend `app/src/test/java/com/obscrum/pchwmonitor/SettingsStoreTest.kt`

**Interfaces (produced):**
- `AppSettings` + `themePaletteId: String = "default"`, `dashboardLayout: DashboardLayout = DashboardLayout.default()`
- Store keys: `stringPreferencesKey("theme_palette")`, `stringPreferencesKey("dashboard_layout")`; changes in `settings` flow map
- `suspend fun setThemePalette(v: String)` (empty→"default"); `suspend fun setDashboardLayout(v: DashboardLayout)`
- VM: `val themePaletteId: StateFlow<String>`, `val dashboardLayout: StateFlow<DashboardLayout>`, `fun setThemePalette(id)`, `fun setDashboardLayout(layout)` (launch scope writes)

- [ ] **Step 1: tests** — defaults `"default"`; `setThemePalette("gold")` round-trips; `setDashboardLayout(layout)` round-trips `==`.
- [ ] **Step 2: Run** → FAIL.
- [ ] **Step 3: Implement** as described. Round-trip correctness relies on `DashboardLayout` (data class) equality and JSON storage.
- [ ] **Step 4: Run** → PASS (old tests + new).
- [ ] **Step 5: Commit** `feat(app): persist theme palette and dashboard layout in settings`

---

### Task 3: Layout engine (`LayoutHelper.kt`)

**Files:** Modify `app/src/main/java/com/obscrum/pchwmonitor/ui/dashboard/LayoutHelper.kt`; extend `app/src/test/java/com/obscrum/pchwmonitor/LayoutHelperTest.kt`

**Interfaces (produced):**
- `enum class DashboardSizeClass { PHONE, TABLET }`; `fun DashboardSizeClass.forWidth(maxWidth: Dp)` (=≥600→TABLET)
- `fun columnCount(size, maxWidth: Dp): Int` → PHONE=1; TABLET: <420→2, <1200→3, else→4
- `data class RenderPlan(columns: Int, firstScreen: List<CardId>, rest: List<CardId>, isGrid: Boolean)`
- `fun layoutDashboard(layout, size, maxWidth, landscape): RenderPlan` — landscape-phone: firstRow=pinned visible, rest=others, isGrid=true; else firstRow empty, rest=all visible, isGrid = size==TABLET || landscape
- `fun buildRows(plan): List<List<CardId>>` = `(firstScreen+rest).chunked(columns)`
- Keep `isLandscapeLayout(...)` unchanged

- [ ] **Step 1: tests** (append): phonePortrait1col (columns=1, firstRow empty, rest=8); phoneLandscapePins (`[cpu,gpu,fps,ram]` first, `[igpu,disk,net,fan]` rest, columns=2); tablet columns 2/3/4 at 700/1000/1300dp; forWidth 599/600; `buildRows` rows `[cpu,gpu]`,`[fps,ram]`,`[net,fan]`.
- [ ] **Step 2: Run** → FAIL.
- [ ] **Step 3: Implement** replacing `LayoutHelper.kt` (pure functions, no Compose imports beyond Dp).
- [ ] **Step 4: Run** → PASS.
- [ ] **Step 5: Commit** `feat(app): dashboard layout engine with pinned-first landscape and tablet columns`

---

### Task 4: Card menu model + `MetricCard` menu slot

**Files:** Create `app/src/main/java/com/obscrum/pchwmonitor/ui/dashboard/CardMenu.kt`; modify `app/src/main/java/com/obscrum/pchwmonitor/ui/components/MetricCard.kt`; test `app/src/test/java/com/obscrum/pchwmonitor/CardMenuTest.kt`

**Interfaces (produced):**
- `enum class CardMenuAction { HIDE, SHOW, PIN, UNPIN }`
- `fun applyLayoutAction(action, layout, card): DashboardLayout` — HIDE→visible=false; SHOW→true; PIN/UNPIN→pinned; unknown card→unchanged
- `fun applyReorder(layout, from: Int, to: Int): DashboardLayout` — out-of-range→unchanged
- `MetricCard(title, modifier, compact, menu: @Composable RowScope.() -> Unit = {}, content)` — header becomes `Row { Text(title, weight(1f)); menu() }`

- [ ] **Step 1: test** `CardMenuTest.kt`: hide FAN→visible=false; hide NET→SHOW→visible=true; PIN IGPU→true then UNPIN→false; reorder(2→5): entries[5].card==igpu and entries[2].card==fps; range-9 reorder → unchanged layout (default equality).
- [ ] **Step 2: Run** → FAIL.
- [ ] **Step 3: Implement** as described. `MetricCard` header:
```kotlin
Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
    Text(text=title, style=..., fontWeight=FontWeight.SemiBold, modifier=Modifier.weight(1f))
    menu() }
```
(imports: Row, RowScope, Alignment, fillMaxWidth)
- [ ] **Step 4: Run** → PASS; `./gradlew :app:compileDebugKotlin` OK (menu defaults `{}`).
- [ ] **Step 5: Commit** `feat(app): card menu action model and MetricCard header menu slot`

---

### Task 5: Card menu wiring + layout-aware dashboard

**Files:** Modify `app/src/main/java/com/obscrum/pchwmonitor/ui/dashboard/DashboardScreen.kt`, `FpsCard.kt` (move MoreVert into shared menu), `AppNavHost.kt`; strings in `values/strings.xml`, `values-tr/strings.xml`; extend `CardMenuTest.kt` (reorder-preserves-visibility).

**Interfaces (produced):**
- `DashboardScreen` new params: `layout: DashboardLayout`, `onLayoutChange: (DashboardLayout) -> Unit`, `editMode: Boolean` (default false), `onEditModeChange: (Boolean) -> Unit`, labels: `labelMenuHide`, `labelMenuPin`, `labelMenuUnpin`, `labelMenuEdit`, `labelEditDone`, `labelEditCancel`, `labelHiddenCards`
- strings EN/TR: `menu_hide` Hide card/Kartı gizle; `menu_pin` Keep on first screen/Ilk ekranda tut; `menu_unpin` Remove from first screen/Ilk ekrandan kaldır; `menu_edit_layout` Edit layout/Düzeni düzenle; `edit_done` Done/Bitti; `edit_cancel` Cancel/Iptal; `hidden_cards` Hidden cards/Gizli kartlar

- [ ] **Step 1: Add strings** to both `values/strings.xml` and `values-tr/strings.xml`
- [ ] **Step 2: FpsCard** — remove inner `IconButton(MoreVert)`; keep `showHint` dialog + `onFpsDetails: (() -> Unit)? = null`; pass `menu` through to `MetricCard` (Menu item "detaylar" calls `onFpsDetails`)
- [ ] **Step 3: DashboardScreen rewrite** — inside `BoxWithConstraints` compute `plan=layoutDashboard(...)`, `rows=buildRows(plan)`; render single `LazyColumn` (works for all sizes): each row = `Row(spaced 12) { cardIds.forEach { RenderCard(it, Modifier.weight(1f)) } }`. `RenderCard` dispatches to Cpu/Gpu/Fps/Ram/Disk/Net/Fan with existing args + shared `menu`. Kebab opens `DropdownMenu` with HIDE always; PIN/UNPIN depending `layout` entry pinned; FPS details item. Hidden cards (visible=false) NOT rendered now.
- [ ] **Step 4: AppNavHost** — pass `layout = viewModel.dashboardLayout.collectAsState().value`, `onLayoutChange = viewModel::setDashboardLayout`, local `rememberSaveable editMode`, + new strings
- [ ] **Step 5: Verify + commit** `feat(app): wire card menu and layout-aware dashboard rendering`

---

### Task 6: Edit mode (reorder + hidden management)

**Files:** Modify `app/src/main/java/com/obscrum/pchwmonitor/ui/dashboard/DashboardScreen.kt` (top-level edit controls block), `CardMenuTest.kt` (add regression guard).

**Interfaces:** consume `applyReorder`, `applyLayoutAction`; no new public API beyond Task 5 params.

- [ ] **Step 1: test guard** — `reorderPreservesVisibility`: default→HIDE disk→reorder(0→3): entries[3].card==cpu, disk.visible==false (should already pass; it locks behavior).
- [ ] **Step 2: Edit UI** — when `editMode`:
  - header row shows `labelEditCancel` (onEditModeChange(false)) and `labelEditDone` (onEditModeChange(false))
  - each visible card gets up/down `IconButton`s calling `applyReorder(layout, idx, idx±1)` (disabled at ends)
  - below list: `labelHiddenCards` section listing hidden cards each with “+” `IconButton` → `applyLayoutAction(SHOW,...)`
  - when not editMode, kebab menu appears instead (Task 5)
- [ ] **Step 3: Verify** — compile + unit tests + device smoke (edit mode toggling, hide/show, reorder persists across rotation)
- [ ] **Step 4: Commit** `feat(app): dashboard edit mode with card reorder and hidden card management`

---

### Task 7: Theme palettes + palette-aware theme

**Files:** Create `app/src/main/java/com/obscrum/pchwmonitor/ui/theme/Palette.kt`; modify `Theme.kt` (`PcHWMonitorTheme`); modify `MainActivity.kt`; test `app/src/test/java/com/obscrum/pchwmonitor/PaletteTest.kt`

**Interfaces (produced):**
- `object PaletteDefinitions { val ids: List<String> = listOf("default","ocean","ember","forest","gold"); fun schemeFor(id: String, dark: Boolean): ColorScheme }` — unknown/id default
- `PcHWMonitorTheme(themeMode: ThemeMode = SYSTEM, paletteId: String = "default", content)` — scheme from `PaletteDefinitions.schemeFor(paletteId, darkTheme)`, drop dynamic-color branch
- Palettes (light primary / dark primary / dark bg/surface):
  - default: reuse EXISTING `LightColorScheme`/`DarkColorScheme` == current colors
  - ocean: `#1E6FC2` / `#8FC6FF` / bg `#0E1319`
  - ember: `#C4501A` / `#FFB089` / bg `#19100C`
  - forest: `#26743C` / `#8FD0A0` / bg `#0E1510`
  - gold: light primary `#9A7B0F`; dark primary `#E8C34A`, bg `#0E0E0E`, surface `#141414`

- [ ] **Step 1: test** `PaletteTest.kt`: distinct primaries (default vs ocean light); gold dark bg == #0E0E0E; all 5 ids produce schemes for both dark and light (no exception).
- [ ] **Step 2: Run** → FAIL.
- [ ] **Step 3: Implement** `Palette.kt` + `Theme.kt` + `MainActivity` (`paletteId = settings.themePaletteId`).
- [ ] **Step 4: Run** — PaletteTest PASS; build `./gradlew :app:assembleDebug` OK.
- [ ] **Step 5: Commit** `feat(ui): add 5 color palettes incl. black-gold and palette-aware theme`

---

### Task 8: Settings palette picker

**Files:** Modify `ui/settings/SettingsScreen.kt`, `AppNavHost.kt`, strings (values + values-tr)

**Interfaces (produced):** SettingsScreen new params `labelThemePalette: String`, `paletteLabels: List<Pair<String, String>>` (id→label), `paletteId: String`, `onPaletteChange: (String)->Unit`. Instant persistent (no Save required).

strings: `theme_palette`="Color palette"/"Renk paleti"; `palette_default`="Default"/"Varsayılan"; `palette_ocean`="Ocean"/"Okyanus"; `palette_ember`="Ember"/"Kor"; `palette_forest`="Forest"/"Orman"; `palette_gold`="Black & Gold"/"Siyah & Altın"

- [ ] **Step 1: strings** both locales.
- [ ] **Step 2: SettingsScreen** — new “Palette” radio group (same pattern as Theme radios).
- [ ] **Step 3: AppNavHost** — pass `paletteId = viewModel.themePaletteId`, `onPaletteChange = viewModel::setThemePalette`, labels.
- [ ] **Step 4: Build + all tests**; **Step 5: Commit** `feat(app): settings palette picker; theme applies instantly`

---

### Task 9: Final integration & verification

- [ ] `./gradlew :app:testDebugUnitTest` → all pass
- [ ] `./gradlew :app:assembleDebug` → builds
- [ ] `cd server && python -m pytest -q` → 37 pass (server untouched)
- [ ] Manual device smoke: portrait list; landscape pinned first-screen (no scroll to see CPU/GPU/RAM/FPS); card menu hide/pin/unpin persists; edit mode reorder + hidden restore; palette switching instant across light/dark; rotation persistence
- [ ] Commit polish (`chore: v1.4 polish`) if any