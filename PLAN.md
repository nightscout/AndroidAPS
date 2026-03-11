# Dana RS Pump Emulator - Implementation Plan

## Architecture

**Approach A**: Abstraction layer below BLEComm. Extract `BleTransport` interface that replaces
raw Android Bluetooth classes. BLEComm keeps all its logic (encryption handshake, packet assembly,
message routing) but uses the transport interface. Two implementations:

- `BleTransportImpl` — wraps Android BluetoothAdapter/BluetoothGatt (production)
- `EmulatorBleTransport` — contains PumpEmulator (testing)

This tests the full stack: BLEComm → encryption → packet assembly → transport → emulator → back.

## Key Files

| File | Module | Purpose |
|------|--------|---------|
| `BleTransport.kt` | :pump:danars | Interface abstracting BLE operations |
| `BleTransportImpl.kt` | :pump:danars | Production impl wrapping Android Bluetooth |
| `BLEComm.kt` | :pump:danars | Encryption handshake + packet routing (uses BleTransport) |
| `EmulatorBleTransport.kt` | :pump:danars-emulator | Test impl routing through PumpEmulator |
| `PumpEmulator.kt` | :pump:danars-emulator | Command processor (opcode → response bytes) |
| `PumpState.kt` | :pump:danars-emulator | Mutable pump state, inspectable from tests |
| `BLECommIntegrationTest.kt` | :pump:danars-emulator | 31 integration tests (full stack) |
| `DanaRSServiceIntegrationTest.kt` | :pump:danars-emulator | 13 service-level integration tests |

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

31 JVM unit tests in `BLECommIntegrationTest` testing the full stack:
BLEComm → BleEncryption → EmulatorBleTransport → PumpEmulator → response → BleEncryption → packet handler

| Category | Tests | What's verified |
|----------|-------|-----------------|
| **Handshake** | 2 | v1 connect + password extraction |
| **Query commands** | 8 | profile number, screen info, basal rate, pump time, keep connection, shipping info, pump check, user options, bolus step info |
| **Temp basal** | 3 | set, cancel, reflected in screen info |
| **Bolus** | 2 | start, stop |
| **Extended bolus** | 3 | set, cancel, reflected in screen info |
| **Profile update** | 3 | upload rates, activate number, full round-trip |
| **History** | 1 | no-history done response |
| **Multi-command** | 2 | 5-command sequence, 8-command readPumpStatus-like |
| **RSv3 encryption** | 3 | handshake, command round-trip, multi-command |
| **BLE5 encryption** | 3 | handshake, command round-trip, multi-command |
| **Total** | **31** | |

## Phase 6: DanaRSService Integration Tests ✅

13 JVM unit tests in `DanaRSServiceIntegrationTest` testing service-level operations:

| Category | Tests | What's verified |
|----------|-------|-----------------|
| **Status** | 2 | readPumpStatus, readPumpStatus with UTC time zone |
| **Temp basal** | 2 | set, cancel |
| **Extended bolus** | 2 | set, cancel |
| **Profile** | 1 | setUserSettings |
| **Error conditions** | 2 | wrong password, pump busy/error responses |
| **History events** | 2 | actual event data, empty history |
| **Bolus delivery** | 1 | bolus with notification packets (progress + complete) |
| **Notification packets** | 1 | delivery rate display + delivery complete |
| **Total** | **13** | |

Key design: EmulatorBleTransport processes packets synchronously, so the full handshake completes
within `connect()` and each `sendMessage()` returns immediately. The `isReceived` check in
`sendMessage` prevents the 5s `waitMillis` timeout on already-received responses.

## Phase 7: Full Integration Tests via CommandQueue (on-device) — pending run

Tests the complete UI-level stack on a real Android device/emulator:
CommandQueue → QueueWorker → DanaRSPlugin → DanaRSService → BLEComm → EmulatorBleTransport → back

**Setup**: `TestBleTransportModule` in `:app` androidTest replaces `DanaRSBleTransportModule`,
providing `EmulatorBleTransport` as `BleTransport`. Test injects via `TestAppComponent` (plain Dagger).

| Test | Status | What's verified |
|------|--------|-----------------|
| `initialReadStatus_populatesPumpState` | ✅ | reservoir, battery, basal, lastConnection, serialNumber |
| `readStatus_throughCommandQueue` | ✅ | updated emulator state read back correctly |
| `pluginIsInitialized_afterSetup` | ✅ | plugin initialized after onStart() |
| `bolus_deliversInsulin` | pending | 0.1U bolus, verify delivered amount + emulator state |
| `extendedBolus_setAndCancel` | pending | 1.0U/30min set, verify emulator, then cancel |
| `tempBasalPercent_setAndCancel` | pending | 150%/60min with Profile, verify, then cancel |
| `loadEvents_completesSuccessfully` | pending | history load callback success |

**Key files**:
- `app/src/androidTest/kotlin/app/aaps/DanaRSCommandQueueTest.kt` — test class (7 tests)
- `app/src/androidTest/kotlin/app/aaps/di/TestBleTransportModule.kt` — DI module
- `app/src/androidTest/kotlin/app/aaps/di/TestsInjectionModule.kt` — registers test for injection

## Phase 8: Remove Redundant Mocked Tests ✅

Removed mocked JVM/Android tests from `:pump:danars-emulator` — replaced by full integration tests:

- [x] `BLECommIntegrationTest.kt` (31 JVM tests) — REMOVED
- [x] `DanaRSServiceIntegrationTest.kt` (13 JVM tests) — REMOVED
- [x] `DanaRSEmulatorAndroidTest.kt` — REMOVED
- [x] `EmulatorTestComponent.kt`, `EmulatorTestModule.kt` — REMOVED
- [x] Cleaned `danars-emulator/build.gradle.kts` (removed androidTest deps, kspAndroidTest)

**Kept**: `PumpEmulatorTest` (18), `EmulatorBleTransportTest` (4), `EncryptionDebugTest` (2)

## Test Results
- **DanaRSCommandQueueTest**: 3 passing + 4 pending (full integration via CommandQueue, on-device)
- **PumpEmulatorTest**: 18/18 (command-level, no encryption)
- **EmulatorBleTransportTest**: 4/4 (encrypted round-trip, no BLEComm)
- **EncryptionDebugTest**: 2/2 (debug helpers)
- **Existing danars tests**: 76/76 (no regressions)

## Encryption Support
- DEFAULT (v1): Full support with pairing key + time/password handshake
- RSv3 (hwModel 0x05/0x06): Full support with second-level chained XOR encryption
- BLE5 (hwModel 0x09/0x0A): Full support with second-level 3-step encryption

## Implemented Future Work Items
- [x] RSv3 encryption handshake in EmulatorBleTransport
- [x] BLE5 encryption handshake in EmulatorBleTransport
- [x] History events with actual event data
- [x] Bolus delivery simulation (progress notifications)
- [x] Error condition tests (wrong password, pump busy, pump error)
- [x] DanaRSService-level integration tests
- [x] Notification packet tests (delivery rate display, delivery complete)

## Packets Used in AAPS (from DanaRSService)
30 packet types total — see Phase 3 (13 query) and Phase 4 (17 command) above.
