# ElementType — Unified Visual Identity System

## Goal

Replace scattered icon/color/label assignments with a single `ElementType` enum in `core:ui` that
serves as the **single source of truth** for how each action/element looks across the entire app.

## Current Problem

The same action (e.g. Insulin) has its icon, color, and label defined independently in multiple
places:

- `ElementColors` — defines color fields (`insulin`, `carbs`, `tempTarget`, ...)
- `ManageBottomSheet` — hardcodes `color = AapsTheme.elementColors.insulin` + `IcBolus` per item
- `TreatmentBottomSheet` — same pattern
- `ToolbarActionIcons.kt` — `icon()`, `labelResId()`, `tintColor()` per `ToolbarAction`
- `UserEntryScreen` — maps `Action.ColorGroup` → elementColors
- Various dialog screens — reference elementColors inline

Adding a new action or changing a color requires updating multiple files.

## Design

### 1. Enum Definition (`core:ui`)

```kotlin
package app.aaps.core.ui.compose

enum class ElementType {
    // Treatment
    INSULIN,
    CARBS,
    BOLUS_WIZARD,
    QUICK_WIZARD,
    TREATMENT,

    // CGM
    CGM_XDRIP,
    CGM_DEX,
    CALIBRATION,

    // Profile & Targets
    PROFILE_SWITCH,
    TEMP_TARGET,

    // Careportal
    BG_CHECK,
    NOTE,
    EXERCISE,
    QUESTION,
    ANNOUNCEMENT,

    // Device maintenance
    SENSOR_INSERT,
    BATTERY_CHANGE,
    CANNULA_CHANGE,
    FILL,
    SITE_ROTATION,

    // Basal
    TEMP_BASAL,
    EXTENDED_BOLUS,

    // System
    AUTOMATION,
    PUMP,
    SETTINGS,

    // Running mode / loop
    RUNNING_MODE,
    USER_ENTRY,
    LOOP,
    AAPS
}
```

### 2. Extension Functions (`core:ui`)

All three live next to the enum in `core:ui`:

```kotlin
// ElementTypeStyle.kt

@Composable
fun ElementType.color(): Color = when (this) {
    INSULIN      -> AapsTheme.elementColors.insulin
    CARBS        -> AapsTheme.elementColors.carbs
    BOLUS_WIZARD -> AapsTheme.elementColors.quickWizard
    QUICK_WIZARD -> AapsTheme.elementColors.quickWizard
    // ... etc
}

fun ElementType.icon(): ImageVector = when (this) {
    INSULIN      -> IcBolus
    CARBS        -> IcCarbs
    BOLUS_WIZARD -> IcCalculator
    // ... etc
}

fun ElementType.labelResId(): Int = when (this) {
    INSULIN      -> R.string.configbuilder_insulin
    CARBS        -> R.string.carbs
    BOLUS_WIZARD -> R.string.boluswizard
    // ... etc
}

fun ElementType.descriptionResId(): Int = when (this) {
    INSULIN        -> R.string.treatment_insulin_desc
    CARBS          -> R.string.treatment_carbs_desc
    BOLUS_WIZARD   -> R.string.treatment_calculator_desc
    TREATMENT      -> R.string.treatment_desc
    PROFILE_SWITCH -> R.string.manage_profile_desc
    SITE_ROTATION  -> R.string.manage_site_rotation_desc
    // ... etc (0 for items without description)
}

/**
 * Runtime availability check.
 * Configuration screens show all elements regardless.
 * Runtime UI (toolbar, bottom sheets) uses this to hide/disable unavailable elements.
 */
fun ElementType.isAvailable(
    xDripSource: XDripSource,
    dexcomBoyda: DexcomBoyda,
    isSimpleMode: Boolean
): Boolean = when (this) {
    CGM_XDRIP                                        -> xDripSource.isEnabled() || dexcomBoyda.isEnabled()
    CALIBRATION                                      -> xDripSource.isEnabled()
    BG_CHECK, NOTE, EXERCISE, QUESTION, ANNOUNCEMENT -> !isSimpleMode
    else                                             -> true
}
```

### 3. Consumer Integration

**ToolbarAction** — adds `elementType` property:

```kotlin
data object Insulin : ToolbarAction() {
    override val typeId = "insulin"
    override val elementType = ElementType.INSULIN
}
```

`ToolbarActionIcons.kt` simplifies to delegate: `action.elementType.icon()`,
`action.elementType.color()`.

**ManageBottomSheet** — items reference enum:

```kotlin
ManageItem(
    elementType = ElementType.PROFILE_SWITCH,
    description = stringResource(R.string.manage_profile_desc),
    onClick = onProfileManagementClick,
    onDismiss = onDismiss
)
```

`ManageItem` internally calls `elementType.icon()`, `elementType.color()`,
`elementType.labelResId()`.

**TreatmentBottomSheet** — same pattern as ManageBottomSheet.

**UserEntryScreen** — replace `Action.ColorGroup` mapping with `ElementType.color()`.

### 4. ElementColors Migration

`ElementColors` becomes a **generated/derived** structure from `ElementType.color()` or is replaced
entirely:

**Phase 1** — `ElementType.color()` delegates to `ElementColors` (current fields stay):

```kotlin
INSULIN -> AapsTheme.elementColors.insulin
```

**Phase 2** — Inline the color values directly into `ElementType.color()`:

```kotlin
INSULIN -> if (isDark) Color(0xFF67DFE8) else Color(0xFF1E88E5)
```

**Phase 3** — Remove `ElementColors` data class, `LocalElementColors`, `LightElementColors`,
`DarkElementColors`. The `AapsTheme` CompositionLocal setup simplifies.

