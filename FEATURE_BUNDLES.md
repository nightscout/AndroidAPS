# Feature: Scenarios (working name)

## Naming

### Recommended: **Scenarios**
- No conflicts with existing AAPS terminology
- Translates well across languages (cognate in Romance, Germanic, Slavic)
- Short for UI: "Scenarios", "New Scenario", "Active Scenario"
- Accessible to non-technical users of all ages
- Fits "Quick Scenarios" if we want Quick* family consistency (QuickWizard, QuickLaunch)

### Strong alternative: **Overrides**
- Loop iOS uses this for the exact same feature — APS ecosystem precedent
- Technically precise (temporarily overriding normal settings)
- Con: "override" sounds alarming in a medical safety context

### Rejected names
- **Modes** — conflicts with APS Mode (open/closed/LGS)
- **Activities** — conflicts with Activity plugin
- **Profiles** — conflicts with core Profile concept
- **Routines** — implies repeating habit, not situational response
- **Plans** — implies future planning, not immediate action
- **States** — too technical/developer-oriented

## Problem

When a user wants to handle a situation (e.g., exercise, illness, sleep), they must perform multiple
separate actions manually across different screens:

1. Set a Temp Target (TT)
2. Switch profile (e.g., reduced basal)
3. Disable/enable SMB
4. Create CarePortal entry (e.g., Exercise)
5. Optionally disconnect pump

This is tedious, error-prone (might forget a step), and slow — especially problematic under time
pressure or during a hypo.

## Concept

User-defined "Situation Presets" — bundles of pre-configured actions activated with one tap.

### Example Presets

| Preset | Actions |
|--------|---------|
| Sport | TT 140 mg/dL + Sport-80% profile + disable SMB + Exercise CP entry, 2h |
| Sick day | Lower TT + higher basal profile + aggressive SMB |
| Sleep | Specific TT + specific profile |
| Pre-meal | Low TT + no profile change |
| Disconnect | Suspend pump + set specific profile |

## Existing Infrastructure

The Automation plugin already has all action primitives:
- `ActionStartTempTarget` / `ActionStopTempTarget`
- `ActionProfileSwitch` / `ActionProfileSwitchPercent`
- `ActionSMBChange`
- `ActionCarePortalEvent`

`QuickLaunchAction.AutomationAction` already supports one-tap toolbar triggers.

**Why a dedicated feature is still warranted:** Automation UI is designed around trigger-condition
logic — too complex for the "I'm about to go to the gym" mental model. Bundles need to be
approachable in under 60 seconds.

---

## User-Initiated vs Background: The Key Distinction

Automations have limited features and strong constraints because they execute in the **background**
(triggered automatically by conditions). Bundles are fundamentally different — they are
**user-initiated** (manual tap with confirmation).

### What this changes

| Aspect | Automation (background) | Bundle (user-initiated) |
|--------|------------------------|------------------------|
| Execution trigger | Condition-based, polling every 150s | Explicit user tap + confirmation |
| User awareness | User may be asleep/unaware | User is present and consenting |
| Action scope | Conservative, limited | Can be more powerful |
| Precondition guards | Required (e.g., skip if TT active) | Not needed — user sees conflicts in dialog |
| Constraint gating | `isAutomationEnabled()` blocks all | Bypasses — not an automation |
| Loop suspended | Blocks execution | Warns but doesn't block |
| Revert/undo | No concept | Core feature (End bundle) |
| Duration on profile switch | Not supported (permanent only) | Supported (timed switch) |

### Constraints relaxed for bundles

- **Action precondition guards** — Automation skips TT if one is already active, skips profile
  switch if not at 100%. Bundles show the conflict in the confirmation dialog and let the user
  decide.
- **`isAutomationEnabled()` gate** — Bundles are not automations. If Objectives disables automation,
  bundles should still work (user is explicitly choosing).
- **Loop-suspended block** — Bundles warn ("Loop is suspended — actions will apply but loop won't
  act on them") but don't block.

### Constraints that remain for bundles

- **Physical safety limits** — TT value bounds, profile percentage bounds, max basal. These are
  hard physiological limits, not contextual.
- **Profile validation** — must exist at activation time.
- **No bolus actions** — never, regardless of initiation mode.
- **Pump suspend** — still requires extra acknowledgment.

### Actions bundles can unlock that Automations restrict

| Action | Automation restriction | Bundle capability |
|--------|----------------------|------------------|
| TT with active TT running | Silently skipped (precondition) | User explicitly replaces |
| Named profile switch with duration | Not supported (`durationInMinutes = 0` always) | Uses duration-aware call |
| Profile switch with active override | Silently skipped (precondition) | User explicitly replaces |
| SMB with restore on deactivation | No restore concept | Tracks prior state, restores on End |
| Pump suspend/disconnect | Not in Automation at all | Bundle-only with extra confirmation |

---

## Relationship with Automation User Actions

### Should bundles replace User Actions from Automations?

**Yes — bundles should eventually replace Automation "User Actions".**

Automation User Actions (`userAction = true`) are a workaround: they're automations without real
triggers, manually activated from the Overview screen. They go through the full `processActions()`
path, are gated by `isAutomationEnabled()`, have trigger evaluation overhead, and lack:
- Deactivation/revert capability
- Duration tracking and active state display
- Conflict detection
- Confirmation with resolved values
- Timed profile switches (named, not just percent)

**However**, Automation User Actions have conditions/triggers — bundles don't (and shouldn't).
This is correct: bundles are "do this NOW" while User Actions with conditions are "do this NOW
but only if BG > 150". The condition-based User Actions are a legitimate use case that bundles
don't cover.

### Migration path

1. **Phase 1:** Ship bundles as a separate feature alongside Automation User Actions
2. **Phase 2:** For User Actions WITHOUT conditions (trigger = manual only), show a migration
   prompt: "Convert to Situation Preset? You'll get deactivation, duration tracking, and conflict
   detection."
3. **Phase 3:** For User Actions WITH conditions, keep them in Automation — they are genuinely
   different (conditional manual execution)
