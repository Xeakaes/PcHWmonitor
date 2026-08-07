# v1.4 Landscape Fullscreen & Card Width Bugfix — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Yatay modda alt navigasyon çubuğunu otomatik gizleyip dokununca geri gösteren bir kontrol, ve her kartın yarım/tam genişlik olarak işaretlenebilmesi.

**Architecture:** `LayoutEntry`'ye `wide` Boolean alanı eklenir (toleranlı JSON serileştirme ile geriye dönük uyumlu). `buildRows` genişlik-aware paketlemeye geçer: tam kart kendi satırını açar, yarım kartlar sütun sayısına göre dolar. `AppNavHost` landscape'te `NavigationBar`'ı gizler ve alt-ortada yarı saydam "göster" butonu (overlay) koyar. Kart genişliği edit modu ikonu ve kebab menü öğesiyle değiştirilir.

**Tech Stack:** Kotlin, Jetpack Compose (Material3, Scaffold/NavigationBar, material-icons-extended), kotlinx.serialization, JUnit 4, Gradle (JDK21).

## Global Constraints

- `app/build.gradle.kts` DEĞİŞTİRİLMEZ — versionCode 5 / versionName "1.4" korunur (bugfix, sürüm bump yok).
- Tüm Gradle komutları şu önek ile çalıştırılır: `export JAVA_HOME=/home/xeakaes/jdk21 && export PATH=$JAVA_HOME/bin:$PATH &&`
- Test komutu: `./gradlew :app:testDebugUnitTest` ; derleme: `./gradlew :app:assembleDebug`.
- Mevcut public API uyumlu kalır: `applyLayoutAction(action, layout, card)`, `RenderPlan`, `layoutDashboard(...)`, `columnCount(...)`.
- Yeni string'ler HEM `values/strings.xml` HEM `values-tr/strings.xml`'e eklenir (EN default, TR karşılığı).
- Çalışma worktree'si: `.worktrees/bugfix-landscape-nav-card-width` (branch `bugfix-landscape-nav-card-width`), base `main`.

---

## Task 1: Data model — `wide` alanı + toleranlı serileştirme

**Files:**
- Modify: `app/src/main/java/com/Obscrum/pchwmonitor/ui/dashboard/DashboardLayout.kt`
- Test: `app/src/test/java/com/Obscrum/pchwmonitor/DashboardLayoutTest.kt`

**Interfaces:**
- Produces: `LayoutEntry(..., wide: Boolean = false)`, `LayoutEntryDto(..., wide: Boolean = false)`, `toDto()` / `fromDto()` yeni alanı taşır.

- [ ] **Step 1: Failing testleri ekle** (DashboardLayoutTest.kt sonuna)

```kotlin
@Test
fun wideRoundTripsThroughJson() {
    val layout = DashboardLayout(
        entries = listOf(LayoutEntry(CardId.RAM, wide = true), LayoutEntry(CardId.CPU)),
    )
    val restored = DashboardLayout().fromJson(layout.toJson())
    assertTrue(restored.entries.first { it.card == CardId.RAM }.wide)
    assertFalse(restored.entries.first { it.card == CardId.CPU }.wide)
}
```

```kotlin
@Test
fun jsonWithoutWideDefaultsToFalse() {
    val json = """[{"card":"ram","visible":true,"pinned":false}]"""
    val layout = DashboardLayout().fromJson(json)
    assertFalse(layout.entries.first { it.card == CardId.RAM }.wide)
}
```

- [ ] **Step 2: Testi çalıştır, FAIL beklenir**

Run: `export JAVA_HOME=/home/xeakaes/jdk21 && export PATH=$JAVA_HOME/bin:$PATH && ./gradlew :app:testDebugUnitTest --tests "com.Obscrum.pchwmonitor.DashboardLayoutTest"`

Expected: `wideRoundTripsThroughJson` FAIL (`wide` alanı mevcut değil).

- [ ] **Step 3: Alanı ekle** — DashboardLayout.kt: `LayoutEntry`'ye `wide: Boolean = false`, `LayoutEntryDto`'ya `wide: Boolean = false`; `toDto()` → `LayoutEntryDto(card.storageId, visible, pinned, wide)`; `fromDto()` → `LayoutEntry(it, d.visible, d.pinned, d.wide)`.

