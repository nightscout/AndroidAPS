# Profile Validation Split — Semantic vs. Pump Compatibility

Status: **Phase 1 implemented (not yet committed / not runtime-verified). Phase 2 (tier 2) pending.**
Last updated: 2026-06-08

## 1. Background

- Issue **#4872**: a single invalid local profile silently stops *all* profiles from syncing to
  Nightscout. `DataSyncSelectorV3.processChangedProfileStore()` gates the whole (single-document)
  profile-store upload on `profileStore.allProfilesValid`; one invalid profile makes the early
  `return` fire with no log, no notification, and `ProfileStoreLastSyncedId` never advances — so
  sync is silently frozen, indefinitely. Real-world harm: Nightscout keeps showing a *stale*
  profile while live AAPS values changed, and an endo team dosed against the wrong baseline.
- PR **#4873** proposed only a notification on that gate (surface the symptom). It does **not**
  restore sync — NS stays stale — and we judged it an incomplete fix.

## 2. Root-cause insight (the design decision)

`allProfilesValid` → `ProfileSealed.isValid(...)` mixed **two different concerns**:

1. **Semantic validity** — values within global hard limits (basal/IC/ISF/target/DIA). Pump-independent.
2. **Pump compatibility** — basal deliverable by the *currently active* pump.

Only the **basal block** is pump-dependent, via three checks:
- 30-min vs full-hour granularity (`is30minBasalRatesCapable`)
- basal below pump minimum (`basalMinimumRate`)
- basal above pump maximum (`basalMaximumRate`)

Everything else (DIA, IC, ISF, targets, and the basal *hard-limit* range `0.01..maxBasal`) is semantic.

**Decision (agreed):**
- Editing, local storage and Nightscout sync → gate on **semantic** validity only.
- Pump compatibility → enforced **only at activation** (applying the profile to the pump), plus a
  **non-blocking warning** while editing/viewing.

Rationale: a profile that's merely incompatible with the *current* pump (or after a pump switch) is
still valid data worth keeping and uploading. Conflating the two is what froze sync.