4. **Long term:** Consider if conditional bundles make sense ("activate Sport mode, but only
   suggest it — don't auto-activate — if BG > 180"). This would be the `ActionSuggestBundle`
   automation action that posts a notification with a deep link to the bundle confirmation.

### The hybrid bridge: ActionSuggestBundle

An automation action that **suggests** (never activates) a bundle:
- Automation trigger fires (e.g., "BG > 200 for 30 min")
- Action posts a notification: "BG has been high. Activate Sick Day mode?"
- Notification tap opens bundle confirmation dialog (standard flow)
- The automation NEVER calls `bundleExecutor.activate()` directly

This gives conditioned bundles without merging the two systems.

---

## Design Decisions

### Creation UI: Guided Wizard

The creation experience should feel like a **conversation**, not a form. One question at a time,
friendly language, large tappable answer cards. Think Duolingo onboarding, not Automation builder.

**Two entry points:**

1. **"New from template"** — user picks a template (Sport, Sick Day, Sleep, Pre-meal), wizard opens
   with values pre-filled. User reviews/adjusts each step. This is the primary path — most users
   want a common scenario, not a blank slate.
2. **"New blank scenario"** — wizard starts empty, every step defaults to "No".

**Templates as the primary creation path:**

```
Create new scenario

  ┌──────────────────────────────────┐
  │  🏃 Exercise / Sport             │
  │  TT + profile + SMB off          │
  └──────────────────────────────────┘
  ┌──────────────────────────────────┐
  │  🤒 Sick Day                     │
  │  TT + profile adjustment         │
  └──────────────────────────────────┘
  ┌──────────────────────────────────┐
  │  🌙 Sleep                        │
  │  TT + profile                    │
  └──────────────────────────────────┘
  ┌──────────────────────────────────┐
  │  🍽️ Pre-meal                     │
  │  Low TT                          │
  └──────────────────────────────────┘
  ┌──────────────────────────────────┐
  │  ✨ Blank scenario               │
  │  Start from scratch              │
  └──────────────────────────────────┘
```

Picking a template opens the wizard with pre-filled answers the user MUST review and can adjust.
Values are shown as "suggested" (not final) so users don't blindly accept defaults.

**Wizard steps:**

```
Step 1: Name + Icon/Color
        "What would you like to call this scenario?"
        [text field, pre-filled from template]
        [icon badge — tap to change icon + color]

Step 2: Duration
        "How long should this scenario last?"
        [No time limit]  [30 min]  [1h]  [2h]  [Custom...]

Step 3: Temp Target
        "Do you want to set a Temp Target?"
        [No Temp Target]
        [Activity — 140 mg/dL]     ← values in user's preferred units
        [Hypo protection — 120 mg/dL]
        [Custom...]

Step 4: Profile
        "Do you want to change your profile?"
        [No profile change]
        [Adjust by percentage →]   → reveals slider 50%-150%
        [Switch to a profile →]    → reveals profile picker

Step 5: SMB
        "Do you want to change SMB behavior?"
        (brief explanation: "SMB controls whether the loop can deliver
         small extra doses automatically")
        [No change]  [Disable SMB]  [Enable SMB]

Step 6: CarePortal Event
        "Do you want to log an event?"
        [No event]  [Exercise]  [Announcement]  [Custom note...]

Step 7: End-of-scenario (ONLY shown if Step 2 had a duration)
        "When this scenario ends, would you like anything to happen?"
        [Nothing — just end it]
        [Send me a reminder]
        [Suggest another scenario →]  → reveals scenario picker

Step 8: Summary + Save
        Shows all selected options. Each row tappable to jump back to
        that step. [Save] button.
```

**Wizard UX principles:**
- Large tappable answer cards (min 56dp height), not radio buttons
- "No" / "No change" is always first option and default
- Auto-advance on simple selections (no extra "Continue" tap needed)
- Swipe left/right to navigate between steps
- Thin progress bar at top (no step numbers — reduces anxiety)
- Questions in large typography (titleLarge) — feels like a conversation
- Wizard state persists if user backgrounds the app (BG alert interrupts)
- Adaptive: steps skipped when irrelevant (no duration → no end-of-scenario step)
- All values shown in user's preferred units (mg/dL or mmol/L)

**Editing existing scenarios:**

Never re-run the wizard. The Summary view (Step 8) becomes the persistent edit screen:

```
┌────────────────────────────────┐
│ [🏃 green] Sport Mode          │  (tap badge to change icon/color)
│ Duration: 2 hours              │  [Edit]
├────────────────────────────────┤
│ Temp Target: 140 mg/dL         │  [Edit]
│ Profile: Sport-80%             │  [Edit]
│ SMB: Disabled                  │  [Edit]
│ CarePortal: Exercise           │  [Edit]
│ On end: Remind me              │  [Edit]
└────────────────────────────────┘

[Activate Now]    [Delete Scenario]
```

Each row expands inline to show options — this is the checklist UI, but reached via
the wizard initially. Power users can also use "New blank scenario" → immediately skip
to this editor view via a "Skip to full editor" link at the bottom of the wizard.

### Activation Location

**Primary: QuickLaunch toolbar** — already supports custom actions via `QuickLaunchAction`. New
sealed class variant:

```kotlin
data class BundleAction(val bundleId: String) : QuickLaunchAction() {
    override val typeId = "bundle"
    override val dynamicId = bundleId
}
```

**Secondary: Dedicated "Situations" card on Overview** — for users who want bundles visible
without configuring the toolbar.

**NOT:** FAB (competes with BG display), notifications (too casual for medical actions).

### Confirmation Dialog (Always Required)

```
Activate "Sport Mode"?

This will:
  - Set Temp Target to 140 mg/dL for 2 hours
  - Switch profile to Sport-80% for 2 hours
  - Disable SMB for 2 hours
  - Log Exercise careportal entry

        [Cancel]  [Activate]
```

- Show **resolved values** (actual numbers, profile names, durations)
- Duration adjustable at confirmation time ("today's run is only 45 min")
- Show conflicts if another bundle/TT/profile is active
- Both buttons equal visual weight (no dominant "Activate" button)

### Active Bundle Status

- Persistent banner on Overview showing bundle name + time remaining
- `LinearProgressIndicator` draining over duration
- Single "End [Sport Mode]" button
- Running bundle buttons in toolbar show animated ring/border

### Deactivation

"End" button shows confirmation with what will be reversed:

```
End "Sport Mode"?  (47 minutes remaining)

This will:
  - Cancel Temp Target (currently 140 mg/dL)
  - Revert profile to [previous profile name]
  - Re-enable SMB

        [Keep active]  [End now]
```

- **Never auto-deactivate silently** — prompt when timer expires
- CarePortal entries cannot be reversed (note this in dialog)
- Store previous state at activation time for clean reversal

### Conflict Handling

Detect at activation time, surface in confirmation dialog:

- **Active TT:** "Note: Active Temp Target (120 mg/dL, 35 min remaining) will be replaced."
- **Active profile:** "Currently running: Sick-Day-Profile (45 min remaining). Will be replaced."
- **Active bundle:** Prominent warning: "'Sick Day' is currently active (2h 15m remaining).
  Activating 'Sport Mode' will replace it."

### Templates

When creating a new scenario, the user picks a template to pre-fill the wizard, or starts blank.

**Fixed scenarios (non-deletable, created at install/migration) double as base templates:**
- Eating Soon, Activity, Hypo Protection — start as single-action TT scenarios
- Users are encouraged to enhance them (add profile, SMB, etc.)

**Additional templates for "New scenario" wizard:**

| Template | Pre-filled values |
|----------|------------------|
| Exercise / Sport | Duration: 2h, TT: Activity, Profile: -20%, SMB: off, CP: Exercise |
| Sick Day | Duration: no limit, TT: low, Profile: +20%, SMB: on |
| Sleep | Duration: 8h, TT: moderate, Profile: no change |
| Blank | Everything empty, user builds from scratch |

All template values are suggestions the user reviews and adjusts in the wizard.

No "Disconnect pump" template — too much pump-specific safety risk for a default.

---

## Safety Guardrails

### Non-negotiable

- **Always confirm** with full resolved action list before activation
- **No bolus actions** in bundles — enforced at architecture level
- **Pump disconnect/suspend** requires extra acknowledgment checkbox in confirmation
- **Validate all references** at activation time (profile might have been deleted)
- **Partial failure feedback** — show which actions succeeded/failed after activation
- **Full audit trail** — log all activations/deactivations to User Entry system

### Validation

- **At save time:** Every reference (profile name, TT preset) validated against current state
- **At activation time:** Re-validate before showing confirmation
- Broken bundles show error state in config list and are disabled in toolbar

### SMB Handling

SMB toggle is a preference change, not a temporary override. Bundle deactivation must explicitly
re-enable SMB if the bundle disabled it. Must track that the bundle (not some other mechanism)
disabled it.

---

## Architecture

### Separate system, shared execution vocabulary

Bundles should NOT extend or reuse Automation's `Action` class hierarchy because:
- `Action` classes use old `HasAndroidInjector` pattern (migrating away from this)
- `Action.generateDialog()` is View-based (we're in Compose)
- `Action.instantiate()` is a closed registry
- Automation's `processActions()` polling loop doesn't apply

Instead: extract execution logic into shared interfaces in `core/interfaces`.

### Module structure

```
core/interfaces/
  bundles/
    BundleAction.kt          -- interface: summary(), execute(), revert(), isValid()
    BundleActionResult.kt    -- sealed: Success / Failure
    BundleActionType.kt      -- enum: TEMP_TARGET, PROFILE_SWITCH, PROFILE_PERCENT,
                                      SMB, PUMP_SUSPEND, CAREPORTAL
    BundleDefinition.kt      -- data class: id, name, icon, color, actions, defaultDuration
    ActiveBundleState.kt     -- data class: bundleId, activationTime, duration, priorSnapshot
    BundleRepository.kt      -- interface: getAll(), getById(), getActive(), save(),
                                           activate(), deactivate()

plugins/bundles/
  actions/
    BundleTempTargetAction.kt        -- calls persistenceLayer directly
    BundleNamedProfileSwitchAction.kt -- WITH duration (unlike Automation's Action)
    BundleProfilePercentAction.kt
    BundleSmbAction.kt               -- captures prior state, restores on revert()
    BundlePumpSuspendAction.kt       -- requires extra confirmation flag
    BundleCarePortalAction.kt        -- no revert (noted in deactivation dialog)
  BundleExecutor.kt                  -- execute list, collect results, log to UserEntry
  BundleRepositoryImpl.kt

plugins/automation/
  actions/
    ActionSuggestBundle.kt           -- posts notification with deep link (suggest only)

ui/
  compose/quickLaunch/
    QuickLaunchAction.kt             -- add: BundleAction variant
```

### Data storage

- **Bundle definitions:** JSON in preferences (same pattern as TT presets, Automation events)
- **Active bundle state:** Room DB (needs to survive restarts, observable via Flow for
  Overview banner)

### BundleExecutor flow

1. Re-validate all `BundleAction.isValid()`
2. Surface validation failures in confirmation dialog
3. For each action in order: call `action.execute()`, collect `BundleActionResult`
4. After all: log single `UserEntry` with all results
5. Store `ActiveBundleState` to Room
6. Return outcome to ViewModel for UI feedback
7. Partial failures shown explicitly — never silent

---

---

## Reference: Loop iOS Overrides

Loop (iOS DIY APS) has an "Overrides" feature that is the closest analog to what we're designing.
Worth studying as prior art.

### What Loop Overrides adjust
- **Correction target range** — replaces scheduled range (like our TT)
- **Overall insulin needs %** — adjusts basal rates, ISF, AND carb ratios proportionally
  (more powerful than our profile % switch — it affects ALL insulin, including bolus calculations)
- **Duration** — 15-minute increments or indefinite

### Key differences from our Scenario concept
| Aspect | Loop Overrides | AAPS Scenarios (proposed) |
|--------|---------------|--------------------------|
| Parameters | Target + insulin needs % (2 params) | TT + profile + SMB + CP + pump (5+ actions) |
| Granularity | Single % affects basal+ISF+CR together | Can adjust profile (basal) independently from other settings |
| Presets | Named with emoji, reusable | Named with icon+color, wizard-created |
| Custom one-time | Yes (no save) | Via manual TT dialog (stays separate) |
| Future scheduling | Yes (calendar icon, start time) | Not in v1 (potential v2 via Automation bridge) |
| Edit while active | Yes (tap to change duration/%) | Yes (duration adjustable at confirmation) |
| Deactivation | Tap heart icon, no confirmation | Confirmation dialog showing what will revert |
| Auto-deactivate | Yes, silently on expiry | Prompt on expiry (medical safety choice) |
| Watch support | Yes (toggle on/off) | TODO |
| Remote control | Yes (via Nightscout push) | TODO |

### What we can learn from Loop
1. **Emoji for identity** — simple, effective, instantly recognizable. We use Material icons which
   is fine but consider allowing emoji too.
2. **Press-and-hold slider for fine adjustment** (1% increments vs 10% default) — good interaction
   pattern for percentage adjustments.
3. **Future scheduling** — valuable feature (e.g., set Sport override to activate at 5 PM before
   evening practice). Could be v2 via `ActionSuggestBundle` or a scheduled `WorkManager` job.
4. **Visual indicator on chart** — when override is active, the glucose chart shows a darker blue
   bar for the target range + duration. We should show something similar on the Overview graph.
5. **No confirmation on deactivation** — Loop just turns it off on tap. We chose to confirm because
   reverting SMB/profile has bigger implications in AAPS. This is a deliberate safety difference.
6. **"Overall insulin needs" is simpler but less flexible** — one slider controls everything.
   Our approach of separate profile + SMB + TT gives more control but requires more setup.
   The wizard mitigates this.

Sources:
- [Overrides - LoopDocs](https://loopkit.github.io/loopdocs/operation/features/overrides/)
- [Override Targets - LoopTips](https://loopkit.github.io/looptips/how-to/overrides/)

---

## TT Presets and Scenarios: Unification

### The overlap problem

TT presets (Activity, Hypo, Eating Soon) are essentially single-action scenarios. Having both
TT presets AND scenario templates creates user confusion: "Do I set a TT or activate a scenario?"

### Decision: TT presets merge into Scenarios

**The 3 built-in TT presets become 3 fixed (non-deletable) Scenarios:**

| Old TT Preset | New Fixed Scenario | Default actions |
|---------------|--------------------|-----------------|
| Eating Soon | Eating Soon | TT low (e.g., 80/4.4), 1h |
| Activity | Activity | TT high (e.g., 140/7.8), 2h |
| Hypo | Hypo Protection | TT medium (e.g., 120/6.7), 30min |

These fixed scenarios:
- **Cannot be deleted** (like current built-in TT presets)
- **Can be customized** — user can edit values, add more actions (profile, SMB, etc.)
- **Start as single-action** (TT only) but user can enhance them via the wizard/editor
- **Serve as examples** — show users what scenarios look like before they create their own

**Custom TT presets** (user-created) also migrate to scenarios at upgrade time.

**Manual TT dialog stays** — for ad-hoc custom TT values not worth saving as a scenario.
Accessible via QuickLaunch static action or Overview.

**TT preset UI removed** — no separate TT preset management. Everything is in Scenarios.

**Migration:**
- One-shot migration on first launch after upgrade
- Reads `StringNonKey.TempTargetPresets`, creates scenarios, migrates QuickLaunch entries
- One-time prompt: "Your TT presets have been converted to Scenarios. Tap to review."
- If migration fails, TT presets remain — no worse off

### Adjustable values at activation time

The scenario confirmation dialog should support adjusting a **subset** of values before activation
(like TT presets do today):

| Parameter | Adjustable at activation? | Reason |
|-----------|--------------------------|--------|
| Duration | Yes | Most commonly varied ("today's run is shorter") |
| TT value | Yes | Physiology varies day to day |
| Profile % | No | Carefully set at design time |
| SMB toggle | No | Deliberate safety choice |
| CarePortal | No | Just a log entry |

Rule: **adjust parameters, not structure**. At activation time you can tune numeric values.
You cannot add/remove actions — that requires the editor.

---

---

## Available Actions Catalog

### v1 — Core actions

| Action | Reuse from Automation? | Revert on End? | Notes |
|--------|----------------------|----------------|-------|
| Temp Target | ActionStartTempTarget (logic only) | Cancel TT | Core action |
| Profile Switch (named) | ActionProfileSwitch (add duration) | Revert to previous | Duration not in current Action |
| Profile % | ActionProfileSwitchPercent (logic) | Revert to 100% | |
| SMB toggle | ActionSMBChange (logic) | Restore prior state | Must track who disabled it |
| CarePortal event | ActionCarePortalEvent (logic) | No revert | Logging only |
| Pump suspend/disconnect | New | Reconnect on End | Extra confirmation required |
| Loop mode change | New | Restore prior mode | Open Loop, LGS, Closed Loop |
| Notification | ActionNotification (logic) | N/A | "Remember to check BG" |

### v1 — Algorithm/preference overrides (beyond SMB toggle)

Beyond the simple SMB on/off, there are many algorithm settings users would want to temporarily
change. Grouped by how commonly they'd be used in scenarios:

**Tier 1 — Very commonly needed:**

| Setting | Key | What it does | Scenario example |
|---------|-----|-------------|-----------------|
| SMB on/off | `ApsUseSmb` | Enable/disable micro boluses | Exercise: off |
| UAM on/off | `ApsUseUam` | Unannounced Meal detection | Exercise: off (prevents false meal detection from rising BG) |
| Max IOB | `ApsSmbMaxIob` | Ceiling for insulin on board | Night: lower. Sick day: higher |
| LGS threshold | `ApsLgsThreshold` | BG level for Low Glucose Suspend | Exercise: raise. Alcohol: raise |
| DynISF adjustment factor | `ApsDynIsfAdjustmentFactor` | How aggressive DynISF is (%) | Exercise: lower. Sick: higher |

**Tier 2 — Useful for specific scenarios:**

| Setting | Key | What it does | Scenario example |
|---------|-----|-------------|-----------------|
| SMB with high TT | `ApsUseSmbWithHighTt` | Allow SMB when TT is high | Some users want SMB even during exercise TT |
| SMB always | `ApsUseSmbAlways` | SMB regardless of conditions | Restrict to conditional only during sleep |
| Max SMB frequency | `ApsMaxSmbFrequency` | Min minutes between SMBs | Reduce aggressiveness at night |
| Autosens on/off | `ApsUseAutosens` | Sensitivity auto-detection | Disable during erratic situations |
| Sensitivity raises target | `ApsSensitivityRaisesTarget` | Auto-raise target when sensitive | Disable during exercise (already have high TT) |
| Resistance lowers target | `ApsResistanceLowersTarget` | Auto-lower target when resistant | Disable during sick day |
| Max basal | `ApsMaxBasal` | Maximum basal rate | Lower during exercise. Higher during illness |

**Tier 3 — Advanced / power users only:**

| Setting | Key | What it does |
|---------|-----|-------------|
| DynISF on/off | `ApsUseDynamicSensitivity` | Switch algorithm entirely |
| Autosens min/max | `AutosensMin` / `AutosensMax` | Bounds on sensitivity adjustment |
| AutoISF weights | Various `ApsAutoIsf*` keys | Fine-tune ISF calculation |
| SMB delivery ratio | `ApsAutoIsfSmbDeliveryRatio` | How much of needed insulin given as SMB |
| Carb absorption rate | `ApsSmbMin5MinCarbsImpact` | Min carb impact per 5 min |

**Note:** Profile switch with % already adjusts basal + ISF + IC together (like Loop's "overall
insulin needs"). For most users, Profile % + the Tier 1 settings cover 90% of use cases.
Tier 2-3 settings are for power users who want fine-grained control.

**Safety constraint:** Max safety limits (max bolus, max carbs) should NOT be overridable by
scenarios — these are hard physiological limits.

**Implementation:** Each setting override in a scenario stores the key + new value + original value
(for revert on End). The wizard exposes Tier 1 as named toggles/sliders. Tier 2-3 available
via an "Advanced settings" expandable section in the editor (not wizard).

### v1 — End-of-scenario actions

| Action | Notes |
|--------|-------|
| Notification/reminder | "Sport mode ended — check your BG" |
| Alarm (audible) | Audible alert when scenario expires |
| Suggest follow-up scenario | Sport → Recovery, Meal → Post-meal |

These are configured in wizard Step 7 ("When this scenario ends...").

### v2 — Extended actions

| Action | Notes |
|--------|-------|
| Send SMS to caregiver | Notify parent/partner ("Starting exercise" / "Exercise ended") |
| Auto-bolus toggle | Independent from SMB toggle |
| Carb suggestion (display only) | "Consider eating 15g before starting" — no actual carb entry |
| Recovery TT at end | Automatically set a different TT when scenario ends |

### Deliberately excluded (safety)

| Action | Reason |
|--------|--------|
| Bolus delivery | Requires full wizard with IOB/BG checks |
| Carb entry | Affects loop immediately, can't revert |
| Change safety limits | Max basal, max IOB must stay as hard constraints |

### Reuse strategy from Automation

The execution logic (calls to `persistenceLayer`, `profileFunction`, `preferences`) is reusable.
The class hierarchy is NOT — Automation Actions use old `HasAndroidInjector`, View-based dialogs,
and precondition Triggers. Scenario actions should be new implementations calling the same
underlying interfaces.

**Reusable patterns from Automation:**
- Callback-based async execution pattern
- JSON serialization approach
- Time-based triggers (`TriggerTime`) for end-of-scenario scheduling
- Element UI system (input validators) — though we'll build Compose equivalents

**Not reusable:**
- `Action` base class (old DI pattern)
- `generateDialog()` (View-based, not Compose)
- `processActions()` polling loop (scenarios execute once, not polling)
- Precondition system (scenarios show conflicts in dialog instead)
- `AutomationEventObject` data model (wrong shape — trigger-centric)

---

---

## Use Case Catalog (27 real-life scenarios identified)

### Key insight: Notifications are the hidden killer feature

Many scenarios derive most clinical value from **timed reminders**, not just settings changes.
"Check BG after 2h", "Delayed hypo risk at 4h", "Remember to reconnect pump" — these are the
steps people forget. The wizard must make notifications a first-class action, not an afterthought.

### Exercise (5 variants)
| Scenario | Key differentiator |
|----------|--------------------|
| Cardio | LGS mode + profile 60% + end notification |
| Strength Training | Less reduction than cardio (80%), delayed hypo warning 2-4h |
| Swimming | Pump disconnect + **reconnect reminder** |
| Competition / Race Day | **Open Loop** (adrenaline makes predictions unreliable) |
| Post-Exercise Recovery | Delayed notifications at 2h + 4h, SMB back on |

### Work / School (3)
| Scenario | Key differentiator |
|----------|--------------------|
| Sedentary / Desk Day | **Increased** basal (110-115%) — less activity = more insulin |
| Stress / Exam | Increased basal + **post-stress drop** reminder |
| Night Shift | Circadian reversal profile |

### Travel (3)
| Scenario | Key differentiator |
|----------|--------------------|
| Long-Haul Flight | Reduced basal + **timezone review** reminder at end |
| Jet Lag (Eastward) | Conservative mode for 24h |
| Driving | Safe TT + **2h drive-check** notification (legal requirement in some countries) |

### Social (3)
| Scenario | Key differentiator |
|----------|--------------------|
| Alcohol | **LGS only** + overnight hypo notifications — one of the most dangerous T1 situations |
| Restaurant / Extended Meal | 2h post-meal check notification |
| Pizza / High-Fat Meal | Extended basal increase 4-6h + notifications at 3h and 5h |

### Medical (4)
| Scenario | Key differentiator |
|----------|--------------------|
| Medical Fasting | LGS + **"inform staff about pump"** reminder |
| Post-Surgery | Open loop + high TT, 12-48h |
| Post-Vaccination | 48h immune response window |
| Sick Day — Vomiting | **Opposite of standard sick day** — reduced basal, LGS (can't absorb food) |

### Hormonal / Demographic (3)
| Scenario | Key differentiator |
|----------|--------------------|
| Pre-Period (Luteal Phase) | Increased basal 120-130%, 3-5 days |
| During Period | Return toward normal, hypo risk |
| Growth Spurt (Teen) | 130-150% basal, 48-72h with reassess notification |

### Environmental (2)
| Scenario | Key differentiator |
|----------|--------------------|
| Hot Weather | **Reduced** basal (heat accelerates absorption) |
| Cold / Skiing | Site failure reminder + slow absorption |

### Caregiver / Child (3)
| Scenario | Key differentiator |
|----------|--------------------|
| School Day | Conservative + **scheduled parent notifications** at lunch/end |
| PE / Sports Class | Parent notification at start + end |
| Sleepover | LGS only + **midnight parent check-in** notifications |

### Meta (1)
| Scenario | Key differentiator |
|----------|--------------------|
| End Scenario / Reset | **Cancel everything**, return to defaults — the universal escape hatch |

### Which become templates?

Not all 27 should be shipped as templates — that's overwhelming. Recommended v1 templates:

**Fixed (non-deletable, from TT migration):**
- Eating Soon, Activity, Hypo Protection

**Wizard templates (new scenario creation):**
- Exercise / Sport (covers most exercise needs, user customizes)
- Sick Day
- Sleep
- Alcohol (unique and dangerous enough to warrant guidance)
- Driving (safety/legal implications)
- Blank

The rest are documented as **examples in help/onboarding** so users know what's possible and
can build their own. The catalog above serves as inspiration, not a template library.

### Design implications from the catalog

1. **Notification scheduling is v1** — not optional. Too many scenarios depend on it.
2. **Loop mode change is v1** — LGS, Open Loop are critical for alcohol, competition, fasting.
3. **"End Scenario / Reset"** should be a built-in fixed scenario (non-deletable) — the escape hatch.
4. **Duration ranges are huge** (30 min to 5 days) — the wizard must handle short and multi-day.
5. **Follow-up scenarios are common** — Sport → Recovery, Pre-meal → Post-meal. The "suggest
   another scenario at end" feature earns its place.
6. **Caregiver notifications** (SMS to parent) could be v1 if `ActionSendSMS` is reusable.
7. **Sick Day needs TWO variants** — standard (increase insulin) vs vomiting (decrease insulin).
   Templates should offer both.

---

## HCP Clinical Guidelines for Template Defaults

Evidence-based default values for scenario templates, sourced from published clinical guidelines.
These serve as **starting points** — the wizard explicitly labels them as "suggested" since
individual responses vary significantly.

### Exercise / Sport

**Sources:** EASD/ISPAD 2024 Position Statement on AID and Exercise; ADA Standards of Care 2024-2025;
ISPAD 2022 Exercise Guidelines; DiaTribe AID Exercise Guide.

| Parameter | Guideline value | Template default | Notes |
|-----------|----------------|-----------------|-------|
| Pre-exercise BG target | 125-180 mg/dL (7.0-10.0 mmol/L) | TT: 140 mg/dL (7.8 mmol/L) | EASD/ISPAD: start exercise ≥7.0 mmol/L |
| AID exercise target | 150 mg/dL (8.3 mmol/L) | TT: 140 mg/dL (7.8 mmol/L) | 670G uses 150, Control-IQ uses 140-160 |
| Basal reduction (aerobic) | 50-80% reduction | Profile: 50% (-50%) | ISPAD: 50-80% reduction activated 90 min before exercise reduces hypo risk |
| Basal reduction (strength) | 20-50% reduction | Profile: 80% (-20%) | Less reduction needed than cardio |
| Timing | Set 60-90 min before exercise | Duration: 2h (covers pre+during) | Earlier activation = better protection |
| SMB | Disable during exercise | SMB: off | Omnipod 5 activity mode reduces delivery ~50% and disables auto-bolus |
| Carb threshold | Eat 15g fast carbs if BG <130 mg/dL before exercise | (Notification suggestion) | Cannot auto-enter carbs (safety) |

**Duration guidance:** Aerobic: 1-2h typical. Strength: 45min-1.5h. Competition: full event + 1h.
Post-exercise hypo risk persists 6-12h (nocturnal hypo risk after afternoon exercise).

### Sick Day

**Sources:** ADA Standards of Care 2024; Children with Diabetes 2025 Sick Day Guidelines;
Breakthrough T1D Sick Day Resources; ADA Sick Day Guide.

| Parameter | Guideline value | Template default | Notes |
|-----------|----------------|-----------------|-------|
| Insulin adjustment (standard illness) | **Never stop basal insulin** | Profile: 100% (no change) | Body produces more glucose when sick — may need MORE insulin |
| Insulin adjustment (vomiting/unable to eat) | Reduce to 50-70% | Profile: 60% (-40%) | Separate "Sick Day — Vomiting" template |
| TT | No specific guideline | No TT change | Focus is on frequent monitoring, not target change |
| SMB | Keep enabled | SMB: on | May need more aggressive correction |
| Ketone correction bolus | Add 5% TDD when ketones 1.5-2.9 mmol/L | (Notification: check ketones) | Cannot auto-bolus (safety) |
| Monitoring | Check BG every 2-4h, ketones every 4-6h | Notifications at 2h intervals | Key value of scenario is reminder scheduling |
| Seek help threshold | Vomiting >3x/24h, fever >101°F/24h, ketones >2.9 | (Info in confirmation dialog) | |

**Two templates needed:**
1. "Sick Day — Standard" (fever, infection): Keep or increase insulin, frequent monitoring
2. "Sick Day — Vomiting" (GI illness): Reduce basal 40%, LGS mode, aggressive monitoring

### Alcohol

**Sources:** Breakthrough T1D Alcohol Resources; ADA 2025 Standards (Section 6);
Leeds Teaching Hospitals NHS; Type1Support.ca.

| Parameter | Guideline value | Template default | Notes |
|-----------|----------------|-----------------|-------|
| Basal reduction | 10-20% reduction for next dose | Profile: 80% (-20%) | Liver busy processing alcohol, stops releasing glucose |
| Loop mode | LGS recommended | Loop mode: LGS | Prevents insulin stacking while liver is impaired |
| Duration | Risk persists up to 24h | Duration: 12h (overnight) | Minimum: covers sleep after drinking |
| TT | Higher target overnight | TT: 120 mg/dL (6.7 mmol/L) | Prevents loop from aggressively correcting |
| SMB | Disable | SMB: off | Prevents insulin stacking |
| Notifications | Critical | 2h + 4h + morning reminders | "Check BG before bed", "Set alarm for 3 AM check" |
| Pre-drinking BG | ≥5.0 mmol/L (90 mg/dL) before drinking | (Info in confirmation dialog) | Never drink with low BG |

**One of the most dangerous T1D situations.** Template should include prominent safety warnings.
Delayed hypo can occur 6-24h after drinking.

### Driving

**Sources:** DVLA (UK) Guidelines; Pediatric Endocrine Society.

| Parameter | Guideline value | Template default | Notes |
|-----------|----------------|-----------------|-------|
| Minimum BG to drive | ≥5.0 mmol/L (90 mg/dL) | TT: 108 mg/dL (6.0 mmol/L) | DVLA: do NOT drive if ≤4.0 mmol/L |
| Check interval | Every 2h on long drives | Notification every 2h | Legal requirement in some countries |
| Recovery after hypo | Wait 45 min after BG returns to ≥5.0 | (Info in confirmation dialog) | Brain function needs recovery time |
| SMB | No change needed | No change | Loop can help maintain stable BG |

**Key value:** The 2-hour reminder notification is the main feature here. Many drivers forget to check.

### Sleep / Overnight

**Sources:** ADA 2024-2025 Standards; general clinical practice.

| Parameter | Guideline value | Template default | Notes |
|-----------|----------------|-----------------|-------|
| TT | Individualized | TT: 100 mg/dL (5.6 mmol/L) | Tighter control overnight if tolerated |
| Duration | 8h typical | Duration: 8h | Adjustable at activation |
| Profile | No universal guideline | No change | Dawn phenomenon users may need separate profile |
| LGS threshold | Consider raising slightly | (Advanced: raise LGS threshold) | Extra hypo protection during sleep |

### Pre-Meal (Eating Soon)

**Sources:** General clinical practice; existing AAPS TT preset values.

| Parameter | Guideline value | Template default | Notes |
|-----------|----------------|-----------------|-------|
| TT | Lower to increase pre-meal insulin | TT: 80 mg/dL (4.4 mmol/L) | Existing AAPS "Eating Soon" preset |
| Duration | 30-60 min before meal | Duration: 45 min | Time for insulin to start working |
| SMB | Keep enabled or enable | SMB: on | Want aggressive pre-meal dosing |

### Menstrual Cycle (Luteal Phase)

**Sources:** Type1Support.ca; MDPI Systematic Review 2023; Type 1 Diabetes Exercise Initiative Study.

| Parameter | Guideline value | Template default | Notes |
|-----------|----------------|-----------------|-------|
| Basal increase | ~3% average increase (highly individual, some 10-30%) | Profile: 115% (+15%) | Start conservative, user adjusts |
| Duration | 3-7 days (luteal phase) | Duration: 5 days | Highly variable between cycles |
| TT | No specific guideline | No change | |

**Important caveat:** Individual variation is enormous. Some women see 30%+ increase in insulin needs,
others see minimal change. The template should emphasize this is a starting point.

### Hot Weather

**Sources:** CDC Managing Diabetes in Heat; Beyond Type 1; Cleveland Clinic.

| Parameter | Guideline value | Template default | Notes |
|-----------|----------------|-----------------|-------|
| Basal reduction | Reduce (heat accelerates absorption 3-5x) | Profile: 85% (-15%) | Blood vessel dilation speeds insulin absorption |
| Monitoring | Increase frequency | Notification every 2h | |
| Duration | While heat persists | Duration: 8h (daytime) | |

**No specific percentage in guidelines** — recommendations are to monitor more frequently and be
prepared to reduce insulin. 15% reduction is a conservative starting point.

### Perioperative / Medical Procedure

**Sources:** ADA Standards of Care 2024 (Section 16); BJA Education 2024; StatPearls.

| Parameter | Guideline value | Template default | Notes |
|-----------|----------------|-----------------|-------|
| Target BG | 100-180 mg/dL (5.6-10.0 mmol/L) | TT: 150 mg/dL (8.3 mmol/L) | ADA perioperative range |
| Loop mode | Open loop or LGS | Loop mode: Open Loop | Anesthesia/procedure makes predictions unreliable |
| Duration | Procedure + recovery | Duration: no limit | End manually when stable |
| Notifications | "Inform staff about pump" | Pre-procedure reminder | Critical safety step |

**Note:** For procedures >3h, guidelines recommend switching to IV insulin — the scenario should
include a warning about this.

### Summary of Key Numbers

| Situation | Profile % | TT mg/dL | SMB | Loop Mode | Key Notifications |
|-----------|-----------|----------|-----|-----------|-------------------|
| Cardio exercise | 50% | 140 | Off | Closed | Post-exercise hypo check |
| Strength training | 80% | 140 | Off | Closed | 2-4h delayed hypo warning |
| Swimming | 50% | 140 | Off | -- | Reconnect pump reminder |
| Competition | 100% | 160 | Off | Open | -- |
| Sick day (standard) | 100-120% | -- | On | Closed | Check ketones every 4h |
| Sick day (vomiting) | 60% | -- | Off | LGS | Check ketones every 2h |
| Alcohol | 80% | 120 | Off | LGS | BG check before bed, 3 AM, morning |
| Driving | 100% | 108 | -- | Closed | Every 2h drive check |
| Sleep | 100% | 100 | -- | Closed | -- |
| Eating Soon | 100% | 80 | On | Closed | -- |
| Luteal phase | 115% | -- | -- | Closed | Reassess in 3 days |
| Hot weather | 85% | -- | -- | Closed | BG check every 2h |
| Medical procedure | 100% | 150 | Off | Open | Inform staff about pump |

**Disclaimer for all templates:** Values are derived from published clinical guidelines and serve as
starting points only. Individual insulin sensitivity, fitness level, alcohol tolerance, and other
factors vary widely. Users should consult their healthcare team and adjust based on personal
experience. The wizard explicitly labels these as "suggested" values.

---

## Wizard Step: Automatic Recommendation Trigger

### Concept

An optional wizard step that bridges Scenarios with the Automation system:

```
Step N: Auto-Suggest (optional)
        "Would you like to be recommended to activate this scenario
         when something is detected?"
        [No, I'll activate manually]
        [Yes, suggest when... →]
          → BG rising above [threshold] for [duration]
          → BG dropping below [threshold]
          → Time of day [time picker]
          → Activity detected (accelerometer)
```

This creates an `ActionSuggestBundle` automation rule (as described in the Architecture section)
that posts a **notification** when conditions are met — never auto-activates.

### How it works

1. User enables auto-suggest in wizard for "Sport" scenario
2. System creates an Automation rule: Trigger = "BG > 180 for 15 min" → Action = `ActionSuggestBundle("sport")`
3. When trigger fires: notification appears "BG has been above 180 for 15 min. Activate Sport mode?"
4. User taps notification → opens standard confirmation dialog
5. If ignored, notification stays but doesn't re-fire for configured cooldown period

### Template-specific suggestions

| Template | Suggested trigger | Rationale |
|----------|------------------|-----------|
| Sick Day | BG > 250 for 1h + rising | Persistent high may indicate illness |
| Post-Exercise Recovery | Scenario "Exercise" ends | Chain follow-up scenario |
| Hypo Protection | BG < 80 and dropping | Proactive hypo protection |
| Pre-meal (Eating Soon) | Time of day (lunch time) | Habitual meal times |
| Alcohol | Time of day (evening) | Social drinking patterns |

### Safety constraints

- **Never auto-activate** — notification only, user must confirm
- **Cooldown period** — don't spam if condition persists (default: 1h)
- **User can disable per-scenario** without deleting the trigger
- **Transparent** — clearly shows "This scenario has an auto-suggest rule" in editor

### Implementation

Reuses the existing Automation infrastructure:
- `TriggerBg`, `TriggerTime`, `TriggerDelta` for conditions
- `ActionSuggestBundle` (new) for the notification action
- Standard `processActions()` evaluation loop
- The Automation rule is tagged as "scenario-suggest" and managed from the Scenarios UI,
  not from the Automation screen (though visible there as read-only)

---

## Scenes vs Granular Features: Unification Depth

### What Scenes can absorb

**TT presets → Scenes:** Clean fit. TT presets are already "named preset with 1 action." Scene is
the generalization. Decision already made above (TT Presets and Scenarios: Unification section).

**Profile switches → Scenes: Partial fit.** Profile switches have two distinct use cases:
- **Temporary override** (with duration) — "exercise profile for 2h" → Scene action. Perfect fit.
- **Permanent change** (no duration) — "endo adjusted my dosing", "new pump site absorbs differently"
  → NOT a scene. Stays as standalone profile switch.

**Loop mode → Scenes: Same split.**
- **Situational** — "open loop during competition", "LGS during alcohol" → Scene action. Perfect fit.
- **Troubleshooting** — "open loop because sensor died", "LGS because site is failing" → Not a scene.
  Stays as standalone loop mode change.

**Conclusion:** Scenes absorb the *temporary/situational* uses of profiles and loop mode, but the
*permanent/troubleshooting* uses remain as standalone features.

### Storage: Active Scene in Room DB

Yes, active scene needs Room DB storage (similar pattern to RunningMode):
- Survives app restart, phone reboot
- Observable via Flow (Overview banner needs real-time updates)
- Tracks: activation time, expected end time, prior state snapshot (for revert)
- Richer than RunningMode — stores multiple actions + prior state per action

RunningMode itself could eventually become a field inside ActiveScene rather than a separate concept,
but that's a bigger refactor and not required for v1.

### The accessibility problem

TT and Profile Switch are **daily-driver features** — used multiple times per day. Current flow:

```
Set Eating Soon TT:  QuickLaunch tap → done (1 tap)
Switch profile:      QuickLaunch tap → done (1 tap)
```

If everything goes through Scenes with confirmation:

```
Activate Eating Soon scene:  tap → confirmation dialog → Activate (2-3 taps)
```

This is a **regression for the most common actions**. The confirmation dialog that makes sense for
"Sport Mode" (5 actions, SMB changes, pump changes) is overkill for "just set a TT."

### Options considered

**A) Scenes don't replace granular features — layer on top only.**
- TT dialog stays. Profile switch stays. Loop mode stays.
- Scenes only for combinations of 2+ actions.
- Simple, but contradicts TT preset unification decision.

**B) Single-action scenes get a fast path (no confirmation).**
- 1 action → activate immediately on tap (like current TT preset behavior).
- 2+ actions → show confirmation dialog.
- Fixed TT-origin scenes (Eating Soon, Activity, Hypo) start as single-action → 1 tap.
- If user adds a profile action to "Activity" → becomes multi-action → now gets confirmation.
- Elegant but the behavior change when adding an action could surprise users.

**C) Per-scene "skip confirmation" toggle.**
- Any scene can be marked "quick activate" by the user.
- Most flexible but undermines the safety design.

**D) Granular features stay, standalone actions optionally create background scenes.**
- Every manual TT/profile/mode change secretly creates an "anonymous scene" behind the scenes.
- Pro: unified state tracking, conflict detection, revert capability for everything.
- Con: abstraction leaks. User sets a manual TT, then activates Sport Mode → system says
  "conflict with active scene" → user thinks "what scene? I just set a TT!"

### Decision: Hybrid approach — granular features stay first-class

```
┌─────────────────────────────────────────────────┐
│              What the user sees                  │
├─────────────────────────────────────────────────┤
│  Granular actions: TT, Profile, Mode            │
│  → Stay as quick standalone features            │
│  → 1-tap from QuickLaunch, no confirmation      │
│                                                 │
│  Scenes: named bundles of 1+ actions            │
│  → Created via wizard                           │
│  → Confirmation dialog (for multi-action)       │
│  → Active banner, End button, revert            │
├─────────────────────────────────────────────────┤
│              What the system sees                │
├─────────────────────────────────────────────────┤
│  ActiveScene (Room DB)                          │
│  → Explicit scenes stored normally              │
│  → Manual TT/Profile/Mode NOT wrapped as scenes │
│  → Scene conflict detection checks ALL active   │
│    state (TTs, profiles, modes) regardless of   │
│    whether they originated from a scene or not  │
└─────────────────────────────────────────────────┘
```

**Granular features remain independent.** Scenes don't secretly wrap them. But scene conflict
detection is smart enough to inspect all active state (running TTs, active profiles, current loop
mode) regardless of origin.

**Scene activation with existing manual state:**
When activating a scene and a manual TT is already running:
→ "Note: Active Temp Target (120 mg/dL, 35 min remaining) will be replaced."
Same conflict UI, no fake scene needed.

**Manual action during active scene:**
When a scene is active and user manually sets a TT:
→ Warn: "Sport Mode is active. Setting a manual TT will override the scene's TT.
  The scene's other actions (profile, SMB) remain active."
→ Scene tracks that "my TT was overridden externally" — revert becomes partial
  (on End: revert profile and SMB, but don't touch TT since user changed it manually).

**This keeps the system honest without forcing an abstraction on users who just want to set a TT.**

### Single-action scene fast path (Option B supplement)

Even within the hybrid approach, the fixed TT-origin scenes (Eating Soon, Activity, Hypo Protection)
should behave like current TT presets — 1 tap, no confirmation. This means:

- Single-action scenes activate immediately on tap (matching current TT preset UX)
- Multi-action scenes show confirmation dialog
- User enhances a fixed scene by adding actions → it gains the confirmation step
- This is transparent: the scene editor shows "This scene will ask for confirmation before
  activating" when it has 2+ actions

---

## Active Scene Storage: DB vs Preferences

Scenes are **always temporary** (duration or manual end). Granular features stay independent. So
the active scene is **grouping metadata** — "these things were set together and should be reverted
together." The individual actions (TT, profile switch, loop mode) are already persisted in their
own tables/preferences.

### Option 1: Room DB

**Pros:**
- **Survives restart/reboot.** Phone reboots during 2h Sport Mode → scene resumes with banner,
  End button, coordinated revert. Without persistence, individual actions survive (they're in their
  own tables) but we lose the grouping. User must manually undo each action separately.
- **Observable via Flow.** Overview banner, QuickLaunch ring indicator, Wear OS — all need reactive
  updates. Room DAOs return Flow natively.
- **Audit trail.** When did scenes activate/deactivate? Useful for Nightscout reports, HCP review.
- **Consistent pattern.** AAPS stores TT, profile switches, RunningMode in DB. Active scene fits.
- **Partial revert tracking.** "User manually overrode the TT mid-scene" flag survives restart,
  so revert logic stays correct.

**Cons:**
- **More infrastructure.** Room entity, DAO, DB migration, repository. Non-trivial boilerplate.
- **Heavy for the job.** At most one active scene at a time — a single row written and deleted.
- **State duplication risk.** TT is in `TemporaryTarget` table, profile is in its table, mode is
  in its place. Active scene duplicates "what's running" in a parallel structure. Risk of going
  out of sync (e.g., app crash between action execution and scene DB write).
- **Cleanup burden.** Need careful transaction handling for crash safety.

### Option 2: SharedPreferences (JSON)

**Pros:**
- **Simpler.** Store active scene as JSON in a single preference key (same pattern as Automation
  events and TT presets today).
- **Survives restart.** Preferences persist across app restarts.
- **Observable.** `preferences.observe(key)` returns `StateFlow` — already used throughout AAPS.
- **No DB migration.** No Room entity, no schema version bump.
- **Matches the cardinality.** One key = one active scene (or null). Perfect for 0-or-1 semantics.

**Cons:**
- **No history/audit trail.** Only tracks current state, not past activations. But: activations
  can be logged to UserEntry separately (which already goes to DB).
- **JSON parsing on every read.** Minor perf concern, mitigated by caching in repository.
- **Less type-safe.** String-based storage vs Room's compile-time checked entities.

### Leaning: SharedPreferences for active state + UserEntry for audit

Active scene state (0 or 1 at a time) maps naturally to a single preference key. History/audit
goes through the existing UserEntry system. This avoids Room boilerplate for what is essentially
a single transient value.

Scene **definitions** (the saved templates/presets) also go in preferences as JSON — same pattern
as TT presets and Automation events. No DB table needed for definitions either.

---

## Active Scene vs RunningMode: Merge or Separate?

### What RunningMode is today

A simple concept: which loop mode is active — Closed Loop, Open Loop, LGS, Suspend. Stored
persistently, observable, always has exactly one value. Every component (algorithms, plugins,
Wear OS, Nightscout sync) reads it.

### Analysis

**Pros of merging:**
- **One "operational state" concept.** "What's the system doing right now?" has a single answer.
- **Fewer concepts for users.** Instead of "RunningMode is LGS because of Alcohol scene" →
  just "Alcohol scene is active" (which implies LGS).
- **Natural overlap.** RunningMode changes are often scene-driven.

**Cons of merging:**
- **Different cardinality.** RunningMode always has exactly one value (every moment has a loop
  mode). Active scene is 0 or 1 — usually none. These are fundamentally different shapes.
- **Different lifecycles.** RunningMode can be **permanent** ("I switched to LGS and will leave
  it"). Scenes are **always temporary**. Merging forces either RunningMode to become temporary
  or scenes to support permanent — neither is right.
- **Independence.** RunningMode changes without scenes (troubleshooting: "sensor died → open
  loop"). Scenes exist without changing RunningMode (scene that only sets TT + profile).
  Neither is a subset of the other.
- **Complexity explosion.** RunningMode is a simple enum. Merging makes it "enum OR rich scene
  object." Every consumer (algorithms, plugins, Wear OS, NS sync) must handle both cases.
- **Breaking existing contracts.** Everything that reads RunningMode would need updating — high
  blast radius for a concept that works fine today.
- **Revert semantics mismatch.** "End scene" reverts to prior mode. RunningMode has no concept
  of "prior" — it's just "current." Merging would force revert semantics onto RunningMode.

### Decision: Separate concepts that interact

```
RunningMode:  "What mode is the loop in?"     → always has a value, can be permanent
ActiveScene:  "Is a temporary bundle active?"  → 0 or 1, always temporary, reverts on end
```

A scene can **set** RunningMode as one of its actions, and **revert** it on end. But they are
different concepts — just like a scene **sets** a TT but isn't the TT itself.

The relationship is: Scene → (action) → changes RunningMode. Not: Scene = RunningMode.

---

## Open Questions

- TODO: Icon/color picker for bundle buttons
- TODO: Maximum number of bundles? (suggested soft limit: 10)
- TODO: Import/export bundles for sharing between users?
- TODO: Integration with Wear OS / watchfaces?
- TODO: Should bundle duration be editable while active? ("I need 30 more minutes")
- TODO: How should "End scene" handle actions that were manually overridden mid-scene?
  Current thinking: skip reverting those actions, note it in the End confirmation dialog.
- TODO: Should Option B (single-action fast path) apply to ALL single-action scenes or only
  the 3 fixed TT-origin ones? User-created single-action scenes might still warrant confirmation
  if they contain a profile switch or loop mode change.
- TODO: SharedPreferences vs Room DB — leaning SharedPreferences + UserEntry. Decide before
  implementation.
