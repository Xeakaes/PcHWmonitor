# Design — Sub-project B: Server tunnel/auth rework (v1.6.1)

**Status:** approved by user (2026-10-04). Binding authority for the implementation plan.
**Context:** Sub-project A (Android UI rework) is code-complete (`8534346`, `9ac0baa`, 117/117
tests). This spec covers the server-side rework plus the two Android leftovers parked for B.

## 1. Problem

1. **Two independent tunnel code paths.** `server/cli.py:129-186` and `server/tray.py:26-156`
   each implement cloudflared discovery, process launch, URL scraping, and lifecycle — with
   diverging behavior (CLI: no auth/create/list, `sys.exit(1)` on missing binary, `sleep(3)`
   wait, no kill fallback; tray: auth gate, create/list, callbacks, notify).
2. **Double-process bug.** The `cli.py` `--tunnel` block runs *before* the frozen/tray branch
   (`cli.py:132` vs `:167`), so `PcHwMonitor.exe --tunnel quick` starts a cloudflared the tray
   never sees: status shows "Tunnel: off", Stop/Copy act on nothing, menu can start a second
   tunnel.
3. **No named-tunnel URL.** `cloudflared tunnel run <name>` is invoked bare — no ingress
   config, no DNS route — so a properly configured named tunnel prints no `https://` line,
   `_url` stays `None`, and QR/status fall back to LAN ip:port (useless remotely).
4. **No persisted tunnel config.** Restart forgets the tunnel name; no auto-start.
5. **No login feedback.** `cloudflared tunnel login` result is logged only
   (`tray.py:431-437`); named-start success/failure likewise (`:368-373`, `:397-402`).
6. **Android `useTls` gap.** The manual add path (`ServerEditViewModel.kt:80-90`) never sets
   `useTls`, so a tunnel hostname saved manually yields `ws://` + `AppError.PlaintextToken`
   (`MonitorController.kt:81-89`). QR (`QrMatcher.kt:31`) and edit (`SettingsStore.kt:266`)
   paths are correct. Violates `2026-09-17-android-ui-rework-design.md:117-119`.
7. **Issue 7 (parked):** no test guards the legacy `server_ip/port/auth_token` → `servers_json`
   migration (`SettingsStore.kt:57-69`); Task 1 flagged its gating change for reviewer
   confirmation (`.superpowers/.../task-1-report.md:25,89`).

## 2. Goals / success criteria

- One tunnel code path shared by CLI flag and tray menu; `--tunnel` in frozen builds uses the
  same manager instance the tray displays (no second process).
- Named tunnel: user provides tunnel name + hostname once → ingress config + DNS route are
  created automatically, config persists, QR carries the hostname (`wss://` on Android).
- Tray reports login and tunnel start success/failure via `notify`.
- Auto-start of the last named tunnel at tray launch when the saved `auto_start` flag is on.
- Android manual-add path sets `useTls=true` iff hostname is present; guarded by a test.
- Issue 7 migration test lands in the JVM suite.
- Gates: `python -m pytest server/tests` green (incl. new tunnel tests), `./gradlew
  :app:testDebugUnitTest` green (117 + new).

## 3. Non-goals

Room `MigrationTest` instrumentation; tray icon/title state visuals; auto-start for quick
tunnels; protocol changes (`WelcomeMessage` gains no hostname); LAN discovery of tunnel
servers; a tunnel settings UI beyond the existing tray dialogs; Windows service install.

## 4. `server/tunnel.py` — the single owner (approach 1, user-approved)

### 4.1 Module API

```python
class TunnelState(Enum): OFF, STARTING, RUNNING, ERROR

class TunnelManager:
    def __init__(self, on_state=None, on_url=None): ...   # callbacks (state, url, error)
    # read-only: state, url (quick URL or https://hostname), tunnel_name, last_error
    def start_quick(self, port: int) -> bool
    def start_named(self, name: str, hostname: str, port: int) -> bool
    def stop(self) -> None            # terminate -> wait(5) -> kill fallback
    def list_tunnels(self) -> list[TunnelInfo]   # parse `cloudflared tunnel list`
    def login(self) -> bool           # `cloudflared tunnel login` + browser open
    def apply_auto_start(self, port: int) -> bool  # called once at tray start
```

