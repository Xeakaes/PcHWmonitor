# Android UI Rework — Design Spec (Alt proje A)

**Date:** 2026-09-17
**Target release:** 1.6.1 (version bump happens only after sub-projects A **and** B are complete)
**Status:** Approved in conversation; pending written-spec review

## Problem Statement

Version 1.6 has UI-level defects reported by the maintainer: an overly complex
connect flow, non-working buttons, a glassmorphism switch that loses state and
resides in the wrong place, saved-servers corruption, and translation gaps.
A code audit confirmed the reports and found root causes (see Audit Findings).

Out of scope here: the server/tunnel/auth rework (sub-project B). This spec
covers the Android app only.

## Goals / Success Criteria

1. Connect flow is QR-first and simple; the global connect form is gone.
2. Saved servers can be added, selected, edited per-row, and deleted safely —
   no write to the wrong server, no silent data loss, no dead end states.
3. Glassmorphism switch persists across restarts and always agrees with the
   dashboard rendering; it lives as an independent row in Settings.
4. All user-visible text is in string resources; all 14 locales (base + 13)
   are fully translated with key-set and format-specifier parity.
5. Background connection model is an explicit setting (default: active server
   only); preference changes never open/close sockets.
6. Existing test suites stay green; new unit tests cover the store/viewmodel
   logic; a manual device checklist is executed before 1.6.1.

## Non-Goals

- Server-side changes (Cloudflare tunnel, tray, auth) — sub-project B.
- Visual redesign of the dashboard or glassmorphism aesthetics (user chose
  "keep current visuals, fix state management").
- Bottom navigation stays 3 tabs (dashboard / history / settings).

---

## 1. Navigation & Screen Structure

Bottom nav keeps 3 tabs. Two new full-screen routes:

```
dashboard (start)     → unchanged (multi-server tab row kept)
history               → unchanged
settings              → REWORKED: preferences only
   ├ Connection summary card (active server name + status + "Manage →",
   │   + "background_connect_all" switch — see section 3)
   ├ Theme / Palette / Chart window / Language
   ├ Custom background + Glassmorphism switch (independent, persisted)
   └ Patreon
servers (NEW route)
   ├ Status pill (active server)
   ├ Server list rows: name + ip:port|tunnel, active marker,
   │   tap → select, ✎ → edit, 🗑 → delete (confirmation dialog)
   ├ "Add via QR / match" (PRIMARY full-width button)
   ├ "Add manually"
   └ Empty state text + CTA
server_edit?serverId=<id|null> (NEW route)
   ├ New: QR / network scan / manual fields
   ├ Edit: prefilled from that server id; SAVE writes only that row
   └ Validation: IP required, port 1–65535, duplicate-name warning
```

Entry points: Settings → connection card → `servers`; dashboard with zero
servers → empty-state CTA → `servers`.

Removed behaviors: the old global connect form in Settings, the Elle/Tara
picker block there, the second "Kaydet" button, and the duplicated
"Kaydedildi" feedback. "Bağlan" is replaced by "connect to selected server" /
"QR connect" flows.

Legacy DataStore keys (`server_ip`, `server_port`, `auth_token`) are kept for
backward compatibility (mirrored from the active server) but are no longer
written by any form.

## 2. Data Model & Store Changes

### Store API (new/fixed)

| Operation | Behavior |
|---|---|
| `addServer(config)` | Validate, append, **make it active**, connect |
| `updateServerConnection(id, ip, port, token, hostname, useTls)` | Writes only that row (revives the currently-dead method → fixes C1/C6) |
| `removeServer(id)` | Deletes; if active → fall back to next; if list empty → `active=null` and dashboard shows empty state (fixes C3/C4) |
| `setActiveServer(id)` | Changes active; ViewModel state resets/syncs immediately (fixes S1 flash) |
| `savePreferences(...)` | **NEW** — only theme/palette/language/chart-window/background/glassmorphism; never touches connection fields (fixes C2) |
| `activeServerId` getter | Validates membership on every read; dangling id → `firstOrNull()` (fixes S2) |

### QR auto flow (decision A)

