# CGM driver design

How a CGM driver, and every BG source, does the parts they share. `CGM_DRIVER_ACCEPTANCE.md` says what must pass;
this file says how to build it. When the two disagree, the acceptance file wins and this file is fixed. It is the CGM
side of `PUMP_DESIGN.md` and `PUMP_DRIVER_ACCEPTANCE.md`, and follows them where a CGM works the same way.

**Status (2026-11-21):** sections 1, 2, 4 and 6 describe the code as it is, from the Eversense driver
(`:plugins:source:eversense`, `plugins/source/eversense/TECHNICAL.md`). Sections 3 and 5 are decided and not built;
section 7 is a design, not built. The decisions are in section 9.

## 1. Two kinds of BG source

| Kind | What it does | Today |
|---|---|---|
| Receiver | Another app talks to the sensor. AAPS gets the readings by broadcast, content provider or notification. | xDrip, BYODA, Glimp, Glunovo, Intelligo, Instara, the notification reader and others. Android only. |
| CGM driver | AAPS talks to the transmitter itself, over Bluetooth. It owns the link, the pairing, the calibration and the alarms. | Eversense E3 and 365. Android and iOS. |

A CGM driver holds the only link the transmitter allows, so the vendor app is stopped while AAPS runs it. **AAPS is
then the only app that can warn the user**, about the glucose and about the transmitter. This is the main
difference from a receiver, and the reason for section 5.

Section 3 is for every source. The rest of this file is for CGM drivers.

## 2. The source contract

### What a source implements

| What | How | Notes |
|---|---|---|
| The source | `BgSource` and `PluginBase` with `PluginType.BGSOURCE` | Every member of `BgSource` has a default; a source overrides what it knows. |
| Sensor lifetime | `BgSource.sensorLifetimeHours` | Only when the source knows its sensor model. The master keeps it in `IntNonKey.SensorLifetimeHours`, synced to the clients, and the sensor age colours follow `SensorLifetime` on every phone. The manual limits are hidden while it is known. |
| Battery | `BgSource.sensorBatteryLevel` | -1 when the source does not have it. |
| Calibration | `calibrationMode`, and in `CalibrationMode.SOURCE` also `calibrationRangeMgdl`, `calibrationRefusal`, `calibrationNote`, `calibrate` | The AAPS dialog takes the value; the source passes it on. |
| Vendor app | `appPackages` | What the CGM button opens. Empty for a driver. |

A CGM driver also has, as a pump driver does:

| What | How |
|---|---|
| Its screen | `ComposablePluginContent`: the status in the pumps' layout (`PumpOverviewScreen`: banner, value rows, picture, buttons at the bottom), the pairing wizard and its other steps (Eversense: the placement guide) as routes of the same screen. No Activity of its own. |
| Its registration | A binding container per platform that has the ports (Eversense: `EversenseAndroidRegistration`, `EversenseIosRegistration`). The desktop has none. |
| Its emulator | An emulated transmitter on `EmulatedBleCentral`, switched on by an option file (Eversense: `emulate_eversense`). Section 7. |

### What a source uses

- `PersistenceLayer.insertCgmSourceData(caller, glucoseValues, calibrations, sensorInsertionTime)`, once per batch.
  Storage keeps one row per time and sensor: the same reading again updates the row, it does not add one. A sensor
  insertion time records the sensor change.
- A CGM driver also uses:
  - `BleCentral` (Bluetooth version 2). It has no Bluetooth code of its own.
  - `BleScanStep` and `WizardScan` for the scan.
  - `BleBonding` to remove the bond.
  - `Alerts` for its alarms (section 5). `NotificationManager` is only for plain cards that are not alarms.
  - Its own store for the login and the keys, not the AAPS preferences.

### Rules

- **A value the source does not have is "none"** (0, -1 or null), never a made-up value. A 365 with no sensor sends
  its zero time (2000-01-01) as the insertion date; stored as a sensor change, it gave a wrong sensor age.
- **The time of a reading is the sensor's time**, not the time it arrived.
- **A source does not write the user's settings.** Eversense wrote the sensor age limits (8040 h, above their maximum of
  720 h), and they stayed after a change of CGM. A source reports what it knows (`sensorLifetimeHours`), and core decides.
- **A client sees what the master sees.** A value the overview shows (the sensor lifetime colours) is synced from the
  master, not worked out on each phone.

## 3. Reading checks in one place (decided 2026-11-21, not built)

Today each source checks something different. Eversense refuses a reading more than 2 minutes ahead of the phone.
Glunovo and Intelligo refuse any time in the future. Dexcom and xDrip check only the sensor start time. Storage
checks neither the time nor the value.

A reading dated in the future does harm in every source: as the newest reading, it makes every real reading look
"not newer" until the clock passes it, and the loop runs on it. So the checks every source needs belong in one
place, `insertCgmSourceData`, tested once, instead of in each source:

