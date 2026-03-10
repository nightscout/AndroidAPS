# Site Rotation Refactor Plan

## Goal

Merge Management + Editor screens into one unified screen with expandable row editing.
Extract a shared `SiteLocationPicker` for use in Fill Dialog, CGM Insert Dialog, and patch pump
wizards.

## Architecture

### Shared Composable: `SiteLocationPicker` (`:core:ui`)

- Full-screen content: body diagram (front+back, zoomable), zone selection, arrow picker
- Pure composable: state-in, events-out, no ViewModel dependency
- API:
  ```kotlin
  @Composable
  fun SiteLocationPicker(
      siteType: TE.Type,              // filters zones (pump vs CGM)
      bodyType: BodyType,
      entries: List<TE>,              // recent history for color coding
      selectedLocation: TE.Location,
      selectedArrow: TE.Arrow,
      onLocationSelected: (TE.Location) -> Unit,
      onArrowSelected: (TE.Arrow) -> Unit,
      modifier: Modifier
  )
  ```

### Compact Widget: `SiteLocationSummary` (`:core:ui`)

- For embedding in dialogs: "Last site: XXX" text + "Pick site" button
- After selection: "Selected: XXX"
- Triggers navigation to picker screen

### Usage Contexts

| Context                  | Component Used        | How                                        |
|--------------------------|-----------------------|--------------------------------------------|
| Fill Dialog              | `SiteLocationSummary` | Button opens picker screen, returns result |
| CGM Insert Dialog        | `SiteLocationSummary` | Button opens picker screen, returns result |
| Equil wizard             | `SiteLocationPicker`  | Direct embed as wizard step                |
| Medtrum wizard           | `SiteLocationPicker`  | Direct embed as wizard step                |
| Site Rotation Management | `SiteLocationPicker`  | Inside expandable entry row for editing    |

## Module Dependency Impact

- `BodyType` enum must move from `:ui` → `:core:ui` (it already only references `:core:ui` icons)
- `BodyView` composable must move from `:ui` → `:core:ui`
- `computeZoneColors()` must move with `BodyView`
- Zone path data already in `:core:ui` (ManFrontPaths, etc.)
- No new inter-module dependencies needed (pump plugins already depend on `:core:ui`)

## Phases

### Phase 0: Prerequisites — Move shared code to `:core:ui` ✅

Moved to `core/ui/src/main/kotlin/app/aaps/core/ui/compose/siteRotation/`:

- [x] `BodyType.kt` — enum extracted from `SiteRotationUiState.kt`
- [x] `BodyView.kt` — composable + `computeZoneColors()` + `interpolateColor()`
- [x] `ArrowSelectionDialog.kt` — extracted from `SiteRotationEditorDetails.kt`
- [x] `ArrowExtensions.kt` — `Arrow.directionToComposeIcon()` (duplicated from `:core:objects` to
  avoid dependency)
- [x] `SiteEntryList.kt` — `SiteEntryDisplayData` + `SiteEntryList` + `TE.toDisplayData()`
- [x] Moved `edit_site` and `select_arrow` strings to `:core:ui` strings.xml
- [x] Updated all imports in `:ui` screens
- [x] Deleted old `BodyView.kt` and `SiteEntryList.kt` from `:ui`
- [x] Both `:core:ui` and `:ui` compile successfully

### Phase 1: Create `SiteLocationPicker` in `:core:ui` ✅

Created in `core/ui/src/main/kotlin/app/aaps/core/ui/compose/siteRotation/`:

- [x] `ZoomableBodyDiagram.kt` — front+back side by side, pinch-to-zoom (1x–3x), double-tap reset
- [x] `SiteLocationPicker.kt` — single-type picker (for Fill/Care dialogs, pump wizards)
    - Selected location label + arrow button + arrow dialog
    - Body diagram (zoomable)
    - Pure composable: state-in, events-out
- [x] `SiteLocationPickerWithFilters` — extended version with pump/CGM toggles (for Management
  screen)
- [x] Added `site_filter_info` and `selected_location` strings to `:core:ui`
- [x] Compiles successfully

### Phase 2: Create `SiteLocationSummary` in `:core:ui` ✅

- [x] `SiteLocationSummary.kt` — compact widget for dialogs
    - Shows type icon + "Last site: XXX" + "Pick site" button
    - After selection: "Selected: XXX" in primary color
    - Pure composable with `onPickSiteClick` callback
- [x] Added `last_site_location` and `pick_site` strings
- [x] Compiles successfully

### Phase 3: Integrate into Fill Dialog ✅

- [x] `FillDialogUiState`: added `siteRotationEnabled`, `siteLocation`, `siteArrow`,
  `lastSiteLocationString`, `selectedSiteLocationString`
- [x] `FillDialogViewModel`: added `updateSiteLocation()`, `updateSiteArrow()`, `bodyType()`,
  `loadLastSiteLocation()`
- [x] `FillDialogViewModel.confirmAndSave()`: includes location + arrow in saved CANNULA_CHANGE TE
- [x] Fixed bug: `SiteRotationManageCgm` → `SiteRotationManagePump` for pump site changes
- [x] Smart fallback: only shows SiteRotationEditor post-save if no location was picked inline
- [x] `FillDialogScreen`: shows `SiteLocationSummary` when site change checked + rotation enabled
- [x] `SiteLocationPickerScreen`: new full-screen composable in `:core:ui` with confirm button
- [x] `AppRoute.SiteLocationPicker`: new navigation route
- [x] Navigation: Fill Dialog → Picker → results via savedStateHandle → ViewModel update
- [x] All three modules compile (`:core:ui`, `:ui`, `:app`)

