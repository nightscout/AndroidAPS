# Dana RS Pump Emulator - Implementation Plan

## Architecture

**Approach A**: Abstraction layer below BLEComm. Extract `BleTransport` interface that replaces
raw Android Bluetooth classes. BLEComm keeps all its logic (encryption handshake, packet assembly,
message routing) but uses the transport interface. Two implementations:

- `BleTransportImpl` — wraps Android BluetoothAdapter/BluetoothGatt (production)
- `EmulatorBleTransport` — contains PumpEmulator (testing)

This tests the full stack: BLEComm → encryption → packet assembly → transport → emulator → back.

## Key Files

| File                        | Module                | Purpose                                                   |
|-----------------------------|-----------------------|-----------------------------------------------------------|
| `BleTransport.kt`           | :pump:danars          | Interface abstracting BLE operations                      |
| `BleTransportImpl.kt`       | :pump:danars          | Production impl wrapping Android Bluetooth                |
| `BLEComm.kt`                | :pump:danars          | Encryption handshake + packet routing (uses BleTransport) |
| `EmulatorBleTransport.kt`   | :pump:danars-emulator | Test impl routing through PumpEmulator                    |
| `PumpEmulator.kt`           | :pump:danars-emulator | Command processor (opcode → response bytes)               |
| `PumpState.kt`              | :pump:danars-emulator | Mutable pump state, inspectable from tests                |
| `BLECommIntegrationTest.kt` | :pump:danars-emulator | 25 integration tests (full stack)                         |

## Production Changes Made

- `BLEComm`: Removed `internal` from constructor (needed for cross-module test access)
- `BLEComm.sendMessage()`: Added `isReceived` check before `waitMillis(5000)` — prevents 5s
  timeout when response arrives synchronously. Also a production improvement for fast BLE responses.
- `DanaRSModule`: Binds `BleTransportImpl` as `BleTransport` via Dagger

## Phase 0: Interface & Refactoring (in :pump:danars) ✅

- [x] Create `BleTransport` interface
- [x] Create `BleTransportImpl` implementing with Android Bluetooth
- [x] Refactor `BLEComm` to use `BleTransport` instead of raw Bluetooth
- [x] Update DI module to provide `BleTransportImpl`
- [x] Compile and verify (76 existing danars tests pass)

## Phase 1: Emulator Module Structure ✅

- [x] Create `pump/danars-emulator/` module
- [x] `build.gradle.kts` with deps on `:pump:danars`, `:pump:dana`
- [x] Register in `settings.gradle`
- [x] `PumpState` data class — mutable pump state
- [x] `PumpEmulator` — command processor (opcode → response bytes)
- [x] `EmulatorBleTransport` — implements `BleTransport`, wires to PumpEmulator

## Phase 2: Encryption Handshake (DEFAULT v1) ✅

- [x] Emulator handles PUMP_CHECK → responds "OK" (4 bytes)
- [x] Emulator handles CHECK_PASSKEY → responds pairing OK + syncs cfPassKey
- [x] Emulator handles TIME_INFORMATION → responds with time + password + syncs encryption state
- [x] Full handshake test: PUMP_CHECK → CHECK_PASSKEY → TIME_INFORMATION → connected
- [x] Command round-trip after handshake (GET_PROFILE_NUMBER, APS_SET_TEMPORARY_BASAL)

## Phase 3: Core Packets (used in readPumpStatus) ✅

All 13 query packet types implemented in PumpEmulator:

- EtcKeepConnection, GeneralGetShippingInformation, GeneralGetPumpCheck
- BasalGetProfileNumber, BasalGetBasalRate
- BolusGetBolusOption, BolusGetCalculationInformation, BolusGetStepBolusInformation
- BolusGetCIRCFArray / BolusGet24CIRCFArray
- OptionGetUserOption, GeneralInitialScreenInformation
- OptionGetPumpTime / OptionGetPumpUTCAndTimeZone
- APSHistoryEvents ("no history" response)