- A reading more than 2 minutes ahead of the phone is not stored, and is logged. The 2 minutes allow for a
  transmitter clock that is a little ahead.
- A sensor insertion time that is 0 or in the future records no sensor change.
- A reading stored twice is one row. Storage does this already.

What stays in the source: whether a value is a reading at all. Only the source knows its sensor's range and its
error codes ("glucose unavailable", 39 for LOW), so it does not pass those on as readings.

Open: whether the 2-minute margin suits every receiver (section 9). Check it for each receiver when this is built.

## 4. A CGM driver

### The link

- The driver and its session run on one thread (Eversense: one dispatcher with parallelism 1). The session lives in
  the link's scope and ends with it, so nothing of an old link runs on.
- Connection rules (Eversense, from the first version):
  - A connection attempt has 180 s to sign in.
  - A lost link is tried again after 5 s, doubling up to a minute.
  - A link with no data for 7 minutes is replaced. A keep-alive counts as data.
- **iOS runs no timer for an app in the background.** There a link lost by the radio is connected again at once, with
  a connect that waits for the transmitter with no time limit (`reconnectWithoutTimers`).
- A platform value the rules depend on is a port, and the driver takes it as a parameter, so its tests run the same
  on every platform. One example is the status of a link the transmitter closed: 19 on Android, 7 on iOS.
- After a gap, the driver reads the missing readings from the transmitter's history, each one once.

### Pairing and unpairing

- The wizard uses the shared steps. The scan looks for the transmitter's **service UUID, never its name**: a test
  transmitter advertised as `D313640`, and every name rule missed it. The scan ends by itself after 30 s, and at once
  when Bluetooth is off or not allowed (`WizardScan`).
- The pairing consent of Android can stay open for a while, so the first subscribe waits 30 s.
- Unpair closes the link and removes the phone's bond where the platform can (Android). iOS cannot; the driver keeps
  the transmitter's name until the next pairing and tells the user to forget it in the Bluetooth settings.

### Calibration

`CalibrationMode.SOURCE`: the AAPS dialog, limited to `calibrationRangeMgdl`, then `BgSource.calibrate`, which sends it
to the transmitter. A reason to refuse (`calibrationRefusal`) is shown before the confirmation. A value the
transmitter refuses is a failed calibration, not a lost link.

### Vendor account and cloud

- The login is a step of the wizard and is kept in the driver's store.
- An upload to the vendor cloud is the user's choice, and never runs from an emulator.
- A registration that needs the vendor server (the first pairing of a 365) sends nothing while offline, neither to
  the server nor over Bluetooth.

## 5. CGM alarms through the alert core (decided 2026-11-21, built for Eversense 2026-11-24)

A CGM driver raises its transmitter's alarms through the alert core, as a pump driver does (`PUMP_DESIGN.md`, section
2). The core then shows, rings, silences, raises again after a restart and tells the watch, with no code of the
driver's own. The core is `Alerts` (named `PumpAlerts` until 2026-11-21; `ALARMS.md`, section 6); `source` is the
driver's id (`"eversense"`).

**The driver raises its device and error states only:** sensor failed or expired, battery, calibration, transmitter
off the sensor. **It raises no glucose alarm** (low, high, falling or rising fast, predictive) **and no
missed-readings alarm.** AAPS's own BG alarms raise those in the core, from the stored readings, for every source
(`ALARMS.md`, section 6). Where the two overlap (the transmitter says there is no glucose), the driver's alert says
why, and the core's stale-data rule rings for the missing readings.

**No gap.** Today AAPS has no glucose alarm of its own, and the vendor app is stopped while AAPS holds the link. So a
driver drops its transmitter's glucose alarms only in the same change that brings the core BG alarms, or after it.
Until then they ring as they do now.

- **Severity, by what the event does to dosing** (`DosingEffect`, decided 2026-11-24, the rule for every driver):
  - NOW, CRITICAL: no reading, or a reading that cannot be trusted, now (sensor failed, battery empty, temperature,
    calibration expired or failed, wrong clock, transmitter off the sensor).
  - SOON, WARNING: the same soon, unless the user acts (battery low, calibration due).
  - ACTION, WARNING: not dosing, but the user has to act (the transmitter ends in some days).
  - INFO: the rest.
  - GLUCOSE: a glucose alarm of the transmitter. Not raised: the core's BG alarms raise it.
  - A kind with an event that affects dosing (NOW or SOON) is protected: it cannot be set Silent or Off. One kind
    per alarm name, as Carelevo has; a kind is protected when one of its events is.
  - The transmitter's own classes (Eversense: warning and info) are not used for it: they say how the vendor app
    shows an alarm, not what it does to dosing.
  - Where they overlap, the transmitter's alert rings at once with the reason, and the core's stale-data rule rings
    after its threshold if still no reading comes.
