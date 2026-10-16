# AndroidAPS Client-Control & Master-Authored Two-Step Execution

> Consolidated reference for the signed NS `settings` round-trip channel and the role-transparent **prepare → commit** execution path that lets a paired **client** (an `AAPSCLIENT` phone with no pump) or a **watch** trigger therapy that the **master** (the `full` phone owning the pump) authors, confirms, and executes. Describes the current shipped, device-verified end state. Audience: developers extending the system.

---

## 1. The core idea

The **master is the single source of truth for both the confirmation *and* the execution.** A client or watch never computes a dose, never builds a confirmation string, and never delivers anything. It relays an *intent*; the master:

1. computes/caps the action against **its own** live state (profile, COB, IOB, temp-target, pump),
2. authors the exact `ConfirmationLine` list (role + color-coded text) the user will see,
3. parks the result by an id, and
4. delivers it once — exactly once — when the client confirms.

Two structural consequences:

- **One confirmation surface everywhere.** The phone dialog, every paired client, and every watch render the *master's* `ConfirmationLine` list verbatim. There is no client-built confirmation to drift out of sync.
- **One role-transparent code path.** The same executor call runs on a master (locally) and on a client (over the signed round-trip). The role fork lives in exactly one place — `RoleBranch` — so handlers, dialogs, and the wear adapter are role-agnostic.

The channel splits cleanly into two paradigms:

| Paradigm | Direction | Carries | Mechanism |
|---|---|---|---|
| **Command** | client → master (only mutation channel) | *intent* (bolus, scene, pref edit, …) | signed `ClientControlMessage` envelope + two-step ACK |
| **State mirroring** | master → all clients | authoritative config + active scene | `RunningConfiguration` cold/hot `settings` docs |

A client never pushes state directly. It sends a command; the master processes it, applies it locally, and re-publishes the result as state.

---

## 2. Transport — the signed NS `settings` round-trip

Everything travels as documents in the Nightscout APIv3 `settings` collection, keyed by deterministic identifiers. No secrets ever transit NS — only HMAC signatures.

### 2.1 Pairing & PIN exchange (QR-based)