- `_find_cloudflared()` lives here **once** (frozen + dev candidates from `cli.py:189-212`).
- All subprocess calls: `Popen` guarded by try/except, stdout reader thread captures output,
  URLs/errors parsed, output tail retained for error messages.
- Quick URL: scrape `trycloudflare.com` from stdout (existing logic). Named URL: derived from
  the configured hostname — set `url = f"https://{hostname}"` when the process reaches
  RUNNING; no stdout scraping needed.
- Every state change and URL arrival fires the callbacks; subscribers decide presentation.

### 4.2 Named-tunnel bootstrap (`start_named`)

1. `cert.pem` absent → `ERROR("Cloudflare login required")` (tray surfaces an offer to log in;
   CLI prints the same). Parity with `tray.py:286-299`.
2. `tunnel list` → if `name` exists, read its ID; else `cloudflared tunnel create <name>` and
   parse the ID from structured output (keep the "last line might be the ID" fallback, but
   validated as a UUID before use; failure → `ERROR` with captured output tail).
3. Write ingress config to `<config_dir>/cloudflared/config.yml`:

   ```yaml
   tunnel: <tunnel-id>
   ingress:
     - hostname: <hostname>
       service: http://localhost:<port>
     - service: http_status:404
   ```

   No `credentials-file` line: cloudflared resolves its own credentials (created by
   `tunnel create` under `~/.cloudflared/`). Port is written from the **current** `--port`
   on every start (a changed port never leaves a stale mapping).
4. `cloudflared tunnel route dns <name> <hostname>` — idempotent: treat "already exists" /
   equivalent as success; any other failure → `ERROR` with output tail.
5. `cloudflared tunnel run --config <config.yml> <name>` → STARTING → RUNNING (reader thread
   confirms process alive); set `url = https://<hostname>`.

Failure at any step → `ERROR` + `last_error`; partially started child processes are stopped.

### 4.3 Persistent config

File `<config_dir>/tunnel.json`, written only for named tunnels:

```json
{"tunnel_id": "...", "tunnel_name": "...", "hostname": "...", "auto_start": true}
```

`_config_dir()`: Windows `%APPDATA%\PcHwMonitor\`, else `$XDG_CONFIG_HOME/pchwmonitor` or
`~/.config/pchwmonitor` (created on demand; write failures → logged warning, session still
works without persistence). Quick tunnels never touch this file. The manager owns all reads
and writes of `tunnel.json`; no other module touches it.

`auto_start` is toggled from the tray menu (see §5) and honored once at tray launch by
`apply_auto_start(port)` in a background thread; quick tunnels are never auto-started.
Precedence: an explicit `--tunnel` argument always wins — when present, `apply_auto_start`
is not called (no second tunnel).

## 5. Integration

### 5.1 `server/cli.py`

- Delete the inline tunnel block (`:129-186`) and `_find_cloudflared` (`:189-212`).
- Build one `TunnelManager` with console callbacks (state → log/print, url → `TUNNEL: <url>`,
  preserving the `cli.py:176-179` contract) **before** the frozen/tray branch and pass the
  same instance into `_run_with_tray(app, port, ssl_cert, ssl_key, tunnel_manager)`.
- `--tunnel [quick|name]` unchanged; new optional `--tunnel-hostname <host>` (used for
  `start_named`; falls back to `tunnel.json`'s hostname when omitted).
- Missing cloudflared with `--tunnel` → keep CLI contract: log error + `sys.exit(1)`.
- Cleanup on shutdown calls `manager.stop()` (replaces `:183-186`).

### 5.2 `server/tray.py`

- Delete `TunnelManager` (`:26-156`) and the duplicated `_find_cloudflared` (`:159-184`).
- Menu handlers delegate to the shared manager; keep the existing pick-existing/create-new
  dialogs (`:324-402`) but feed their result to `start_named(name, hostname, port)` — the
  create-new dialog gains a hostname field (required, since ingress needs it).
- `notify` on: login success/failure, named start success/failure, quick start
  failure/success (URL), URL arrival for named tunnels. Log lines are kept.
- Status line (`:479-482`) reads `manager.state` / `manager.url`.
- New menu items: `Auto-start named tunnel` (checkable, toggles `tunnel.json.auto_start`),
  keep `Cloudflare Login` (now delegating to `manager.login()`).
- `_connection_payload()` prefers `manager.url`'s hostname (fixes QR falling back to LAN
  while a named tunnel runs).
- Tray launch: `manager.apply_auto_start(port)` in a background thread.

### 5.3 Feedback rules (both surfaces)

| Event | CLI | Tray |
|---|---|---|
| cloudflared missing | error + exit 1 | `notify` error |
| login result | print ok/failed | `notify` ok/failed |
| named start ok | print `https://<host>` | `notify` + status RUNNING |
| named/quick start fail | print `last_error` | `notify` `last_error` |
| stop | log | existing `notify` |