Whether to do Phase 2+3 is optional — keeping `ElementColors` as the color store and `ElementType`
as the mapper is also a valid end state. The key win is that consumers never touch `ElementColors`
directly.

## Visibility / Availability

### Design Principle

**Configuration screens show all elements** — users should always be able to configure their
toolbar, bottom sheets, etc. regardless of current runtime state. **Runtime UI uses `isAvailable()`
to hide or disable** elements whose conditions aren't met.

### Current Duplication

Visibility checks are currently scattered across multiple ViewModels:

- `TreatmentViewModel` — `showCgm = xDripSource.isEnabled() || dexcomBoyda.isEnabled()`,
  `showCalibration = xDripSource.isEnabled()`, careportal hidden in simple mode
- `ManageBottomSheet` — `isSimpleMode` hides careportal section,
  `showTempTarget = profile != null && isLoopRunning`
- `MainViewModel` (toolbar) — needs same checks for floating toolbar display

### Centralized in ElementType

`isAvailable()` centralizes these checks. Consumers:

```kotlin
// Toolbar (MainViewModel) — filters unavailable actions at runtime
val toolbarItems = savedActions
    .filter { it.elementType.isAvailable(xDripSource, dexcomBoyda, simpleMode) }
    .map { resolveToolbarItem(it) }

// Config screen — shows everything, no filtering
val allActions = ToolbarAction.staticActions.map { resolveItem(it) }

// ManageBottomSheet — hides unavailable items
if (ElementType.CGM_XDRIP.isAvailable(...)) {
    ManageItem(elementType = ElementType.CGM_XDRIP, ...)
}
```

### Concrete Examples

| Element                                                    | Condition                                              | Source             |
|------------------------------------------------------------|--------------------------------------------------------|--------------------|
| `CGM_XDRIP`                                                | `xDripSource.isEnabled() \|\| dexcomBoyda.isEnabled()` | TreatmentViewModel |
| `CALIBRATION`                                              | `xDripSource.isEnabled()`                              | TreatmentViewModel |
| `BG_CHECK`, `NOTE`, `EXERCISE`, `QUESTION`, `ANNOUNCEMENT` | `!isSimpleMode`                                        | ManageBottomSheet  |
| `TEMP_TARGET`                                              | `profile != null && isLoopRunning`                     | ManageViewModel    |
| `TEMP_BASAL`, `EXTENDED_BOLUS`                             | pump capability checks                                 | ManageViewModel    |

## Migration Plan

### Phase 0: Create ElementType + Extensions

- Create `ElementType` enum in `core:ui`
- Create `ElementTypeStyle.kt` with `color()`, `icon()`, `labelResId()` extensions
- All values delegate to existing `ElementColors` fields + existing icon constants
- No consumers changed yet — purely additive
- **Compile & verify**

### Phase 1: Migrate ToolbarAction

- Add `elementType` property to `ToolbarAction` sealed class
- Replace `ToolbarActionIcons.kt` functions to delegate to `elementType`
- Remove `tintColor()`, `icon()`, `labelResId()` from `ToolbarActionIcons.kt`
- **Compile & verify**

### Phase 2: Migrate ManageBottomSheet

- Refactor `ManageItem` to accept `ElementType` instead of separate icon/color params
- Update all call sites in `ManageBottomSheetContent`
- Items without an `ElementType` (cancel variants, custom pump actions) keep explicit params
- **Compile & verify**

### Phase 3: Migrate TreatmentBottomSheet

- Same pattern as ManageBottomSheet
- **Compile & verify**

### Phase 4: Migrate UserEntryScreen

- Replace `Action.ColorGroup` → `ElementType` mapping
- **Compile & verify**

### Phase 5: Migrate Remaining Consumers

- Grep for `elementColors\.` across codebase
- Migrate dialog screens, graph components, etc. where applicable
- Some inline usages (e.g. graph colors, loop mode colors) may stay on `ElementColors` if they don't
  map to an action type
- **Compile & verify**

### Phase 6 (Optional): Remove ElementColors

- Inline light/dark color values into `ElementType.color()` (using `isSystemInDarkTheme()` or passed
  parameter)
- Remove `ElementColors` data class, `LocalElementColors`, light/dark instances
- Simplify `AapsTheme` CompositionLocal setup
- **Compile & verify**

## Scope Estimate

- ~15-20 files touched
- Phases 0-1: Small, safe
- Phases 2-4: Medium, mechanical refactoring
- Phase 5: Case-by-case evaluation
- Phase 6: Optional, can defer indefinitely

## Notes

- Dynamic items (QuickWizard, Automation, Profile) have runtime labels but still have fixed icons
  and colors via their `ElementType`
- Some elements exist only in `ElementColors` but not as user-facing actions (e.g. `cob`,
  `loopClosed`, `sensitivity`) — these stay in `ElementColors` or get their own `ElementType` values
  as needed
- The `labelResId()` function returns 0 for dynamic items whose label is resolved at runtime
- `icon()` and `color()` are always available for every `ElementType`
- `descriptionResId()` provides optional descriptions (reuses strings from
  TreatmentBottomSheet/ManageBottomSheet)
- Description strings are shared between: bottom sheets, QuickLaunch config screen, and search
  index (`SearchableItem.dialogSummaryResId`)
- When adding a new `ElementType`, ensure its `descriptionResId()` is also used in
  `DialogSearchables.kt` for search indexing
- Dynamic items (QuickWizard, Automation, TT Presets, Profile) resolve descriptions at runtime in
  ViewModels (e.g., automation action summaries, TT duration, QW carbs)
