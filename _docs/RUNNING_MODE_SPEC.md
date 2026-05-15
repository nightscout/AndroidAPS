# Running Mode Specification

Single source of truth for the `RM.Mode` subsystem. When you change behavior for any
mode, update this file in the same PR.

## Why this exists

The running-mode subsystem is concern-centric: each concern (transitions, pump
commands, reconciliation, UI) lives in one focused file. The price is that
understanding *one mode* requires reading 8–10 files. This doc inverts the view:
one mode per row, every concern in columns.

If something disagrees with the code, the code is right and this doc is stale —
fix it.

## Component map

```
─── PURE LOGIC ──────────────────────────────────────────────────
  RM.Mode                    enum + helpers (isSuspended, isLoopRunning, …)
                             core/data/.../model/RM.kt
  PumpCommandGate                    (mode × command) → Allow/Reject
                             core/objects/.../runningMode/PumpCommandGate.kt
  ReconcilerDecision         (prev × next) → pump action
                             plugins/aps/.../runningMode/ReconcilerDecision.kt

─── WRITE PATHS ─────────────────────────────────────────────────
  Loop.handleRunningModeChange    orderly RM row write
                                  plugins/aps/.../loop/LoopPlugin.kt
  Loop.runningModePreCheck        auto-forces RM rows on pump-suspend / constraints
                                  (runs as side effect of every runningMode() call)
  DstHelperPlugin.dstCheck        triggers SUSPENDED_BY_DST from KeepAliveWorker
                                  plugins/constraints/.../dstHelper/DstHelperPlugin.kt

─── ENFORCEMENT ─────────────────────────────────────────────────
  RunningModeGuard           UI/SMS/Wear pre-check (snackbar reject)
                             core/objects/.../runningMode/RunningModeGuard.kt
  CommandQueueImpl gate      last-resort queue-level reject (callback)
                             implementation/.../queue/CommandQueueImplementation.kt
  RunningModeReconciler      observes RM DB changes, drives pump commands
                             plugins/aps/.../runningMode/RunningModeReconciler.kt
  RunningModeExpiryWorker    cleans EMULATED_PUMP_SUSPEND TBR at natural RM expiry
                             plugins/aps/.../runningMode/RunningModeExpiryWorker.kt
```

## Mode matrix

| Mode                | Loop runs? | Suspended? | Pump suspended? | Bucket         | Allowed commands            | Reconciler entry action                  |
|---------------------|------------|------------|-----------------|----------------|-----------------------------|------------------------------------------|
| `OPEN_LOOP`         | yes        | no         | no              | Working        | all                         | NoOp                                     |
| `CLOSED_LOOP`       | yes        | no         | no              | Working        | all                         | NoOp                                     |
| `CLOSED_LOOP_LGS`   | yes        | no         | no              | Working        | all                         | NoOp                                     |
| `DISABLED_LOOP`     | no         | no         | no              | Stopped        | all                         | CancelTbr                                |
| `SUPER_BOLUS`       | no         | yes (¹)    | no              | ZeroDelivery   | CANCEL_TBR, TBR_ZERO, BOLUS | IssueZeroTbr + cancelExtendedBolus       |
| `DISCONNECTED_PUMP` | no         | yes        | yes             | ZeroDelivery   | CANCEL_TBR, TBR_ZERO        | IssueZeroTbr + cancelExtendedBolus       |
| `SUSPENDED_BY_PUMP` | no         | yes        | yes             | PumpReported   | CANCEL_TBR                  | NoOp (handled by `runningModePreCheck`)  |
| `SUSPENDED_BY_USER` | no         | yes        | no              | Stopped        | all                         | CancelTbr                                |
| `SUSPENDED_BY_DST`  | no         | yes        | no              | SuspendedNoTbr | CANCEL_TBR                  | CancelTbr                                |
| `RESUME`            | n/a        | n/a        | n/a             | Working        | all                         | NoOp / CancelTbr if exiting ZeroDelivery |

¹ `RM.Mode.pausesLoopExecution()` returns `true` for `SUPER_BOLUS` — meaning
"loop algorithm paused" while the wizard delivers the bolus and runs basal at 0.
Not the same as "manual bolus blocked": `PumpCommandGate` allows BOLUS during
SUPER_BOLUS.

