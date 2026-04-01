# RileyLink Pairing Wizard — Compose Migration

## Goal

Replace `RileyLinkBLEConfigActivity` (legacy Activity) with a shared Compose wizard using existing
`BleScanStep` + `WizardScreen` infrastructure. Add "Pair RileyLink" button to Eros overview.
Remove RL pairing from preferences.

## Scope

- Shared wizard in `pump:rileylink` module
- Integration into Eros overview (already Compose)
- Medtronic integration deferred (separate plan)

## Phase 0: Prerequisites

- [ ] Create `RileyLinkBleScanner` implementing `BleScanner` interface
    - UUID filter: `GattAttributes.SERVICE_RADIO`
    - Disconnect current RL before scanning (so it's discoverable)
    - Reconnect on stop
    - 15s scan timeout
    - File: `pump/rileylink/.../ble/RileyLinkBleScanner.kt`

## Phase 1: Wizard ViewModel

- [ ] Create `RileyLinkPairWizardViewModel` in `pump/rileylink/.../compose/`
    - Collect `scannedDevices` from `RileyLinkBleScanner`
    - On device selected: save MAC + name to preferences, call `verifyConfiguration(true)`,
      trigger pump config changed event
    - No remove action — user just pairs a new device to replace
    - File: `pump/rileylink/.../compose/RileyLinkPairWizardViewModel.kt`

## Phase 2: Wizard Screen

- [ ] Create `RileyLinkPairWizardScreen` composable
    - Single step: `BleScanStep` — scan for devices, tap to select
    - On device tap: save MAC + name, trigger reconnect, close wizard
    - No finish/confirm step needed — just pick and done
    - File: `pump/rileylink/.../compose/RileyLinkPairWizardScreen.kt`

## Phase 3: Eros Integration

- [ ] Add "Pair RileyLink" button to `ErosOverviewViewModel.buildManagementActions()`
- [ ] Add `ShowRileyLinkPairWizard` event to `OmnipodOverviewEvent`
- [ ] Handle wizard navigation in `OmnipodErosComposeContent`
    - New `showRileyLinkPairWizard` state, render wizard when true

## Phase 4: Cleanup

- [ ] Remove `RileyLinkBLEConfigActivity` preference integration from `OmnipodErosPumpPlugin`
    - Remove `RileyLinkIntentPreferenceKey.MacAddressSelector` from Eros preferences
- [ ] Verify RL pairing still works end-to-end (user test)

## Notes

- `BleScanStep` needs no changes — UUID filtering happens in scanner, not UI
- `BleScanner` interface needs no changes — each pump provides own implementation
- `RileyLinkBLEConfigActivity` kept alive until Medtronic also migrated, then deleted