- [ ] **Step 4: Testleri çalıştır — PASS**
- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/Obscrum/pchwmonitor/ui/dashboard/DashboardLayout.kt app/src/test/java/com/Obscrum/pchwmonitor/DashboardLayoutTest.kt && git commit -m "feat(app): add wide flag to dashboard card layout entries"
```

---

## Task 2: Kart genişliği aksiyonu — `setCardWidth`

**Files:**
- Modify: `app/src/main/java/com/Obscrum/pchwmonitor/ui/dashboard/CardMenu.kt`
- Test: `app/src/test/java/com/Obscrum/pchwmonitor/CardMenuTest.kt`

**Interfaces:**
- Consumes: `LayoutEntry.wide`
- Produces: `fun setCardWidth(layout: DashboardLayout, card: CardId, wide: Boolean): DashboardLayout` — kart varsa genişliği set eder, kart yoksa `layout`'u değiştirilmemiş halde döner.

- [ ] **Step 1: Failing testler** (CardMenuTest.kt sonuna)

```kotlin
@Test
fun setCardWidthTogglesWide() {
    var layout = setCardWidth(DashboardLayout.default(), CardId.RAM, true)
    assertTrue(layout.entries.first { it.card == CardId.RAM }.wide)
    layout = setCardWidth(layout, CardId.RAM, false)
    assertFalse(layout.entries.first { it.card == CardId.RAM }.wide)
}
```

```kotlin
@Test
fun setCardWidthOnUnknownCardIsNoop() {
    val layout = DashboardLayout(entries = emptyList())
    assertEquals(layout, setCardWidth(layout, CardId.RAM, true))
}
```

- [ ] **Step 2: FAIL doğrula** — `CardMenuTest` çalıştır.
- [ ] **Step 3: Implementasyon** (CardMenu.kt'ye):

```kotlin
fun setCardWidth(layout: DashboardLayout, card: CardId, wide: Boolean): DashboardLayout {
    if (layout.entries.none { it.card == card }) return layout
    return DashboardLayout(layout.entries.map { if (it.card == card) it.copy(wide = wide) else it })
}
```

- [ ] **Step 4: PASS doğrula**
- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/Obscrum/pchwmonitor/ui/dashboard/CardMenu.kt app/src/test/java/com/Obscrum/pchwmonitor/CardMenuTest.kt && git commit -m "feat(app): add setCardWidth action for half/full card layout"
```

---

## Task 3: Genişlik-aware satır paketleme — `buildRows`

**Files:**
- Modify: `app/src/main/java/com/Obscrum/pchwmonitor/ui/dashboard/LayoutHelper.kt`
- Modify: `app/src/main/java/com/Obscrum/pchwmonitor/ui/dashboard/DashboardScreen.kt` (çağrı güncelleme)
- Test: `app/src/test/java/com/Obscrum/pchwmonitor/LayoutHelperTest.kt`

**Interfaces:**
- Consumes: `LayoutEntry.wide`, `RenderPlan`, `DashboardLayout`
- Produces: `fun buildRows(plan: RenderPlan, layout: DashboardLayout): List<List<CardId>>` — imza `layout` parametresi eklenerek değişir.
- Paketleme kuralı:
  - Sıralı dolaş: `plan.firstScreen + plan.rest`.
  - Kart `wide` ise → bekleyen yarım satırı flush et ve kartın kendi satırını aç (`[tek eleman]`).
  - Kart `wide` değilse → mevcut yarım satıra ekle; `pending.size >= plan.columns` olunca flush et.
  - Sonda `pending` boş değilse flush et.
  - Bu tek algoritma hem telefon yatay (columns=2) hem tablet (2/3/4) hem dikey (columns=1; burada wide sonuç etkilemez) kapsar: dikeyde her kart zaten kendi satırına düşer.

- [ ] **Step 1: Failing testler** — LayoutHelperTest.kt içindeki mevcut iki `buildRows` çağrısını `buildRows(plan, DashboardLayout.default())` olarak güncelle, sonuna şu testleri ekle:

```kotlin
@Test
fun wideCardOwnsItsRowInLandscape() {
    val layout = DashboardLayout(
        entries = DashboardLayout.default().entries.map { if (it.card == CardId.RAM) it.copy(wide = true) else it },
    )
    val plan = RenderPlan(
        columns = 2,
        firstScreen = listOf(CardId.CPU, CardId.GPU, CardId.FPS, CardId.RAM),
        rest = listOf(CardId.IGPU, CardId.DISK, CardId.NET, CardId.FAN),
        isGrid = true,
    )
    assertEquals(
        listOf(
            listOf(CardId.CPU, CardId.GPU),
            listOf(CardId.FPS),
            listOf(CardId.RAM),
            listOf(CardId.IGPU, CardId.DISK),
            listOf(CardId.NET, CardId.FAN),
        ),
        buildRows(plan, layout),
    )
}
```

