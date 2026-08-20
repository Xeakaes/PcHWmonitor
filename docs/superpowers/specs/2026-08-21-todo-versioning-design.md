# TODO Structure & Versioning Plan (v1.5 / v1.6) — Design

Date: 2026-08-21

## Context

PC HW Monitor is at v1.4 (versionCode 5). An F-Droid inclusion MR (fdroiddata!44635) is
open with label `waiting-for-upstream`; the MR is technically complete (green pipeline,
signed APKs with hashes posted) and depends on a maintainer removing the label. If
F-Droid approves, the user wants to ship a "big update" release as a promo moment.

The existing `ROADMAP.md` lists all future ideas under "v1.5 (Planned)" and "v2.0".
We need a working TODO structure that tracks per-version work without duplicating the
vision.

## Decisions

1. **Two files:**
   - `ROADMAP.md` — stays as the long-term vision document.
   - `TODO.md` (new) — tracks actual work per version with checkboxes.
2. **TODO structure (approach A — simple):**
   - `## v1.5` — features committed to the next release
   - `## v1.6` — planned next-next release
   - `## Backlog` — everything else from the roadmap, flat, unchecked
   - F-Droid MR tracking line pinned at top so it stays visible.
3. **v1.5 scope (4 quick wins):**
   - Material You dynamic theming (dynamic color extraction, integrates with existing palette system + light/dark)
   - Local network discovery (auto-detect the PC server on the same LAN)
   - Notification improvements (ongoing notification with key metrics, expandable)
   - New color palettes (more palette options, user-customizable)
4. **v1.6 scope (start of work):**
   - Multiple PC support (connect to several PCs simultaneously)
   - TLS/SSL secure connection (encrypted WebSocket)
5. **ROADMAP.md updates:**
   - Change the "v1.5 (Planned)" header to a generic "Vision" framing (v1.5/v1.6 items now live in TODO.md), keeping all idea bullets.
   - Keep v2.0 long-term section as-is.

## Non-goals

- No sub-task breakdown inside TODO.md (detailed plans come per-version via writing-plans).
- No GitHub Projects/Issues migration.
- No code changes in this step — file reorganization only.

## Outputs

- Create `TODO.md` at repo root.
- Update `ROADMAP.md` section headers.
- Commit both files.