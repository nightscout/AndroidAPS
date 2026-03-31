# Feature: Scenes (Situation Presets)

## Summary

User-defined "Situation Presets" — bundles of pre-configured actions activated with one tap.
When a user wants to handle a situation (e.g., exercise, illness, sleep), they activate a Scene
instead of performing multiple separate actions manually.

**Example:** "Sport Mode" = TT 140 mg/dL + Sport-80% profile + disable SMB + Exercise CP entry, 2h

## Current Implementation Status

### Done

- **Data model**: `Scene`, `SceneAction` (sealed: TempTarget, ProfileSwitch, SmbToggle, LoopModeChange, CarePortalEvent), `SceneEndAction`, `ActiveSceneState` in `core/data`
- **Serialization**: JSON in SharedPreferences via `SceneSerializer` (same pattern as TT presets)
- **Repository**: `SceneRepository` — CRUD operations, observable flow
- **Execution**: `SceneExecutor` — activate (captures prior state, executes actions, schedules expiry worker), deactivate (reverts actions), dismiss
- **Active state**: `ActiveSceneManager` — tracks active scene in SharedPreferences, observable flow
- **Expiry**: `SceneExpiryWorker` — WorkManager job fires when scene duration expires
- **User entry logging**: All activations/deactivations logged via `UserEntryLogger` with `Action.SCENE_ACTIVATED` / `Action.SCENE_DEACTIVATED`
- **Scene creation wizard**: 9-step guided flow (template → info → profile → TT → SMB → loop mode → careportal → duration → name/icon), each step in its own file under `wizard/`
- **Edit via wizard**: Same wizard supports editing existing scenes (loads scene data, skips template/info steps, preserves scene ID)
- **Templates**: 11 templates with clinically-informed defaults (Exercise, Sick Day, Sleep, Pre-meal, Swimming, Alcohol, Driving, Sick Day Vomiting, Luteal Phase, Hot Weather, Medical Procedure, Blank)
- **Scene list screen**: Shows all scenes with activate/deactivate/edit/delete, conflict detection, deactivation revert summary
- **Active scene banner**: Persistent banner on Overview showing scene name + time remaining + End button
- **ElementType integration**: `SCENE` and `SCENE_MANAGEMENT` element types with colors, icons, labels, descriptions, protection levels
- **Navigation**: Scene list, wizard (new + edit) accessible via Manage bottom sheet → Scene management
- **Automation bottom sheet**: Scenes appear alongside automation user actions for quick activation
- **QuickLaunch**: `QuickLaunchAction.SceneAction` — scenes can be added to the quick launch toolbar via config screen (Scenes category)
- **Confirmation dialog**: Scene activation goes through `MainViewModel.requestSceneConfirmation()` with action summary
- **Conflict detection**: Checks for active TT, active profile switch, active scene before activation
- **Translator**: `Action.SCENE_ACTIVATED` / `Action.SCENE_DEACTIVATED` translated for user entry display
- **Icon picker**: `SceneIconPicker` with categorized material icons (activity, medical, social, time, weather, food, transport, general)

### Not Yet Implemented

- **TT preset unification**: TT presets not yet merged into Scenes (both coexist)
- **Single-action fast path**: All scenes show confirmation dialog (no 1-tap for single-action scenes)
- **Duration adjustment at activation**: Confirmation dialog doesn't allow adjusting duration
- **Active scene persistence in Room DB**: Using SharedPreferences (survives restart but no history)
- **Notification/reminder end actions**: `SceneEndAction.Notification` exists in model but only shows a basic expiry notification, no scheduled reminders during scene
- **ActionSuggestBundle**: No automation integration to suggest scenes based on triggers
- **Wear OS support**: No watch activation/display
- **Nightscout sync**: Active scene not synced
- **Algorithm preference overrides**: Only SMB toggle implemented. No UAM, MaxIOB, LGS threshold, DynISF factor overrides
- **Import/export scenes**: No sharing between users
- **Extend duration while active**: Not supported
- **Manual override tracking**: If user manually changes TT during active scene, revert doesn't know

## Architecture

### Module Structure

