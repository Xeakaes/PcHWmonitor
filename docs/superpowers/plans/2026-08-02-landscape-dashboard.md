# Landscape Dashboard Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Show all Dashboard metric cards (CPU, GPU, iGPU, RAM) on a single screen in landscape orientation via a compact 2x2 grid, keeping the existing style; portrait stays unchanged.

**Architecture:** Add a `compact: Boolean = false` parameter to `MetricCard`, `RadialGauge`, and the three cards (`CpuCard`, `GpuCard`, `RamCard`); compact mode tightens paddings/typography and hides the per-card sparkline. `DashboardScreen` detects landscape via `BoxWithConstraints` (`maxWidth > maxHeight`) and renders a `LazyVerticalGrid` with 2 columns of compact cards under the `ConnectionBar`.

**Tech Stack:** Kotlin, Jetpack Compose, Material 3, `LazyVerticalGrid`, `BoxWithConstraints`.

## Global Constraints

- Comment rule: section-header comments only, in English; no line-by-line comments.
- Portrait layout must remain pixel-identical (no changes to existing non-compact code paths).
- All labels/strings reused as-is; no new strings.
- No new dependencies.
- This workspace is not a git repo; skip all `git commit` steps.

---

### Task 1: Layout helper + unit test

**Files:**
- Create: `app/src/main/java/com/example/pchwmonitor/ui/dashboard/LayoutHelper.kt`
- Test: `app/src/test/java/com/example/pchwmonitor/LayoutHelperTest.kt`

**Interfaces:**
- Produces: `internal fun isLandscapeLayout(maxWidth: Dp, maxHeight: Dp): Boolean` in package `com.example.pchwmonitor.ui.dashboard`.

- [ ] **Step 1: Write the failing test**

`app/src/test/java/com/example/pchwmonitor/LayoutHelperTest.kt`:

```kotlin
package com.example.pchwmonitor

import androidx.compose.ui.unit.dp
import com.example.pchwmonitor.ui.dashboard.isLandscapeLayout
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LayoutHelperTest {
    @Test
    fun wideViewportIsLandscape() {
        assertTrue(isLandscapeLayout(maxWidth = 800.dp, maxHeight = 360.dp))
    }

    @Test
    fun tallViewportIsNotLandscape() {
        assertFalse(isLandscapeLayout(maxWidth = 360.dp, maxHeight = 800.dp))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `JAVA_HOME=~/jdk21 ./gradlew :app:testDebugUnitTest --tests "*LayoutHelperTest" --console=plain`
Expected: FAIL (unresolved reference `isLandscapeLayout`).

- [ ] **Step 3: Write minimal implementation**

`app/src/main/java/com/example/pchwmonitor/ui/dashboard/LayoutHelper.kt`:

```kotlin
package com.example.pchwmonitor.ui.dashboard

import androidx.compose.ui.unit.Dp