Parse payload → search `servers` for matching `hostname` or `ip:port` →
**match: select + connect**; **no match: create + select + connect**. The QR
flow must never overwrite a different server's stored address.

### Persistence robustness

- `servers_json` decode uses `ignoreUnknownKeys = true`; on decode failure the
  raw value is **never written back as `[]`** (prevents the wipe scenario S4).
- The `server_edit` screen keeps form state in a screen-scoped ViewModel
  (survives rotation; fixes C8).
- Legacy `server_ip/port/token` keys are mirrored whenever the active server
  changes (backward compatibility for any legacy reader).
- After `trustCert` swaps a controller, the ViewModel re-subscribes to it
  (fixes S3); coroutine jobs of removed/replaced controllers are cancelled.

### Cleanup

Dead code removed or wired: unused `onServerSelected` parameter, unused
`labelServer`, duplicated "Kaydedildi", dead MainActivity glassmorphism
collection. Dead store methods (`setServers`, `updateServerHostname`,
`updateServerConnection`) are either used by the new flows or deleted.

### Validation

IP non-empty; port 1–65535; if a tunnel hostname is set then `useTls=true`
automatically (forces `wss://` — this is what actually repairs Cloudflare
tunnel connectivity from the app side).

## 3. Connection Controller Behavior

**New setting:** `background_connect_all` — "Connect to all servers in the
background". **Default: OFF.** Lives as a switch under the Settings
connection card; translated into all 13 languages.

| Situation | OFF (default) | ON |
|---|---|---|
| Startup | Connect active server only | Connect all (old behavior) |
| Switch active server | Connect new active, **disconnect old** | All stay connected |
| ON_STOP (backgrounded) | Disconnect active (battery saving) | Connections kept, best-effort |
| ON_START (foreground) | Reconnect active | Reconnect all |

**Sync separation (critical):** `syncControllers()` runs only on
connection-relevant changes (active server, server list, token/address,
`background_connect_all`). Theme/palette/language/background changes never
open or close sockets — this removes the current "connect everything on every
settings emission" defect (S5).

Lifecycle: controllers are created lazily; replaced/removed controllers have
their jobs cancelled (S3 leak fix). Connection errors surface through the
error-code pipeline (section 5). Dashboard always reflects the true state of
the active server — never stale "Bağlı" after deletion (C4).

## 4. Glassmorphism & Appearance State

**Single-source principle:** every appearance setting lives in
`AppSettings` (DataStore); the ViewModel reads it once; UI collects that
flow directly. One-shot `remember { mutableStateOf(settings...) }` copies
are removed from the settings screen.

Glassmorphism flow (decision A — visuals unchanged, state repaired):

1. `SettingsStore.setGlassmorphismEnabled` (currently a dead writer) becomes
   the persisted write path → survives restart.
2. Switch and dashboard read the same settings-derived source → no more
   self-toggling or divergence.
3. `setCustomBackgroundEnabled` no longer forces the glass flag on/off —
   the two settings are independent; the blur layer still only renders when
   a background bitmap exists (current visual behavior), while translucent
   card styling follows the glass flag alone.
4. Placement: independent always-visible row in Settings (per section 1).
5. `custom_background*` strings translated (section 5).

Related button fix: dashboard edit mode — **Cancel reverts** to a snapshot
taken on entry, **Done commits** (currently both just exit).

## 5. Locales & Hardcode Cleanup

Scope: base + **13 translations** (de, es, fr, it, ja, nl, pl, pt, pt-BR, ru,
tr, zh, zh-TW), fully in sync.

1. **Base file repair:** `values/strings.xml:136` — Turkish `ort.` → `avg`.
2. **Missing translations (~25 keys × 13 locales):**
   - `custom_background*` ×7 (English in all 13 locales today)
   - Certificate-trust dialog ×5 + saved-servers block ×7
     (`saved_servers`, `select`, `delete_label`, `add_new_server`,
     `server_name`, `ip_address`, `token_optional`) + `cancel`/`add`
   - zh/zh-rTW extras ×8 (palette names, `discover`, `no_servers_found`,
     notification channel texts)
   - Singletons: `gpu_hotspot` (de), `palette_ocean` (pl), `fps_1pct_low`
     (ja, tr), `summary_format` (tr)
   - `formatted="false"` parity for `fps_1pct_low` in base + tr