```
core/data/
  model/
    Scene.kt                    -- data class: id, name, icon, defaultDurationMinutes, actions, endAction
    SceneAction.kt              -- sealed: TempTarget, ProfileSwitch, SmbToggle, LoopModeChange, CarePortalEvent
    SceneEndAction.kt           -- sealed: Notification, SuggestScene
    ActiveSceneState.kt         -- data class: scene, activationTime, expiryTime, priorState

core/data/ue/
    Action.kt                   -- SCENE_ACTIVATED, SCENE_DEACTIVATED
    Sources.kt                  -- Scene

core/ui/compose/navigation/
    ElementType.kt              -- SCENE, SCENE_MANAGEMENT

app/
    receivers/SceneExpiryWorker.kt  -- WorkManager for scene expiry

ui/compose/scenes/
    SceneRepository.kt          -- CRUD, observable flow (SharedPreferences)
    SceneSerializer.kt          -- JSON <-> Scene list
    SceneExecutor.kt            -- activate/deactivate/dismiss
    ActiveSceneManager.kt       -- active state tracking (SharedPreferences)
    SceneListScreen.kt          -- list with activate/deactivate/edit/delete
    SceneListViewModel.kt       -- list state, conflict detection
    SceneEditorScreen.kt        -- [DELETED — wizard handles editing]
    SceneEditorViewModel.kt     -- [DELETED — wizard handles editing]
    SceneTemplate.kt            -- 11 templates with defaults
    SceneIcon.kt                -- icon catalog + picker
    ActionEditors.kt            -- shared action editor composables (TT, profile, SMB, loop, careportal)
    ActiveSceneBanner.kt        -- Overview banner composable
    SceneExecutionResult.kt     -- result model for activation feedback

ui/compose/scenes/wizard/
    SceneWizardScreen.kt        -- main wizard screen with step routing
    SceneWizardViewModel.kt     -- wizard state, create + edit mode (via SavedStateHandle sceneId)
    ActionToggle.kt             -- shared toggle component
    TemplatePickerStep.kt       -- step 0: template selection
    InfoStep.kt                 -- step 1: template info/description
    ProfileStep.kt              -- step 2: profile switch toggle + editor
    TempTargetStep.kt           -- step 3: temp target toggle + editor
    SmbStep.kt                  -- step 4: SMB toggle + editor
    LoopModeStep.kt             -- step 5: loop mode toggle + editor
    CarePortalStep.kt           -- step 6: careportal event toggle + editor
    DurationStep.kt             -- step 7: duration picker
    NameIconStep.kt             -- step 8: name + icon picker
    PreviewData.kt              -- shared preview state for @Preview composables
```

### Data Storage

- **Scene definitions**: JSON in `StringNonKey.SceneDefinitions` (SharedPreferences)
- **Active scene state**: JSON in `StringNonKey.ActiveSceneState` (SharedPreferences)
- **Audit trail**: `UserEntry` system (existing Room DB)

### Key Design Decisions

1. **Scenes are separate from Automation** — different execution model (user-initiated vs condition-triggered), different UI (wizard vs trigger builder), shared only at the action level
2. **Scenes are separate from RunningMode** — a scene can *set* RunningMode as one of its actions, but they are different concepts with different lifecycles
3. **Wizard for both create and edit** — no separate editor screen. Edit mode skips template/info steps
4. **Granular features stay independent** — TT, profile switch, loop mode remain as standalone quick actions. Scenes layer on top for combinations
5. **Always confirm multi-action scenes** — safety requirement for medical context
6. **SharedPreferences over Room DB** — simpler for 0-or-1 active scene cardinality, observable via `preferences.observe()`

## Activation Flow

```
User taps scene (QuickLaunch / Automation sheet / Scene list)
  → MainViewModel.requestSceneConfirmation(sceneId)
  → Show confirmation dialog with resolved action summary
  → User confirms
  → ComposeMainActivity dispatches to SceneExecutor.activate()
  → SceneExecutor:
      1. Captures prior state (SMB setting, profile name/%, loop mode)
      2. Executes each action (TT insert, profile switch, SMB toggle, loop mode, careportal)
      3. Stores ActiveSceneState in SharedPreferences
      4. Schedules SceneExpiryWorker if duration > 0
      5. Logs UserEntry (SCENE_ACTIVATED)
  → Overview shows ActiveSceneBanner
```

## Deactivation Flow

```
User taps "End" on banner / scene list
  → SceneListViewModel.requestDeactivation()
  → Show confirmation with revert summary
  → User confirms
  → SceneExecutor.deactivate()
  → SceneExecutor:
      1. Reverts each action to prior state
      2. Clears ActiveSceneState
      3. Cancels SceneExpiryWorker
      4. Logs UserEntry (SCENE_DEACTIVATED)
```

## Future Work (Priority Order)

1. **Duration adjustment at activation** — let user override default duration in confirmation dialog
2. **Notification reminders** — scheduled notifications during scene ("check BG at 2h")
3. **Single-action fast path** — skip confirmation for scenes with only 1 action
4. **TT preset migration** — convert TT presets to single-action scenes
5. **Algorithm preference overrides** — UAM, MaxIOB, LGS threshold, DynISF factor
6. **Manual override tracking** — detect when user changes scene-managed state manually
7. **Extend duration** — "I need 30 more minutes" button on active banner
8. **ActionSuggestBundle** — automation action that suggests (never activates) a scene via notification
9. **Wear OS** — activate/deactivate scenes from watch
10. **Nightscout sync** — sync active scene state