### Phase 4: Integrate into Care Dialog ✅

- [x] `CareDialogUiState`: added `siteLocation`, `siteArrow`, `lastSiteLocationString`,
  `selectedSiteLocationString`, `showSiteRotationSection`
- [x] `CareDialogViewModel`: added `loadLastSensorLocation()`, `updateSiteLocation()`,
  `updateSiteArrow()`, `bodyType()`, `siteRotationEntries()`
- [x] `CareDialogViewModel.confirmAndSave()`: includes location + arrow in saved SENSOR_CHANGE TE
- [x] Smart fallback: only shows SiteRotationEditor post-save if no location was picked inline
- [x] `CareDialogScreen`: shows `SiteLocationSummary` when event type is SENSOR_INSERT + rotation
  enabled
- [x] Navigation: Care Dialog → Picker → results via savedStateHandle → ViewModel update
- [x] Both `:ui` and `:app` compile successfully

### Phase 5: Refactor Management Screen ✅

- [x] `SiteEntryList`: added `editingTimestamp` + `editingContent` slot with `AnimatedVisibility`
- [x] `SiteRotationManagementViewModel`: absorbed all editor functionality (startEditing,
  cancelEditing, updateEditLocation/Arrow/Note, buildConfirmationSummary, confirmAndSave)
- [x] `SiteRotationManagementScreen`: replaced two BodyViews with `ZoomableBodyDiagram` (front+back,
  pinch-to-zoom)
- [x] Inline editing: tap Edit → row expands with date, location, arrow picker, note field,
  Save/Cancel
- [x] Zone taps: when editing → update edited entry's location; when not editing → filter entries
- [x] Top bar adapts: shows Save when editing, Settings when not
- [x] Back button: cancels editing first, then closes screen
- [x] Confirmation dialog with `OkCancelDialog` before saving edits
- [x] Arrow selection dialog integrated
- [x] Removed `onEditEntry` navigation from ComposeMainActivity (no longer navigates to separate
  Editor)
- [x] EditorScreen kept for Fill/Care Dialog post-save fallback (cleanup in Phase 7)
- [x] All three modules compile (`:core:ui`, `:ui`, `:app`)

### Phase 6: Patch Pump Wizards ✅

- [x] **Equil**: Added `SITE_LOCATION` wizard step after CONFIRM
    - `EquilWizardStep.SITE_LOCATION` added, workflows updated to 7 steps
    - `saveActivation()` deferred TE creation: moves to SITE_LOCATION if rotation enabled, else
      creates TE immediately
    - `finishWithTherapyEvents()`: creates CANNULA_CHANGE/INSULIN_CHANGE TEs, updates with
      location/arrow via PersistenceLayer
    - `SiteLocationStep.kt`: uses `SiteLocationPicker`, Save/Skip buttons
    - `PersistenceLayer` injected for TE update (same `:core:interfaces` module, no new dependency)
- [x] **Medtrum**: Added `SITE_LOCATION` step between ACTIVATE_COMPLETE and COMPLETE
    - `PatchStep.SITE_LOCATION` added, activation flow total steps = 6 when rotation enabled
    - `moveToSiteLocationOrComplete()`: checks `SiteRotationManagePump` preference
    - `completeSiteLocation()`: finds CANNULA_CHANGE TE by `patchStartTime` and updates with
      location/arrow
    - `MedtrumSiteLocationStep.kt`: uses `SiteLocationPicker`, Save/Skip buttons
    - `PersistenceLayer` and `Preferences` injected (same `:core:interfaces`, no new dependency)
- [x] Both pump modules compile successfully

### Phase 7: Cleanup ✅

- [x] Deleted `SiteRotationEditorScreen.kt`
- [x] Deleted `SiteRotationEditorDetails.kt`
- [x] Deleted `SiteRotationEditorViewModel.kt`
- [x] Removed `AppRoute.SiteRotationEditor` route
- [x] Removed Editor composable + ViewModel from `ComposeMainActivity`
- [x] Removed `SideEffect.ShowSiteRotationDialog` from `FillDialogViewModel` and
  `CareDialogViewModel`
- [x] Removed `onShowSiteRotationDialog` parameter from `FillDialogScreen` and `CareDialogScreen`
- [x] Removed unused strings: `site_front`, `site_back`
- [x] Verified no remaining references to Editor code via grep
- [x] All 5 modules compile (`:core:ui`, `:ui`, `:app`, `:pump:equil`, `:pump:medtrum`)
- Note: Old XML `SiteRotationDialog.kt`, `SiteRotationViewAdapter.kt`, and layout files kept — still
  used by `ActionsFragment` and old XML dialogs (separate cleanup scope)

## Decisions

- Arrow selection: in the picker screen (not in SiteLocationSummary)
- Picker screen: evaluate best practice during implementation (route vs shared composable)
- Expandable row: animated expansion
- Zoom: max 3x, double-tap to reset to 1x
