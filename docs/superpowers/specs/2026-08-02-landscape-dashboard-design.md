# Landscape Dashboard Design (2026-08-02)

## Goal

When the phone is in landscape orientation, the Dashboard shows all metric cards
(CPU, GPU, iGPU, RAM) on a single screen in a 2x2 grid, preserving the existing
card style. Portrait orientation keeps the current single-column scroll layout.

## Scope

- Android app, Dashboard screen only. History and Settings screens keep their
  current layouts in both orientations.
- Server/protocol: no changes.

## Layout

- Orientation detection: `BoxWithConstraints` at the Dashboard content level;
  treat as landscape when `maxWidth > maxHeight` (robust to multi-window,
  not just sensor rotation).
- Landscape layout:
  - `ConnectionBar` full width at the top (unchanged component).
  - Below it a `LazyVerticalGrid` with 2 columns, `Arrangement.spacedBy(12.dp)`.
  - Card order: CPU, GPU, iGPU (only when `status.igpu != null`), RAM.
  - With 3 cards the grid fills naturally (RAM next to GPU, one empty slot).
- Portrait layout: unchanged `LazyColumn` (existing code path).

## Compact Cards

- Add `compact: Boolean = false` parameter to `CpuCard`, `GpuCard`, `RamCard`.
- Compact mode keeps ALL data fields but reduces size:
  - Big value typography one step smaller (e.g. `displaySmall` -> `headlineMedium`),
  - reduced internal paddings / spacings,
  - target card height ~150dp so 2x2 + ConnectionBar fits a typical phone
    landscape viewport (~360dp height).
- The per-card sparkline (`LineChart`) is **hidden in compact mode** — the
  trend is fully available in the History screen. This is what makes the
  2x2 grid fit on one screen without scrolling.
- Colors, shapes, icons, labels: identical to portrait.

## Verification

- `./gradlew :app:testDebugUnitTest :app:assembleDebug` passes.
- No new unit tests required (pure UI change; Compose UI test suite not set up).
- Manual device check by user in both orientations.

## Out of Scope

- Landscape-specific History/Settings layouts.
- Locking screen orientation.
- New cards or new metrics.
