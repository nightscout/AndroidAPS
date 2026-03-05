# Insulin Concentration - Compose Migration Plan

## Status: Pending

## Context

Insulin concentration feature was originally built on legacy XML UI.
After rebasing on `compose` branch, some dialogs have both XML + Compose versions,
but the core concentration workflow is XML-only.

## Current State

### Both XML + Compose (already done)

- **Insulin Dialog** (bolus entry) — Compose version has concentration support
- **Fill Dialog** (prime/fill) — Compose version has concentration support via `ConcentrationHelper`

### XML-only (needs Compose migration)

- **ConcentrationDialog** (`dialog_concentration.xml`, `ConcentrationDialog.kt`,
  `ConcentrationFragment.kt`, `ConcentrationActivity.kt`)
    - Concentration approval/change workflow
- **InsulinSwitchDialog** (`dialog_insulinswitch.xml`, `InsulinSwitchDialog.kt`)
    - Insulin type selection dropdown, displays concentration/peak/DIA
- **InsulinNewFragment** (`insulin_new_fragment.xml`, `InsulinNewFragment.kt`)
    - Insulin plugin management screen: insulin/concentration selection, peak/DIA editing,
      ActivityGraph

### Shared infrastructure (no migration needed)

- `ConcentrationHelper` / `ConcentrationType` — interfaces used by both XML and Compose
- `ConcentrationHelperImpl` — implementation

## Concentration Workflow (XML-based)

1. User triggers concentration change
2. `ConcentrationActivity` → `ConcentrationFragment` → `ConcentrationDialog`
3. On confirmation → `InsulinSwitchDialog` (select insulin)
4. Then → `ProfileSwitchDialog` (profile changes)
5. `EventConcentrationChange` broadcast

## Notes

- `WizardInfoDialog` injector was added during rebase but removed — verify if still needed
- `UiModule.kt` has Dagger injectors for ConcentrationDialog, ConcentrationActivity,
  InsulinSwitchDialog
- These Dagger injectors will remain needed until Compose migration is complete
