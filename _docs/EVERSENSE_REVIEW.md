# Eversense driver (PR #5536): review and plan

Working notes for bringing the Eversense E3 / 365 driver from PR #5536 into AAPS. Written 2026-11-20.

> The glucose-data findings below are not fixed yet. Fix them, or cut this file down to the plan, before the
> `eversense` branch is pushed (see "Never put sensitive content in this public repo" in `CLAUDE.md`).

## Where it is

- Local branch `eversense` = `carelevo` + the latest `dev` + PR #5536 (author BernhardRo, fork branch `dev_es365`).
  The PR on GitHub targets the branch `eversense`, which on GitHub is still a plain copy of `dev`.
- Driver module: `:plugins:source:eversense` (was `:plugins:eversense` in the PR). Source adapter, helpers and the
  three Activities are in `:plugins:source` (`EversensePlugin`, `eversense/`, `activities/`). Calibration dialog in
  `:ui` (`eversenseCalibrationDialog`). Author's notes: `plugins/source/eversense/TECHNICAL.md` and `README.md`.

## Findings

### Tests that fail today

The PR's own CI on `dev` fails, and so does the full gate here.

- `app`: `ContributedPluginsTest`, `GraphScopeStabilityTest`, `PluginLifetimeWorkScanTest`, `PumpDriverBucketTest`,
  `ViewModelOwnershipTest` (`EversenseCalibrationDialogViewModel` injects `EversenseCalibrationDynamics`, which
  nothing owns).
- `plugins:source`: all `GlunovoPluginTest` and `IntelligoPluginTest`. With the Eversense module on the classpath,
  `Uri.parse` returns null in the unit tests and `IntelligoPlugin.<init>` fails.

### Glucose data

- A reading with a time in the future is accepted, stored and kept as `recentGlucoseDatetime`
  (`Eversense365Communicator.readGlucose`, `EversenseE3Communicator.readGlucose` only check `<= recent`). Real
  readings are then refused as "still recent" until the clock passes that time.
- The 365 decoder has no lower bound (`GlucoseDecoders`: only `>= 450` is refused; E3 takes 20..450). A 0 or an error
  value can be stored as glucose. The same goes for 365 history (`GetGlucoseLogValuesPacket`).
- `recentGlucoseDatetime` is saved before the reading is delivered (`EversenseCGMPlugin.watchersFor` posts it only
  while the session is active). A link drop in between loses that reading; E3 has no backfill and the 365 backfill
  window starts after it.
- `SourceSensorExtensions.ADVANCED_FILTERING_SENSORS` gains `EVERSENSE_E3` and `EVERSENSE_365`, with no reason given.
  This changes which loop features Eversense users get.
- The state JSON is read, changed and written from several threads (`onRssiRead`, `readSignalStrength`,
  `sendCalibration`, `fullSync`). An old write can move `recentGlucoseDatetime` back and give duplicates.
- The data watchdog counts only new readings, so a sensor with no new value for 7 minutes is reconnected every cycle.

### Effects on other features

- `EversensePlugin.onStateChanged` writes `IntKey.OverviewSageWarning` / `OverviewSageCritical` on every update: the
  user's own SAGE limits are overwritten and stay after a change of CGM.
- `NotificationId.EVERSENSE_ALARM` is one card per id: a later battery or expiry notice replaces an open urgent
  alarm, and nothing removes the card when the alarm ends. `EVERSENSE_CREDENTIALS` and `EVERSENSE_PLACEMENT` are
  posted at another level than their declared one.
- `QuickLaunchResolver`: the "Eversense Calibration" action is offered to every user, whatever the CGM.
- `LongNonKey.EversenseLastCalibrationSubmittedAt` puts a device key into `core:keys` only so that `ui` can read it.
- `StringPreferenceKey.requiresPasswordConfirmation` (default true): only the Eversense password skips confirmation.

### Project rules

- 16 translated `values-xx/strings.xml` files are edited (English only, translations come from Crowdin).
- About 40 of the 113 `eversense_*` strings are not used.
- Two enums named `EversenseStringKey` in one module; fully qualified names in `EversensePlugin`; unused
  `dateFormatter`; `System.currentTimeMillis()` instead of `DateUtil`.
- Help link points to the author's fork.
- `EversenseCalibrationDialogScreen`: plain `Card`, hard-coded `20.dp`, a unit joined to a number in code, no preview.
- Dependencies by hard-coded coordinates (Bouncy Castle 1.81, OkHttp mockwebserver 4.12.0); logback-android and slf4j
  added as `api`. `plugins:source` takes the driver as `api`, so every user of `plugins:source` gets all of it.

### Architecture