## Phase 4: Command Packets ✅

All 17 command packet types implemented in PumpEmulator:

- BasalSetTemporaryBasal / APSBasalSetTemporaryBasal, BasalSetCancelTemporaryBasal
- BolusSetStepBolusStart / BolusSetStepBolusStop
- BolusSetExtendedBolus / BolusSetExtendedBolusCancel
- BasalSetProfileBasalRate / BasalSetProfileNumber
- BolusSet24CIRCFArray, GeneralSetHistoryUploadMode
- OptionSetPumpTime / OptionSetPumpUTCAndTimeZone, OptionSetUserOption
- APSSetEventHistory

## Phase 5: Integration Tests (BLEComm + EmulatorBleTransport) ✅

25 JVM unit tests in `BLECommIntegrationTest` testing the full stack:
BLEComm → BleEncryption → EmulatorBleTransport → PumpEmulator → response → BleEncryption → packet
handler

| Category           | Tests  | What's verified                                                                                                               |
|--------------------|--------|-------------------------------------------------------------------------------------------------------------------------------|
| **Handshake**      | 2      | v1 connect + password extraction                                                                                              |
| **Query commands** | 8      | profile number, screen info, basal rate, pump time, keep connection, shipping info, pump check, user options, bolus step info |
| **Temp basal**     | 3      | set, cancel, reflected in screen info                                                                                         |
| **Bolus**          | 2      | start, stop                                                                                                                   |
| **Extended bolus** | 3      | set, cancel, reflected in screen info                                                                                         |
| **Profile update** | 3      | upload rates, activate number, full round-trip                                                                                |
| **History**        | 1      | no-history done response                                                                                                      |
| **Multi-command**  | 2      | 5-command sequence, 8-command readPumpStatus-like                                                                             |
| **Total**          | **25** |                                                                                                                               |

Key design: EmulatorBleTransport processes packets synchronously, so the full handshake completes
within `connect()` and each `sendMessage()` returns immediately. The `isReceived` check in
`sendMessage` prevents the 5s `waitMillis` timeout on already-received responses.

## Test Results (all passing)

- **BLECommIntegrationTest**: 25/25 (full-stack integration via BLEComm, DEFAULT v1)
- **BLECommRSv3IntegrationTest**: 7/7 (full-stack integration, RSv3 encryption)
- **BLECommBLE5IntegrationTest**: 7/7 (full-stack integration, BLE5 encryption)
- **PumpEmulatorTest**: 18/18 (command-level, no encryption)
- **EmulatorBleTransportTest**: 4/4 (encrypted round-trip, no BLEComm)
- **EncryptionDebugTest**: 2/2 (debug helpers)
- **Existing danars tests**: 76/76 (no regressions)
- **Total**: 139 tests

## Encryption Support

All three encryption variants are implemented:

- **DEFAULT (v1)**: Password-based handshake (PUMP_CHECK → CHECK_PASSKEY → TIME_INFO)
- **RSv3**: Pairing-key-based second-level encryption (PUMP_CHECK 9-byte → TIME_INFO with keys)
- **BLE5**: Simple 3-byte key second-level encryption (PUMP_CHECK 14-byte → TIME_INFO)

`EmulatorBleTransport` accepts an `encryptionType` parameter to select the variant.

## Possible Future Work

- [ ] Dana RS Easy (hwModel 0x06) — RSv3 with extra EASY_MENU_CHECK step
- [ ] History events with actual event data (currently only "no history" / "done")
- [ ] Bolus delivery simulation (progress notifications)
- [ ] Error condition tests (wrong password, pump busy, pump error)
- [ ] DanaRSService-level integration tests (requires more DI setup)
- [ ] Notification packet tests (delivery rate display, alarms)

## Packets Used in AAPS (from DanaRSService)

30 packet types total — see Phase 3 (13 query) and Phase 4 (17 command) above.