3. **New feature keys:** every new string from sections 1–4 (server list,
   edit screen, delete confirmation, validation errors, empty states,
   `background_connect_all`, QR flow feedback) added to base + 13 locales.
4. **Kotlin hardcode → resources:**
   - `MonitorNotificationService.kt:167-189` — 17 Turkish hardcoded lines →
     parameterized format strings in all locales
   - `SettingsScreen.kt:271,407` — "Cloudflare Tunnel URL" → `R.string`
   - `SettingsScreen.kt:284` — "Port" → reuse `R.string.port`
   - 8 error strings (`MonitorController` ×4, `WebSocketClient` ×3,
     `StatusParser` ×1) → error codes (enum) in the data layer, resolved to
     localized text by a UI-side ErrorMapper
   - User-visible defaults ("My PC", "PC", "Unknown") → resources where shown
5. **Quality gate:** a script/CI check verifies key-set parity and
   format-specifier parity across all 14 locale files.

## 6. Error Handling & Testing

### Error handling
- Connection errors: data layer emits enum codes → UI `ErrorMapper` resolves
  localized text; `lastError` carries resolved strings (never English).
- Validation: inline per-field errors in `server_edit` (empty IP, port range,
  duplicate-name warning — warn, don't block).
- Empty states: no servers (list + CTA), not connected (dashboard "not
  connected", metric cards hidden), no scan results — all localized.
- Delete: confirmation dialog with server name.
- Certificate-trust dialog kept, now localized.

### Tests
1. **Unit (new/updated):**
   - `SettingsStore`: add/update/remove, dangling active-id fallback,
     `savePreferences` isolation from connection fields, decode-failure data
     preservation, QR match logic (hostname/ip:port → select vs create)
   - `MonitorViewModel`: sync only on connection-relevant changes (theme
     emission must not connect), `background_connect_all` ON/OFF behavior,
     active-server deletion resets state, trustCert re-subscription
   - Error code → mapper coverage (key exists in every locale)
2. **Translation parity test:** key-set + format-specifier equality across 14
   files, runs in CI.
3. **Regression:** all existing Android unit tests and server tests stay green.
4. **Manual device checklist** (executed before 1.6.1): QR add→connect; row
   edit updates the right row; delete→confirm→empty state; form survives
   rotation; glassmorphism survives restart; background setting OFF keeps a
   single connection; spot-check all 13 locales.

---

## Audit Findings Reference (why these changes)

Confirmed defects fixed by this spec, with IDs from the audit:

- **C1** "Select" never refreshed the form → connect overwrote the wrong
  server's address (fixed by per-row edit screens, section 2)
- **C2** "Kaydet" rewrote connection data even for theme-only changes (fixed
  by `savePreferences`)
- **C3** Deleting all servers bricked connect permanently (fixed by explicit
  empty state; the global connect button no longer exists)
- **C4** Dashboard showed a deleted PC as "Bağlı" forever (fixed by state
  reset on removal, section 3)
- **C5** Add-server lacked validation and rarely became active (fixed:
  validated, becomes active)
- **C6** QR + connect overwrote the active server (fixed by QR auto flow)
- **C7** No per-row edit; four dead store APIs (fixed, section 2)
- **C8** `remember`-only form state lost on navigation (fixed by
  ViewModel-scoped edit state)
- **S1–S6** suspicious/race issues: each addressed in sections 2–3 as noted
  (tab flash, dangling active id, trustCert re-subscribe, decode wipe,
  connect-on-every-emission, notification never updating)
- Glassmorphism: dead persisted writer, dual sources of truth, forced
  coupling with custom background (section 4)
- Translations: ~25 untranslated keys, base-file `ort.` contamination,
  Turkish notification hardcode, 2 English field labels, 8 English error
  strings (section 5)

## Open Risks

- 13-locale translation volume is large; quality gate must run before merge.
- Disconnecting the non-active server on tab switch (default mode) changes
  observable behavior — reconnect latency on switch; accepted in design.
- Legacy key mirroring must be verified against any consumer found outside
  the audited files during implementation.