- Three `MetroAppCompatActivity` screens (status, calibration, placement) outside the app navigation, and a second
  calibration screen next to the shared dialog.
- `EversensePlugin` holds driver work: expiry, battery and calibration notices, credential sync, SAGE sync, cloud
  upload. It also creates its `ioScope` twice and never cancels the first.
- Calibration: `EversenseCalibrationDialogViewModel.submit` truncates the mg/dL value (`toInt()`, 2.2 mmol/L gives 39);
  `EversensePlugin.calibrate` has no range check; the dialog's coroutine has no `catch`.
- The DMS certificate request has no timeout, and registration can hold the only Bluetooth thread for up to 130 s.
- `EversenseConnectionController.attempt` catches only `SecurityException`; anything else leaves it in CONNECTING.

### Tests that are missing

The two communicators (stale, duplicate, future readings; order of save and delivery; backfill window; full-sync
rules), `RangeCalculator`, `EversenseCrypto365Util` (counter, shortcut flag), calibration range, `QuickLaunchResolver`
with the Eversense action, the calibration banner countdown, `EversensePlugin.onStateChanged` notices.

`src/androidHostTest/resources/protocol-reference.zip` holds 156 frozen source files that a Gradle task compiles into
the host tests, to compare the refactored driver with the old one. It proves "same as before", not "right", and it
also pins the old faults. Keep it until the crypto is moved, then replace it with hex fixtures (frames, decoded values,
ciphertexts) and delete the zip, `generateProtocolReference` and `ReferenceIntegrityTest`.

## Moving it to shared code (KMP)

The module already uses `kmp-android-library` (iOS and JVM targets declared); all code is in `androidMain`.

| Need | Use |
|---|---|
| Bluetooth | `BleCentral` / `BleLink`; `EmulatedBleCentral` in tests |
| Crypto | cryptography-kotlin, as `ClientControlKeyExchangeCrypto` |
| HTTP (DMS) | ktor `HttpClient` with a per-platform engine; `MockEngine` in tests |
| Timers, threads | coroutines, `Mutex`, `withTimeout`, `CompletableDeferred` |
| Storage | `EversenseStore` (already a port), backed by `Preferences` |
| Time | kotlinx-datetime and `DateUtil` |
| Logging | `AAPSLogger` (`LTag.BGSOURCE`); drop slf4j and logback |
| Alerts | `NotificationManager` |

Crypto for 365: ECDSA P-256, ECDH P-256, HKDF-SHA256 and random are in cryptography-kotlin. **AES-CCM is not**
(no provider has it on Android, desktop or iOS): write RFC 3610 once in commonMain over AES-ECB, with 16-byte key,
8-byte nonce, 64-bit tag, the 2-byte prefix as associated data, and pin it with test vectors.

`BleCentral` v2 covers discovery, notifications, MTU and write types. Two changes: a SecureV2 frame longer than the
write length goes out as one write per chunk, and the 365 "reuse the same GATT handle" after status 19 becomes a new
connect (the full handshake already runs each time). On iOS the device id is a UUID, not a MAC.

## Emulator

An `EversenseTransmitterPeripheral : EmulatedPeripheral`, switched on by an `EMULATE_EVERSENSE` option file, as
Carelevo. E3: CRC16-CCITT frames, about 45 commands, a register map. 365: chunked frames, WhoAmI, identity, the
ECDH start and AES-CCM after it. A fake DMS (ktor `MockEngine`) for registration and upload. The pump scenario
classes are built around the command queue, so this needs its own small CGM scenario class.

Built from the code and not from captures, so it is `UNVERIFIED` until real frames are recorded.

First scenarios: a reading every 5 minutes (limits, trend, time); 365 gap and backfill; registration, offline
reconnect, transmitter forgot the client; calibration accepted and refused; push alarm; link drop and back-off;
Bluetooth off and on; a lost answer; app restart.

## Steps

1. Module move to `:plugins:source:eversense`. Done.
2. Install on the test phone, look at the UI, test with a real transmitter as the PR is, record frames.
3. Cleanup and alignment with AAPS: the failing tests, the glucose-data findings, the effects on other features,
   the rules, calibration through the AAPS paths, the missing tests.
4. KMP: pure files, then crypto with our own CCM, logger, DMS on ktor, a common session on `BleCentral` (test on the
   real transmitter after this one).
5. Emulator, fake DMS and the scenario tests.
6. iOS registration and its tests in `ios-ci.yml`.

## Open decisions

- Where the plugin lives: all of `EversensePlugin`, its helpers and screens in `:plugins:source:eversense`, as a pump
  driver has one module, so that `plugins:source` no longer depends on it.
- Advanced filtering for Eversense: remove or keep.
- `protocol-reference.zip`: keep until step 4, then replace with fixtures.