`RESUME` is a synthetic mode: `handleRunningModeChange` cancels the current temporary
RM row and restores the underlying mode. Never persisted as `RESUME`.

### Allowed transitions out

| From                | To (allowed via `Loop.allowedNextModes()`)                                                     |
|---------------------|------------------------------------------------------------------------------------------------|
| `OPEN_LOOP`         | DISABLED_LOOP, CLOSED_LOOP, CLOSED_LOOP_LGS, DISCONNECTED_PUMP, SUSPENDED_BY_USER, SUPER_BOLUS |
| `CLOSED_LOOP`       | DISABLED_LOOP, OPEN_LOOP, CLOSED_LOOP_LGS, DISCONNECTED_PUMP, SUSPENDED_BY_USER, SUPER_BOLUS   |
| `CLOSED_LOOP_LGS`   | DISABLED_LOOP, OPEN_LOOP, CLOSED_LOOP, DISCONNECTED_PUMP, SUSPENDED_BY_USER, SUPER_BOLUS       |
| `DISABLED_LOOP`     | OPEN_LOOP, CLOSED_LOOP, CLOSED_LOOP_LGS, DISCONNECTED_PUMP, SUPER_BOLUS                        |
| `SUPER_BOLUS`       | DISCONNECTED_PUMP, RESUME                                                                      |
| `DISCONNECTED_PUMP` | RESUME                                                                                         |
| `SUSPENDED_BY_PUMP` | (empty — auto-cleared by `runningModePreCheck` when pump unsuspends)                           |
| `SUSPENDED_BY_USER` | DISCONNECTED_PUMP, RESUME                                                                      |
| `SUSPENDED_BY_DST`  | DISCONNECTED_PUMP only — **not RESUME**. User cannot manually exit; expires by duration.       |
| `RESUME`            | error (invalid mode)                                                                           |

Constraint filtering: `allowedNextModes()` further removes OPEN/CLOSED/LGS modes when
`isLoopInvocationAllowed=false`, and CLOSED_LOOP when `isClosedLoopAllowed=false`.

### Who sets each mode

| Mode                | User entry points                                              | Auto-forced from / by                                                    |
|---------------------|----------------------------------------------------------------|--------------------------------------------------------------------------|
| `OPEN_LOOP`         | RunningMode UI, SMS, Wear                                      | `runningModePreCheck`: from `CLOSED_LOOP` if `closedLoopAllowed=false`   |
| `CLOSED_LOOP`       | RunningMode UI, SMS, Wear                                      | —                                                                        |
| `CLOSED_LOOP_LGS`   | RunningMode UI, SMS                                            | `runningModePreCheck`: from `CLOSED_LOOP` if `lgsModeForced=true`        |
| `DISABLED_LOOP`     | RunningMode UI, SMS                                            | `runningModePreCheck`: from any working mode if `!loopInvocationAllowed` |
| `SUPER_BOLUS`       | `BolusWizard.executeNormal` when `useSuperBolus=true`          | —                                                                        |
| `DISCONNECTED_PUMP` | RunningMode UI, SMS, Wear, Garmin                              | —                                                                        |
| `SUSPENDED_BY_PUMP` | —                                                              | `runningModePreCheck`: when `pump.isSuspended()=true` (config.APS only)  |
| `SUSPENDED_BY_USER` | RunningMode UI, SMS, Wear                                      | —                                                                        |
| `SUSPENDED_BY_DST`  | —                                                              | `DstHelperPlugin.dstCheck` from `KeepAliveWorker` when `wasDST()=true`   |
| `RESUME`            | RunningMode UI ("Resume", "Reconnect", "Cancel SB"), SMS, Wear | —                                                                        |

### Predicates on `RM.Mode`

