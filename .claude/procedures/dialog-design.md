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
- Fallback text: `stringResource(CoreUiStrings.ok)` when disabled

### Card Structure
- Use **2 cards maximum**: one for calculation/info (if applicable), one for all inputs
- All inputs in a single card using the gap-as-divider pattern
- Do NOT create separate cards for each input — group them

### Gap-as-Divider Pattern
- Outer wrapper: `Card(containerColor = surface)` — provides elevation, gap color
- Inner: `Column(verticalArrangement = Arrangement.spacedBy(AapsSpacing.extraSmall))`
- Each item: `itemModifier` = `Modifier.fillMaxWidth().clip(RoundedCornerShape(AapsSpacing.small)).background(surfaceContainer).padding(horizontal = AapsSpacing.extraLarge, vertical = AapsSpacing.medium)`
- No `HorizontalDivider` — the `extraSmall` (2dp) gap shows `surface` color through
- Spacing comes from `AapsSpacing` (`extraSmall` 2dp, `small` 4dp, `medium` 8dp, `large` 12dp,
  `extraLarge` 16dp), never a written dp value. Some older screens still have the dp values; use the
  names when you touch them.
- Define `itemModifier` once and reuse across items

### Primary and secondary inputs
- **Primary** = the number the screen is about. It stays an open `NumberInputRow`: carbs, insulin,
  temp basal rate and duration, extended bolus insulin and duration, calibration BG, fill amount,
  temp target and its duration, profile percentage and duration.
- **Secondary** = everything else. It is folded (see below): wizard BG, correction, percentage and
  carb time, carbs dialog duration and carb time, insulin dialog record-only time, profile timeshift,
  care dialog duration, and care dialog BG for Question and Announcement (open for BG check).
- Why: a second open field that looks like the primary one invites the primary's number into it.
  In the wizard, grams were typed into the correction and bolused as units.
- Settings-style editors (quick wizard editor, automation, scenes, pump options) are not dialogs in
  this sense. Every field there is set on purpose, so they stay open.

### Folded rows
- **A number**: `ExpandableNumberInputRow` (core/ui/compose).
  - Collapsed: `Label: value  (-) (+) (chevron)`. The +/- work without opening.
  - Expanded: the full `NumberInputRow`. The chevron closes it again.
  - `valueContent` when the row must say more than the number (BG age, meter type).
- **A pick from a list** (wizard profile): `Label: value  (chevron)`, chevron in the same column as
  on the number rows. The whole row is the `menuAnchor` of an `ExposedDropdownMenuBox`, so a tap
  anywhere on it opens the list, as wide as the row. The chevron is a plain icon, not a button: a
  second click target inside the anchor toggles the list twice. Do not put a dropdown field behind
  a "Change" button: that is one tap more, and the button breaks the column of chevrons.
- **A date/time only**: `EventTimeRow` (`Label: value [Change]`).
- **Auto-expand** when the value needs attention (wizard: BG older than 9 min, no BG data). Only a
  user's tap scrolls the row into view (`rememberBringIntoViewOnExpand`), not an auto-expand.
- Text styles: label = `labelLarge` + `onSurfaceVariant`, value = `titleMedium` + `onSurface`
- Use `rememberSaveable` for expanded state to survive rotation
- This is different from the arrow expand/collapse pattern (used for "show more info" like calculation details)

### TimeOffsetRow (shared component in core/ui/compose)
- A time as minutes from now, folded at "Now". Built on `ExpandableNumberInputRow`.
- The caller gives the label: "Carb time" (wizard, carbs dialog) or "Time" (insulin record-only).
- Alarm is optional: pass `onAlarmChange` for carb time, leave it null where there is no alarm.
- Shows alarm bell icon when alarm is set and offset > 0
- Alarm toggle disabled (grayed out) when offset <= 0
- Supports `dateTimeContent` slot for date/time pickers (carbs and insulin dialogs)
- No local state — all changes go directly to parent
- Value format: "Now" when offset=0, or "17:55\n(+15 min)" with resolved time

### NumberInputRow Usage
- Direct text input with +/- buttons (no slider mode — use `SliderWithButtons` directly if a slider is needed)
- No baked-in vertical padding — caller controls spacing via `modifier`/`itemModifier`
- Range text shown as plain `Text` below field (not in `supportingText`) — aligned with TextField width
- Empty field when value is 0 (not prefilled with "0")
- Clamp to range on focus loss (not error)
- Free text input accepted (no step rounding on typed values, only +/- buttons use step)

### Calculation Section (Wizard-specific)
- Expandable with arrow icon (show/hide details pattern — different from the folded rows above)
- Result summary always visible in header row
- Toggles (BG, TT, Trend, IOB, COB) always visible using MultiChoiceSegmentedButtonRow
- Default M3 styling for toggle selection (no custom icon tints)
- `checked` must account for `enabled` conditions (e.g., `checked = uiState.useTT && uiState.hasTempTarget`)

### Space Savings
- Top-level `verticalArrangement = Arrangement.spacedBy(AapsSpacing.medium)` between cards
- Bottom spacer: `AapsSpacing.medium`
- No extra vertical padding in NumberInputRow
- Card internal padding: `padding(horizontal = AapsSpacing.extraLarge, vertical = AapsSpacing.medium)`

### Dark Mode
- `secondaryContainer` adjusted to `#635F6A` for better SegmentedButton contrast
- Default M3 dark scheme `secondaryContainer` is too close to surface colors

### Related Items
- Group related inputs in one item block (e.g., carbs + quick-add buttons, carb time + alarm)
- Use `padding(top = AapsSpacing.small)` for sub-elements within an item (e.g., food selector below carbs)

## Screens that follow these rules

All of them already do. Use them as examples, and keep them in line when you change the rules.

| Screen | Path |
|---|---|
| WizardDialogScreen | `ui/.../wizardDialog/` |
| TreatmentDialogScreen | `ui/.../treatmentDialog/` |
| TempBasalDialogScreen | `ui/.../tempBasalDialog/` |
| InsulinDialogScreen | `ui/.../insulinDialog/` |
| FillDialogScreen | `ui/.../fillDialog/` |
| ExtendedBolusDialogScreen | `ui/.../extendedBolusDialog/` |
| CareDialogScreen | `ui/.../careDialog/` |
| CarbsDialogScreen | `ui/.../carbsDialog/` |
| ProfileActivationScreen | `ui/.../profileManagement/` |
| ProfileHelperScreen | `ui/.../profileHelper/` |
| InsulinManagementScreen | `ui/.../insulinManagement/` |
| DanaUserOptionsScreen | `pump/dana/common/.../compose/` |
| CalibrationDialogScreen | `ui/.../calibrationDialog/` |