```kotlin
@Test
fun wideCardSingleColumnBehavesLikeNormal() {
    val layout = DashboardLayout(
        entries = DashboardLayout.default().entries.map { if (it.card == CardId.RAM) it.copy(wide = true) else it },
    )
    val plan = RenderPlan(columns = 1, firstScreen = emptyList(), rest = listOf(CardId.RAM, CardId.CPU), isGrid = false)
    assertEquals(listOf(listOf(CardId.RAM), listOf(CardId.CPU)), buildRows(plan, layout))
}
```

- [ ] **Step 2: Testleri çalıştır** — mevcut buildRows testleri derleme hatası + yeni testler FAIL; beklenir.
- [ ] **Step 3: `buildRows`'u yeniden yaz** (LayoutHelper.kt):

```kotlin
fun buildRows(plan: RenderPlan, layout: DashboardLayout): List<List<CardId>> {
    val wideCards = layout.entries.filter { it.wide }.map { it.card }.toSet()
    val rows = mutableListOf<List<CardId>>()
    var pending = mutableListOf<CardId>()
    for (card in plan.firstScreen + plan.rest) {
        if (card in wideCards) {
            if (pending.isNotEmpty()) { rows.add(pending.toList()); pending = mutableListOf() }
            rows.add(listOf(card))
        } else {
            pending.add(card)
            if (pending.size >= plan.columns) { rows.add(pending.toList()); pending = mutableListOf() }
        }
    }
    if (pending.isNotEmpty()) rows.add(pending.toList())
    return rows
}
```

- [ ] **Step 4: DashboardScreen çağrısını güncelle** — `DashboardScreen.kt:252` `buildRows(plan)` → `buildRows(plan, layout)`.
- [ ] **Step 5: PASS doğrula** — `:app:testDebugUnitTest` tamamlanır.
- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/Obscrum/pchwmonitor/ui/dashboard/LayoutHelper.kt app/src/main/java/com/Obscrum/pchwmonitor/ui/dashboard/DashboardScreen.kt app/src/test/java/com/Obscrum/pchwmonitor/LayoutHelperTest.kt && git commit -m "feat(app): width-aware row packing in dashboard layout"
```

---

## Task 4: String'ler — EN + TR

**Files:**
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-tr/strings.xml`