1. Master generates a `clientId` (UUID) + a **32-byte** secret, wraps the secret in a user PIN, and publishes a `PairingPayload` QR (`masterInstallId`, `clientId`, `secretHex`, `expiresAt`) under `aaps_clientcontrol_offer_<…>`.
2. Client scans, the user confirms, and `ClientPairingRepository.pair()` stores the secret wrapped via `SecureEncrypt` (AES/GCM, AndroidKeyStore-backed).
3. Client sends **`Hello`** (`counter = 1`) under `aaps_clientcontrol_hello_<…>`, promoting the master's entry **Pending → Active**.
4. **Revocation:** swipe-to-delete on the master removes the entry; later envelopes from that `clientId` get an "unknown clientId" log and the doc is deleted.
5. **Orphan detection:** the master publishes an `authorizedClients.clientIds` roster (Active entries only) in the cold config doc; a client checks itself in, guarded by a race window (`docSrvModified ≥ pairedAt + 60s`, so a doc in flight from before `Hello` isn't mistaken for an orphan signal).

### 2.2 Signed envelope, counter, validity

The wire doc carries a `SignedEnvelope` + the NSv3 required fields (`date = 946684800001` constant, `utcOffset`, `app = "AAPS"`, `schemaVersion = 1`).

**Canonical signature input** (HMAC-SHA256, hex-encoded, UTF-8 — `SignedEnvelope.kt`):

```
$clientId|$counter|$timestamp|$validUntil|$wantsAck|$type|$payload
```

`payload` is the already-serialized JSON of the `ClientControlMessage` (including its `@SerialName` discriminator), so the signature protects the exact bytes that travelled. Verification uses constant-time comparison (`MessageDigest.isEqual`).

Independent gates on the master (`ClientControlReceiver`), in order:

| Gate | Rule | On fail |
|---|---|---|
| **Signature** | `verifyEnvelope` against the stored secret | drop (forged `Ok` impossible) |
| **Counter** | `envelope.counter > counterReceived` (**strictly** greater) | reject as replay (doc left for diagnostics) |
| **Skew** | `\|timestamp − now\| ≤ ±5 min` (`MAX_TIMESTAMP_SKEW_MS`) | reject |
| **Validity** | `now ≤ validUntil` | **consume the counter** + write terminal `Done/Expired` ack |

> **The counter is the real replay defence.** Skew alone only rejects far-future/far-past forgeries; NS delivery latency (minutes when a client was offline) means in-window replays are possible, and `counter ≤ counterReceived` rejects them. The client increments the counter atomically under a process-local lock (`ClientPairingRepository.nextSignedEnvelope`); the master bumps `counterReceived` via `bumpLastSeen` **before** dispatch, so a failed execution is not retried and a duplicate delivery is skipped. A backup-restore that regresses the client's counter makes the next message fail the counter gate — **the client must re-pair** (no silent recovery; this is what keeps the replay window shut).

### 2.3 Command document identifiers

| Identifier prefix | Direction | Semantics |
|---|---|---|
| `aaps_clientcontrol_` | — | shared prefix; gates WS routing + polling fallback |
| `aaps_clientcontrol_hello_<…>` | client→master | first message, Pending → Active |
| `aaps_clientcontrol_cmd_<type>_<clientId>` | client→master | **per-type** command slot, **latest-wins** overwrite |
| `aaps_clientcontrol_ack_<clientId>` | master→client | one ACK doc per client, overwritten in place |
| `aaps_clientcontrol_progress_<clientId>` | master→client | live bolus-progress mirror, overwritten in place |
| `aaps_clientcontrol_offer_<…>` | master-published | PIN-wrapped pairing offer (receiver skips it) |

Per-type slots prevent cross-type collision; **same-type overwrites are intentional** (latest-wins matches "user changed their mind": tap *Sleep* then *Exercise* → *Exercise* wins). Docs are never hard-deleted on the success path — NS soft-deletes (tombstones), and the next same-type command PUTs over the slot; the counter gate still prevents re-execution. Only error-path deletes (malformed/unverifiable garbage) purge a doc.

### 2.4 Two-step ACK lifecycle (Executing → Done)

A command with `wantsAck = true` gets **two** signed `AckEnvelope`s written to `aaps_clientcontrol_ack_<clientId>` (correlated by `commandCounter`):

1. **`Executing` / `Pending`** — written *before* dispatch ("received, applying now").
2. **`Done` / `Ok` | `Failed` | `Expired`** — written *after* dispatch (terminal result, optional `reason` = `FailureReason` enum name, optional `payload` = signed `BolusPreview` for prepares).

The client filters acks by `commandCounter`; a stale ack from a prior command is ignored. Because only **one command is in flight per client** (`AtomicBoolean inFlight`; a second concurrent dispatch returns `Rejected(Busy)`), a single ACK identifier per client suffices — no per-command id is needed. The modal blocks the UI during the wait anyway.

| Phase | Status | Meaning |
|---|---|---|
| `Executing` | `Pending` | received, dispatching |
| `Done` | `Ok` / `Failed` / `Expired` | terminal result |
| `Delivery` | `Failed` | **out-of-band** late async delivery failure (§2.7) |

**Fire-and-forget** commands set `wantsAck = false` (signed, so a client can't be tricked) → the master writes **no** ACK doc (saves write amplification). Used for `SceneStop`, `DismissAlarm`, `StopBolus`. The canonical ack signature input is `"$clientId|$commandCounter|$phase|$status|$reason|$payload|$timestamp"`.

### 2.5 ACK reception, wait window & TTLs

The client receives acks via WS push (`NSClientV3Service` matches `aaps_clientcontrol_ack_<clientId>`, parses, verifies signature, filters by counter, emits to an `ackEvents` SharedFlow with `replay = 16` so an ack landing between publish and collect isn't lost). A single `pollAck` GET is the last-chance fallback before declaring `Unconfirmed`.

```
wait deadline = validUntil + PROPAGATION_MARGIN_MS
```

The master enforces the **matching** `validUntil`, so it never applies a command *after* the client has stopped waiting; the margin (and `pollAck`) catch a last-moment ack.

| Constant | Value | Use |
|---|---|---|
| `ROUND_TRIP_TTL_MS` | `8_000` | fast commands (default wait window) |
| `PUMP_ROUND_TRIP_TTL_MS` | `60_000` | **pump-direct** commits (TBR / extended-bolus SET or CANCEL) |
| `PROPAGATION_MARGIN_MS` | `2_000` | grace for the `Done` ack to travel back |
| `PING_TTL_MS` | `10_000` | liveness probe validity |
| `FIRE_AND_FORGET_TTL_MS` | `300_000` (5 min) | fire-and-forget validity |
| `MAX_TIMESTAMP_SKEW_MS` | `300_000` (±5 min) | skew tolerance |
| `MIN_MODAL_VISIBLE_MS` | `1_500` | min modal display so a sub-second round-trip doesn't flash |

> **Why a 60s pump-direct window?** A bolus *delivery* can take minutes — it gets the queue-ack + progress mirror (§2.7) rather than a long wait. But a TBR/cancel is bounded by the master↔pump *connection* time (a BLE reconnect is ≤ ~1 min) and its real enacted `Ok`/`Failed` is worth waiting for. The flag lives on the dispatcher-level `Command.BolusCommit(bolusId, asAdvisor, pumpDirect)`; when set, the client waits `PUMP_ROUND_TRIP_TTL_MS`. (The *wire* `ClientControlMessage.BolusCommit` carries only `bolusId` + `asAdvisor`; the longer wait is a client-side concern derived from the in-flight `BatchAction`.)

### 2.6 Liveness — `masterReachable` & the PING-PONG

`masterReachable` (interface on `NsClient`, impl in `NSClientV3Plugin`) gates every two-step action and every bidirectional-pref row on a client. It is true when:

```
masterReachable = AAPSCLIENT
               && wsConnected (5s falling-edge grace)
               && fresh liveness signal (≤ 9 min)
               && paired
               && authorized (in roster, not orphan)
```

Freshness is fed by **any** authenticated master signal (`_lastMasterSignalAt` bumped by): (1) a devicestatus heartbeat, (2) a **PING pong**, or (3) a live config republish. The `freshness(thresholdMs = 9 min, tickMs = 60s, pristine = false)` term re-evaluates ~every minute so a stale timestamp flips `fresh → false` within ~1 min of crossing the threshold. `pristine = false` means it **fails closed at boot** (offline until a signal arrives). The flow is shared via `stateIn(WhileSubscribed(5000))`.

**PING-PONG:** the client sends `ClientControlMessage.Ping` (signed, `wantsAck = true`); the master does nothing but increment the counter and reply `AckOutcome(Ok, null)` — **the ack itself is the pong**. Any authenticated pong proves the master is alive *now*, faster than waiting for the next ~minutes-apart devicestatus heartbeat. It is sendable while `masterReachable = false` (that's the point) and fires on client start and when entering an offline-gated screen while WS is up (debounced).

### 2.7 Bolus progress mirror & the late Delivery/Failed ack

A bolus delivery outlives the round-trip (`Done/Ok` only means "queued"), so two async mechanisms relay the real outcome:

- **Progress mirror.** On `onVerifiedBolusCommit` the master arms `progressClientId` (TTL 60s). `BolusProgressData` state changes → `writeProgress(clientId, phase, state)` throttled ~1/s to `aaps_clientcontrol_progress_<clientId>` (signed, overwritten). Phases: `Active` (start the client's progress dialog), `Complete` (100%), `Cleared` (failure/cancel). The client's own `BolusProgressData` observer drives its un-gated progress dialog. `StopBolus` (fire-and-forget) lets the client's Stop button reach `cancelAllBoluses()` on the master. The progress signature input is `"$clientId|$phase|$insulin|$percent|$status|$delivered|$stopDeliveryEnabled|$timestamp"`.
- **Late `Delivery/Failed` ack.** If the pump fails *after* the `Done` ack already reported "queued", the master relays an out-of-band `AckPhase.Delivery / AckStatus.Failed` ack. This is handled in `onAckDoc` **before** the round-trip stream (it is *not* emitted into `ackEvents` — the `Done` ack already terminated the wait) and the client turns it into an **URGENT** alarm. A one-directional `DismissAlarm` lets the client clear the master's copy when it mutes its own (the reverse is deliberately *not* done — the master silencing its alarm must not rob the remote initiator of its failed-bolus notice).

### 2.8 Applied / Rejected / Unconfirmed

The client maps the round-trip to exactly three outcomes:

| Outcome | When | Meaning for the user |
|---|---|---|
| **Applied** | `Done/Ok` before deadline (or immediate local execute on a master) | it happened |
| **Rejected** | signature/counter/skew fail, `Done/Failed`/`Expired`, send failed, not paired, or timeout while `masterReachable = false` | definitely *did not* happen |
| **Unconfirmed** | sent but no `Done` ack before the deadline, or connection lost mid-wait | **state unknown** — the client flips `masterReachable` offline (`forceOfflineProbe`), pings/re-pulls, and reconciles real state via sync-back |

> `masterReachable` is observed as `.drop(1).filter { !it }` during a round-trip — the wait **early-aborts** to `Unconfirmed` only on a *transition* to false (connection loss), not on an initial-false (the timeout/poll path covers that).

---

## 3. The two-step execution path (prepare → commit)

### 3.1 `RoleBranch` — the single role fork

`RoleBranch` (`implementation/.../bolus/RoleBranch.kt`) is the one place the client/master split lives. It is shared by `BatchExecutorImpl`, `WizardExecutorImpl`, and `SceneActionsImpl`.

```kotlin
suspend fun prepare(label, clientCommand, masterPrepare): ActionProgress {
    if (config.AAPSCLIENT) {
        if (!nsClient.masterReachable.value) return Rejected(NotReachable)   // offline-block
        return dispatcher.run(clientCommand, label)                          // signed round-trip
    }
    return when (val r = masterPrepare()) {                                  // local, no modal
        is Preview -> Prepared(r.bolusId, r.lines, r.advisorApplies, r.advisorLines)
        is Error   -> Rejected(ExecutionFailed, r.message)
    }
}
// commit() mirrors this: client → dispatcher.run; master → masterConfirm { onError }.
```

- **Master:** prepares locally and returns the lines as the confirmation (no app-level modal — the caller renders them).
- **Client:** gates on `masterReachable` (offline → `Rejected(NotReachable)`, no round-trip attempted), else dispatches the signed command and surfaces the master's `ActionProgress`.

### 3.2 Three prepare families

| Family | Command | Master behaviour |
|---|---|---|
| **WizardPrepare** | `WizardPrepare(inputs)` | **recomputes** the dose on live profile/COB/IOB/temp-target from raw wizard inputs, constraint-caps |
| **BolusPrepare** | `BolusPrepare(guid)` | computes a QuickWizard (WIZARD-mode) dose from the **synced entry** identified by `guid`, caps |
| **BatchPrepare** | `BatchPrepare(actions)` | **no recompute** — caps + validates **fixed** amounts, builds the merged confirmation for the whole bundle |
| **ScenePrepare** | `ScenePrepare(sceneId, durationMinutes?)` | resolves the scene against its own state, gates (`validateActivation`), authors lines from the scene's actions |

### 3.3 Consume-once, parked-by-id

`WizardBolusExecutorImpl` keeps a `ConcurrentHashMap<Long, PendingBolus>` keyed by `bolusId` (the master timestamp at park time; TTL **10 min**, trimmed on the next prepare). On commit, `pending.remove(bolusId)` is **atomic** — two concurrent commits of the same id can't both deliver (the loser gets `null` → `ConfirmResult.NoPending` → `Rejected(NoPendingBolus)`). This is the **single point** where a parked dose leaves the slot. Triple-redundant against a double-dose: atomic `remove` + receiver `commandMutex` + the per-client counter gate.

The **scene executor uses a separate `pendingScenes` map** (own id-space, shorter `PARK_TTL_MS` = 2 min — no long round-trip window needed), so a `SceneCommit` and a `BolusCommit` can never drain each other's parked state. This is exactly why `SceneCommit` is a distinct wire type from `BolusCommit`.

### 3.4 The master authors the confirmation

All confirmation builders live on the master in `WizardBolusExecutorImpl`: `buildConfirmationLines` (manual wizard) and, for batches, `buildFixedLines` + `buildTtLine` + `buildPsLine` + `buildRmLine` + `buildTempBasalLine` + `buildExtendedBolusLine` + `buildCancelLine` + `buildInsulinActivateLine`. Each line carries a `ConfirmationRole` (`BOLUS`, `CARBS`, `PRIMARY`, `NORMAL`, `WARNING`) → mapped to a theme color in the UI layer. The client/watch render the list verbatim.

**Decision-B bundle order at commit** (`WizardBolusExecutorImpl` confirm path) — the safety net survives an async pump failure:

1. **TempTarget** if target-**raising** (hypo/activity) — unconditional, first;
2. **Bolus / Carbs** delivery;
3. **TempTarget** if target-**lowering** — only if the bolus was accepted;
4. **ProfileSwitch / RunningMode / InsulinActivate**.

---

## 4. Entity coverage

`BatchAction` (`core/interfaces/.../bolus/BatchAction.kt`) is the domain union (at most one of each per batch): `Bolus`, `TempTarget`, `ProfileSwitch`, `RunningMode`, `TempBasal`, `ExtendedBolus`, `CancelTempBasal`, `CancelExtendedBolus`, `InsulinActivate`. `BatchActionDto` (`plugins/sync/.../clientcontrol/BatchActionMapping.kt`) is the flat type-tagged wire form (`TYPE_*` constants).

| User action | Command / route | Pattern | Note |
|---|---|---|---|
| Manual wizard bolus | `WizardPrepare` → `BolusCommit` | two-step | master recomputes on its own COB/IOB; advisor (high-BG correct-now) → `asAdvisor`, delivered as `CORRECTION_BOLUS` |
| QuickWizard bolus | `BolusPrepare(guid)` → `BolusCommit` | two-step | master computes from the synced entry; super-bolus only here (gated on the entry) |
| Fixed bolus (Insulin/Treatment dialog) | `BatchPrepare[Bolus]` → `BolusCommit` | two-step | `recordOnly` (pen bolus) is **not** capped; regular delivery is capped |
| Carbs / eCarbs | `BatchPrepare[Bolus(insulin=0,…)]` → `BolusCommit` | two-step | carbs-only funnels through `deliverECarbs`; negative carbs clamped to current COB, back-dated removal zeroed |
| TempTarget | `BatchAction.TempTarget` (standalone or in batch) | two-step | duration 0 → CANCEL_TT; reason localized on the showing device |
| ProfileSwitch | `BatchAction.ProfileSwitch` (dialog / QuickLaunch / batch) | two-step | `profileName = null` → master's **current** active profile; non-null re-validated at prepare (TOCTOU) |
| RunningMode (loop state) | `BatchAction.RunningMode` | two-step | master re-validates the transition (`Loop.allowedNextModes`) |
| TempBasal set | `BatchAction.TempBasal` | two-step | `pumpDirect` (60s TTL); master re-validates percent/absolute vs **its** pump → rejects on mismatch |
| TempBasal cancel | `BatchAction.CancelTempBasal` | two-step | `pumpDirect`; cancels the master's *current* TBR |
| ExtendedBolus set | `BatchAction.ExtendedBolus` | two-step | `pumpDirect`; caps, then rejects if net ≤ 0 |
| ExtendedBolus cancel | `BatchAction.CancelExtendedBolus` | two-step | `pumpDirect`; cancels the master's *current* extended bolus |
| InsulinActivate | `BatchAction.InsulinActivate(iCfg)` | two-step | master re-applies its **current** active profile with the new insulin (Fill dialog) |
| Scene activate | `ScenePrepare` → `SceneCommit` | two-step | **separate id-space** from bolus; master re-resolves the scene |
| **Scene stop** | `SceneStop(triggerChain)` | single-step | `dispatcher.execute`, no preview (already master-authoritative); chain target re-resolved from master state |
| **Preference edit** (bidirectional key) | `PreferenceEdit(prefs)` | single-step | pessimistic round-trip + republish (§5.2); locally-built modal copy |
| **Fill / prime** | — | off-relay | local-only; `rejectIfAapsClient()`; PRIMING bolus + profile-switch chaining, no relay command |
| **SMS** | — | off-relay | not on the client-control channel |
| Calibration | — | off-relay | local `activeCalibration.addEntry`, NS-synced |
| CarePortal | — | off-relay | additive `persistenceLayer.insert` + NS sync |

---

## 5. Config / state mirroring

The master publishes its running configuration across **two** `settings` docs, split by write frequency. Both are shaped `{date, utcOffset, app, schemaVersion, runningConfig}`; both use the constant `DOC_DATE = 946684800001L` (configuration snapshots, not timestamped events — a fixed date satisfies NS's immutable-`date` rule on re-PUT).

### 5.1 Cold & hot docs

| | COLD (`SettingsIdentifiers.COLD`) | HOT (`SettingsIdentifiers.STATE`) |
|---|---|---|
| Debounce | 5 s (`COLD_DEBOUNCE_MS`) | 1 s (`HOT_DEBOUNCE_MS`) |
| Carries | `syncedPrefs` (flat `Map<String,String>`), pump type, `isFakingTempsByExtendedBoluses`, version, plugin selection (`ActivePlugin*` keys), scene/quickwizard/automation/insulin defs, `authorizedClients` roster | active scene snapshot (or null) + `usedAutosensOnMainPhone` |
| Triggers | WS-connect rising edge, any synced-key change, `EventConfigBuilderChange`, roster changes, `forceColdRepublish` | scene lifecycle (`hotKeys()`) |

Kept separate so a cold-doc apply (which has no `activeScene` block) **never clears a running scene**.

**Client apply (read-only mirror):**
- **Cold** → adopt version (notify on mismatch), switch `VirtualPump` to the master's pump type, apply each synced pref via `putRemote` (does **not** echo, §5.2), and mirror `isFakingTempsByExtendedBoluses` verbatim to `VirtualPump.fakeDataDetected` (null = older master → leave unchanged). This is a **read-only mirror** — the client must not derive these itself.
- **Hot** → pass the active-scene snapshot (or null) to `ActiveSceneSync.applyActiveScene()` and put `usedAutosensOnMainPhone`.

Notable invariants:
- **Raw value, not mode-adjusted.** `syncedPrefs` serializes the **raw** persisted value, not `preferences.get()`'s mode-adjusted effective value. Simple/Expert mode is per-device presentation; each device re-applies its own mode on read. Master in Simple + client in Expert both sync the raw setting.
- **Flat map.** A new bidirectional setting needs **zero** publisher changes — it auto-appears as another map entry.
- **Roster appended at publish time.** `AuthorizedClientsRepository` is in `:plugins:sync` but `RunningConfigurationImpl` is in `:plugins:configuration`; to avoid an inter-module dep, the roster is appended to the JSON in `RunningConfigurationPublisher.publishCold` *after* `configuration()`. Block absence is a backward-compat marker — clients must **not** infer orphan from `null`, only from "block present + clientId missing". Pending entries are deliberately omitted (a pairing client would otherwise wrongly think it isn't paired yet).
- **Self-healing republish.** On WS reconnect (debounced 3 s, `WS_RECONNECT_DEBOUNCE_MS`) the master republishes its **entire** config (not just changes), recovering: an offline edit that failed to publish, an auto-pruned doc, or a `start()`-before-NS-ready initial publish. `LoadSettingsWorker` unconditionally GETs both docs on every loop, pulling any missed WS push. Republishing unchanged config is idempotent (per-key LWW no-ops → no echo storm).

### 5.2 Bidirectional preference sync — LWW + echo-break (pessimistic)

Distinct from the command round-trip in *semantics*, though it rides the same signed channel:

1. A client user edits a `SyncSpec(direction = Bidirectional)` key → `put()` emits on `Preferences.syncedLocalChanges`.
2. `PreferencesClientPublisher` (AAPSCLIENT-only) batches changed keys over a **500 ms** settle window, then runs `ClientControlRoundTrip.run(PreferenceEdit(prefs), modal)`. **The client does not store the value locally** — it sends the current value + per-key `lastModified` and shows a modal with a *tentative* copy.
3. Master `onVerifiedPreferencesUpdate` applies **LWW per key** (skips if `incoming.lastModified ≤ stored SyncedPrefModified`) via `putRemote` (which does **not** emit on `syncedLocalChanges` — the **echo-break**), then **always** calls `requestColdRepublish()` *even if LWW dropped the push as a no-op*.
4. The republished cold doc is the confirmation: the client's `applyCold` writes the authoritative value via `putRemote` and the modal resolves **Applied**. The republish **is** the ack — no ACK payload needed.

> **Why always-republish?** A client that *loses* a concurrent LWW race would otherwise be stuck on its optimistic value forever (the key didn't change on the master, so nothing would propagate). Always-republishing lets the loser adopt the winner — honest, no per-key applied/superseded flags. Two clients edit concurrently → master picks the LWW winner → both see it via sync-back.

> **Echo-break correctness.** `PreferencesClientPublisher` listens *only* to `syncedLocalChanges` (local `put()`), never to `putRemote` (applied-from-sync writes). So the master's republish doesn't loop back out. If the user edits again while the modal is up, the publisher is suspended in the round-trip await, so edits queue and ship in the next round-trip (pref edits are fully serialized — one in flight at a time).

This contrasts with the command round-trip: **commands** carry an ACK *payload* (the bolus preview) with an `Ok/Failed/Expired` result; **prefs** deliver "here's the authoritative value" via forced republish (sync-back) with no new payload.

> The **plugin active-selection + settings** are *designed* to move onto this same generic key-sync (flat per-single-select-type `ActivePlugin` keys behind one exhaustive `when(PluginType)`), but that migration is **not yet implemented**. See `PLUGIN_SELECTION_KEY_SYNC.md`.

---

## 6. The watch / wear path

The wear phone adapter (`DataHandlerMobile`) funnels **all** watch therapy into the *same* `BatchExecutor` / `WizardExecutor` prepare/commit — the role fork lives once in `RoleBranch`, so the handlers are role-agnostic.

```
ActionBolusPreCheck(insulin,carbs)
  → handleBolusPreCheck → contacting() + shipPrepared(batchExecutor.prepare([Bolus]), …)
  → master authors Prepared(bolusId, lines)
  → EventData.ConfirmAction(returnCommand=ActionBolusConfirmed(bolusId), lines=[ConfirmActionLine], deferConfirm=AAPSCLIENT)
  → user taps ✓ on watch → ActionBolusConfirmed(bolusId)   (id only — NEVER an amount)
  → batchExecutor.commit(bolusId, Sources.Wear) → deliver
```

| Watch action | Prepare route | Confirm command |
|---|---|---|
| Fixed bolus | `batchExecutor.prepare([Bolus])` | `ActionBolusConfirmed(bolusId)` |
| QuickWizard | `wizardExecutor.prepare(QuickWizard(guid))` | `ActionWizardConfirmed(bolusId)` |
| Full wizard | `wizardExecutor.prepare(Manual(inputs))` (needs non-null `actualBg()`) | `ActionWizardConfirmed(bolusId)` |
| eCarbs | `batchExecutor.prepare([Bolus(insulin=0,…)])` | `ActionECarbsConfirmed(bolusId)` |
| TempTarget | `batchExecutor.prepare([TempTarget])` | `ActionTempTargetConfirmed(bolusId)` |
| ProfileSwitch | `batchExecutor.prepare([ProfileSwitch])` | `ActionProfileSwitchConfirmed(bolusId)` |
| RunningMode | `batchExecutor.prepare([RunningMode])` | `RunningModeConfirmed(bolusId)` |

Key wear mechanics:
- **`EventData.ConfirmActionLine{role, text}`** — the watch's `AcceptActivity` color-codes by the role→color map, rendering the *exact* master-authored confirmation the phone dialog shows.
- **`deferConfirm = config.AAPSCLIENT`** — on a client the watch does **not** show instant success on ✓; it waits for the master's real terminal. `EventData.RemoteDelivered` (sent only on a client after the master commit succeeds) triggers the deferred success animation; the master's own progress mirror then takes over as the durable terminal.
- **`EventData.ContactingMaster`** — a transient spinner (single `data object`, no phase) emitted on a client before every prepare and commit, dismissed when the resolving terminal arrives.
- **No amount ever returns from the watch.** The confirm command carries only the master-assigned `bolusId` (consume-once token). The watch-derived timestamp is replaced by the master's id, so a stale prepare can't leak into an unrelated bolus.
- **Fill is excluded** — `handleFillPreCheck`/`handleFillPresetPreCheck` still call `rejectIfAapsClient()`; no relay command exists.
- **Still bespoke (not yet on the unified line-rendering):** the QuickWizard *message* (capping is shared, but the message text is concatenated) and the full-Wizard `ActionWizardResult` breakdown.

**Watch-on-a-client** is the natural extension: a watch paired to an `AAPSCLIENT` phone uses these same handlers, but `RoleBranch` routes *every* prepare and commit through `dispatcher.run()` (a three-device relay: watch → client phone → master). The watch always renders the master's lines and final state.

---

## 7. Key invariants & gotchas

- **Consume-once / no double-apply.** Atomic `pending.remove(bolusId)` is the only drain point; a re-sent commit finds an empty slot → `NoPendingBolus`. Reinforced by the receiver `commandMutex` and the strictly-`>` counter gate.
- **`activePumpInternal`, not `activePump`.** When mirroring `fakeDataDetected`, the master/client must cast `activePlugin.activePumpInternal as? VirtualPump` — `activePump` is a `PumpWithConcentration` wrapper (`isVP == false`), so an `as? VirtualPump` on it silently no-ops.
- **Prepare never mutates state.** `ScenePrepare` does **not** touch `ActiveSceneManager`; `WizardPrepare`/`BatchPrepare` only park. Nothing leaves the slot until commit. The watch's instant-✓ → deferred-success contract depends on this.
- **TOCTOU re-validation at commit/prepare.** A client may relay something the master no longer has or that conflicts with the master's current state. The master re-validates at prepare time: profile name lookup (null → current active), TempBasal pump-style (percent vs absolute, rejects on mismatch — `clientcontrol_pump_out_of_sync`), RunningMode transition legality, scene chain target (re-resolved from master config, **never** from the wire).
- **Offline-block.** `RoleBranch` returns `Rejected(NotReachable)` immediately when a client is offline — no round-trip attempted. Enforces single-authority (master online) for all two-step actions.
- **Pump-direct TTL.** TBR/extended-bolus commits set `pumpDirect = true` so the client waits 60 s; a bolus does **not** (queue-ack + progress mirror cover the minutes-long delivery).
- **Separate id-spaces.** Bolus and scene pending maps never collide → distinct `BolusCommit` vs `SceneCommit` wire types.
- **Record-only is not capped.** A pen bolus given outside AAPS is logged as-is; regular delivery is `capFixed()`-capped.
- **Extended bolus net-zero rejected** (a 0-percent TBR — suspend basal — is allowed); negative carbs never remove more than current COB.
- **Executor is the single bolus-failure catch point.** `!result.success` (SMB excluded) raises the URGENT `BOLUS_DELIVERY_FAILED` alarm in the executor; SMB/loop boluses bypass the executor structurally, so they are not double-alarmed. There is no per-dialog failure handler.
- **ACK write must never abort execution.** It is exception-safe; the command is already applied locally before the `Done` ack is mapped. A failed ACK write just degrades the client to sync-back fallback.
- **`wantsAck` is signed** — a client cannot be tricked into wanting/not-wanting an ack.
- **Late `Delivery/Failed` ack is never emitted into `ackEvents`** — it's out-of-band in `onAckDoc` and raises an URGENT notification (the `Done` ack already ended the round-trip).
- **Backup-restore counter regression ⇒ re-pair.** No silent recovery — that's what keeps the replay window shut.

---

## 8. Extending it — adding a new two-step action

To add a new master-authored two-step action (e.g. a new therapy command):

1. **Domain type.** Add a `BatchAction` subtype (if it fits the batch bundle) *or* a new `ClientControlMessage` variant + matching `ClientControlActionDispatcher.Command`. If a `ClientControlMessage`, give it a **stable `@SerialName`** (the wire contract — never rename later) and add it to the `when` in `ClientControlReceiver.onVerifiedEnvelope`.
2. **Wire mapping.** If it's a `BatchAction`, add a `TYPE_*` constant + fields to `BatchActionDto` and the to/from mapping in `BatchActionMapping.kt`.
3. **Master execution + confirmation.** Add the prepare logic (cap/validate against the master's live state, park by `bolusId`) and a `buildXxxLine` confirmation builder in `WizardBolusExecutorImpl`. Re-validate anything that could be stale (TOCTOU). Return `PrepareResult.Preview(bolusId, lines, …)`.
4. **Receiver handler.** Add `onVerifiedXxx` that calls the executor's prepare/confirm and returns an `AckOutcome`.
5. **Route through `RoleBranch`.** Call `batchExecutor.prepare(...)` / `commit(bolusId, ...)` (or `wizardExecutor` for recompute) from the dialog/screen/wear handler. **Do not** add a role check in the caller — `RoleBranch` already handles client-vs-master, the offline gate, and the `ActionProgress` mapping.
6. **Wear (optional).** Add a `handleXxxPreCheck` in `DataHandlerMobile` that calls `contacting()` + `shipPrepared(...)`, and an `onEvent<ActionXxxConfirmed>` that calls `commit(bolusId)`. The watch renders the master's lines automatically.

Guidance: prefer the **batch** path (fixed amounts, no recompute) unless the action genuinely needs server-side recomputation (then mirror `WizardPrepare`). Keep all confirmation authoring on the master. Set `pumpDirect = true` only for slow pump commands. Never send an amount back on commit — only the `bolusId`.
