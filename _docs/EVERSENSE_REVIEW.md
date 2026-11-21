# Eversense driver (PR #5536): review and plan

Working notes for bringing the Eversense E3 / 365 driver from PR #5536 into AAPS. Written 2026-11-20.

> The glucose-data findings below are not fixed yet. Fix them, or cut this file down to the plan, before the
> `eversense` branch is pushed (see "Never put sensitive content in this public repo" in `CLAUDE.md`).

## Where it is

- Local branch `eversense` = `carelevo` + the latest `dev` + PR #5536 (author BernhardRo, fork branch `dev_es365`).
  The PR on GitHub targets the branch `eversense`, which on GitHub is still a plain copy of `dev`.
- Everything Eversense is in one module, `:plugins:source:eversense` (was `:plugins:eversense` in the PR), as a pump
  driver has one: the transmitter driver and the AAPS plugin with its screens, keys, strings and tests
  (`EversensePlugin`, `ui/`, `placement/`, `cloud/`, `storage/`, `keys/`, `di/`). It depends on `:plugins:source` for
  the shared BG source base; `:plugins:source` does not depend on it. `:appshell` picks it up with the other
  `:plugins:*` modules, and `ios/shell` lists it. Author's notes: `plugins/source/eversense/TECHNICAL.md`; the user
  guide moves to the wiki.

## Findings

### Tests that fail today

The PR's own CI on `dev` failed, and so did the full gate here.

- Fixed by moving the plugin into its own module: `GlunovoPluginTest`, `IntelligoPluginTest` and the `app` graph tests
  (`ContributedPluginsTest`, `GraphScopeStabilityTest`, `PumpDriverBucketTest`). With the driver module on
  `:plugins:source`'s classpath, `Uri.parse` returned null and `IntelligoPlugin.<init>` failed. `ContributedPluginsTest`
  then also needed Eversense's order key 415.
- Fixed earlier: `ViewModelOwnershipTest` (the PR's calibration dialog is gone).
- Fixed by the lifecycle step (3.1): `PluginLifetimeWorkScanTest`, for the `Handler` that `EversensePlugin` created at
  construction. Nothing of the PR fails any more.

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

1. Module move to `:plugins:source:eversense`, then the plugin, screens, keys and strings into it too. Done.
2. Install on the test phone, look at the UI, test with a real transmitter as the PR is, record frames.
3. Cleanup and alignment with AAPS. Done so far: calibration through the AAPS paths (`CalibrationMode.SOURCE`), its own
   screen opened from Manage (`CgmDriver`). Agreed next, each its own commit:
   1. **Lifecycle.** Done. `onStart` builds a `Running` (coroutines, cloud upload, counters) and loads the login into
      the driver, which reads the transmitter and keys from its store; `onStop` drops it whole, stops the driver (link,
      scan, retries, timers, failure counters, login in memory) and clears the notices the plugin itself would clear.
      A late callback after the stop finds no `Running` and does nothing. Bluetooth back on comes from the shared
      `BleCentral.adapterState`, not an Android receiver; the permission is asked on the app's permission sheet
      (`bluetoothPermissionGroup()`, now public), not by an Activity at start. Reload test as a pump's.
   2. **Pairing wizard, like a pump's.** Done. The account step asks which Eversense (E3, 365 USA, 365 other
      countries) instead of a region switch: the E3 and the two 365 regions sign in at different servers, and the model
      is known only after a connection that a 365 cannot make without the login. Later logins record a refusal
      (`StorageKeys.LOGIN_REFUSED`), so a password changed on the Eversense side shows as "sign in again". The PR's
      `requiresPasswordConfirmation` and the `EVERSENSE_CREDENTIALS` notification are gone with the login settings.
      The plan as agreed: Before you start (the official app is set up and force stopped, Bluetooth
      permission) - Account (username, password, region; "Sign in" makes a real DMS login; with a stored login it
      shows "Signed in as ..." with Continue and Sign out) - Transmitter (10 s scan, pick one) - Connect
      (authentication, "Registering online..." for a 365, done when the link is ready). The setup text of the README
      goes into the steps, so Crowdin translates it. The login leaves the preferences for the driver's own storage and
      is not exported; cloud upload and diagnostic logging stay preferences. Unpair on the status tab forgets the
      transmitter and its keys and keeps the login. `CgmDriver.attention` (not paired, sign-in failed) puts a "!" on
      the bottom bar's BG source button, and the status tab shows the reason and the button that fixes it; not on a
      client. Screens and view models in `commonMain`, behind a driver interface, so iOS can follow.
   3. **Clean-up.** Done, except the wiki draft, which waits until the wizard was tried on a phone. Also removed as
      dead: the "Default sync days" dropdown (the driver never read the value), the never-read
      `EversenseCloudUploadToast` setting, `forceReconnect()`, `activeSessionStore()`, and 64 unused strings. As agreed:
      Connect, Disconnect, Change Transmitter and the Documentation button go: the link follows the
      plugin, and the user documentation moves to the wiki (draft for AndroidAPSdocs once the wizard is final).
      `README.md` and its screenshots go; `TECHNICAL.md` stays.

   Review of the branch after 3.3 (calibration work included), fixed:
   - The calibration reset on a BG source switch never ran from the Configuration screen: it compared the sources
     before `storeSettings` made the new one active. The test now changes the source the way `PluginStore` does.
   - The Linear calibration was switched off only at an election. A source that learns its mode later (the
     notification reader at its first reading, a vendor app installed) left it fitting calibrated values; `calibrate()`
     now checks the rule too.
   - `com.dexcom.g6.region3.mmol` was missing from the catalog (bundled one and the `versions` copy now version 2).
   - A `calibration` place unknown to an older AAPS read as "AAPS may calibrate"; it now reads as `OTHER` (calibrated
     elsewhere). A fetched catalog without an id the code asks for is not used.
   - The Manage sheet's "not on a client" check for the CGM screen had become empty.
   - Eversense refuses a calibration with no transmitter before the confirmation, instead of waiting 30 s.
   - The master refuses a calibration outside the dialog's range (an SMS `CAL 100` with mmol/L).
   - Four `EversensePluginTest` tests had never run (JUnit 5 skips a test that returns a value).

   - The SMS reply for an xDrip calibration had lost the "xDrip+ must accept calibrations" hint. A BG source now has a
     `calibrationNote()`; the master shows it in the confirmation and the SMS reply repeats it.

   Known: with xDrip+ and Juggluco both installed the calibration goes to xDrip+ (as before this branch).

   Then the rest of the list above: the glucose-data findings, the effects on other features, the rules, the missing
   tests.
4. KMP: pure files, then crypto with our own CCM, logger, DMS on ktor, a common session on `BleCentral` (test on the
   real transmitter after this one).
5. Emulator, fake DMS and the scenario tests.
6. iOS registration and its tests in `ios-ci.yml`.

## Open decisions

- Advanced filtering for Eversense: remove or keep.
- `protocol-reference.zip`: keep until step 4, then replace with fixtures.
