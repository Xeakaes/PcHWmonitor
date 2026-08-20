# README Redesign Design

- Date: 2026-08-21
- Status: Approved

## Purpose

Restructure the GitHub README so a visitor immediately understands what the app is, what it looks like, and how to get it — without reading long paragraphs. The redesign focuses on:

1. A hero section with app name, tagline, badges, and a 3-layered phone showcase.
2. Cleanly separated, grammatically reviewed English and Turkish sections (bilingual, English first).
3. New Screenshots, Download, and FAQ sections to answer pre-download questions.
4. Keeping all existing technical content (Features, Architecture, Setup, Protocol, Troubleshooting, License).

## Layout

### Hero (top, language-neutral)

- `# PC HW Monitor` heading.
- One-line tagline in English + one-line in Turkish.
- Badges row (kept from current README): CodeQL, AlternativeTo, Website, Android Weekly.
- 3-layered showcase mirroring the site's hero (`docs/index.html`): `hero_left.jpg` (left, smaller), `main_ember.jpg` (center, larger), `hero_right.jpg` (right, smaller) — wrapped in a centered HTML div with inline widths. Images are already in `docs/images/` (no new files added to the repo).

### `## English` (primary)

1. **Features** — existing bullet list cleaned up: Android (app) and Windows EXE (server) subsections, readable grouping.
2. **Screenshots** — 4-image gallery: `main_ember.jpg`, `main_light.jpg`, `main_landscape.jpg`, `settings_light.jpg`. Rendered as a 2×2 Markdown table with captions.
3. **Download** — GitHub Releases link + F-Droid status note ("awaiting review" — MR open upstream).
4. **Architecture** — existing ASCII diagram.
5. **Getting Started** — merges current "Windows EXE", "Server Setup (development)", and "Android Setup" into one section: recommended user path (EXE → Settings → connect), then developer setup (server + Android).
6. **WebSocket Protocol** — existing content.
7. **Troubleshooting** — existing table.
8. **FAQ** — new, brief:
   - Does it work over the internet? — No, designed for local Wi-Fi (LAN only).
   - Why does the EXE need administrator rights? — CPU temperature access (UAC manifest).
   - Is there no-PC mode? — `--simulate` generates realistic fake data for testing.
   - Which languages does the app support? — 14, selectable in Settings.
   - Is it free? — Yes, AGPL-3.0; development supported via Patreon.
9. **License** — AGPL-3.0 + bundled LibreHardwareMonitor notice (existing content).

### `## Türkçe` (secondary)

Mirror of the English structure with the same nine sections, translated. Separated by a clear `---` divider. Turkish content is already translated in the current README; it gets restructured to match the new section set.

## Design Notes

- No emojis in section headers (user preference). Section names plain text.
- Symmetric section numbering/naming between EN and TR so the two halves stay maintainable.
- Images referenced relative to repo (`docs/images/...`) so they render on both GitHub and rendered Markdown.
- FAQ content is short; long answers stay in Troubleshooting or Protocol sections.
- Badges and Patreon support links preserved from the current README.

## Success Criteria

- A first-time visitor can see: what the app is (hero), what the UI looks like (showcase + gallery), where to get it (Download), and how to run it (Getting Started) — without scrolling past the first screen of the README.
- No broken image links; both language halves contain the same sections.
- Existing technical content is not lost or shortened.

## Out of Scope

- Website (`docs/index.html`) changes.
- Turkish-only README file split (bilingual single file stays).
- Adding new screenshot files to the repo (only the 6 images already in `docs/images/` are used).