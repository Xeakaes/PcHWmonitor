# Android UI Rework (Sub-project A) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix all 1.6 Android-side defects — restructure the connect flow into focused screens, repair saved-servers corruption, make glassmorphism persistent, apply the background-connection policy, and reach full 14-locale translation coverage — as the first half of the 1.6.1 release.

**Architecture:** Three new UI units (servers list, server edit, reworked settings) behind two new nav routes; all connection writes become per-row store operations (`updateServerConnection(id, ...)`) instead of a global form; a pure `ConnectionPolicy` decides which controllers connect/disconnect and `MonitorViewModel` applies it on a `distinctUntilChanged` connection-key projection; localization finishes with a CI parity test over all 14 locale files.

**Tech Stack:** Kotlin, Jetpack Compose (Material3), DataStore Preferences, kotlinx.serialization, OkHttp WebSocket, JUnit4, Gradle (no new dependencies).

**Spec:** `docs/plans/2026-09-17-android-ui-rework-design.md` — the plan argues from the spec; executors read both.

**Out of scope:** `server/` changes (sub-project B), versionCode/versionName bump (happens after A+B), dashboard visual redesign, Windows build scripts.

## Global Constraints

- Every Gradle command runs with `export JAVA_HOME=/home/xeakaes/jdk21` first (no `java` on default PATH; SDK at `/home/xeakaes/android-sdk`, `local.properties` already correct).
- Unit tests: `./gradlew :app:testDebugUnitTest` (optionally `--tests 'com.Obscrum.pchwmonitor.FooTest'`). Compile check: `./gradlew :app:compileDebugKotlin`. Server tests must stay green: `server/.venv/bin/python -m pytest server/tests/` (not touched by this plan).
- Existing ~79 unit tests must keep passing after every task.
- No new libraries/dependencies. Native Linux only (no `/mnt/c`, no WSL paths).
- From Task 10 onward, `LocaleParityTest` is green after every commit: all 14 locale files (`values/` + 13 translations) have identical key sets and identical format-specifier sets per key.
- Base `values/strings.xml` is the English source of truth (exception fixed in Task 10: `summary_format` currently contains Turkish `ort.`).
- versionCode/versionName unchanged (stays 1.6).

## Review Focus

1. **Corrupt `servers_json` must never be wiped by a background read.** Today a decode failure triggers the legacy migration which rewrites the key. Expected: reads leave a corrupt raw value untouched and surface an empty list. Pinned by `SettingsStoreTest.corrupt_serversJson_readPreservesRaw_andReturnsEmpty` (Task 1).
2. **QR payload identity ambiguity.** A hostname payload has `ip=""`; an existing server may match by hostname or by `ip:port`; the same payload must never attach to the wrong row. Expected: hostname match wins over `ip:port`; hostname-only payload with no hostname match creates a new server. Pinned by `QrMatcherTest` cases (Task 2).
3. **Rapid active-server switching in OFF mode must converge to last-selected.** Expected: after switching A→B→C quickly, plan ends with only C connected (intermediate connects may happen but the final applied plan is derived from final state, and applying is idempotent). Pinned by `ConnectionPolicyTest.plan_isIdempotent_overRepeatedActiveChange` (Task 4) plus checklist M7 (Task 5).
4. **Preference-only changes must not touch sockets.** Expected: theme/language/palette/chart-window/background/glassmorphism produce an identical `ConnectionKey`, so no controller action fires. Pinned by `ConnectionPolicyTest.connectionKey_ignoresAllPreferenceFields` (Task 4) plus checklist M8 (Task 5).
5. **Deleting the last server must reset the dashboard — no stale green "Bağlı".** Expected: status/connection/error clear and the dashboard shows the empty-state CTA. MonitorViewModel is an AndroidViewModel (not unit-testable without Robolectric); pinned by checklist M4 (Task 5 applies the state reset, Task 6 renders the CTA) — do not skip M4.

---

## File Structure

| File | Change | Task |
|---|---|---|
| `app/src/main/java/com/Obscrum/pchwmonitor/data/SettingsStore.kt` | per-row API, `savePreferences`, validation, decode safety, legacy mirror, `backgroundConnectAll` | 1, 4 |
| `app/src/main/java/com/Obscrum/pchwmonitor/data/ServerValidator.kt` | NEW — pure validation rules | 1 |
| `app/src/main/java/com/Obscrum/pchwmonitor/util/QrMatcher.kt` | NEW — pure QR→server matching | 2 |
| `app/src/main/java/com/Obscrum/pchwmonitor/data/AppError.kt` | NEW — error codes | 3 |
| `app/src/main/java/com/Obscrum/pchwmonitor/ui/ErrorMapper.kt` | NEW — code→string resolution | 3 |
| `data/network/{WsClient,StatusParser,WebSocketClient}.kt` | `ParseFailure` carries `AppError` | 3 |
| `MonitorController.kt` | `lastError: StateFlow<AppError?>`, `dispose()` | 3, 5 |
| `app/src/main/java/com/Obscrum/pchwmonitor/data/ConnectionPolicy.kt` | NEW — pure plan/key functions | 4 |
| `MonitorViewModel.kt` | policy-driven sync, state reset, trustCert resubscribe, `connectViaQr`, glassmorphism derivation | 5, 6 |
| `BackgroundConnectionHandler.kt` | foreground/background callbacks | 5 |
| `ui/navigation/AppNavHost.kt` | routes `servers`/`server_edit`, error resolution, empty CTA | 3, 6, 7, 8 |
| `ui/components/EmptyStateCta.kt` | NEW — shared empty state | 6 |
| `ui/servers/ServersScreen.kt` | NEW — list + QR + delete confirm | 6 |
| `ui/servers/ServerEditScreen.kt` | NEW — add/edit form | 7 |
| `ui/servers/ServerEditViewModel.kt` | NEW — screen-scoped form state | 7 |
| `ui/settings/SettingsScreen.kt` | gut connect block; summary card, prefs immediate-apply, glass row | 8 |
| `ui/dashboard/DashboardScreen.kt` | edit-mode Cancel reverts | 9 |
| `MainActivity.kt` | remove dead glassmorphism collection | 8 |
| `service/MonitorNotificationService.kt` | hardcoded text → resources | 11 |
| `app/src/main/res/values/strings.xml` + `values-*/strings.xml` | new keys, translation catch-up, `avg` fix | 3–11 |
| `app/src/test/.../SettingsStoreTest.kt` | extend | 1, 4 |
| `app/src/test/.../QrMatcherTest.kt` | NEW | 2 |
| `app/src/test/.../ErrorMapperTest.kt` | NEW | 3 |
| `app/src/test/.../ConnectionPolicyTest.kt` | NEW | 4 |
| `app/src/test/.../BackgroundConnectionHandlerTest.kt` | update | 5 |
| `app/src/test/.../ServerEditViewModelTest.kt` | NEW | 7 |
| `app/src/test/.../LocaleParityTest.kt` | NEW | 10 |

---

### Task 1: SettingsStore per-row API + validation + decode safety

