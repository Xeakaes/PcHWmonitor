# Server Tunnel/Auth Rework (Sub-project B) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Unify the duplicated cloudflared tunnel logic into a single `TunnelManager` shared by CLI and tray, add automatic named-tunnel setup (ingress + DNS) with persistent config and tray feedback, fix the Android manual-add `useTls` gap, and land the parked Issue 7 migration test.

**Architecture:** New module `server/tunnel.py` owns cloudflared discovery, subprocess lifecycle, state/callbacks, ingress config generation, DNS routing, and `tunnel.json` persistence. `cli.py` constructs one manager and passes the same instance into the tray (kills the double-process bug); `tray.py` keeps only pystray dialogs/notifications. Android gets two small independent commits (useTls, migration test).

**Tech Stack:** Python 3 (FastAPI/pystray/tkinter existing stack, pytest), Kotlin (JUnit4 + kotlinx-coroutines-test, existing DataStore test harness).

**Spec:** `docs/plans/2026-10-04-server-tunnel-rework-design.md` (binding authority — this plan argues from it)

## Global Constraints

- Server gate: `python3 -m pytest server/tests -q` — the 46 existing tests stay green every task; new tests join the same suite.
- Android gate: `export JAVA_HOME=/home/xeakaes/jdk21 && export ANDROID_HOME=/home/xeakaes/android-sdk && ./gradlew :app:testDebugUnitTest` — the 117 existing tests stay green every task.
- No new runtime dependencies (esp. NO PyYAML — ingress YAML is written with an f-string).
- No new Android user-visible strings (locale parity gate: 187 keys × 14 files must stay exact).
- Direct commits on `main` (user consent, same as sub-project A); one commit per task; append a ledger line to `.superpowers/sdd/2026-09-17-android-ui-rework-plan/progress.md` in each task's commit step.
- All imports stay headless-safe: `tray.py` must remain importable without pystray/tkinter/display (lazy imports stay lazy) — tests import it.
- Spec sections referenced per task; where this plan pins a value the spec left open (callback signatures, constructor injection), the plan value is the decision.

## Review Focus

Five failure modes no single task's happy path exercises; each has its test in the owning task.

