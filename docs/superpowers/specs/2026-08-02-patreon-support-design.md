# Patreon Support Section — Design

**Date:** 2026-08-02
**Status:** Approved by user

## Goal

Let users support the project through Patreon from two entry points: the GitHub repo's right sidebar and the bottom of the Android app's Settings tab. The main dashboard screen stays untouched.

## Background / Decisions

- Buy Me a Coffee and Ko-fi were ruled out: both pay out via Stripe/PayPal, neither of which supports receiving money in Turkey.
- Patreon supports Turkey (bank transfer in TRY via Payoneer, $0.25 + 1.55%).
- Patreon cannot do one-time pay-what-you-want donations; a single (or a few) fixed monthly tier(s) is the accepted compromise (user chose Patreon-only).
- Patreon page URL is `https://www.patreon.com/cw/Obscrum` — note the `/cw/` prefix, so GitHub's default `patreon: <username>` FUNDING.yml key would produce a wrong URL. Use `custom` with the full URL instead.

## 1. GitHub Sidebar (Sponsor button)

- Create `.github/FUNDING.yml`:

  ```yaml
  custom: ["https://www.patreon.com/cw/Obscrum"]
  ```

  GitHub renders a "Sponsor" button in the repo's right sidebar from this file.
- Add a short "Support" section to `README.md` (bilingual, 2 lines) linking the Patreon page. No emojis.

## 2. Android App — Settings Tab

### Placement

- Bottom of the Settings tab only (below the Save button area). The dashboard (main screen) is NOT modified.
- Style follows the existing Settings layout: `titleMedium` semi-bold section header, `bodyMedium` description, primary `Button`.

### Content

- Section title: "Support" (localized)
- Description: localized sentence — "This app is free and open source. If you want to support the development, you can join on Patreon." (per-language translation)
- Button: "Support on Patreon" (localized) → opens `https://www.patreon.com/cw/Obscrum` in the default browser via `Intent(ACTION_VIEW)`.

### Strings

- 3 new keys added to all 14 `strings.xml` files:
  - `label_support` (section title)
  - `label_support_description` (description)
  - `label_support_patreon` (button)
- Key parity must hold: all 14 files get the same 3 keys.

### Code structure

- `DonateLinks.kt` (new, package `com.example.pchwmonitor.util`): `const val PATREON_URL = "https://www.patreon.com/cw/Obscrum"`.
- `SettingsScreen.kt`: 3 new label parameters threaded from the caller (`PcHwMonitorApp.kt` resolves them from string resources like the existing labels); button `onClick` opens the browser with the constant URL.
- Version bump: `versionCode 1 -> 2`, `versionName "1.0" -> "1.1"` in `app/build.gradle.kts`.

## Testing

- Unit test: `DonateLinksTest` asserts `PATREON_URL` equals the expected URL (guards against typos/regressions).
- Existing tests must keep passing (`./gradlew :app:testDebugUnitTest`).
- Manual check: build APK, open Settings, tap the button, verify the browser opens the Patreon page.
- GitHub: after pushing, verify the Sponsor button renders on the repo page.

## Constraints

- No new dependencies, no new icons/assets.
- Comment rule: section-header comments only, in English; no line-by-line comments.
- Dashboard and all other screens byte-identical.
- Release flow: after implementation, create a new GitHub release `v1.1.0` with the fresh APK (EXE unchanged).