// Landscape detection shared by the dashboard layout branches.
internal fun isLandscapeLayout(maxWidth: Dp, maxHeight: Dp): Boolean = maxWidth > maxHeight
```

- [ ] **Step 4: Run test to verify it passes**

Run: `JAVA_HOME=~/jdk21 ./gradlew :app:testDebugUnitTest --tests "*LayoutHelperTest" --console=plain`
Expected: PASS (both tests).

---

### Task 2: Compact mode for shared components

**Files:**
- Modify: `app/src/main/java/com/example/pchwmonitor/ui/components/MetricCard.kt`
- Modify: `app/src/main/java/com/example/pchwmonitor/ui/components/RadialGauge.kt`

**Interfaces:**
- Consumes: nothing from earlier tasks.
- Produces: `MetricCard(title, modifier, compact: Boolean = false, content)` and `RadialGauge(value, max, color, label, modifier, unit, compact: Boolean = false)`.

- [ ] **Step 1: Add `compact` to `MetricCard`**

`MetricCard.kt` — new signature:

```kotlin
@Composable
fun MetricCard(
    title: String,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
    ) {
        Column(modifier = Modifier.padding(if (compact) 10.dp else 16.dp)) {
            Text(
                text = title,
                style = if (compact) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Column(modifier = Modifier.padding(top = if (compact) 6.dp else 12.dp)) {
                content()
            }
        }
    }
}
```

- [ ] **Step 2: Add `compact` to `RadialGauge`**

`RadialGauge.kt` — new signature; compact uses a smaller canvas, stroke, and value typography:

```kotlin
@Composable
fun RadialGauge(
    value: Float,
    max: Float,
    color: Color,
    label: String,
    modifier: Modifier = Modifier,
    unit: String = "",
    compact: Boolean = false,
) {
    // ... unchanged body except three spots:
    // 1. Canvas modifier: Modifier.fillMaxWidth(0.72f).height(if (compact) 72.dp else 140.dp)
    // 2. stroke: if (compact) 8.dp else 14.dp
    // 3. value Text style: if (compact) MaterialTheme.typography.titleLarge
    //    else MaterialTheme.typography.headlineMedium
    // 4. label Text padding: if (compact) Modifier.padding(top = 2.dp) else Modifier.padding(top = 4.dp)
}
```

- [ ] **Step 3: Verify build**

Run: `JAVA_HOME=~/jdk21 ./gradlew :app:compileDebugKotlin --console=plain`
Expected: BUILD SUCCESSFUL (existing call sites keep compiling because the new parameters default to `false`).

---

### Task 3: Compact mode for the three cards

**Files:**
- Modify: `app/src/main/java/com/example/pchwmonitor/ui/dashboard/CpuCard.kt`
- Modify: `app/src/main/java/com/example/pchwmonitor/ui/dashboard/GpuCard.kt`
- Modify: `app/src/main/java/com/example/pchwmonitor/ui/dashboard/RamCard.kt`

**Interfaces:**
- Consumes: `MetricCard`/`RadialGauge` with `compact` from Task 2.
- Produces: `CpuCard(cpu, labelTemp, labelUsage, labelClock, labelPower, labelCores, modifier, compact = false)`, `GpuCard(gpu, labelTemp, labelHotspot, labelUsage, labelVram, labelCoreClock, labelMemClock, labelPower, modifier, titleFallback = "GPU", compact = false)`, `RamCard(ram, labelUsage, labelUsed, labelClock, modifier, compact = false)`.

- [ ] **Step 1: Add `compact` to `CpuCard`**

`CpuCard.kt` — new signature and body changes:

```kotlin
@Composable
fun CpuCard(
    cpu: CpuInfo?,
    labelTemp: String,
    labelUsage: String,
    labelClock: String,
    labelPower: String,
    labelCores: String,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    MetricCard(title = labelCores, modifier = modifier, compact = compact) {
        // tempColor / spark / points / LaunchedEffect: unchanged
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 16.dp),
        ) {
            RadialGauge(
                value = cpu?.tempC ?: Float.NaN,
                max = 100f,
                color = tempColor,
                label = labelTemp,
                unit = "°C",
                compact = compact,
                modifier = Modifier.weight(1f),
            )
            Column(
                modifier = Modifier.weight(1.2f).padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 12.dp),
            ) {
                StatRow(labelUsage, formatPct(cpu?.usagePct))
                StatRow(labelClock, formatMhz(cpu?.clockMhz))
                StatRow(labelPower, formatPower(cpu?.powerW))
                FilledBar(
                    valuePct = cpu?.usagePct ?: 0f,
                    color = TemperatureColor.forUsage(cpu?.usagePct ?: 0f),
                )
                CoresStrip(cpu?.loads)
            }
        }
        if (!compact) {
            LineChart(
                points = points,
                color = tempColor,
                min = 30f,
                max = 100f,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}
```

Note: keep `StatRow`, `CoresStrip`, and the `format*` helpers untouched.

- [ ] **Step 2: Add `compact` to `GpuCard`**

`GpuCard.kt` — same pattern: new trailing `compact: Boolean = false` parameter; pass `compact` to `MetricCard` and `RadialGauge`; row spacing `if (compact) 10.dp else 16.dp`; stats column spacing `if (compact) 5.dp else 10.dp`; wrap the final `LineChart` in `if (!compact) { ... }`.

- [ ] **Step 3: Add `compact` to `RamCard`**

`RamCard.kt` — new trailing `compact: Boolean = false` parameter; pass `compact` to `MetricCard`; column spacing `if (compact) 8.dp else 12.dp`; the big percentage text style `if (compact) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineSmall`; wrap the final `LineChart` in `if (!compact) { ... }`.

- [ ] **Step 4: Verify build**

Run: `JAVA_HOME=~/jdk21 ./gradlew :app:compileDebugKotlin --console=plain`
Expected: BUILD SUCCESSFUL.

---

### Task 4: Landscape grid in DashboardScreen

**Files:**
- Modify: `app/src/main/java/com/example/pchwmonitor/ui/dashboard/DashboardScreen.kt`

**Interfaces:**
- Consumes: `isLandscapeLayout` (Task 1); compact card params (Task 3).
- Produces: nothing new; `DashboardScreen` keeps its signature.

- [ ] **Step 1: Wrap content in `BoxWithConstraints`**

Replace the `LazyColumn(...)` body with a `BoxWithConstraints` wrapper. The current portrait branch stays byte-identical inside `if (!landscape) { ... }`; add a landscape branch:

```kotlin
@Composable
fun DashboardScreen(...) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val landscape = isLandscapeLayout(maxWidth = maxWidth, maxHeight = maxHeight)
        if (landscape) {
            LandscapeDashboard(
                status = status,
                connection = connection,
                labelConnecting = labelConnecting,
                labelConnected = labelConnected,
                labelDisconnected = labelDisconnected,
                labelCpu = labelCpu,
                labelCpuTemp = labelCpuTemp,
                labelUsage = labelUsage,
                labelClock = labelClock,
                labelPower = labelPower,
                labelCores = labelCores,
                labelGpuTemp = labelGpuTemp,
                labelHotspot = labelHotspot,
                labelVram = labelVram,
                labelCoreClock = labelCoreClock,
                labelMemClock = labelMemClock,
                labelIntegratedGpu = labelIntegratedGpu,
                labelRam = labelRam,
                labelRamUsed = labelRamUsed,
                labelNoData = labelNoData,
            )
        } else {
            // existing LazyColumn content, unchanged
        }
    }
}
```

- [ ] **Step 2: Add the landscape composable**

In the same file, add:

```kotlin
@Composable
private fun LandscapeDashboard(
    status: SystemStatus?,
    connection: ConnectionState,
    labelConnecting: String,
    labelConnected: String,
    labelDisconnected: String,
    labelCpu: String,
    labelCpuTemp: String,
    labelUsage: String,
    labelClock: String,
    labelPower: String,
    labelCores: String,
    labelGpuTemp: String,
    labelHotspot: String,
    labelVram: String,
    labelCoreClock: String,
    labelMemClock: String,
    labelIntegratedGpu: String,
    labelRam: String,
    labelRamUsed: String,
    labelNoData: String,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        ConnectionBar(
            state = connection,
            serverName = status?.pc?.name,
            labelConnecting = labelConnecting,
            labelConnected = labelConnected,
            labelDisconnected = labelDisconnected,
        )
        if (status == null || !status.available) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = labelNoData,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = status?.error ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(top = 8.dp, bottom = 12.dp),
            ) {
                item { CpuCard(cpu = status.cpu, labelTemp = labelCpuTemp, labelUsage = labelUsage, labelClock = labelClock, labelPower = labelPower, labelCores = labelCpu, compact = true, modifier = Modifier.fillMaxWidth()) }
                item { GpuCard(gpu = status.gpu, labelTemp = labelGpuTemp, labelHotspot = labelHotspot, labelUsage = labelUsage, labelVram = labelVram, labelCoreClock = labelCoreClock, labelMemClock = labelMemClock, labelPower = labelPower, compact = true, modifier = Modifier.fillMaxWidth()) }
                if (status.igpu != null) {
                    item { GpuCard(gpu = status.igpu, titleFallback = labelIntegratedGpu, labelTemp = labelGpuTemp, labelHotspot = labelHotspot, labelUsage = labelUsage, labelVram = labelVram, labelCoreClock = labelCoreClock, labelMemClock = labelMemClock, labelPower = labelPower, compact = true, modifier = Modifier.fillMaxWidth()) }
                }
                item { RamCard(ram = status.ram, labelUsage = labelRam, labelUsed = labelRamUsed, labelClock = labelClock, compact = true, modifier = Modifier.fillMaxWidth()) }
            }
        }
    }
}
```

New imports needed in `DashboardScreen.kt`: `androidx.compose.foundation.lazy.grid.GridCells`, `androidx.compose.foundation.lazy.grid.LazyVerticalGrid`, `androidx.compose.foundation.layout.BoxWithConstraints`, `androidx.compose.foundation.layout.PaddingValues`, `androidx.compose.foundation.layout.Column`, `androidx.compose.foundation.layout.Arrangement`, `androidx.compose.foundation.layout.fillMaxSize`, `androidx.compose.ui.Alignment` (some already imported).

- [ ] **Step 3: Full verification**

Run: `JAVA_HOME=~/jdk21 ./gradlew :app:testDebugUnitTest :app:assembleDebug --console=plain`
Expected: BUILD SUCCESSFUL, all unit tests pass.

- [ ] **Step 4: Sync to Windows**

Run: `rsync -a --exclude .venv --exclude __pycache__ --exclude app/build --exclude build --exclude .gradle --exclude .kotlin --exclude local.properties /home/xeakaes/PcHWmonitor/ /mnt/c/Users/msi/PcHWmonitor/`
Expected: sync completes; user builds the APK in Android Studio and verifies both orientations on the device.

---

## Amendment (review-driven)

Final whole-branch review (2026-08-02) found the compact GPU card (~205dp) made the 2x2 grid scroll on a ~340dp usable landscape viewport, violating the spec goal. Amended as corrective Task 5 (all compact-only, portrait untouched):

- `GpuCard`/`CpuCard` compact: stats arranged in 2 columns (width-rich landscape cards), `spacedBy 5/10`, top pad 4; `CoresStrip` below the 2-col row in CPU.
- `StatRow` gains `compact: Boolean = false`: label `labelSmall` (maxLines 1, ellipsis), value `labelMedium`; portrait defaults identical.
- `RadialGauge` compact canvas 72 -> 64dp.
- `RamCard` compact: `spacedBy 8 -> 6`, % text `titleLarge -> titleMedium`.
- `LayoutHelperTest`: added square-viewport edge test.

Resulting heights: CPU/GPU ~128dp, RAM ~130dp; grid ~290dp + ConnectionBar ~40dp = ~330dp, fits the ~340dp usable viewport. Sparkline `LaunchedEffect` deliberately left unguarded (rotation back to portrait would blank the 60s sparkline). 24/24 unit tests pass.
