# Eternal Build — Exact Change Record

Companion to `SHTF_LOOP_RESILIENCE_PLAN.md`. This file records every code change made to produce
the "eternal" build (AAPS 3.4.2.6 base, commit `598e2eb3` = upstream release tag `3.4.2.6`), so a
future agent can re-apply the same changes after a version bump. All edits are marked in-source
with `SHTF eternal build` comments — `grep -rn "SHTF eternal build" --include=*.kt` finds them all.

## Goal

No code path may ever disable or degrade closed-loop operation because of:

1. **Version age / expiry** (stock: maxIOB forced to 0 after a stored expiry date passes), or
2. **Objectives (learning program) state** (stock: loop/closed-loop/SMB/autosens/automation vetoed
   until objectives are started; progress silently wiped if the device clock is behind stored
   timestamps — unrecoverable offline because restarting an objective requires an internet time check).

All other safety constraints (max IOB values, max bolus, pump limits, hard limits, BG quality,
signature revocation of *leaked* APKs) are deliberately untouched.

## Changed files

### 1. `plugins/constraints/src/main/kotlin/app/aaps/plugins/constraints/versionChecker/VersionCheckerPlugin.kt`

- `applyMaxIOBConstraints()` — stock code read the `AppExpiration` preference (keyed by version
  name) and forced `maxIob = 0.0` ("application expired") once the date passed. Now returns the
  constraint unchanged, always. The version checker itself still runs harmlessly (it can only
  produce a low-priority "new version available" notification).
- Unused `Config` and `DateUtil` constructor parameters and imports removed.

### 2. `plugins/constraints/src/main/kotlin/app/aaps/plugins/constraints/versionChecker/VersionCheckerUtilsImpl.kt`

- `triggerCheckVersion()` — stock code stored an expiry date for the running version, sourced from
  the bundled `app/src/main/assets/definition.json` **merged with Firebase Remote Config pushed
  from the network** (see `MainApp.setupRemoteConfig()`), then raised expiry warnings/urgent
  notifications. Now it only performs the informational new-version comparison and **writes 0 to
  the `AppExpiration` preference on every run**, so any expiry date persisted by an earlier run of
  a stock build can never resurface.
- `onExpireDateDetected()` and `shouldWarnAgain()` removed — no expiry notification can fire.

### 3. `plugins/constraints/src/main/kotlin/app/aaps/plugins/constraints/objectives/ObjectivesPlugin.kt`

- All six constraint vetoes are now no-ops returning the constraint unchanged:
  `isLoopInvocationAllowed`, `isLgsForced`, `isClosedLoopAllowed`, `isAutosensModeEnabled`,
  `isSMBModeEnabled`, `isAutomationEnabled`.
- `isAccomplished(index)` / `isStarted(index)` (the `Objectives` interface, consumed by the setup
  wizard) always return `true`, so the wizard's objectives screen never appears or blocks.

### 4. `plugins/constraints/src/main/kotlin/app/aaps/plugins/constraints/objectives/objectives/Objective.kt`

- **Clock landmine removed.** Stock `accomplishedOn` getter permanently zeroed an objective's
  progress whenever its stored timestamps were more than 3 hours in the future relative to the
  device clock. A cold-stored spare whose RTC resets (boots believing an old date) would wipe all
  objectives on first launch. The getter now simply returns the stored value.
- `isAccomplished` no longer requires `accomplishedOn < now`, so completed objectives display
  correctly even with a reset clock.

### Tests updated to match

- `plugins/constraints/src/test/kotlin/app/aaps/plugins/constraints/versionChecker/VersionCheckerPluginTest.kt`
- `plugins/constraints/src/test/kotlin/app/aaps/plugins/constraints/objectives/ObjectivesPluginTest.kt`
  (includes a regression test proving progress survives a device clock far in the past)
- `plugins/constraints/src/test/kotlin/app/aaps/plugins/constraints/ConstraintsCheckerImplTest.kt`
  (objectives no longer contribute veto reasons)

## Verified not to need changes

- `SignatureVerifierPlugin.kt` — only disables the loop for APKs signed with certificates on the
  downloaded revoked-certs list (anti-leak measure). Its own comments state self-compiled APKs
  with privately held certificates "cannot and will not be disabled." A self-signed build is safe
  as-is; offline, the revoked-list download simply fails harmlessly.
- Objectives progress, profiles, and all algorithm settings are restored by
  Maintenance → Import settings; `GeneralSetupWizardProcessed` is part of the export, so the
  onboarding wizard is skipped after import.

## Re-applying after a future version bump

1. Check out the new base version.
2. `grep -rn "AppExpiration\|onExpireDateDetected" --include=*.kt` and re-apply change 1–2 to
   whatever the constraint/checker look like then.
3. `grep -rn "objectivenotstarted\|isLgsForced" --include=*.kt` and re-apply change 3.
4. `grep -n "3 hours in the future" -r --include=*.kt` (or read the `accomplishedOn` getter) and
   re-apply change 4.
5. Re-run the constraints module tests and the Section-10 pre-storage checklist, including the
   clock-forward AND clock-backward tests.