| Mode                | `isLoopRunning` | `pausesLoopExecution` | `isPumpSuspended` | `isClosedLoopOrLgs` | `mustBeTemporary`  |
|---------------------|:---------------:|:---------------------:|:-----------------:|:-------------------:|:------------------:|
| `OPEN_LOOP`         |        ✓        |           ✗           |         ✗         |          ✗          |         ✗          |
| `CLOSED_LOOP`       |        ✓        |           ✗           |         ✗         |          ✓          |         ✗          |
| `CLOSED_LOOP_LGS`   |        ✓        |           ✗           |         ✗         |          ✓          |         ✗          |
| `DISABLED_LOOP`     |        ✗        |           ✗           |         ✗         |          ✗          |         ✗          |
| `SUPER_BOLUS`       |        ✗        |         **✓**         |         ✗         |          ✗          |         ✓          |
| `DISCONNECTED_PUMP` |        ✗        |           ✓           |         ✓         |          ✗          |         ✓          |
| `SUSPENDED_BY_PUMP` |        ✗        |           ✓           |         ✓         |          ✗          | ✓ (Long.MAX_VALUE) |
| `SUSPENDED_BY_USER` |        ✗        |           ✓           |         ✗         |          ✗          |         ✓          |
| `SUSPENDED_BY_DST`  |        ✗        |           ✓           |         ✗         |          ✗          |         ✓          |
| `RESUME`            |       n/a       |          n/a          |        n/a        |         n/a         |        n/a         |

### NS sync mapping (`NSOfflineEvent`)

`RM.toNSOfflineEvent()` lives in `plugins/sync/.../nsclientV3/extensions/RunningModeExtension.kt`.
The `Reason` field is hardcoded to `OTHER` and marked `// Unused`. Working modes
(`OPEN_LOOP`/`CLOSED_LOOP`/`CLOSED_LOOP_LGS`) serialize with `duration = 0` and the
real duration in `originalDuration`; offline modes serialize the real duration in both fields,
**except `DISABLED_LOOP`** which can be permanent locally (`duration == 0`). Because Nightscout's
offline-marker plugin (`lib/plugins/openaps.js findOfflineMarker`) only renders an offline window
when `treatment.duration > 0`, permanent `DISABLED_LOOP` substitutes a long duration
(`Int.MAX_VALUE` minutes ≈ 4085 years) on the wire while preserving the original `0` in
`originalDuration` for round-trip back to AAPS.

| `RM.Mode`           | `NSOfflineEvent.Mode` | NS `duration` field                                       | `originalDuration`              |
|---------------------|-----------------------|-----------------------------------------------------------|---------------------------------|
| `OPEN_LOOP`         | `OPEN_LOOP`           | `0`                                                       | real duration                   |
| `CLOSED_LOOP`       | `CLOSED_LOOP`         | `0`                                                       | real duration                   |
| `CLOSED_LOOP_LGS`   | `CLOSED_LOOP_LGS`     | `0`                                                       | real duration                   |
| `DISABLED_LOOP`     | `DISABLED_LOOP`       | `Int.MAX_VALUE * 60 * 1000` if `duration == 0`, else real | real duration (0 for permanent) |
| `SUPER_BOLUS`       | `SUPER_BOLUS`         | real duration                                             | real duration                   |
| `DISCONNECTED_PUMP` | `DISCONNECTED_PUMP`   | real duration                                             | real duration                   |
| `SUSPENDED_BY_PUMP` | `SUSPENDED_BY_PUMP`   | real duration                                             | real duration                   |
| `SUSPENDED_BY_USER` | `SUSPENDED_BY_USER`   | real duration                                             | real duration                   |
| `SUSPENDED_BY_DST`  | `SUSPENDED_BY_DST`    | real duration                                             | real duration                   |
| `RESUME`            | error("Invalid mode") | n/a                                                       | n/a                             |

### UI affordances per mode

| Mode                |  Dialogs force record-only? (¹)   |                              TBR/EB hidden in ManageView? (²)                              | Loop algorithm runs? (³) |
|---------------------|:---------------------------------:|:------------------------------------------------------------------------------------------:|:------------------------:|
| `OPEN_LOOP`         |                no                 |                                             no                                             |           yes            |
| `CLOSED_LOOP`       |                no                 |                                             no                                             |           yes            |
| `CLOSED_LOOP_LGS`   |                no                 |                                             no                                             |           yes            |
| `DISABLED_LOOP`     |                no                 |                                             no                                             |            no            |
| `SUPER_BOLUS`       | no (PumpCommandGate allows BOLUS) |   no (only `isDisconnected`/`pump.isSuspended()` hide; super bolus leaves them visible)    |            no            |
| `DISCONNECTED_PUMP` |                yes                |                                            yes                                             |            no            |
| `SUSPENDED_BY_PUMP` |                yes                |                               yes (via `pump.isSuspended()`)                               |            no            |
| `SUSPENDED_BY_USER` |                no                 | no (only `pump.isSuspended()` and `isDisconnected` hide; user-suspend leaves them visible) |            no            |
| `SUSPENDED_BY_DST`  |                yes                |                                             no                                             |            no            |