## 6. Android leftovers (separate commits, TDD)

1. **`useTls` fix** — `ServerEditViewModel.save()`: `useTls = trimmedHostname.isNotBlank()`
   (semantics identical to `QrMatcher.kt:31`). No UI toggle (implicit per A-design §"Glass
   & connection", `:117-119`). Test-first: cover hostname-present → `useTls=true`, blank →
   false. `ServerValidator` unchanged (it validates transport fields, not policy).
2. **Issue 7 — legacy migration test** (ruling this spec: SettingsStore legacy→servers_json,
   not Room; Room instrumentation stays out of scope per §3). Two directions: legacy keys
   present + `servers_json` absent → migrated list written back and legacy keys removed (per
   `SettingsStore.kt:57-69` actual behavior); no legacy keys → no write-back. Written in
   `SettingsStoreTest`, JVM.

## 7. Testing

- **New `server/tests/test_tunnel.py`** (pytest, no network): a fake `cloudflared` shim
  (small script printing canned output) is placed on PATH/`_find_cloudflared` seam to assert:
  quick URL scrape → RUNNING + `on_url`; named bootstrap writes the expected ingress YAML
  (structure + current port); `route dns` "already exists" tolerated; missing binary →
  ERROR not exception; `stop()` terminates (and kills after a hang); `tunnel.json`
  round-trip; auto-start fires only for saved named config.
- **Existing gates:** `python -m pytest server/tests -v` (46 + new), smoke test
  (`ci.yml:66-77` unchanged), `./gradlew :app:testDebugUnitTest`.
- **Manual/device (blocked until user + real server):** M1, M2, M7, M8-remainder, M9,
  M6-visual half, M10 — unchanged from the A checklist.

## 8. Docs & commits

- README §5b: replace the outdated "enter tunnel URL as IP, port 443, Enable TLS" step with
  the hostname + auto-`useTls` flow; document the tray tunnel submenu, Cloudflare Login, and
  auto-start item (currently undocumented).
- Commit sequence: (1) `server/tunnel.py` + cli/tray integration + tests, (2) Android `useTls`
  fix (test-first), (3) Issue 7 migration test, (4) README updates. Each passes its gates
  before the next; ledger entries in `.superpowers/sdd/2026-09-17-android-ui-rework-plan/progress.md`
  after each.

## 9. Risks / compatibility

- **Windows path:** frozen exe may run from a read-only dir — hence config under `%APPDATA%`;
  cloudflared's own credentials stay untouched.
- **`tunnel list`/`create` output formats vary by cloudflared version** — parse defensively,
  fall back to UUID-validation of the last line, surface raw output on failure.
- **Behavior change:** `--tunnel quick` under the frozen exe no longer double-starts; anyone
  relying on the CLI block running *instead* of the tray now gets the tray (intended fix).
- Existing 46 server tests and 117 Android tests must stay green throughout.