- **`raisedAt`** is the transmitter's time of the alarm, or the time the driver stored when it first saw it, never
  the time of the call.
- **Lifetime.** CONDITION when the driver can read the transmitter's active alarms (the 365 can) and the event
  affects dosing now, so the alert rings until the transmitter drops it. The others, and every alarm of a
  transmitter that only pushes (the E3), are EVENT: the user's answer ends them; the 365's list still ends one that
  left it. An alarm is raised when it is new in the list, so an answered one does not come back at the next sync.
- **The action** (confirm the alarm on the transmitter, where there is one) runs on the driver's thread; a CGM has no
  command queue.
- **The end.** `resolveAll(source)` when the driver stops, the transmitter is unpaired or another source is chosen.

What changes in core:

1. Done 2026-11-22: an alert carries its `AlertOrigin`, and only a pump's alert is filed under the active pump; the
   app's own is written with no pump identity. `AlertOrigin.CGM` added 2026-11-24, written as the app's own.
2. Done 2026-11-21: the KDoc of `AlertSeverity` and `AlertAction` covers CGMs too.
3. Nothing else is known to depend on a pump; check this again when it is built.

Eversense raises its alarms so since 2026-11-24; its transmitter's glucose alarms went in the same change. The details
are in `plugins/source/eversense/TECHNICAL.md`.

## 6. A driver in shared code (KMP)

As `PUMP_DESIGN.md`, section 3:

- The driver, its screens and its view models are in commonMain.
- Platform work is behind ports, with an implementation per platform.
- A platform without the ports has no such driver.
- The shared tests run on iOS (`.github/workflows/ios-ci.yml`).

Learned moving the Eversense tests to commonTest:

- A test that uses a platform value (a GATT status) fails on the other platforms; pass the value in.
- Truth, Mockito and `java.*` stay in androidHostTest; a class a test fakes gets `@OpenForTesting`.
- `assertEquals` compares in the other direction on iOS, so a hand-written collection fake needs `equals`.

See the `kmp-module-flip` skill.

## 7. Emulator and scenario tests

### What exists (Eversense)

- An emulated E3 and 365 on `EmulatedBleCentral`, built from the driver's code, not from captured frames:
  `UNVERIFIED` until real frames are recorded.
- The option file `emulate_eversense` runs the emulated E3 in the app, with the cloud upload off.
- Tests run the real driver against the emulator in virtual time, on every platform, and a first 365 registration
  runs against a fake vendor server.

### What is missing (design, not built)

- **`CgmEmulatorControl`** in `:core:interfaces`, as `PumpEmulatorControl` is for pumps. A hook the transmitter cannot
  do returns false, so a missing hook never looks like a passed test. The hooks:
  - lose the next answer;
  - out of range and back;
  - the transmitter forgets the phone;
  - raise and drop an alarm;
  - no readings for a time, for a gap;
  - the clock off;
  - the next reading now;
  - save and restore the state, for the app restart test.
- **`AbstractCgmScenarioTest`** in the app's instrumented tests: the driver against its emulator, through the plugin
  and the database, checked on what AAPS stored. A pump scenario goes through the command queue; a CGM scenario goes
  through `insertCgmSourceData`, the sensor change and the calibration dialog. It runs on a temporary emulator, as the
  pump scenarios do.
- **Speed.** Readings come every 5 minutes. A JVM test runs in virtual time. An instrumented test asks for the next
  reading with a hook, and does not make the clock faster: the transmitter clock is the phone clock.

## 8. Order of work

1. The reading checks of section 3, in storage with tests.
2. The alert core for CGM: the announcement without a pump (section 5), then Eversense moves its device alarms to
   alerts, with a test on a real transmitter. Its glucose alarms stop only when the core BG alarms exist (`ALARMS.md`,
   section 6, order of work).
3. `CgmEmulatorControl` and the Eversense hooks.
4. `AbstractCgmScenarioTest`, the Eversense class, and its CI shard.
5. Real frames recorded from a transmitter with a sensor: parsing tests with the real bytes, and the emulator
   checked against them (`UNVERIFIED` removed where they match).
6. The real-device list of the acceptance file, on an E3 and on a 365.

## 9. Decisions (2026-11-21)

1. **CGM alarms use the alert core** (`Alerts`), not a second alert core and not plain cards.
2. **The acceptance file covers CGM drivers in full**, and has one section of reading rules for every BG source.
3. **A CGM driver raises no glucose or missed-readings alarm.** The core's own BG alarms raise them for every source;
   the driver raises its device and error states. Until the BG alarms exist, the driver's glucose alarms keep ringing,
   because AAPS has no other. (This replaces the first version of this decision, which had the transmitter's glucose
   alarms ring through the alert core for good.)
4. **The reading checks every source needs are in storage** (section 3), once for every source, not in each source.

Open:

- Whether 2 minutes is right for every receiver.