¹ Unified rule across `InsulinDialog`, `TreatmentDialog`, and `WizardDialog`:
`forcedRecordOnly = PumpCommandGate.check(mode, BOLUS) is Reject || !pumpInitialized || config.AAPSCLIENT`.
Source of truth is `PumpCommandGate`: each dialog forces record-only iff the gate would reject
a real bolus, the pump is uninitialized, or the app is in AAPSCLIENT mode.

Per-dialog presentation:

- `InsulinDialog` — switch is disabled when forced; user can still uncheck if eligible. Submit
  uses `runningModeGuard.checkWithSnackbar(BOLUS)` as defense-in-depth.
- `TreatmentDialog` — no override switch. `WarningBanner` at top of dialog when forced;
  confirmation message lists "Bolus will be recorded only". Submit auto-routes to record-only.
- `WizardDialog` / `BolusWizard.executeNormal`/`executeBolusAdvisor` — same banner + confirmation
  line; the execute methods take `forcedRecordOnly: Boolean` and bypass `commandQueue.bolus`,
  writing directly via `persistenceLayer.insertOrUpdateBolus`/`insertOrUpdateCarbs`. The mode
  snackbar guard is suppressed when `forcedRecordOnly = true` (the user already saw the banner).

² `ManageViewModel.kt:98–127` — hides EB/TBR if `!capable || !init || pump.isSuspended() ||
   isDisconnected || AAPSCLIENT` (+ `pump.isFakingTempsByExtendedBoluses` for EB only).
Note: this uses `pump.isSuspended()` (driver-level) rather than the loop's
`RM.Mode.isPumpSuspended()`. They agree only on `SUSPENDED_BY_PUMP`.

³ Loop invocation guard: `LoopPlugin.invoke()` short-circuits when
`runningMode().pausesLoopExecution()`.
SMB delivery guard requires `isClosedLoopOrLgs()`.

### Wear entry-point gating

All Wear handlers that can deliver insulin reject up-front in AAPSCLIENT mode
(no real pump locally — `VirtualPump` would otherwise fake-deliver and write a
`BS` row, which is misleading from a remote watch with no checkbox). After the
client-mode guard, they consult `runningModeGuard.rejectionMessage(BOLUS)` for
the running-mode dimension. Sites:

- `handleWizardPreCheck`
- `handleQuickWizardPreCheck`
- `handleBolusPreCheck`
- `handleFillPresetPreCheck`
- `handleFillPreCheck`

Plus `doFillBolus` (post-confirm path) which already used the guard.

`SmsCommunicatorPlugin` does not need the AAPSCLIENT block — SMS is not
available in client mode. Its dispatcher previously short-circuited BOLUS with
a hardcoded `pumpsuspended` string and the wrong predicate; that pre-empt was
deleted because `processBOLUS` already calls `runningModeGuard.rejectionMessage(BOLUS)`.

UserAction (Automation) and Scene precheck handlers are *not* gated for
AAPSCLIENT explicitly — Automation does not run in client mode, and scenes do
not issue insulin directly.

### Algorithmic side effects

- `CLOSED_LOOP_LGS` caps `maxIOB` at `HardLimits.MAX_IOB_LGS` via
  `Loop.applyMaxIOBConstraints` (LoopPlugin.kt:428).
- `DISABLED_LOOP` (Stopped bucket): reconciler issues `cancelTempBasal` on entry,
  same as SUSPENDED_BY_USER. User-triggered and constraint-forced behave identically.
- `SUSPENDED_BY_USER` is the temporary counterpart of `DISABLED_LOOP`: loop algorithm
  is paused (`pausesLoopExecution = true`) and the entry-side TBR cancel runs, but the
  pump remains fully usable for manual delivery — `PumpCommandGate` allows BOLUS, TBR,
  and EB the same as in `DISABLED_LOOP`. The two differ only in `mustBeTemporary`
  (SUSPENDED_BY_USER must carry a duration) and the loop-pause guard
  (`pausesLoopExecution` is set for SUSPENDED_BY_USER, unset for DISABLED_LOOP — both
  prevent algorithm execution but via different short-circuits in `LoopPlugin.invoke()`).
