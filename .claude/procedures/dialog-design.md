# Dialog Design Guidelines

## Applies to: Full-screen dialog screens (Scaffold-based forms)

### Top Bar
- Simple: icon + title text
- Settings gear if applicable
- NO action buttons (OK/confirm) in top bar

### Bottom Button (Confirm/Submit)
- Fixed at bottom using Scaffold `bottomBar`
- Full-width `Button` with `imePadding()` — stays above keyboard
- Shows result text when available (e.g., "2.50 U 25 g")
- Always visible, **disabled** when no result/action available
- Fallback text: `stringResource(CoreUiR.string.ok)` when disabled

### Card Structure
- Use **2 cards maximum**: one for calculation/info (if applicable), one for all inputs
- All inputs in a single card using the gap-as-divider pattern
- Do NOT create separate cards for each input — group them

### Gap-as-Divider Pattern
- Outer wrapper: `Card(containerColor = surface)` — provides elevation, gap color
- Inner: `Column(verticalArrangement = Arrangement.spacedBy(2.dp))`
- Each item: `itemModifier` = `Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)).background(surfaceContainer).padding(horizontal = 16.dp, vertical = 8.dp)`
- No `HorizontalDivider` — the 2dp gap shows `surface` color through
- Define `itemModifier` once and reuse across items

### Collapsible "Change" Pattern
- For secondary/rarely-changed values (BG, carb time, percentage, profile)
- **Collapsed**: `Label: Value [Change]` — single compact row
- **Expanded**: shows NumberInputRow or other editor below the header
- `[Change]` is a `FilledTonalButton`, only visible when collapsed
- **No close button** — once expanded, stays expanded (user already decided to edit)
- **Auto-expand** when value needs attention (e.g., BG older than 9 min, no BG data)
- Text styles: label = `labelLarge` + `onSurfaceVariant`, value = `titleMedium` + `onSurface`
- Use `rememberSaveable` for expanded state to survive rotation
- This is different from the arrow expand/collapse pattern (used for "show more info" like calculation details)

### CarbTimeRow (shared component in core/ui/compose)
- Expand/collapse inline (not a popup dialog) — no local state, all changes go directly to parent
- Shows alarm bell icon when alarm is set and offset > 0
- Alarm toggle disabled (grayed out) when offset <= 0
- Supports `dateTimeContent` slot for date/time pickers (Carbs dialog only)
- No clock icon — just text label
- Value format: "Now" when offset=0, or "17:55\n(+15 min)" with resolved time

### NumberInputRow Usage
- Default `useSlider = false` — direct text input with +/- buttons
- No baked-in vertical padding — caller controls spacing via `modifier`/`itemModifier`
- Range text shown as plain `Text` below field (not in `supportingText`) — aligned with TextField width
- Empty field when value is 0 (not prefilled with "0")
- Clamp to range on focus loss (not error)
- Free text input accepted (no step rounding on typed values, only +/- buttons use step)

### Calculation Section (Wizard-specific)
- Expandable with arrow icon (show/hide details pattern — different from "Change" pattern)
- Result summary always visible in header row
- Toggles (BG, TT, Trend, IOB, COB) always visible using MultiChoiceSegmentedButtonRow
- Default M3 styling for toggle selection (no custom icon tints)
- `checked` must account for `enabled` conditions (e.g., `checked = uiState.useTT && uiState.hasTempTarget`)

### Space Savings
- Top-level `verticalArrangement = Arrangement.spacedBy(8.dp)` between cards
- Bottom spacer: `8.dp`
- No extra vertical padding in NumberInputRow
- Card internal padding: `padding(horizontal = 16.dp, vertical = 8.dp)`

### Dark Mode
- `secondaryContainer` adjusted to `#635F6A` for better SegmentedButton contrast
- Default M3 dark scheme `secondaryContainer` is too close to surface colors

### Related Items
- Group related inputs in one item block (e.g., carbs + quick-add buttons, carb time + alarm)
- Use `padding(top = 4.dp)` for sub-elements within an item (e.g., food selector below carbs)

## Screens to Update

| # | Screen | Status | Path |
|---|---|---|---|
| 1 | WizardDialogScreen | ✅ Done | `ui/.../wizardDialog/` |
| 2 | TreatmentDialogScreen | ✅ Done | `ui/.../treatmentDialog/` |
| 3 | TempBasalDialogScreen | ✅ Done | `ui/.../tempBasalDialog/` |
| 4 | InsulinDialogScreen | ✅ Done | `ui/.../insulinDialog/` |
| 5 | FillDialogScreen | ✅ Done | `ui/.../fillDialog/` |
| 6 | ExtendedBolusDialogScreen | ✅ Done | `ui/.../extendedBolusDialog/` |
| 7 | CareDialogScreen | ✅ Done | `ui/.../careDialog/` |
| 8 | CarbsDialogScreen | ✅ Done | `ui/.../carbsDialog/` |
| 9 | ProfileActivationScreen | ✅ Done | `ui/.../profileManagement/` |
| 10 | ProfileHelperScreen | ✅ Done | `ui/.../profileHelper/` |
| 11 | InsulinManagementScreen | ✅ Done | `ui/.../insulinManagement/` |
| 12 | DanaUserOptionsScreen | ✅ Done | `pump/dana/.../compose/` |
| 13 | CalibrationDialogScreen | ✅ New | `ui/.../calibrationDialog/` |

## Review Findings (2026-03-28)

### Fixed
- Duplicate comment in NumberInputRow (line 255)
- Dead `SectionHeader` functions removed from CarbsDialog and InsulinDialog
- InsulinDialog confirmation + bolus timestamp: now uses `state.eventTime` instead of `now() + offset`
- CalibrationDialog button: shows BG value + unit when valid
- WizardDialog BG expand: starts collapsed, auto-expands via `LaunchedEffect(bgIsOld)` reactively