1. **Second concurrent cloudflared process** (the exact regression this project exists to fix — CLI flag and tray must never both spawn). Pinned by Task 1 `test_start_refused_while_process_alive` and Task 3 `test_apply_auto_start_refused_when_already_running`.
2. **Hung cloudflared freezes the tray** (named bootstrap runs `list/create/route dns` synchronously). Pinned by Task 3 `test_bootstrap_timeout_sets_error` — sleeping shim + `bootstrap_timeout=1.0` → returns False, state ERROR, no exception, within the timeout.
3. **Stale ingress port after `--port` changes** (config written once would keep mapping the old port forever). Pinned by Task 3 `test_ingress_rewritten_with_current_port` — start with 8765, stop, start with 9999 → config.yml contains only `localhost:9999`.
4. **Manual-add with hostname still connects `ws://`** (plaintext token error, Cloudflare tunnel unusable — the spec's §6 gap). Pinned by Task 6 `hostnamePresent_setsUseTlsTrue` and `hostnameBlank_setsUseTlsFalse`.
5. **Legacy migration write-back loops or corrupts** (the `dataStore.edit` inside the settings `map` at `SettingsStore.kt:67` re-runs on every emission). Pinned by Task 7 `legacyKeysPresent_migratesAndWritesBackExactlyOnce` and `noLegacyKeys_doesNotWriteBack`.

---

### Task 1: `TunnelManager` core — quick tunnel, state, lifecycle

**Files:**
- Create: `server/tunnel.py`
- Test: `server/tests/test_tunnel.py`

**Interfaces:**
- Produces (Task 2/3/4/5 consume all of these):
  - `class TunnelState(Enum): OFF, STARTING, RUNNING, ERROR` (values `"off"`, `"starting"`, `"running"`, `"error"`)
  - `class TunnelManager:` with
    `def __init__(self, *, cloudflared_finder=None, config_dir=None, cert_path=None, on_state=None, on_url=None, bootstrap_timeout=30.0)` — all keyword-only; `cloudflared_finder: Callable[[], Path | None] | None` defaults to the module's private `_find_cloudflared`; `config_dir`/`cert_path` stored as given and default to None — resolved lazily by the Task 2/3 helpers (`default_config_dir()` from Task 2, `Path.home() / ".cloudflared" / "cert.pem"`); `on_state: Callable[[TunnelState], None] | None`, `on_url: Callable[[str], None] | None` invoked from background threads
  - properties: `state: TunnelState`, `url: str | None`, `tunnel_name: str | None`, `last_error: str | None`, `is_running: bool` (True iff a child process exists and `poll() is None`)
  - `def start_quick(self, port: int) -> bool`
  - `def stop(self) -> None`
  - `def has_cloudflared(self) -> bool` (finder is not None)
  - `def find_cloudflared() -> Path | None` module-level public wrapper used by nothing outside tests
- Private: `_find_cloudflared()` moved verbatim from `cli.py:189-212` (single copy)

- [ ] **Step 1: Write the failing tests**

Create `server/tests/test_tunnel.py` with the same `sys.path.insert(0, str(Path(__file__).resolve().parent.parent))` preamble as `test_main.py:1-11`, plus a fixture:

```python
def write_shim(tmp_path, body: str) -> Path:
    """Executable python script standing in for cloudflared; body = script source after shebang."""
    p = tmp_path / "cloudflared_shim"
    p.write_text("#!/usr/bin/env python3\n" + body, encoding="utf-8")
    p.chmod(0o755)
    return p
```

Tests (each builds `TunnelManager(cloudflared_finder=lambda: shim, config_dir=tmp_path/"cfg", ...)`; URL/state callbacks record into `threading.Event`s and wait with `event.wait(timeout=5)`):

1. `test_start_quick_reaches_running_and_reports_url` — shim prints `https://quick-test.trycloudflare.com` then `time.sleep(60)`; assert `start_quick(8765)` is True, `on_url` fired with that URL, `state is TunnelState.RUNNING`, `is_running` True, `url` set; then `stop()` → `state is TunnelState.OFF`, `url is None`, `is_running` False.
2. `test_start_quick_missing_binary_sets_error` — finder returns `None`; assert returns False, `state is TunnelState.ERROR`, `last_error` mentions cloudflared, no exception raised.
3. `test_start_refused_while_process_alive` — start with the sleeping shim, then second `start_quick(9999)` returns False and `tunnel_name` is still `"quick"` (only one process — this pins the double-process regression at manager level).
4. `test_process_early_exit_marks_error` — shim prints nothing, `sys.exit(3)`; within 5s assert `state is TunnelState.ERROR` and `"exited (3)" in last_error` (reader thread observes EOF + non-zero returncode).
5. `test_stop_kills_hung_process` — shim ignores SIGTERM (`signal.signal(signal.SIGTERM, signal.SIG_IGN)` then sleep); `stop()` must still complete (kill fallback) with `is_running` False within ~6s.

- [ ] **Step 2: Run tests to verify they fail**

Run: `python3 -m pytest server/tests/test_tunnel.py -q`
Expected: FAIL — `ModuleNotFoundError: No module named 'tunnel'`.

- [ ] **Step 3: Implement `server/tunnel.py` core**

Approach notes (the tests pin behavior; body is yours):
- `TunnelState` enum; `TunnelManager.__init__` stores injected callables, initializes `self._process = None`, `self._state = TunnelState.OFF`, `self._url = self._tunnel_name = self._last_error = None`.
- `start_quick(port)`: refuse if `is_running` (return False); finder miss → `_set_state(ERROR, "cloudflared not found")`, return False; else Popen `[cloudflared, "tunnel", "--url", f"http://localhost:{port}"]` in try/except (failure → ERROR + `last_error=str(e)`), set `_tunnel_name = "quick"`, state RUNNING, start daemon reader thread, return True.
- Reader thread: per line → `logger.info`; extract first `https://` word → set `_url`, fire `on_url`. On EOF: `proc.wait()`; if returncode not in `(0, None)` and state is RUNNING → ERROR `"cloudflared exited ({code})"`.
- `stop()`: terminate → `wait(timeout=5)` → except → kill → except: pass; then clear `_process/_url/_tunnel_name`, state OFF, `last_error = None`.
- Callbacks are fired inside try/except so a raising subscriber never kills the reader thread.

- [ ] **Step 4: Run tests to verify they pass**

Run: `python3 -m pytest server/tests/test_tunnel.py -q`
Expected: 5 passed.

- [ ] **Step 5: Run the whole server suite**

Run: `python3 -m pytest server/tests -q`
Expected: 51 passed (46 + 5).

- [ ] **Step 6: Commit**

```bash
git add server/tunnel.py server/tests/test_tunnel.py
git commit -m "feat: TunnelManager core — quick tunnel, state machine, lifecycle"
```
Append ledger line: `- Sub-project B Task 1: complete (tunnel.py core, 51 server tests green)`

---

### Task 2: Persistent config, ingress writer, tunnel-id parser, DNS route

**Files:**
- Modify: `server/tunnel.py`
- Test: `server/tests/test_tunnel.py` (append)

**Interfaces:**
- Consumes: Task 1's module.
- Produces (Task 3 and Task 4 consume):
  - `@dataclass class TunnelConfig: tunnel_id: str | None = None; tunnel_name: str | None = None; hostname: str | None = None; auto_start: bool = False`
  - `def default_config_dir() -> Path` — Windows: `Path(os.environ["APPDATA"]) / "PcHwMonitor"` (fallback `Path.home() / "AppData/Roaming/PcHwMonitor"` if unset); else `XDG_CONFIG_HOME/pchwmonitor` or `~/.config/pchwmonitor`
  - `def load_tunnel_config(config_dir: Path) -> TunnelConfig` — file `<config_dir>/tunnel.json`; missing file or corrupt JSON → default instance (never raises)
  - `def save_tunnel_config(config_dir: Path, cfg: TunnelConfig) -> None` — mkdir parents; on OSError log warning and return (session keeps working unpersisted, per spec §4.3)
  - `def parse_tunnel_id(output: str) -> str | None` — first `id=` token win; else token after literal `id`; else last non-empty line **iff it matches the UUID regex** `^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$`; else None (spec §7 "parse defensively, UUID-validated")
  - `def write_ingress_config(config_dir: Path, tunnel_id: str, hostname: str, port: int) -> Path` — writes `<config_dir>/cloudflared/config.yml` (mkdir parents), exact content:

    ```
    tunnel: {tunnel_id}
    ingress:
      - hostname: {hostname}
        service: http://localhost:{port}
      - service: http_status:404
    ```
    (f-string, trailing newline; NO `credentials-file` line — spec §4.2)
  - `def route_dns(cloudflared: Path, tunnel_name: str, hostname: str, timeout: float = 30.0) -> bool` — runs `tunnel route dns <name> <host>`; True if returncode 0 **or** `"already exists"` in combined output (case-insensitive); subprocess exception/timeout → False

- [ ] **Step 1: Write the failing tests** (append to `server/tests/test_tunnel.py`)

1. `test_config_roundtrip_and_corrupt_defaults` — `save_tunnel_config(dir, TunnelConfig("id-1", "pc", "t.example.com", True))` → `load_tunnel_config` returns equal values; write garbage into `tunnel.json` → load returns `TunnelConfig()` defaults; missing dir → defaults.
2. `test_parse_tunnel_id_variants` — four asserts: `"Created tunnel t1 id=aaa"` → `"aaa"`; `"Created tunnel t1 with id bbb"` → `"bbb"`; last line `11111111-2222-3333-4444-555555555555` → that UUID; last line `some error text` → None.
3. `test_write_ingress_config_exact_content` — write with `("tid", "pc.example.com", 8765)` → file text equals the pinned YAML block exactly (string compare, trailing newline included).
4. `test_route_dns_tolerates_already_exists` — shim exits 1 printing `ERR: pc.example.com already exists` → returns True; shim exits 1 printing `permission denied` → returns False; shim exits 0 → True.

- [ ] **Step 2: Run tests to verify they fail**

Run: `python3 -m pytest server/tests/test_tunnel.py -q`
Expected: FAIL — `ImportError: cannot import name 'TunnelConfig'`.

- [ ] **Step 3: Implement the four helpers in `server/tunnel.py`**

Signatures exactly as pinned above; `route_dns` uses `subprocess.run(..., capture_output=True, text=True, timeout=timeout)` wrapped in try/except (CalledProcessError/TimeoutExpired/OSError → False + `logger.error`).

- [ ] **Step 4: Run tests to verify they pass**

Run: `python3 -m pytest server/tests/test_tunnel.py -q`
Expected: 9 passed (5 + 4).

- [ ] **Step 5: Commit**

```bash
git add server/tunnel.py server/tests/test_tunnel.py
git commit -m "feat: tunnel config persistence, ingress writer, id parser, DNS route"
```
Append ledger line: `- Sub-project B Task 2: complete (config + ingress + dns helpers, 55 server tests green)`

---

### Task 3: Named bootstrap, auto-start, login

**Files:**
- Modify: `server/tunnel.py`
- Test: `server/tests/test_tunnel.py` (append)

**Interfaces:**
- Consumes: Task 1 (state/lifecycle), Task 2 (`TunnelConfig`, `load/save_tunnel_config`, `parse_tunnel_id`, `write_ingress_config`, `route_dns`).
- Produces (Task 4/5 consume):
  - `@dataclass class TunnelInfo: id: str; name: str`
  - `def list_tunnels(self, timeout: float | None = None) -> list[TunnelInfo]` — `tunnel list` parse (skip `ID`-header/`---`/blank lines, `parts[0]=id, parts[1]=name`, mirror old `tray.py:301-322`); failure → `[]`
  - `def start_named(self, name: str, hostname: str, port: int) -> bool`
  - `def apply_auto_start(self, port: int) -> bool` — sync (caller threads it): returns False if `is_running` (precedence rule, spec §4.3); loads config; needs `auto_start && tunnel_name && hostname` else False; else `start_named(...)`
  - `def login(self) -> bool` — blocks: Popen `tunnel login`, opens first `cloudflare`-containing https URL via `webbrowser.open` (logic moved from `tray.py:410-437`), waits, returns `cert_path.exists()`; never raises
  - `def get_config(self) -> TunnelConfig` / `def set_auto_start(self, enabled: bool) -> None` (load → mutate → save; Task 4's menu toggle)
- `start_named` flow (spec §4.2, in the caller's thread):
  1. `is_running` → False. finder miss → ERROR, False.
  2. `cert_path` missing → `_set_state(ERROR, "Cloudflare login required")`, False.
  3. `list_tunnels()` contains `name` → its id; else `tunnel create <name>` (subprocess, `timeout=bootstrap_timeout`); parse via `parse_tunnel_id`; None → ERROR with stripped output tail, False.
  4. `write_ingress_config(config_dir, tunnel_id, hostname, port)` — **every call, current port** (Review Focus #3).
  5. `route_dns(...)` False → stop, ERROR with `last_error`, False.
  6. Popen `[cloudflared, "tunnel", "run", "--config", str(cfg_path), name]` → state RUNNING, `self._url = f"https://{hostname}"`, fire `on_url` (spec §4.2: named URL is derived, no scraping), save `TunnelConfig(tunnel_id, name, hostname, auto_start=<preserve previous value>)`, return True.
  7. Any exception mid-flow → ERROR + `str(e)`, return False; no child process left running.
  - All of steps 3/5 run with `timeout=self._bootstrap_timeout` (constructor param from Task 1).

- [ ] **Step 1: Write the failing tests** (append)

Shim bodies are parameterized by `sys.argv` dispatch, e.g. list shim:

```python
import sys, time
if sys.argv[1:3] == ["tunnel", "list"]:
    print("ID                                   NAME     CREATED")
    print("------------------------------------ -------- ----------")
    print("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee pc       2026-01-01")
elif sys.argv[1:3] == ["tunnel", "route"]:
    print("Updated pc.example.com route")
    time.sleep(60)
...
```

1. `test_start_named_existing_tunnel_no_create_and_ingress_written` — list shim contains `pc`; assert True, state RUNNING, `url == "https://pc.example.com"`, `on_url` fired, `tunnel.json` saved with `tunnel_name=="pc"`, and `config.yml` contains the Task-2 YAML with the current port; also assert the shim log shows `run --config` was used (shim appends its argv to a file `tmp_path/"calls.log"`).
2. `test_ingress_rewritten_with_current_port` — start 8765 → stop → start 9999 → `config.yml` contains `localhost:9999` and does NOT contain `8765` (Review Focus #3).
3. `test_start_named_missing_cert_errors` — `cert_path` injected as nonexistent path → False, `last_error == "Cloudflare login required"`, `tunnel.json` NOT written.
4. `test_bootstrap_timeout_sets_error` — shim for `list` does `time.sleep(60)`; manager with `bootstrap_timeout=1.0` → `start_named` returns False, state ERROR, elapsed < 5s, no exception (Review Focus #2).
5. `test_create_failure_surfaces_output_tail` — `create` shim prints `Account is out of credit` exit 1 (and list returns empty) → False, `"out of credit" in last_error`.
6. `test_route_dns_failure_aborts_start` — route shim prints `permission denied` exit 1 → False, state ERROR, no `run` call in `calls.log`.
7. `test_apply_auto_start_refused_when_already_running` — `start_quick` first, then valid auto-start config → `apply_auto_start` False, still exactly one live process (Review Focus #1); and `test_apply_auto_start_starts_saved_tunnel` — saved config with `auto_start=True` → True, RUNNING; `test_apply_auto_start_off_or_missing_returns_false`.
8. `test_login_reports_success_and_failure` — success shim writes the injected `cert_path` file then exit 0 → True; failing shim exits 1 without writing → False.
9. `test_set_auto_start_persists_via_get_config`.

- [ ] **Step 2: Run tests to verify they fail**

Run: `python3 -m pytest server/tests/test_tunnel.py -q`
Expected: FAIL — `AttributeError: 'TunnelManager' object has no attribute 'start_named'`.

- [ ] **Step 3: Implement in `server/tunnel.py`**

Exactly the flow pinned in Interfaces; keep helpers private (`_ensure_tunnel`, `_set_state`); thread-safety: only the reader thread and the caller mutate state — no lock needed for current single-start design (documented in a one-line comment).

- [ ] **Step 4: Run tests to verify they pass**

Run: `python3 -m pytest server/tests/test_tunnel.py -q`
Expected: 20 passed (9 + 11).

- [ ] **Step 5: Run the whole server suite**

Run: `python3 -m pytest server/tests -q`
Expected: 66 passed (46 + 20).

- [ ] **Step 6: Commit**

```bash
git add server/tunnel.py server/tests/test_tunnel.py
git commit -m "feat: named tunnel bootstrap — ingress, DNS route, auto-start, login"
```
Append ledger line: `- Sub-project B Task 3: complete (named bootstrap + auto-start + login, 66 server tests green)`

---

### Task 4: `tray.py` integration — shared manager, feedback, dialogs

**Files:**
- Modify: `server/tray.py` (delete `:26-184` — old `TunnelManager` + duplicate `_find_cloudflared`; rework handlers `:187-526`)
- Test: `server/tests/test_tunnel.py` (append one signature test)

**Interfaces:**
- Consumes: Task 1/3 (`TunnelManager`, `TunnelState`, `TunnelInfo`, `list_tunnels`, `get_config`, `set_auto_start`, `has_cloudflared`).
- Produces (Task 5 consumes): `def _run_with_tray(app: FastAPI, port: int, ssl_cert: str | None = None, ssl_key: str | None = None, tunnel_manager: TunnelManager | None = None) -> None` and `def _run_tray(stop_event, token, port, tunnel: TunnelManager) -> None` — when `tunnel_manager is None`, `_run_with_tray` constructs its own `TunnelManager()` (keeps this task independently green before Task 5 passes one in).

- [ ] **Step 1: Write the failing test** (append)

```python
def test_tray_headless_and_accepts_shared_manager():
    import inspect
    import tray  # must import without pystray/tkinter/display (lazy imports stay lazy)
    sig = inspect.signature(tray._run_with_tray)
    assert "tunnel_manager" in sig.parameters
```

- [ ] **Step 2: Run test to verify it fails**

Run: `python3 -m pytest server/tests/test_tunnel.py::test_tray_headless_and_accepts_shared_manager -q`
Expected: FAIL — old `_run_with_tray(app, port, ssl_cert, ssl_key)` has no `tunnel_manager` parameter.

- [ ] **Step 3: Rework `server/tray.py`**

- Delete old `TunnelManager` class and `_find_cloudflared`; `from tunnel import TunnelManager, TunnelState` (module import stays headless — `tunnel.py` imports only stdlib).
- `_run_tray(stop_event, token, port, tunnel)`: replace `t = TunnelManager()` with the param; wire callbacks **once, before `icon.run()`** (icon created first):
  - `on_url = lambda u: icon.notify(f"Tunnel ready:\n{u}", "PC HW Monitor")`
  - `on_state = lambda s: icon.notify(f"Tunnel error:\n{tunnel.last_error}", "PC HW Monitor") if s is TunnelState.ERROR else None`
- Handlers (all keep their existing guard style):
  - `_start_quick_tunnel`: drop the local `_find_cloudflared` pre-check (use `tunnel.has_cloudflared()`), drop `set_on_url_callback` juggling → just `tunnel.start_quick(port)`; False → `icon.notify(f"Failed: {tunnel.last_error}", ...)`.
  - `_start_named_tunnel`: same guards via `has_cloudflared()` + `tunnel.get_config()`-based auth check (`cert_path` presence stays `Path.home()/".cloudflared"/"cert.pem"`); dialogs run in threads as today; **both dialog paths end in `tunnel.start_named(name, hostname, port)` on a background thread** (bootstrap is blocking — Task 3 contract):
    - hostname source order: saved config when `cfg.tunnel_name == name` (reuse `cfg.hostname`) → else a `simpledialog.askstring("Hostname", "Public hostname (e.g. pc.example.com):")` prompt; blank/None → abort silently.
    - `_ask_new_tunnel_name` gains the hostname prompt after the name prompt (spec §5.2).
    - move `_list_existing_tunnels` → `tunnel.list_tunnels()`.
  - `_cloudflare_login`: replace body with thread → `ok = tunnel.login()` → `icon.notify("Cloudflare login successful." if ok else "Login failed — check cloudflared output.", ...)` (spec §5.3 feedback table).
  - `_tunnel_status_text`: `STARTING → "Tunnel: starting..."; RUNNING → f"Tunnel: {tunnel.url or tunnel.name}"; ERROR → f"Tunnel: error — {tunnel.last_error}"; OFF → "Tunnel: off"`.
  - `_connection_payload`, `_stop_tunnel`, `_copy_tunnel_url`, `_show_tunnel_url`, `_show_info`: keep logic, reading `tunnel.url`/`is_running` (same property names as before — minimal churn).
- New menu item after `Cloudflare Login`: `pystray.MenuItem("Auto-start named tunnel", _toggle_auto_start, checked=lambda item: tunnel.get_config().auto_start)` — toggle handler calls `tunnel.set_auto_start(not current)` and re-notifies the new state.
- Startup: after menu built, before `icon.run()`:
  `threading.Thread(target=tunnel.apply_auto_start, args=(port,), daemon=True).start()` — safe when no config (returns False immediately); when cli passed an already-started manager, `apply_auto_start` refuses (Task 3 guard).
- `_run_with_tray` gains the `tunnel_manager` param (default None → construct) and forwards it to `_run_tray`; cleanup after `_run_tray` returns: `tunnel.stop()` (replaces nothing — was `Exit` handler's job, keep that too).

- [ ] **Step 4: Compile check**

Run: `python3 -m py_compile server/tray.py server/tunnel.py`
Expected: silent success.

- [ ] **Step 5: Run the whole server suite**

Run: `python3 -m pytest server/tests -q`
Expected: 67 passed (66 + 1); `grep -n "_find_cloudflared" server/tray.py` → no matches; `grep -n "class TunnelManager" server/tray.py` → no matches.

- [ ] **Step 6: Commit**

```bash
git add server/tray.py server/tests/test_tunnel.py
git commit -m "feat: tray uses shared TunnelManager with login/URL feedback and auto-start toggle"
```
Append ledger line: `- Sub-project B Task 4: complete (tray unified, feedback + auto-start menu)`

---

### Task 5: `cli.py` integration — single shared instance (double-process fix)

**Files:**
- Modify: `server/cli.py`

**Interfaces:**
- Consumes: Task 1 (`TunnelManager` console wiring), Task 3 (`start_quick/start_named/get_config`), Task 4 (`_run_with_tray(..., tunnel_manager=)`).

- [ ] **Step 1: Rework `server/cli.py`**

- Add argument: `parser.add_argument("--tunnel-hostname", default=None, help="public hostname for a named tunnel (falls back to saved tunnel config)")` (next to `--tunnel`, `cli.py:56-57`).
- **Delete** the whole inline block `cli.py:129-186` (tunnel_process/tunnel_url/Popen/reader/sleep/cleanup) **and** `_find_cloudflared` `cli.py:189-212`. Keep `subprocess` import (still used by SSL `auto`).
- After `build_app(...)` and **before** the frozen/tray branch (`cli.py:167`), construct once:

```python
manager = TunnelManager(
    on_state=lambda s: logger.info("tunnel state: %s", s.value),
    on_url=lambda u: print(f"  TUNNEL: {u}"),
)
```

- Start per args (explicit `--tunnel` wins over tray auto-start — Task 3's `apply_auto_start` guard plus the tray only runs when cli didn't start one):

```python
if args.tunnel == "quick":
    ok = manager.start_quick(args.port)
elif args.tunnel is not None:
    hostname = args.tunnel_hostname or manager.get_config().hostname
    if not hostname:
        logger.error("named tunnel needs a hostname (--tunnel-hostname or saved config)")
        sys.exit(1)
    ok = manager.start_named(args.tunnel, hostname, args.port)
else:
    ok = True
if not ok:
    logger.error("tunnel failed: %s", manager.last_error)
    sys.exit(1)
```

  (missing cloudflared → `start_*` False + ERROR → exit 1, preserving the old `cli.py:134-136` contract.)
- Banner `cli.py:176-179` keeps its shape, reading the manager: `if manager.url: print(f"  TUNNEL: {manager.url}") elif args.tunnel is not None: print("  TUNNEL: starting... check tray icon or logs")` (the `on_url` callback prints the real URL whenever it arrives).
- Frozen branch: `_run_with_tray(app, args.port, ssl_cert=ssl_cert, ssl_key=ssl_key, tunnel_manager=manager)`.
- End of `main()`: replace the `tunnel_process` cleanup (`cli.py:183-186`) with `manager.stop()`.
- Add `from tunnel import TunnelManager` beside the existing `from tray import _run_with_tray` (`cli.py:12`).

- [ ] **Step 2: Compile + interface checks**

Run: `python3 -m py_compile server/cli.py && grep -c "_find_cloudflared\|tunnel_process" server/cli.py || true`
Expected: silent compile; grep prints `0`.

- [ ] **Step 3: Run the whole server suite + CLI help**

Run: `python3 -m pytest server/tests -q && python3 server/cli.py --help | grep tunnel-hostname`
Expected: 67 passed; help line printed. (The `tunnel_manager` signature pin from Task 4 keeps the shared-instance contract green here — no new test in this task.)

- [ ] **Step 4: Commit**

```bash
git add server/cli.py
git commit -m "feat: cli shares one TunnelManager with tray — fix double tunnel process"
```
Append ledger line: `- Sub-project B Task 5: complete (cli/tray single tunnel owner — double-process bug dead; 67 server tests green)`

---

### Task 6: Android `useTls` on manual add (TDD)

**Files:**
- Modify: `app/src/main/java/com/Obscrum/pchwmonitor/ui/servers/ServerEditViewModel.kt:80-90`
- Test: `app/src/test/java/com/Obscrum/pchwmonitor/ServerEditViewModelTest.kt` (append)

**Interfaces:**
- Consumes: existing harness `store(tmpDir())`, `Dispatchers.setMain(UnconfinedTestDispatcher())` (`ServerEditViewModelTest.kt:44-63`), `ServerConfig.useTls` (`ServerConfig.kt:13`), semantics matched from `QrMatcher.kt:31`.

- [ ] **Step 1: Write the failing tests** (append)

```kotlin
@Test
fun hostnamePresent_setsUseTlsTrue_onAdd() = runTest {
    val handle = store(tmpDir())
    val vm = ServerEditViewModel(handle.store, serverId = null)
    vm.port.first { it == "8765" }
    vm.name.value = "Tunnel PC"
    vm.ip.value = ""
    vm.hostname.value = " pc.example.com "
    assertTrue(vm.save())
    val saved = handle.store.settings.first().servers.single()
    assertEquals("pc.example.com", saved.hostname)
    assertTrue(saved.useTls)
    handle.close()
}

@Test
fun hostnameBlank_setsUseTlsFalse_onAdd() = runTest {
    val handle = store(tmpDir())
    val vm = ServerEditViewModel(handle.store, serverId = null)
    vm.port.first { it == "8765" }
    vm.ip.value = "10.0.0.5"
    assertTrue(vm.save())
    assertFalse(handle.store.settings.first().servers.single().useTls)
    handle.close()
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `export JAVA_HOME=/home/xeakaes/jdk21 && ./gradlew :app:testDebugUnitTest --tests '*ServerEditViewModelTest.hostnamePresent*'`
Expected: FAIL — `assertTrue(saved.useTls)` (default false).

- [ ] **Step 3: Fix `ServerEditViewModel.save()`**

In the `isNew` branch only, add to the `ServerConfig(...)` constructor: `useTls = trimmedHostname.isNotBlank(),` — one line, placed after `hostname = ...` (`ServerEditViewModel.kt:88`). Edit path untouched (`SettingsStore.kt:266` already correct).

- [ ] **Step 4: Run the full Android gate**

Run: `export JAVA_HOME=/home/xeakaes/jdk21 && export ANDROID_HOME=/home/xeakaes/android-sdk && ./gradlew :app:testDebugUnitTest`
Expected: 119 passed (117 + 2).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/Obscrum/pchwmonitor/ui/servers/ServerEditViewModel.kt \
        app/src/test/java/com/Obscrum/pchwmonitor/ServerEditViewModelTest.kt
git commit -m "fix: set useTls when manually adding a server with a tunnel hostname"
```
Append ledger line: `- Sub-project B Task 6: complete (useTls add-path fixed TDD, 119 Android tests)`

---

### Task 7: Issue 7 — legacy migration test (TDD)

**Files:**
- Test: `app/src/test/java/com/Obscrum/pchwmonitor/SettingsStoreTest.kt` (append)
- Modify (only if a test exposes a defect): `app/src/main/java/com/Obscrum/pchwmonitor/data/SettingsStore.kt:55-74` — **expected: no production change**; if behavior differs from the test's pinned expectations, STOP and run systematic-debugging before touching the store.

**Interfaces:**
- Consumes: existing `store(tmpDir())` harness; legacy keys `server_ip`/`server_port`/`auth_token` (`SettingsStore.kt:37-39`), write-back at `SettingsStore.kt:67`.

- [ ] **Step 1: Write the failing tests** (append; seed legacy keys via `handle.dataStore.edit { }` — `edit` is already imported at `SettingsStoreTest.kt:6`)

```kotlin
@Test
fun legacyKeysPresent_migratesAndWritesBackExactlyOnce() = runTest {
    val dir = tmpDir()
    val first = store(dir)
    first.dataStore.edit {
        it[stringPreferencesKey("server_ip")] = "192.168.1.50"
        it[intPreferencesKey("server_port")] = 9010
        it[stringPreferencesKey("auth_token")] = "legacytok"
    }
    val s1 = first.store.settings.first()
    assertEquals(1, s1.servers.size)
    assertEquals("192.168.1.50", s1.servers.single().ip)
    assertEquals(9010, s1.servers.single().port)
    assertEquals("legacytok", s1.servers.single().token)
    val s2 = first.store.settings.first()          // second emission: write-back must be stable
    assertEquals(s1.servers, s2.servers)
    first.close()
    val second = store(dir)                         // fresh DataStore read = persisted write-back
    assertEquals("192.168.1.50", second.store.settings.first().servers.single().ip)
    second.close()
}

@Test
fun noLegacyKeys_doesNotWriteBack() = runTest {
    val handle = store(tmpDir())
    handle.store.settings.first()
    val raw = handle.dataStore.data.first()[stringPreferencesKey("servers_json")]
    assertNull(raw)                                  // untouched — no migration write
    assertTrue(handle.store.settings.first().servers.isEmpty())
    handle.close()
}
```

(`intPreferencesKey` import must be added to the file's imports.)

- [ ] **Step 2: Run tests to verify they fail**

Run: `export JAVA_HOME=/home/xeakaes/jdk21 && ./gradlew :app:testDebugUnitTest --tests '*SettingsStoreTest.legacyKeys*'`
Expected: FAIL only if migration is broken; if both tests PASS immediately, they are characterization tests pinning current behavior — record that in the ledger line and proceed (Task 7's deliverable is the pin, not a fix).

- [ ] **Step 3: Implement only if red**

If red: fix `SettingsStore.kt:55-74` minimally per systematic-debugging (TDD, separate fix commit). If green at Step 2, skip this step.

- [ ] **Step 4: Run the full Android gate**

Run: `export JAVA_HOME=/home/xeakaes/jdk21 && export ANDROID_HOME=/home/xeakaes/android-sdk && ./gradlew :app:testDebugUnitTest`
Expected: 121 passed (119 + 2).

- [ ] **Step 5: Commit**

```bash
git add app/src/test/java/com/Obscrum/pchwmonitor/SettingsStoreTest.kt
git commit -m "test: pin legacy server settings migration write-back (Issue 7)"
```
Append ledger line: `- Sub-project B Task 7: complete (Issue 7 migration test landed, 121 Android tests)`

---

### Task 8: README tunnel docs

**Files:**
- Modify: `README.md` §5b (`:152-231`, Turkish mirror `:414-490`), tray menu list (`README.md:100`)

**Interfaces:**
- Consumes: Task 4/5 behavior (final UX).

- [ ] **Step 1: Update the English §5b remote-access section**

- Replace the stale step (`README.md:209-211`) "enter tunnel URL as the IP, port 443, Enable TLS" with: scan the tray QR (or paste the hostname into the **Cloudflare Tunnel URL** field); the app sets TLS automatically for hostnames.
- Add tray tunnel submenu docs: Start Quick/Start Named (name + public hostname prompt), Stop, Copy/Show URL, **Cloudflare Login**, **Auto-start named tunnel**; note named tunnels get automatic ingress + DNS setup and persist across restarts.
- Document `--tunnel [quick|name]` + `--tunnel-hostname <host>` and the config file location (`%APPDATA%\PcHwMonitor\tunnel.json` / `~/.config/pchwmonitor/tunnel.json`).

- [ ] **Step 2: Mirror in the Turkish section**

Same changes in the Turkish block (`:414-490`) — the repo keeps both languages in sync.

- [ ] **Step 3: Verify**

Run: `grep -n "Enable TLS\|--tunnel-hostname\|Auto-start" README.md`
Expected: stale "Enable TLS" step gone; new flag and menu item present (both language sections).

- [ ] **Step 4: Commit**

```bash
git add README.md
git commit -m "docs: update tunnel instructions — hostname flow, tray menu, auto-start"
```
Append ledger line: `- Sub-project B Task 8: complete (README tunnel docs refreshed, both languages)`

---

### Task 9: Final gates, ledger close-out

**Files:**
- Modify: `.superpowers/sdd/2026-09-17-android-ui-rework-plan/progress.md`

- [ ] **Step 1: Run both full gates**

Run: `python3 -m pytest server/tests -q` → expect 67 passed.
Run: `export JAVA_HOME=/home/xeakaes/jdk21 && export ANDROID_HOME=/home/xeakaes/android-sdk && ./gradlew :app:testDebugUnitTest` → expect 121 passed.

- [ ] **Step 2: Compile + smoke**

Run: `python3 -m py_compile server/cli.py server/tray.py server/tunnel.py && python3 server/cli.py --help >/dev/null && git status --short`
Expected: silent; clean tree (all tasks committed).

- [ ] **Step 3: Close out the ledger**

Append: `- Sub-project B: COMPLETE (9 tasks — single TunnelManager owner, named auto-setup + persistence, tray feedback, useTls fix, Issue 7; server 67, Android 121 green). Remaining for 1.6.1: device checklist M1/M2/M7/M9 + blocked halves (needs user + real server), then version bump 1.6.0→1.6.1.`

- [ ] **Step 4: Report to user**

Summary of commits + gate numbers; remind that the device checklist and version bump remain (spec §8 / handoff §8 — not in this plan).