**Interfaces:**
- Produces (resource id'leri): `card_width_half`, `card_width_full`, `nav_show` (contentDescription).

- [ ] **Step 1: EN ekle** (`values/strings.xml`, mevcut `hidden_cards` bloğuna komşu)

```xml
<string name="card_width_half">Half width</string>
<string name="card_width_full">Full width</string>
<string name="nav_show">Show navigation</string>
```

- [ ] **Step 2: TR ekle** (`values-tr/strings.xml`, aynı konum)

```xml
<string name="card_width_half">Yarım genişlik</string>
<string name="card_width_full">Tam genişlik</string>
<string name="nav_show">Çubuğu göster</string>
```

- [ ] **Step 3: Derle (assembleDebug)** — resource hatası yok.
- [ ] **Step 4: Commit**

```bash
git add app/src/main/res/values/strings.xml app/src/main/res/values-tr/strings.xml && git commit -m "feat(app): card width and nav toggle strings (EN/TR)"
```

---

## Task 5: Kart genişliği UI — edit mod ikonu + kebab menü öğesi

**Files:**
- Modify: `app/src/main/java/com/Obscrum/pchwmonitor/ui/dashboard/DashboardScreen.kt`
- Modify: `app/src/main/java/com/Obscrum/pchwmonitor/ui/navigation/AppNavHost.kt`

**Interfaces:**
- Consumes: `setCardWidth`, `card_width_*` stringleri
- Produces: `CardFor` yeni parametreleri `labelCardWidthHalf: String = ""`, `labelCardWidthFull: String = ""`.

- [ ] **Step 1: `CardFor` parametreleri + menü öğesi**

`DashboardScreen.kt:`teki `CardFor(...)` imzasına `labelCardWidthHalf: String = ""` ve `labelCardWidthFull: String = ""` ekle ve `menu` lambda'sındaki `CardMenu` çağrısına ilet.

- [ ] **Step 2: `CardMenu` menü öğesi** (DashboardScreen.kt içindeki private composable)

`CardMenu(...)`'ye `labelCardWidthHalf`, `labelCardWidthFull` parametrelerini ekle; dropdown içine (FPS details öğesiyle aynı şekilde her kart için) şu öğeyi ekle:

```kotlin
val isWide = layout.entries.firstOrNull { it.card == cardId }?.wide == true
...
DropdownMenuItem(
    text = { Text(if (isWide) labelCardWidthHalf else labelCardWidthFull) },
    onClick = {
        open = false
        onLayoutChange(setCardWidth(layout, cardId, !isWide))
    },
)
```

- [ ] **Step 3: Edit mod toggle ikonu** — `CardEditControls`'taki `Row`'a bir `IconButton` ekle:

```kotlin
val isWide = layout.entries.firstOrNull { it.card == cardId }?.wide == true
IconButton(onClick = { onLayoutChange(setCardWidth(layout, cardId, !isWide)) }) {
    Icon(if (isWide) Icons.Filled.UnfoldLess else Icons.Filled.UnfoldMore, contentDescription = null)
}
```

Import ekle: `androidx.compose.material.icons.filled.UnfoldLess` ve `androidx.compose.material.icons.filled.UnfoldMore` (material-icons-extended mevcut).

- [ ] **Step 4: AppNavHost'a string'leri geçir** — DashboardScreen çağrısına:

```kotlin
labelCardWidthHalf = stringResource(R.string.card_width_half),
labelCardWidthFull = stringResource(R.string.card_width_full),
```

- [ ] **Step 5: Derle (assembleDebug)** — tüm çağrılar default parametrelerle uyumlu.
- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/Obscrum/pchwmonitor/ui/dashboard/DashboardScreen.kt app/src/main/java/com/Obscrum/pchwmonitor/ui/navigation/AppNavHost.kt && git commit -m "feat(app): card width toggle in edit mode and card menu"
```

---

## Task 6: Landscape nav gizleme — otomatik gizle + overlay butonu

**Files:**
- Modify: `app/src/main/java/com/Obscrum/pchwmonitor/ui/navigation/AppNavHost.kt`

**Interfaces:**
- Consumes: `nav_show` string, `LocalConfiguration.current` (screenWidthDp / screenHeightDp).

- [ ] **Step 1: Landscape algıla**

AppNavHost bileşiminde `val configuration = LocalConfiguration.current`, `val landscape = configuration.screenWidthDp >= configuration.screenHeightDp`, `var navHidden by rememberSaveable { mutableStateOf(false) }` ekle.

- [ ] **Step 2: `bottomBar`'ı koşullu yap**

`Scaffold`'un `bottomBar` bloğu: `landscape && navHidden` iken `{}` (boş lambda → bar render edilmez); aksi halde mevcut `NavigationBar`. Dikey modda her zaman `NavigationBar`.

- [ ] **Step 3: Overlay "göster" butonu**

`Scaffold`'u bir `Box` ile sarla; `landscape && navHidden` iken `Box` içine `Modifier.align(Alignment.BottomCenter)` ile yarı saydam `IconButton` (Icons.Filled.ArrowDropUp, `Modifier.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))`, `contentDescription = stringResource(R.string.nav_show)`) koy; `onClick = { navHidden = false }`.

- [ ] **Step 4: Derle (assembleDebug)** — Scaffold/Box yapısı doğru.
- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/Obscrum/pchwmonitor/ui/navigation/AppNavHost.kt && git commit -m "feat(app): auto-hide nav bar in landscape with tap-to-show overlay"
```

---

## Task 7: Final integration & verification

- [ ] **Step 1: Tüm app testleri** — `export JAVA_HOME=/home/xeakaes/jdk21 && export PATH=$JAVA_HOME/bin:$PATH && ./gradlew :app:testDebugUnitTest --rerun-tasks` → önceki 61 + yeni (~6) testin tamamı PASS, 0 FAIL.
- [ ] **Step 2: assembleDebug** → BUILD SUCCESSFUL.
- [ ] **Step 3: Server testleri** — `python3 -m pytest tests -q` (server/ altında) → 37 PASS.
- [ ] **Step 4: Sonuç doğrulama** — test sayısını `find app/build/test-results -name "*.xml" | xargs grep -h "<testsuite" | grep -oE 'tests="[0-9]*"|failures="[0-9]*"|errors="[0-9]*"'` ile teyit et.
- [ ] **Step 5: Versiyon kontrolü** — `git diff HEAD -- app/build.gradle.kts` boş; `versionName="1.4"`, `versionCode=5` korundu.
- [ ] **Step 6: Commit (temiz değilse)** — `git add -A && git commit -m "chore: final touches"`

---

## Self-Review Notları

- Spec kararları eşleniyor: nav auto-hide (karar 1, 2), kart yarım/tam (3), satır paketleme (4), varsayılan korunan (5), dikeyde etkisiz (6).
- Dikeyde `wide` sonuçsuzdur çünkü columns=1'de her kart zaten kendi satırına düşer (Task 3 tek-algoritma davranışı).
- Hiçbir Task `app/build.gradle.kts`'e dokunmaz (sürüm bozulmaz).
- Cihaz smoke testi (Task 7 Step 4 manuel adımı) otomasyon kapsamı dışındadır — kullanıcı tarafından yapılır.