### Why NOT other options
- **Notification only (PR #4873):** symptom only; NS stays stale.
- **Upload valid profiles, skip invalid one:** the NS *download* path
  (`ProfileRepositoryImpl.loadFromStoreInternal`) does a full-list **replace** and already drops
  invalid profiles, so a partial upload that round-trips back can **delete the in-progress profile
  locally**. Rejected — data-loss risk.
- **Remove-on-editor-exit:** would delete legitimate profiles that are only *pump-contextually*
  invalid (e.g. valid on another pump / after a % change). Rejected — data-loss risk.

## 3. Phase 1 — what was implemented

### Core split
- `core/objects/.../profile/ProfileSealed.kt`
  - Extracted `validateSemantic(rh, hardLimits)` — pump-independent checks.
  - Extracted `validatePump(from, pump, config, rh, notificationManager, sendNotifications)` — the
    three basal/pump checks (still clamps + posts notifications, behaviour preserved).
  - `isValid(...)` now delegates: `semantic.isValid && pump.isValid` (activation behaviour unchanged).

### Repoint sync/storage to semantic only
- `implementation/.../profile/ProfileStoreObject.kt` — `allProfilesValid` now uses `validateSemantic`.
- `implementation/.../profile/ProfileRepositoryImpl.kt`
  - `loadFromStoreInternal` (NS import accept filter) → `validateSemantic`.
  - `validateStructured` de-pumped: basal floor `pumpDescription.basalMinimumRate` / `10.0`
    → `0.01` / `hardLimits.maxBasal()`.
  - Added `validatePumpCompatibility(profile, percentage)` → builds `ProfileSealed.Pure`, runs
    `validatePump`, returns a BASAL `ProfileValidationError` (clean message) or empty list.
- `core/interfaces/.../profile/ProfileRepository.kt` — declared `validatePumpCompatibility`.

### Third state — non-blocking pump warning (UI)
- Editor: `ProfileEditorViewModel` adds `pumpIncompatible` to UI state (does **not** feed `isValid`/Save).
  `ProfileEditorScreen` shows an amber message on the **basal** tab.
- Carousel: `ProfileManagementViewModel` adds `pumpWarnings: List<Boolean>`; `ProfileCarouselCard`
  shows a distinct **amber** "Pump limit" state (separate from red "INVALID"); `ProfileManagementScreen`
  passes it through.

### Activation dialog — pre-emptive block + snackbar change (user spec)
- `ProfileActivationScreen` takes `checkPumpCompatible: (percentage) -> Boolean`, computes it
  **live / %-aware**, shows a red banner and **disables the Activate button** when incompatible
  (so it can't be executed before OK). Lowering the % re-enables when deliverable.
- `AppNavGraph` wires `checkPumpCompatible = { pct -> profileManagementViewModel.isPumpCompatible(profileIndex, pct) }`.
- `ProfileManagementViewModel.isPumpCompatible(index, pct)` added.
- Removed the post-hoc `profile_activation_failed` snackbar (validation case is now prevented up
  front); kept a **distinct** `profile_switch_save_failed` for genuine DB-write failures.

### Strings (English only)
- `core/ui/.../res/values/strings.xml`: `profile_switch_save_failed`,
  `profile_basal_not_compatible_with_pump`, `profile_pump_incompatible_label`.
- Left now-unused `profile_activation_failed` in place to avoid translation churn.

### Tests / status
Added unit tests (tier 1–3 of the test plan):
- `ProfileSealedTest` (5/0): `semanticValidityIgnoresPumpBasalLimits`, `thirtyMinAlignmentIsPumpOnly`,
  `basalAbovePumpMaxIsPumpOnly`, `pumpCompatibilityIsPercentageAware` (+ existing `doTests`).
- `ProfileStoreTest` (8/0): `allProfilesValidIgnoresPumpCompatibilityTest` (pump-incompatible store is
  still valid for sync; semantically-invalid still rejected).
- `DataSyncSelectorV3Test` (48/0): `processChangedProfileStoreUploadsPumpIncompatibleStoreTest`
  (the #4872 fix — a pump-incompatible store now uploads).
- `ProfileEditorViewModelTest` (4/0, tier 4 — new `:ui` ViewModel harness): new draft saves via `add`
  not `replace`; an existing profile saves via `replace` not `add`; an invalid profile is never
  persisted (the save guard); an external profile-list change does not wipe an open draft.
- Regression green: `ProfileFunctionImplTest` 1/0. Modules compile: `:implementation`, `:ui`, `:app`.

Still NOT covered by tests (verified only by compile + pending device test):
- `validateStructured` de-pumping, `validatePumpCompatibility` (no `ProfileRepositoryImpl` harness).
- `ProfileManagementViewModel.observeNewProfileSelection` (scroll-to-new) and `resetProfile` draft path.
- New-profile valid seed (`addNewProfileInternal`).
- UI (Compose/Robolectric): editor amber warning, carousel amber state, activation dialog
  disable/banner, snackbar.

**NOT** committed; **NOT** runtime-verified on device.

### Known pre-existing nuance (out of scope)
`validateStructured` flags basal only if `.all { }` segments are out of range (lenient), while
`validateSemantic` is strict per-segment. They can disagree on mixed profiles. Pre-existing; not
introduced here.

## 4. Activation entry-point audit

**Safety conclusion:** the pump-limit gate is intact. `createProfileSwitch` still runs
`isValid` (= semantic AND pump) and refuses on failure; **every local path that applies a profile to
this device's pump funnels through it.** Relaxing the sync/storage gate did **not** open a hole at
activation.

**UX gap:** feedback on refusal is poor almost everywhere, and Phase 1 makes users *more* likely to
hit these paths (pump-incompatible profiles now persist/sync). This is pre-existing, not a regression.

Structural root cause: `createProfileSwitch` computes `ValidityCheck.reasons` then **discards** them
(returns bare `null`, `ProfileFunctionImpl.kt:184/209`). No caller can show the specific reason.

| Entry point | File:line | Feedback on refusal |
|---|---|---|
| Profile mgmt dialog (ours) | `ProfileActivationScreen` / `ProfileManagementViewModel.kt:432` | GOOD (pre-emptive block + DB-error msg) |
| Quick-launch | `MainViewModel.kt:770` | **SILENT** (result ignored) |
| Wear | `DataHandlerMobile.kt:2243` | **SILENT** (result ignored) |
| Automation switch | `ActionProfileSwitch.kt:73` | WEAK/misleading (comment always "Ok") |
| Automation % | `ActionProfileSwitchPercent.kt:54` | WEAK (log-only, comment "Ok") |
| SMS | `ProfileSwitchAction.kt:48` | OK-ish (generic "invalid profile") |
| Scenes | `SceneExecutor.kt:351-353` | GOOD-ish (generic "returned null") |
| Omnipod/Medtrum/Equil/Eopatch wizards | `*ViewModel.kt` (≈235/533/277/368) | WEAK (log-only; Medtrum shows name) |
| Autotune | `AutotunePlugin.kt:285`, `AutotuneViewModel.kt:321` | SILENT (success-only log / ignored) |

**NS-incoming profile switches** (`NsIncomingDataProcessor.kt:182-194`) bypass `createProfileSwitch`
and store `EPS`/`PS` directly. This is **by design** (mirroring remote history, gated behind
`NsClientAcceptProfileSwitch`, default off), pre-existing, and not where the pump guard belongs.
*Not* the "critical bypass" an automated sweep flagged it as.

## 5. Phase 2 (tier 2) — recommendation: propagate the refusal reason

Make `createProfileSwitch` return a result that carries the reason instead of bare `null`:

```kotlin
sealed interface ProfileSwitchResult {
    data class Success(val ps: PS) : ProfileSwitchResult
    data class PumpIncompatible(val reasons: List<String>) : ProfileSwitchResult
    data object SaveFailed : ProfileSwitchResult   // DB write failed
}
```

### Caller analysis — is the returned `PS?` used anywhere?
Only **one** production caller reads the `PS` value:
- `SceneExecutor.kt:352` → `recordId = ps?.id`.

All other callers null-check or ignore it (see table above). So `Success(ps)` covers the only real
dependency; every other migration is mechanical (`== null` → `!is Success`; the SILENT ones gain a
real message from `reasons`; automation drops its always-"Ok" lie).

`createProfileSwitchWithNewInsulin` already returns `Boolean` and is a separate surface (insulin/fill
dialogs, Omnipod Eros/Dash, etc.) — leave as-is initially, or convert in a follow-up.

### Scope
- 2 `ProfileFunctionImpl` overloads: distinguish *invalid* (return `PumpIncompatible(reasons)`) from
  *DB-write failure* (`SaveFailed`).
- ~13 call sites updated (most trivial).
- ~6 test stubs that `thenReturn(mock<PS>())` → `Success(mock())`.

### Minimum alternative (if tier 2 is deferred)
Just kill the two SILENT failures: add a snackbar/notification on refusal in **wear**
(`DataHandlerMobile`) and **quick-launch** (`MainViewModel`).

## 5b. Phase 1b — valid new-profile seed (implemented)

Decision (2026-06-08): a newly-added profile was seeded with **all zeros**
(`Constants.DEFAULT_PROFILE_ARRAY`, value `0`), which is *semantically* invalid and silently blocked
the whole profile-store sync (the literal #4872 repro: "add a profile, leave it incomplete, sync
stops"). Phase 1's split only fixed *pump-incompatible* profiles, **not** this structural case.

Fix: `ProfileRepositoryImpl.addNewProfileInternal()` now seeds **conservative, hard-limit-valid**
placeholders (option "Conservative / low-action"), unit-aware for ISF/targets:

| Field | mg/dl profile | mmol/L profile |
|---|---|---|
| basal | 0.1 U/h | 0.1 U/h |
| IC | 15 g/U | 15 g/U |
| ISF | 100 | 5.6 |
| target low / high | 110 / 120 | 6.1 / 6.7 |

All verified within hard limits (`LIMIT_MIN_BG [80,180]`, `LIMIT_MAX_BG [90,200]`, `MIN/MAX_ISF
[2,1000]`, `MIN/MAX_IC [2,100]`, basal ≥ 0.01). A freshly added profile is now valid → syncs
immediately (with placeholders until edited). Compiles; not yet runtime-verified.

Notes / residual:
- Only affects **newly created** profiles. Already-persisted all-zero profiles stay invalid and still
  block sync — covered later by the notification backstop and/or import validation.
- Consequence accepted: a half-configured "LocalProfile N" can appear in Nightscout until edited
  (far better than freezing all sync).
- `Constants.DEFAULT_PROFILE_ARRAY` is now unused; left in place.

## 5c. Phase 1c — "Add profile" creates an empty draft in the editor (implemented)

Decision (2026-06-08): the old "Add" button (`addNewProfile()` → `repo.addNew()`) **persisted a profile
immediately**, so even with valid placeholder seeds (Phase 1b) a half-configured "LocalProfile N" would
appear in Nightscout before the user filled it in. Cleaner: **never persist a user-created profile
until it is valid.**

New flow:
- "Add" navigates to the editor on a new route `ProfileEditorNew` with an **empty (zero) draft** —
  not persisted, every tab red, Save disabled until filled.
- The editor commits the draft to the store (via `repo.add`) only when the user saves a **valid**
  profile. Backing out without a valid save discards it; nothing enters the store.

Changes:
- `ProfileRepository.newDraft()` — factory for an empty, unpersisted `SingleProfile` (unique name).
- `ProfileEditorViewModel` — draft state (`isNewDraft`, `editingIndex = -1`): `saveProfile()` branches
  to `add` vs `replace`; `selectProfile`/`startNewProfileDraft` set the mode; the external-change
  subscriber **skips re-clone** while a draft is open (else the draft would be wiped); `resetProfile`
  resets a draft to fresh-empty; **`saveProfile` now refuses to persist an invalid profile** (closes a
  latent hole where the unsaved-changes dialog's Save could write invalid data).
- `ProfileEditorScreen` — unsaved-changes dialog Save is disabled when invalid (`saveEnabled`).
- `AppRoute.ProfileEditorNew` + `AppNavGraph` composable + `onAddProfile` wiring; `ProfileManagementScreen`
  gains `onAddProfile`; removed `ProfileManagementViewModel.addNewProfile()`.
- Removed the now-unused `ProfileRepository.addNew()` (interface + impl); `addNewProfileInternal` stays
  for the system auto-create invariant.
- `ProfileManagementViewModel.observeNewProfileSelection()` jumps the carousel to a freshly
  added/cloned profile (appended at the end) when the user returns from the editor.

Relationship to Phase 1b: the **valid seed in `addNewProfileInternal` is retained**, but now only serves
the **system auto-create** paths (fresh-install initial profile, "remove last profile recreates a
default") — those must be valid so first sync isn't blocked. User-initiated add no longer produces a
synced placeholder. Compiles (`:implementation`, `:ui`, `:app`); not yet runtime-verified.

## 6. Open follow-ups
- [ ] Commit Phase 1 + device runtime verification (carousel amber state, editor warning, activation
      block, NS sync now unblocked for pump-incompatible profiles).
- [ ] Decide & implement Phase 2 (sealed result) vs. minimum alternative.
- [x] New-profile seed made valid (Phase 1b) — kills the in-app "incomplete profile blocks sync" cause.
- [ ] Notification backstop (PR #4873 idea) for any residual structurally-invalid profile in the store
      (legacy all-zero profiles, corrupt imports) so a blocked sync is never silent.
- [ ] Optional: reject/flag *structurally* invalid profiles at NS import / restore.
- [ ] Optional: reconcile `validateStructured` `.all` leniency vs. `validateSemantic` per-segment.
- [ ] Optional: convert `createProfileSwitchWithNewInsulin` to the same result type.