**Files:**
- Modify: `data/SettingsStore.kt`
- Create: `data/ServerValidator.kt`
- Test: `app/src/test/java/com/Obscrum/pchwmonitor/SettingsStoreTest.kt`

**Interfaces:**
- Consumes: `ServerConfig(id, name, ip, port, token, trustedCertHash, useTls, hostname)`, existing `SettingsStoreTest.store(tmpDir)` helper (its `StoreHandle` must also expose the `DataStore` instance for direct-prefs tests).
- Produces (later tasks rely on these exact signatures):
  - `object ServerValidator { fun validate(ip: String, port: Int?, name: String, hostname: String? = null, existingNames: List<String> = emptyList()): List<FieldIssue> }`
  - `enum class ServerField { NAME, IP, PORT }`, `enum class IssueCode { EMPTY_IP, INVALID_PORT, DUPLICATE_NAME }`, `enum class IssueSeverity { ERROR, WARNING }`, `data class FieldIssue(val field: ServerField, val code: IssueCode, val severity: IssueSeverity)`
  - `suspend fun savePreferences(theme: ThemeMode, language: String?, chartWindowSeconds: Int, themePaletteId: String)` — writes ONLY `keyTheme/keyLanguage/keyChartWindow/keyThemePalette`.
  - `suspend fun setBackgroundConnectAll(value: Boolean)` + `AppSettings.backgroundConnectAll: Boolean = false` (key `"background_connect_all"`, stored as `"true"` string, following the existing boolean convention).
  - `suspend fun addServer(server: ServerConfig): Result<Unit>` — blocks only `ERROR` issues (validator called with the current list's names); on success appends, **always sets `keyActiveServerId = server.id`** (not only when size==1), mirrors legacy.
  - `suspend fun updateServerConnection(id: String, name: String, ip: String, port: Int, token: String?, hostname: String?): Result<Unit>` — blocks `ERROR` issues excluding the row's own name from `existingNames`; writes that row only (adds `name` to the copy); sets `useTls = true` iff hostname non-blank, otherwise leaves `useTls` unchanged; mirrors legacy iff `id` is the active server.
  - `removeServer` / `setActiveServerId`: unchanged signatures; both mirror legacy after the active server changes.
  - Private mirror helper (inside `SettingsStore`): `private fun androidx.datastore.preferences.core.MutablePreferences.mirrorLegacy(active: ServerConfig?)` — writes `keyIp/keyPort/keyAuthToken/keyHostname` from `active`; no-op when `active == null`.
  - Corrupt-JSON handling: the `settings` flow and every writer resolve `servers_json` via `runCatching { json.decodeFromString(...) }.getOrElse { emptyList() }` with `json = Json { ignoreUnknownKeys = true }` (single private instance replacing bare `Json`); **the flow performs the legacy migration write only when the key is absent, never on decode failure**.
  - Active-id validation in the flow: `activeId = prefs[keyActiveServerId]?.takeIf { id -> servers.any { it.id == id } } ?: servers.firstOrNull()?.id`.

- [ ] **Step 1: Write the failing tests**

Add to `SettingsStoreTest.kt` (each uses the existing `store(tmpDir)` pattern):

```kotlin
@Test fun savePreferences_leavesConnectionFieldsUntouched()
// seed: addServer(ServerConfig(id="a", name="A", ip="10.0.0.1", port=9000, token="tok"))
// act: store.savePreferences(ThemeMode.DARK, "de", 30, "midnight")
// assert: s.serverIp=="10.0.0.1", s.serverPort==9000, s.authToken=="tok",
//         s.theme==DARK, s.language=="de", s.chartWindowSeconds==30,
//         s.themePaletteId=="midnight", s.servers.single().ip=="10.0.0.1"

@Test fun addServer_alwaysActivates_andMirrorsLegacy()
// addServer(A) → active==A.id, serverIp==A.ip, serverPort==A.port
// addServer(B) → active==B.id (size!=1 case), serverIp==B.ip, serverPort==B.port, token mirrored

@Test fun addServer_rejectsInvalidPort_andEmptyAddress()
// addServer(port=0).isFailure==true; addServer(ip="", hostname=null).isFailure==true;
// addServer(ip="", hostname="t.trycloudflare.com").isSuccess==true  // hostname-only QR
// list unchanged after failures (servers empty)

@Test fun updateServerConnection_writesOnlyTargetRow_andMirrorsWhenActive()
// add A, add B (active B); updateServerConnection(A, name, ip, port, token, null) success
// → servers.first(A).ip updated, servers[1](B) byte-identical fields, active still B,
//   legacy ip still B.ip; setActiveServerId(A) → legacy ip becomes A.ip

@Test fun updateServerConnection_hostnameSetsUseTls_andClearLeavesIt()
// row useTls=false + hostname update("x.trycloudflare.com") → useTls==true
// then hostname update("") → hostname==null, useTls stays true

@Test fun activeServerId_danglingFallsBackToFirst()
// addServer(A); setActiveServerId("ghost"); settings.first().activeServerId == A.id

@Test fun corrupt_serversJson_readPreservesRaw_andReturnsEmpty()
// dataStore.edit { it[stringPreferencesKey("servers_json")] = "{not-json" }
// s = settings.first(); assert s.servers.isEmpty()
// assert dataStore.data.first()[stringPreferencesKey("servers_json")] == "{not-json"

@Test fun removeServer_lastServer_clearsActive()
// add A; removeServer(A); servers empty, activeServerId null, serverIp remains (mirror no-op)

@Test fun backgroundConnectAll_persists()
// setBackgroundConnectAll(true) → settings.first().backgroundConnectAll==true
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `export JAVA_HOME=/home/xeakaes/jdk21 && ./gradlew :app:testDebugUnitTest --tests 'com.Obscrum.pchwmonitor.SettingsStoreTest'`
Expected: FAIL (compile errors — `savePreferences`, `Result` return, `backgroundConnectAll` undefined).

- [ ] **Step 3: Implement `ServerValidator` + `SettingsStore` changes**

Follow the Interfaces block exactly. Rules: `EMPTY_IP` (ERROR) iff `ip.isBlank() && hostname.isNullOrBlank()`; `INVALID_PORT` (ERROR) iff `port == null || port !in 1..65535`; `DUPLICATE_NAME` (WARNING) iff trimmed name equals an existing trimmed name ignoring case. Store blocks only ERROR issues and returns `Result.failure(IllegalArgumentException(issues.toString()))` on violation. All mutation sites that can change the active server or its address call `mirrorLegacy(...)` in the same `dataStore.edit` block.

- [ ] **Step 4: Run tests to verify they pass**

Run: same as Step 2. Expected: PASS (all SettingsStoreTest cases, old + new).

- [ ] **Step 5: Full test suite + commit**

Run: `./gradlew :app:testDebugUnitTest`. Expected: PASS.
```bash
git add app/src/main/java/com/Obscrum/pchwmonitor/data/SettingsStore.kt \
        app/src/main/java/com/Obscrum/pchwmonitor/data/ServerValidator.kt \
        app/src/test/java/com/Obscrum/pchwmonitor/SettingsStoreTest.kt
git commit -m "feat: per-row server store API with validation, legacy mirror, decode safety"
```

---

### Task 2: QR matcher

**Files:**
- Create: `util/QrMatcher.kt`
- Test: `app/src/test/java/com/Obscrum/pchwmonitor/QrMatcherTest.kt`

**Interfaces:**
- Consumes: `QrPayload.Result(ip: String, port: Int, token: String, hostname: String? = null)` (`util/QrPayload.kt`), `ServerConfig`.
- Produces: `sealed interface QrMatch { data class Existing(val serverId: String) : QrMatch; data class New(val config: ServerConfig) : QrMatch }` and `fun matchServer(servers: List<ServerConfig>, payload: QrPayload.Result): QrMatch`.
  - Match order: (1) non-blank `payload.hostname` equals a server's non-blank `hostname` → `Existing`; (2) `payload.hostname` blank AND `payload.ip == server.ip && payload.port == server.port` → `Existing`; else `New`.
  - `New` config: `id = java.util.UUID.randomUUID().toString()`, `name = payload.hostname ?: payload.ip`, `ip = payload.ip`, `port = payload.port`, `token = payload.token`, `hostname = payload.hostname`, `useTls = payload.hostname != null`.
  - First match wins on ties.

- [ ] **Step 1: Write the failing test**

```kotlin
// QrMatcherTest.kt
@Test fun hostnamePayload_matchesByHostname_evenIfIpsDiffer()
// servers=[S(ip="1.2.3.4", hostname="t.trycloudflare.com")],
// payload=Result(ip="", port=8765, token="tok", hostname="t.trycloudflare.com")
// → Existing(S.id)

@Test fun ipPayload_matchesByIpPort_andIgnoresHostnamelessCandidatesWithDifferentAddr()
// servers=[S1(ip="1.2.3.4",port=8765), S2(ip="5.6.7.8",port=8765)]
// payload=Result(ip="5.6.7.8", port=8765, token="t") → Existing(S2.id)

@Test fun noMatch_createsNew_withHostnameImpliesTls()
// servers=[S(ip="1.2.3.4")]; payload=Result(ip="", port=8765, token="t", hostname="new.tunnel.com")
// → New; assert config.hostname=="new.tunnel.com", useTls==true, ip=="", token=="t", name=="new.tunnel.com"

@Test fun ipPayload_neverMatches_hostnameOnlyServer()
// servers=[S(ip="", hostname="old.tunnel.com")]; payload=Result(ip="1.2.3.4", port=8765, token="t")
// → New (hostname of server is non-blank so ip:port rule skips it? NO — rule 2 applies only when
//    payload.hostname blank; server.ip "" != "1.2.3.4" anyway) assert New
```

- [ ] **Step 2: Run test to verify it fails**

Run: `export JAVA_HOME=/home/xeakaes/jdk21 && ./gradlew :app:testDebugUnitTest --tests 'com.Obscrum.pchwmonitor.QrMatcherTest'`
Expected: FAIL — `QrMatcher` unresolved.

- [ ] **Step 3: Implement `matchServer` in `util/QrMatcher.kt`**

Exact signatures above; pure function, no Android imports.

- [ ] **Step 4: Run test to verify it passes**

Run: same as Step 2. Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/Obscrum/pchwmonitor/util/QrMatcher.kt \
        app/src/test/java/com/Obscrum/pchwmonitor/QrMatcherTest.kt
git commit -m "feat: QR payload to server matcher with hostname precedence"
```

---

### Task 3: AppError codes + ErrorMapper + end-to-end rewire

**Files:**
- Create: `data/AppError.kt`, `ui/ErrorMapper.kt`
- Modify: `data/network/WsClient.kt` (where `WsMessage` is defined — grep `sealed` for it), `data/network/StatusParser.kt:24,27`, `data/network/WebSocketClient.kt:102,135,142`, `MonitorController.kt:26-27,51,55,59,72,78`, `MonitorViewModel.kt:83-84`, `ui/navigation/AppNavHost.kt:214`, `app/src/main/res/values/strings.xml`
- Test: NEW `ErrorMapperTest.kt`; UPDATE `StatusParserTest.kt`, `MonitorControllerTest.kt`

**Interfaces:**
- Consumes: existing `WsMessage.ParseFailure(...)` construction sites (5 total: WebSocketClient ×3, StatusParser ×2).
- Produces:
  - `sealed interface AppError` with exactly: `TokenRequired`, `ConnectFailed`, `SocketFailure`, `AddressInvalid`, `PlaintextToken`, `HistoryWriteFailed`, `DataUnavailable` (objects), `UnknownMessageType(val type: String)`, `ServerMessage(val text: String)`.
  - `WsMessage.ParseFailure(val source: String, val reason: String, val error: AppError? = null)` — existing two-arg constructions stay valid; add `error =` at: WS:102→`ConnectFailed`, WS:135→`SocketFailure`, WS:142→`TokenRequired`, SP:24→`UnknownMessageType(type)`, SP:27 leaves `null`.
  - `data class ErrorText(val resId: Int, val args: List<Any> = emptyList>)` + `fun AppError.toErrorText(): ErrorText` (in `ui/ErrorMapper.kt`) + `fun android.content.Context.resolveError(error: AppError): String` = `getString(toErrorText().resId, *toErrorText().args.toTypedArray())`.
  - `MonitorController.lastError` / `MonitorViewModel.lastError`: `StateFlow<AppError?>`. Controller mapping: `:51→HistoryWriteFailed`; `:55→message.status.error?.let { ServerMessage(it) } ?: DataUnavailable`; `:59→message.error ?: ServerMessage(message.reason)`; `:72→AddressInvalid`; `:78→PlaintextToken`.
  - `AppNavHost.kt:214` area: resolve once — `val errorMessage = lastError?.let { context.resolveError(it) }` passed downstream as `String?` (SettingsScreen's param type unchanged).
  - Base strings (English): `error_token_required`, `error_connect_failed`, `error_socket_failure`, `error_address_invalid`, `error_plaintext_token`, `error_history_write_failed`, `error_data_unavailable`, `error_unknown_message_type` (`Unknown message type: %s`), `error_raw` (`%s`).

- [ ] **Step 1: Write the failing test**

```kotlin
// ErrorMapperTest.kt
@Test fun everyAppError_mapsToAResource_withArgsIntact()
// for each of the 7 objects: assert toErrorText().resId != 0 and args.isEmpty()
// UnknownMessageType("x") → args == listOf("x"); resId == R.string.error_unknown_message_type
// ServerMessage("boom") → args == listOf("boom"); resId == R.string.error_raw

@Test fun mappedKeys_existInBaseStrings()   // read res via findResDir(), assert the 9 names present
```
Also update existing assertions: `StatusParserTest` — for unknown-type parse assert `WsMessage.ParseFailure.error == AppError.UnknownMessageType("...")`; `MonitorControllerTest` — wherever `lastError` string was asserted, assert the `AppError` value instead.

- [ ] **Step 2: Run tests to verify they fail**

Run: `export JAVA_HOME=/home/xeakaes/jdk21 && ./gradlew :app:testDebugUnitTest --tests 'com.Obscrum.pchwmonitor.ErrorMapperTest' --tests 'com.Obscrum.pchwmonitor.StatusParserTest'`
Expected: FAIL — `AppError` unresolved / signature mismatches.

- [ ] **Step 3: Implement AppError, ErrorMapper, rewire producers and consumers**

Exact signatures above. Keep `WsMessage`'s other variants untouched. `ErrorMapperTest` uses a `findResDir()` helper (try `src/main/res`, then `app/src/main/res` relative to `user.dir`) — same helper will be reused by Task 10's parity test; put it in the test file for now (Task 10 may extract).

- [ ] **Step 4: Run full test suite**

Run: `./gradlew :app:testDebugUnitTest`. Expected: PASS (including previously-asserting StatusParser/MonitorController tests, now updated).

- [ ] **Step 5: Compile check + commit**

Run: `./gradlew :app:compileDebugKotlin`. Expected: BUILD SUCCESSFUL.
```bash
git add app/src/main/java/com/Obscrum/pchwmonitor/data/AppError.kt \
        app/src/main/java/com/Obscrum/pchwmonitor/ui/ErrorMapper.kt \
        app/src/main/java/com/Obscrum/pchwmonitor/data/network/ \
        app/src/main/java/com/Obscrum/pchwmonitor/MonitorController.kt \
        app/src/main/java/com/Obscrum/pchwmonitor/MonitorViewModel.kt \
        app/src/main/java/com/Obscrum/pchwmonitor/ui/navigation/AppNavHost.kt \
        app/src/main/res/values/strings.xml \
        app/src/test/java/com/Obscrum/pchwmonitor/
git commit -m "feat: structured AppError codes with resource-backed error mapping"
```

---

### Task 4: ConnectionPolicy (pure) + backgroundConnectAll store field

**Files:**
- Create: `data/ConnectionPolicy.kt`
- Modify: `data/SettingsStore.kt` (add `backgroundConnectAll` per Task 1 Interfaces — if Task 1 already added it, this task only adds the policy tests' store usage)
- Test: NEW `ConnectionPolicyTest.kt`; extend `SettingsStoreTest.kt` if the field landed in Task 1

**Interfaces:**
- Consumes: `AppSettings`, `ServerConfig`, Task 1 store field.
- Produces (Task 5 applies these):
  - `data class ConnectionKey(val servers: List<ServerConfig>, val activeServerId: String?, val backgroundConnectAll: Boolean)` + `fun AppSettings.toConnectionKey(): ConnectionKey`
  - `data class ConnectionPlan(val connect: Map<String, ServerConfig>, val disconnect: Set<String>, val dispose: Set<String>)`
  - `fun planConnections(servers: List<ServerConfig>, activeId: String?, backgroundAll: Boolean, current: Map<String, ServerConfig>): ConnectionPlan`
    - desired ids = if `backgroundAll` then all `servers` ids else `activeId?.let { setOf(it) }` ?: `emptySet()`
    - `dispose` = `current.keys - servers.ids`
    - connection-relevant comparison: `ServerConfig` fields `ip, port, token, hostname, useTls, trustedCertHash` (ignore `id`, `name`) — implement as `private fun ServerConfig.connSignature(): List<Any?>`
    - `disconnect` = (`current.keys - desired`) ∪ {id ∈ current ∩ desired where signature differs}
    - `connect` = {id ∈ desired : id ∉ current ∨ signature differs} mapped to the desired `ServerConfig`
  - `fun planOnStop(current: Map<String, ServerConfig>, backgroundAll: Boolean): ConnectionPlan` — if `backgroundAll` → empty plan (keep all); else `disconnect = current.keys` (empty connect/dispose).

- [ ] **Step 1: Write the failing tests**

```kotlin
// ConnectionPolicyTest.kt  (helper: cfg(id, ip="1.2.3.4", ...) with connSignature fields varying)
@Test fun plan_activeOnly_connectsActive_disconnectsRest()
// current={a,b}, servers=[a,b,c], activeId=c, backgroundAll=false → desired={c}
// → connect=={c}, disconnect=={a,b}, dispose==∅

@Test fun plan_desiredAlreadyConnected_isKept()
// current={a,b,c all same-sig}, servers=[a,b,c], activeId=c → connect==∅, disconnect=={a,b}

@Test fun plan_backgroundAll_connectsAllMissing()
// current={}, servers=[a,b], activeId=a, backgroundAll=true → connect.keys=={a,b}, disconnect=∅

@Test fun plan_nullActive_connectsNothing_disconnectsAll()
// current={a}, servers=[a], activeId=null → disconnect=={a}, connect empty

@Test fun plan_deletedServer_isDisposed()
// current={a,b}, servers=[b] → dispose=={a}; a also in disconnect? — dispose only (pin: dispose excludes from disconnect)

@Test fun plan_paramChange_reconnectsOnlyChanged()
// current={a(sigX),b}, servers=[a(sigY),b], activeId=a, bg=false → desired={a}:
//   a sig differs → disconnect=={a}+connect=={a}; b not desired → disconnect=={b}
// then bg=true with current={a,b}: desired={a,b}, sigs match → empty plan

@Test fun plan_nameChange_doesNotReconnect()
// same as above but only name differs → connect ∅, disconnect ∅ (bg=true, both desired)

@Test fun plan_isIdempotent_overRepeatedActiveChange()
// p1=plan(servers, activeId=c, current={a}); p2=plan(servers, activeId=c, current=apply(p1)→{c})
// assert p2.connect.isEmpty() && p2.disconnect.isEmpty()  // converged

@Test fun connectionKey_ignoresAllPreferenceFields()
// s2 = s1.copy(theme=..., language=..., chartWindowSeconds=..., themePaletteId=...,
//              customBackgroundEnabled=..., glassmorphismEnabled=..., dashboardLayout=...,
//              customBackgroundUri=...)
// assert s1.toConnectionKey() == s2.toConnectionKey()
// copy(backgroundConnectAll = !s1.backgroundConnectAll) → NOT equal; copy(activeServerId="x") → NOT equal

@Test fun planOnStop_offDisconnectsAll_onKeepsAll()
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `export JAVA_HOME=/home/xeakaes/jdk21 && ./gradlew :app:testDebugUnitTest --tests 'com.Obscrum.pchwmonitor.ConnectionPolicyTest'`
Expected: FAIL — unresolved `ConnectionPolicy`.

- [ ] **Step 3: Implement `data/ConnectionPolicy.kt`**

Exact signatures in Interfaces. Pure Kotlin, no Android/coroutine imports. (If Task 1 didn't already add `backgroundConnectAll` to the store, add it now per Task 1's Interfaces.)

- [ ] **Step 4: Run tests to verify they pass**

Run: same as Step 2. Expected: PASS.

- [ ] **Step 5: Full suite + commit**

Run: `./gradlew :app:testDebugUnitTest`. Expected: PASS.
```bash
git add app/src/main/java/com/Obscrum/pchwmonitor/data/ConnectionPolicy.kt \
        app/src/test/java/com/Obscrum/pchwmonitor/ConnectionPolicyTest.kt
git commit -m "feat: pure connection policy with signature-aware reconnect planning"
```

---

### Task 5: MonitorViewModel policy application + lifecycle handler

**Files:**
- Modify: `MonitorViewModel.kt`, `BackgroundConnectionHandler.kt`, `MonitorController.kt` (add `dispose()`), `DashboardScreen.kt` only if state-reset requires it
- Test: UPDATE `BackgroundConnectionHandlerTest.kt`, `MonitorControllerTest.kt`

**Interfaces:**
- Consumes: Tasks 1, 3, 4 signatures.
- Produces:
  - `MonitorController`: `fun dispose()` — cancels the job started in `start()` (store it in `internal var startJob: Job?`) and calls `disconnect()`.
  - `MonitorViewModel`: `private val connectedConfigs = mutableMapOf<String, ServerConfig>()` (authoritative record of what was last connected); `private fun applyPlan(plan: ConnectionPlan)` — order: dispose → disconnect → connect (calling `ensureController(id)` = create `WebSocketClient(StatusParser, cfg.trustedCertHash)` + `HistoryRepository(HistoryDb.get(getApplication()).historyDao())` + `MonitorController(client, hist, viewModelScope, pcId=id)` + `ctrl.start()` if absent); after every key-driven plan: repair `_activeServerId` (invalid/missing → `key.activeServerId ?: key.servers.firstOrNull()?.id`; if resolved value is `null` **also clear `_activeStatus`, `_activeConnection`, `_lastError`**), update `MonitorNotificationService.updateServerName`, and `collectFromActiveController()`.
  - Collection: replace the current `syncControllers(settings)` trigger with `settings.map { it.toConnectionKey() }.distinctUntilChanged().collect { key -> applyPlan(planConnections(key.servers, key.activeServerId, key.backgroundConnectAll, connectedConfigs)) + repairActive(key) ... }` launched in `viewModelScope` (initial emission runs at startup).
  - `removeServer(id)`: dispose controller + drop from `connectedConfigs` immediately, then `settingsStore.removeServer(id)` (flow handles the rest).
  - `trustCert(certHash)`: dispose old controller before replacing; after `ctrl.start()` + `connect(...)` call `collectFromActiveController()` (fixes missing re-subscribe) — collect clears stale state first because the new controller's StateFlows start fresh.
  - `collectFromActiveController()`: in the `activeId == null` branch set `_activeStatus.value = null; _activeConnection.value = null; _lastError.value = null` before returning.
  - `BackgroundConnectionHandler(private val onForeground: () -> Unit, private val onBackground: () -> Unit)` — `ON_START→onForeground()`, `ON_STOP→onBackground()`, else nothing. VM constructs it with `onForeground = { applyPlan(planConnections(settings.value.servers, _activeServerId.value, settings.value.backgroundConnectAll, connectedConfigs)) }`, `onBackground = { applyPlan(planOnStop(connectedConfigs, settings.value.backgroundConnectAll)) }`.
  - Removed: `syncControllers(AppSettings)` old body (its responsibilities move into `applyPlan` + repair), direct `ctrl.connect(...)` calls outside `applyPlan`.

- [ ] **Step 1: Update the failing tests**

`BackgroundConnectionHandlerTest.kt` — rewrite for the new ctor: `ON_START` → `onForeground` invoked once, `ON_STOP` → `onBackground` invoked once, `ON_RESUME`/`ON_PAUSE` → neither.
`MonitorControllerTest.kt` — add: `dispose_cancelsStartJob_andDisconnects`: start with fake client, `ctrl.dispose()`, assert `ctrl.connection.value == ConnectionState.DISCONNECTED && ctrl.startJob?.isActive == false`.

- [ ] **Step 2: Run tests to verify they fail**

Run: `export JAVA_HOME=/home/xeakaes/jdk21 && ./gradlew :app:testDebugUnitTest --tests 'com.Obscrum.pchwmonitor.BackgroundConnectionHandlerTest' --tests 'com.Obscrum.pchwmonitor.MonitorControllerTest'`
Expected: FAIL (ctor mismatch, `startJob`/`dispose` unresolved).

- [ ] **Step 3: Implement**

`MonitorController.dispose()`, handler rewrite, VM policy application per Interfaces. Keep `setActiveServer`'s optimistic behavior (write `_activeServerId`, launch store write, recollect) — the key-driven pass is idempotent afterwards (Task 4 test 9 proves convergence).

- [ ] **Step 4: Run full test suite + compile**

Run: `./gradlew :app:testDebugUnitTest` then `./gradlew :app:compileDebugKotlin`. Expected: PASS/SUCCESS.

- [ ] **Step 5: Commit.** Manual checklist M7/M8 deferred to Tasks 6+ (UI not built yet); M4's state reset verified here by code review of the null-branch in `applyPlan`.

```bash
git add app/src/main/java/com/Obscrum/pchwmonitor/ \
        app/src/test/java/com/Obscrum/pchwmonitor/
git commit -m "feat: policy-driven connection sync with proper dispose and state reset"
```

---

### Task 6: Nav routes + ServersScreen + dashboard empty CTA

**Files:**
- Create: `ui/components/EmptyStateCta.kt`, `ui/servers/ServersScreen.kt`
- Modify: `ui/navigation/AppNavHost.kt` (routes + dashboard gate), `MonitorViewModel.kt` (add `connectViaQr`), `app/src/main/res/values/strings.xml`
- No new unit tests (UI task) — verification is compile + existing suite + checklist M1/M2/M4 UI half

**Interfaces:**
- Consumes: `matchServer` (Task 2), `MonitorViewModel.setActiveServer/addServer/removeServer`, `ConnectionBar` (`ui/components/ConnectionBar.kt`), `QrPayload.parse`, `ScanContract` pattern copied from `SettingsScreen.kt:162-173`.
- Produces:
  - `@Composable fun EmptyStateCta(title: String, description: String, actionLabel: String, onAction: () -> Unit)`
  - `@Composable fun ServersScreen(servers: List<ServerConfig>, activeServerId: String?, connectionState: ConnectionState?, onBack: () -> Unit, onSelect: (String) -> Unit, onEdit: (String) -> Unit, onDelete: (String) -> Unit, onQrPayload: (QrPayload.Result) -> Unit, onAddManual: () -> Unit)` — primary full-width QR button (launches `ScanContract`, parses, forwards), server rows (name, `ip:port` or hostname, active marker, edit icon, delete icon → confirmation `AlertDialog` with the server name as `%1$s`), back top-bar, `EmptyStateCta` when list empty, `ConnectionBar` pill for the active connection state at top.
  - Nav: routes `"servers"` and `"server_edit?serverId={serverId}"` (`navArgument("serverId") { type = NavType.StringType; nullable = true; defaultValue = null }`). Dashboard route: `if (servers.isEmpty()) EmptyStateCta(... onAction = { navigate("servers") }) else` existing content (keep `ScrollableTabRow` etc. inside the else).
   - `MonitorViewModel.connectViaQr(payload: QrPayload.Result)`: `when (val match = matchServer(settings.value.servers, payload)) { is Existing -> setActiveServer(match.serverId); is New -> addServer(config) }` (store's `addServer` activates + mirrors; policy connects).
  - New base strings (English): `servers_title`, `servers_qr_action`, `servers_add_manual`, `servers_delete_confirm_title`, `servers_delete_confirm_message` (`Delete "%1$s"? This cannot be undone.`), `servers_empty_title`, `servers_empty_desc`, `servers_empty_action`, `dashboard_empty_title`, `dashboard_empty_desc`, `dashboard_empty_action`, `back`, `edit`, plus reuse existing `saved_servers/select/delete_label/add_new_server`.

- [ ] **Step 1: Implement the components and wiring**

Follow Interfaces exactly. `onSelect` → `viewModel.setActiveServer(id)` then `popBackStack()` to dashboard? — pin: stay on the list (user sees the marker move); navigating back is the user's action. `onEdit` → `navigate("server_edit?serverId=$id")`; `onAddManual` → `navigate("server_edit")`; `onDelete` → confirm dialog → `viewModel.removeServer(id)`; `onQrPayload` → `viewModel.connectViaQr(payload)` (stay on screen; toast-free — active marker + ConnectionBar show the result).

- [ ] **Step 2: Compile + existing tests**

Run: `export JAVA_HOME=/home/xeakaes/jdk21 && ./gradlew :app:compileDebugKotlin && ./gradlew :app:testDebugUnitTest`. Expected: SUCCESS/PASS.

- [ ] **Step 3: No-stray-literals check**

Run: `grep -rn 'Text("' app/src/main/java/com/Obscrum/pchwmonitor/ui/servers/`
Expected: only dynamic (`"$name..."`) or allowed placeholder literals; every other user-visible text is `stringResource(...)`. If literals remain, resource them before committing.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/Obscrum/pchwmonitor/ui/ \
        app/src/main/java/com/Obscrum/pchwmonitor/ui/navigation/AppNavHost.kt \
        app/src/main/java/com/Obscrum/pchwmonitor/MonitorViewModel.kt \
        app/src/main/res/values/strings.xml
git commit -m "feat: servers list screen with QR connect, delete confirm, empty CTA"
```

---

### Task 7: ServerEditScreen + ServerEditViewModel

**Files:**
- Create: `ui/servers/ServerEditScreen.kt`, `ui/servers/ServerEditViewModel.kt`
- Modify: `ui/navigation/AppNavHost.kt` (route body + discovery callbacks), `MonitorViewModel.kt` (expose `val settingsStore: SettingsStore`), `app/src/main/res/values/strings.xml`
- Test: NEW `ServerEditViewModelTest.kt`

**Interfaces:**
- Consumes: Task 1 store API, `ServerValidator`, `ScanContract`/`QrPayload` pattern, existing discovery callbacks currently passed to `SettingsScreen` (`onDiscover`, scan state/results — reuse the same ViewModel sources, temporarily wired to both screens until Task 8).
- Produces:
  - `class ServerEditViewModel(private val store: SettingsStore, private val serverId: String?) : ViewModel()` with `MutableStateFlow`s `name/ip/port/token/hostname` (all `String`, `port` as text), `val issues: StateFlow<List<FieldIssue>>`, `val isNew: Boolean = serverId == null`; `init` prefills **exactly once** from the first `store.settings` emission (existing row's fields, or `port="8765"` + blanks for new — after prefill, form state is authoritative and later flow emissions never clobber it); `fun save(): Boolean` — runs `ServerValidator.validate(ip, port.toIntOrNull(), name, hostname, existingNames = servers excluding self)`, sets `issues`, returns false on any ERROR, otherwise `store.addServer(...)` (new: `UUID` id, name defaulted by the caller — see below) or `store.updateServerConnection(serverId, name, ip, port, token.ifBlank{null}, hostname.ifBlank{null})`, returns true; companion `fun factory(store: SettingsStore, serverId: String?): ViewModelProvider.Factory`.
  - `@Composable fun ServerEditScreen(...)` fields: server name, IP, port (digits), token, Cloudflare tunnel URL (`stringResource(R.string.tunnel_url_label)`, placeholder `my-tunnel.trycloudflare.com` allowed literal), primary Save button, inline red `FieldIssue` messages under fields (ERROR blocks, WARNING shown), QR fill button (fills FORM only — no auto-save), discovery section (same behavior as current settings scan UI), back bar with title "Add server"/"Edit server".
  - AppNavHost: route body builds `ServerEditViewModel.factory(viewModel.settingsStore, serverId)`; on `save()==true → popBackStack()`; passes existing discovery callbacks.
  - Blank-name default: **resolved at the call site** — `viewModel` receives `name.ifBlank { stringResource(R.string.default_server_name) }` computed in the composable before `save()`.
  - New base strings: `server_edit_title_new`, `server_edit_title_edit`, `tunnel_url_label`, `err_empty_ip`, `err_invalid_port`, `err_duplicate_name`, `default_server_name` (`PC`), `save`, plus reuse `server_name/ip_address/port/token_optional/add/cancel`.

- [ ] **Step 1: Write the failing test**

```kotlin
// ServerEditViewModelTest.kt  (real SettingsStore on tmp dir, same helper as SettingsStoreTest)
@Test fun editMode_prefillsFromStore_andSaveUpdatesThatRowOnly()
// store: add A(1.1.1.1), add B(2.2.2.2)  → vm = ServerEditViewModel(store, serverId=A.id)
// vm fields: ip=="1.1.1.1"; change ip to "3.3.3.3"; save()==true
// → store: A.ip=="3.3.3.3", B.ip unchanged=="2.2.2.2"

@Test fun save_blockingIssue_returnsFalse_andWritesNothing()
// vm(isNew): ip="", port="abc" → save()==false; issues contains EMPTY_IP + INVALID_PORT; store.servers empty

@Test fun newMode_prefillsPort8765_andSavesWithGeneratedId()
// vm(isNew): port=="8765"; fill name/ip; save()==true → store has 1 server, id not blank, port==8765

@Test fun duplicateName_isWarning_only()
// store has "PC"; vm(isNew) name="pc", valid ip → save()==true; issues (before save or after) contains DUPLICATE_NAME WARNING — pin: save succeeds
```

- [ ] **Step 2: Run test to verify it fails**

Run: `export JAVA_HOME=/home/xeakaes/jdk21 && ./gradlew :app:testDebugUnitTest --tests 'com.Obscrum.pchwmonitor.ServerEditViewModelTest'`
Expected: FAIL — class unresolved.

- [ ] **Step 3: Implement VM + screen + nav wiring**

Per Interfaces. Prefill-once via a `private var prefilled = false` guard inside the first emission collection.

- [ ] **Step 4: Run tests + compile + stray-literal check**

Run: `./gradlew :app:testDebugUnitTest && ./gradlew :app:compileDebugKotlin`
Expected: PASS/SUCCESS. Then `grep -rn 'Text("' app/src/main/java/com/Obscrum/pchwmonitor/ui/servers/ServerEditScreen.kt` — only the allowed placeholder literal remains.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/Obscrum/pchwmonitor/ui/servers/ \
        app/src/main/java/com/Obscrum/pchwmonitor/ui/navigation/AppNavHost.kt \
        app/src/main/java/com/Obscrum/pchwmonitor/MonitorViewModel.kt \
        app/src/main/res/values/strings.xml \
        app/src/test/java/com/Obscrum/pchwmonitor/ServerEditViewModelTest.kt
git commit -m "feat: server add/edit screen with validation and rotation-safe form state"
```

---

### Task 8: SettingsScreen rework + glassmorphism unification

**Files:**
- Modify: `ui/settings/SettingsScreen.kt` (gut connect block), `ui/navigation/AppNavHost.kt` (rewire params), `MonitorViewModel.kt` (glassmorphism derivation, `savePreferences`, remove `saveSettings`), `data/SettingsStore.kt` (delete `saveConnectionAndPreferences`), `MainActivity.kt:24`, `app/src/main/res/values/strings.xml`
- No new unit tests — verification: full suite (store test must be adjusted if it referenced removed APIs) + compile + checklist M6

**Interfaces:**
- Consumes: Tasks 1, 4, 6 signatures; `ConnectionBar`.
- Produces:
  - `SettingsScreen` params AFTER (remove: old connect fields/labels/method-picker/QR/`onServerSelected`/`labelServer`/`onSave`-with-connection semantics; add): `servers: List<ServerConfig>`, `activeServerId: String?`, `onManageServers: () -> Unit`, `backgroundConnectAll: Boolean`, `onBackgroundConnectAllToggle: (Boolean) -> Unit`, `glassmorphism: Boolean` (from `viewModel.glassmorphism`), keep theme/palette/chart/language/background params + `onGlassmorphismToggle`. Structure top→bottom: connection summary card (active server name + `ConnectionBar` + "Manage" button → `onManageServers`), `background_connect_all` switch row, theme/palette/chart/language (each applying IMMEDIATELY via its existing setter — no Kaydet button), custom-background section with the **glassmorphism switch as its own always-visible row** (outside the `customBackgroundEnabled` guard), Patreon. Delete: the entire connect form, `connectNow`, the second `Kaydet` + duplicate "Kaydedildi" block.
  - `MonitorViewModel.glassmorphism: StateFlow<Boolean>` = `settingsStore.settings.map { it.glassmorphismEnabled }.stateIn(viewModelScope, SharingStarted.Eagerly, false)` — DELETE `private _glassmorphism`, the init write, and the `_glassmorphism.value = enabled` line inside `setCustomBackgroundEnabled` (decoupling); `setGlassmorphismEnabled(v: Boolean)` now only launches `settingsStore.setGlassmorphismEnabled(v)` (persist; UI follows the flow).
   - `MonitorViewModel.savePreferences(theme, language, chartWindowSeconds, themePaletteId)` replacing `saveSettings(...)`; store: delete `saveConnectionAndPreferences`, `setServers`, and `updateServerHostname` (all three dead after the rework — C7 audit).
  - `AppNavHost` settings route: pass `glassmorphism` from `viewModel.glassmorphism.collectAsState()` (replaces `settings.glassmorphismEnabled` for the switch) — dashboard already collects the same flow, so both sides read one source.
  - `MainActivity.kt`: delete the unused `glassmorphism` collection line.
  - New base strings: `connection_card_title`, `manage_servers`, `background_connect_all_label`, `background_connect_all_desc` (glassmorphism labels already exist: `custom_background_blur*`).

- [ ] **Step 1: Implement the rework**

Per Interfaces. Preference controls call their existing store setters through ViewModel wrappers (grep VM for `setTheme|setLanguage|setChartWindowSeconds|setThemePalette` — add wrappers if absent). Local `remember` copies of theme/palette/chart/language replaced by collecting the corresponding `settings.*` flows.

- [ ] **Step 2: Remove dead APIs + compile**

Run: `export JAVA_HOME=/home/xeakaes/jdk21 && ./gradlew :app:compileDebugKotlin`
Expected: SUCCESS (compiler enforces no remaining callers of `saveConnectionAndPreferences`/`saveSettings`/`setServers`/`updateServerHostname`/`onServerSelected`). If the compiler reports callers, fix them in the same commit — do not keep dead APIs.

- [ ] **Step 3: Full test suite**

Run: `./gradlew :app:testDebugUnitTest`. Expected: PASS (update any test referencing removed APIs).

- [ ] **Step 4: Stray-literal check + commit**

Run: `grep -rn 'Text("' app/src/main/java/com/Obscrum/pchwmonitor/ui/settings/SettingsScreen.kt` — only allowed literals (`my-tunnel.trycloudflare.com` if still present, dynamic strings) remain.
```bash
git add app/src/main/java/com/Obscrum/pchwmonitor/ \
        app/src/main/res/values/strings.xml \
        app/src/test/java/com/Obscrum/pchwmonitor/
git commit -m "feat: simplified settings with connection card, immediate prefs, persistent glassmorphism"
```

---

### Task 9: Dashboard edit-mode Cancel reverts

**Files:**
- Modify: `ui/dashboard/DashboardScreen.kt` (~lines 181-183 where Cancel/Done share `onEditModeChange(false)`)
- No new unit test (Compose state only) — verification: compile + checklist M10

**Interfaces:**
- Consumes: existing edit-mode entry point and the layout-change callback already used while dragging (grep `onEditModeChange` and the layout mutation callback in the file).
- Produces: entering edit mode snapshots the current layout (`remember { mutableStateOf<DashboardLayout?>(null) }` set on edit start); **Cancel** = restore snapshot through the existing layout-change callback + `onEditModeChange(false)`; **Done** = `onEditModeChange(false)` only.

- [ ] **Step 1: Implement snapshot/revert**
- [ ] **Step 2: Compile + suite**

Run: `export JAVA_HOME=/home/xeakaes/jdk21 && ./gradlew :app:compileDebugKotlin && ./gradlew :app:testDebugUnitTest`. Expected: SUCCESS/PASS.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/Obscrum/pchwmonitor/ui/dashboard/DashboardScreen.kt
git commit -m "fix: dashboard edit-mode Cancel reverts layout changes"
```

---

### Task 10: Localization catch-up + LocaleParityTest

**Files:**
- Modify: `app/src/main/res/values/strings.xml` (fix `summary_format` `ort.`→`avg`, `formatted="false"` on `fps_1pct_low`), ALL 13 `values-*/strings.xml`
- Create: `app/src/test/java/com/Obscrum/pchwmonitor/LocaleParityTest.kt`
- No Kotlin source changes except if the stray-literal audit (Step 4) finds leftovers already handled by earlier tasks

**Interfaces:**
- Consumes: all base keys added by Tasks 3–9; `findResDir()` pattern from Task 3's test.
- Produces: parity gate used by every later commit.

- [ ] **Step 1: Write the failing parity test**

```kotlin
// LocaleParityTest.kt
@Test fun everyLocale_hasExactlyTheBaseKeys()
// findResDir(); parse name="..." attrs from values/strings.xml into baseKeys
// for each sibling dir values-XX: assert localeKeys == baseKeys (report missing+extra)

@Test fun everyLocale_hasMatchingFormatSpecifiers()
// for each key: extract specifiers with Regex("%(\\d+\\$)?[-#+,0]*(\\d+)?(\\.\\d+)?[a-zA-Z%]") after
// removing "%%"; assert the sorted multiset equals base's (skip keys with no specifiers)
```

- [ ] **Step 2: Run to verify it fails (documents today's gaps)**

Run: `export JAVA_HOME=/home/xeakaes/jdk21 && ./gradlew :app:testDebugUnitTest --tests 'com.Obscrum.pchwmonitor.LocaleParityTest'`
Expected: FAIL listing missing keys per locale.

- [ ] **Step 3: Fix base + translate everything**

1. Base: `summary_format` → `min %1$.0f / avg %2$.0f / max %3$.0f`; add `formatted="false"` to `fps_1pct_low`.
2. Diff key sets (`grep -o 'name="[^"]*"' res/values*/strings.xml | sort | uniq -c` style) and translate **every key missing a localized value** into all 13 languages — this includes the ~25 pre-existing gaps (`custom_background*` ×7, `cert_trust_*` ×5, `saved_servers/select/delete_label/add_new_server/server_name/ip_address/token_optional`, `cancel/add`, zh extras, `gpu_hotspot`(de), `palette_ocean`(pl), `fps_1pct_low`(ja,tr)) **and every new key from Tasks 3–9** (`error_*`, `servers_*`, `dashboard_empty_*`, `server_edit_*`, `tunnel_url_label`, `err_*`, `connection_card_title`, `manage_servers`, `background_connect_all_*`, `default_server_name`, `back/edit/save`, etc.).
3. Languages: de, es, fr, it, ja, nl, pl, pt, pt-BR, ru, tr, zh, zh-TW — endonyms/units/CPU/GPU/FPS/brand names stay as-is.

- [ ] **Step 4: Run parity test to verify it passes**

Run: same as Step 2. Expected: PASS.

- [ ] **Step 5: Full suite + commit**

Run: `./gradlew :app:testDebugUnitTest`. Expected: PASS (all 14 files parse, format specs match — zero crash risk).
```bash
git add app/src/main/res/
git commit -m "feat: complete 14-locale translation coverage with CI parity gate"
```

---

### Task 11: Kotlin hardcodes → resources (notification, defaults)

**Files:**
- Modify: `service/MonitorNotificationService.kt` (lines ~146-195), `data/network/DiscoveryService.kt:81` (blank default instead of `"Unknown"`), `data/SettingsStore.kt:58` (migration name `""` instead of `"My PC"`), display sites for blank server names (grep `\.name` in `ui/` — servers rows, dashboard tabs, connection card, notification title), `res/values/strings.xml` + 13 locales
- Test: extend `LocaleParityTest` implicitly (new keys must pass); no service unit test (no Robolectric — format correctness verified by parity + checklist M8)

**Interfaces:**
- Consumes: Task 10 parity gate.
- Produces: notification keys (base English; all ×13) — collapsed lines: `notif_cpu_usage` (`CPU: %1$.0f%%`), `notif_cpu_temp` (`CPU: %1$.0f°C`), `notif_gpu_temp`, `notif_gpu_hotspot`, `notif_ram_usage`, `notif_fps` (`FPS: %1$.0f`); expanded: `notif_section_cpu/gpu/ram/fps` (`── CPU ──` style), `notif_label_usage` (`Kullanım: %1$.0f%%` — translated per locale), `notif_label_temp`, `notif_label_freq`, `notif_label_power`, `notif_label_hotspot`, `notif_label_used` (`Kullanılan: %1$.1f GB`), `notif_line_fps`, `notif_line_low1` (`1%% Low: %1$.0f`). Compose lines as `"  " + getString(R.string.notif_label_usage, v)` — indentation stays in code, `%%` for literal percent. Also `unknown_device` (`Unknown`), `default_server_name` display fallbacks wired wherever a blank name renders (Task 7 created the key; Task 11 makes blank names possible + consumed).

- [ ] **Step 1: Replace notification literals**

Extract both builders' strings to `getString(R.string...)` per the key list; keep `notification_waiting_data` fallback as-is. Ensure every format key's arg count matches its specifiers (parity test enforces cross-locale, this step enforces base).

- [ ] **Step 2: Replace remaining hardcodes**

`DiscoveryService` `optString("name", "")`; migration default `name = ""`; UI display sites: `server.name.ifBlank { stringResource(R.string.default_server_name) }` and discovery results `name.ifBlank { stringResource(R.string.unknown_device) }`.

- [ ] **Step 3: Translate new keys ×13 + run gates**

Run: `export JAVA_HOME=/home/xeakaes/jdk21 && ./gradlew :app:testDebugUnitTest`
Expected: PASS — `LocaleParityTest` proves all new keys exist in all 14 files with matching specifiers.

- [ ] **Step 4: Compile + commit**

Run: `./gradlew :app:compileDebugKotlin`. Expected: SUCCESS.
```bash
git add app/src/main/java/com/Obscrum/pchwmonitor/ app/src/main/res/
git commit -m "fix: localize notification text and user-visible defaults"
```

---

## Manual Verification Checklist (run before declaring A complete; device or emulator)

- **M1 (QR new):** servers screen → QR of an unregistered PC → row appears as active and connects (check server console).
- **M2 (QR existing):** scan the same PC again → no duplicate row, it becomes/stays active.
- **M3 (row isolation):** edit server B's IP to a wrong value → reopen edit → only B changed; A's stored address untouched (regression of old C1).
- **M4 (delete reset):** delete every server → dashboard shows empty CTA, no green "Bağlı", no crash (Review Focus #5).
- **M5 (rotation):** type a half-filled form on server_edit, rotate → input intact.
- **M6 (glass persistence):** toggle glassmorphism → kill app → reopen → still on; dashboard cards match the switch (Review Focus #4 visual half).
- **M7 (background policy):** OFF + server console shows only the active PC's socket; switch tab → old drops, new joins; toggle ON → all PCs stay connected (Review Focus #3).
- **M8 (prefs vs sockets):** change theme/palette/language → server console shows no reconnect (Review Focus #4); notification text appears in the device language.
- **M9 (cert trust):** trigger self-signed TLS trust dialog → accept → data flows without restarting the app (S3 regression).
- **M10 (edit cancel):** dashboard edit mode → move a card → Cancel → layout restored; Done → layout kept.