- Zero-delivery modes (`DISCONNECTED_PUMP`, `SUPER_BOLUS`) issue a zero-TBR with
  `tbrType = EMULATED_PUMP_SUSPEND` via the reconciler. `RunningModeExpiryWorker`
  cleans this TBR up at the natural RM end if no earlier change cancels it.
  `BolusWizard` writes the SUPER_BOLUS row and lets the reconciler issue the
  TBR — it does *not* call `commandQueue.tempBasalAbsolute(0.0, ...)` directly.
- Failure feedback: TBR enforcement commands (cancelTempBasal, tempBasalAbsolute,
  tempBasalPercent) issued by the reconciler use `EventShowSnackbar(...Error)` on
  failure. Defensive `cancelExtended` is silent (log only).

## Known inconsistencies (these may be bugs)

### 1. `runningModePreCheck` mutates on read

`Loop.runningMode()` and `runningModeRecord()` route through
`runningModePreCheck()`, which writes to the RM table when pump-suspend state
changes or constraints flip. Callers that just want to *read* the mode trigger a
write side effect.

Consequences:

- `RunningModeGuard.checkWithSnackbar()` uses `runBlocking` to call this suspend
  function from sync code (RunningModeGuard.kt:38–41 TODO).
- Hot paths read the DB indirectly to bypass the precheck (e.g.,
  `OverviewDataCacheImpl.kt:579`).

Splitting the precheck into pure read + explicit reconciliation triggered by
events (pump-state change, constraint change) would clean this up — but it
touches every caller of `runningMode()`.

### 2. Duplicate `PumpCommandGate` removed (historical)

Until `<commit>`, `plugins/aps/.../runningMode/PumpCommandGate.kt` and
`core/objects/.../runningMode/PumpCommandGate.kt` were identical files. The plugins/aps
copy had zero imports and was deleted. **Use `app.aaps.core.objects.runningMode.PumpCommandGate`.**

## Adding a new mode — checklist

When you add a new `RM.Mode`, edit *all* of:

1. **`RM.kt`** — add the enum entry. Update `pausesLoopExecution()`,
   `isLoopRunning()`, `isPumpSuspended()`, `mustBeTemporary()`,
   `isClosedLoopOrLgs()` predicates as needed.
2. **`Loop.allowedNextModes()`** (LoopPlugin.kt:216) — add a `when` branch for
   transitions *out of* the new mode, and add the new mode to *every other
   branch* that should be able to enter it.
3. **`Loop.handleRunningModeChange`** (LoopPlugin.kt:243) — add a `when` branch
   for the write path (RM row insert, plus any inline pump action).
4. **`Loop.runningModePreCheck`** (LoopPlugin.kt:326) — only if the mode should
   be auto-forced or auto-cleared.
5. **`ReconcilerDecision.bucketOf()`** — assign to the right bucket.
   `ReconcilerDecision.decide()` may need a new transition rule if the bucket is
   new.
6. **`PumpCommandGate.check()`** — define which `CommandKind`s are allowed.
   `PumpCommandGate.Reason` may need a new entry; `RunningModeGuard.toStringRes()` then
   needs a string-resource mapping.
7. **`NSOfflineEvent.Mode`** — add an entry. Update `RM.toNSOfflineEvent()` in
   `plugins/sync/.../nsclientV3/extensions/RunningModeExtension.kt`. The `Reason`
   field is currently unused (always `OTHER`).
   Also update DB converters: `RunningMode.Mode.fromDb()` / `RM.Mode.toDb()` in
   `database/persistence/.../converters/RunningModeExtension.kt`.
8. **String resources** — add a label for the mode (used in RunningMode UI,
   notifications, snackbars).
9. **UI** — update `RunningModeManagementViewModel` (button visibility),
   `ManageViewModel` (TBR/EB hide rules), `MainViewModel` (status text),
   `InsulinDialog`/`TreatmentDialog`/`WizardDialog` if record-only logic
   should change for the new mode.
10. **This doc** — add a row to every table above and any inconsistency notes.
