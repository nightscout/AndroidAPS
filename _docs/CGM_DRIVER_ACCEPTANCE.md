# CGM driver acceptance

What a CGM driver must pass before it counts as done. Section B is for every BG source, also one that only receives
readings from another app. A driver is not reviewed against a plan of its own: it passes these requirements, or a
requirement is marked as not possible for that transmitter, with the reason.

Each requirement has an id (B1, D1, E1, ...) so a review, a PR or a test can name it. **Must** is required.
**Should** is expected; a driver that does not do it says why. How to build it is in `CGM_DESIGN.md`. This file says
what has to pass, not how.

## B. Readings (every BG source)

- **B1 No reading from the future.** A reading more than 2 minutes ahead of the phone is not stored. As the newest
  reading it would make every real one look "not newer" until the clock passes it.
- **B2 Only readings are stored.** A value the sensor marks as not a reading (an error code, "glucose unavailable",
  out of the sensor's range) is not passed on as a glucose value.
- **B3 The sensor's time.** A reading is stored with the time the sensor gives it, not the time it arrived.
- **B4 Once.** A reading that arrives again (a backfill, a repeated broadcast) is stored once.
- **B5 The sensor change.** A new sensor is recorded once, at its insertion time. A time of 0, a zero date of the
  device (2000-01-01 on the 365) or a time in the future records nothing.
- **B6 No made-up values.** A value the source does not have (battery, sensor lifetime) is "none", never a number to
  fill the field.
- **B7 The user's settings stay the user's.** A source does not write the user's preferences. What it knows (the
  sensor lifetime) it reports through `BgSource`, and core decides.
- **B8 Calibration.** `calibrationMode` says truly where a calibration goes. A source in `CalibrationMode.SOURCE`
  gives `calibrationRangeMgdl` when the sensor takes less than the dialog offers.

## D. Driver rules (checked in review)

- **D1 Start and stop.** The start loads everything the driver needs from its store. The stop leaves nothing in memory
  that the next start would trust, and nothing of the old link runs on. A reload gives the same transmitter.
- **D2 The driver owns the link.** All talk to the transmitter goes through the driver, on its thread. A screen or a
  ViewModel does not talk to the transmitter directly.
- **D3 No test code.** Faults, events and speed live in the emulator. The driver cannot tell a test from a real
  transmitter.
- **D4 A lost link comes back.** The driver tries again by itself with a back-off. It replaces a link that brings no
  data. It does not loop without a pause, also when the transmitter closes the link each time.
- **D5 The gap is filled.** After a gap, the readings the transmitter keeps are read from its history and stored, each
  once (B4).
- **D6 Pairing.** The wizard uses the shared steps. The scan looks for the transmitter's service, never its name, and
  ends by itself and at once when Bluetooth is off or not allowed.
- **D7 Unpairing.** Unpair closes the link and removes the phone's bond where the platform can. Where it cannot (iOS),
  the user is told how to forget the transmitter before pairing again.
- **D8 Calibration.** A calibration goes through the AAPS dialog. A refused one is reported as failed, with the
  reason, and does not end the link.
- **D9 Vendor account.** The login and the keys are in the driver's store, never in a log. An upload to the vendor
  cloud is the user's choice. A step that needs the vendor server sends nothing while offline.

## A. Alarms (checked in review)

How the core shows, rings and silences an alert is in `PUMP_DESIGN.md`, section 2; what is different for a CGM is in
`CGM_DESIGN.md`, section 5.

- **A1** Every alarm the transmitter raises goes through `PumpAlerts`, the glucose alarms too: AAPS has no glucose
  alarm of its own, and the vendor app is stopped. Messages that are not alarms stay plain cards.
- **A2** The driver plays no sound and has no notification channel, alarm screen or foreground check of its own.
- **A3** The severity follows the transmitter. An urgent glucose alarm, and a fault after which no reading comes, are
  CRITICAL. A glucose alarm is never INFO.
- **A4** The start raises again what the transmitter still holds, with the same key and `raisedAt`. `raisedAt` comes
  from the transmitter or the driver's store, never from the time of the call.
- **A5** The step on the transmitter (confirm the alarm), where there is one, is the alert's action.
- **A6** The driver resolves an alert when the transmitter drops it, and all of them when it stops, is unpaired or
  another source is chosen.

## K. A driver in shared code (KMP)

- **K1** The driver logic, its screens and its view models are in commonMain; platform work is behind ports, with the
  Android implementation in androidMain.
- **K2** A platform without the driver's ports has no such driver: the driver is registered by a binding container
  that each platform includes.
- **K3** The driver's shared tests run on iOS (`.github/workflows/ios-ci.yml`).

## E. Emulator (in the tree, next to the driver)

- **E1** The emulator implements `CgmEmulatorControl`. A hook the transmitter cannot do returns false, so a missing
  hook never looks like a passed test.
- **E2** Each behaviour copies something seen on a real transmitter, with the source and the date in a comment. A
  guess is marked `UNVERIFIED`, with what is guessed.
- **E3** The transmitter clock is the phone clock, unless a test sets it off on purpose. A test does not make readings
  come faster than the transmitter gives them: it asks for the next one.
- **E4** The emulator saves and restores its state, for the app restart test.
- **E5** An emulated transmitter never reaches a vendor cloud, a real account or a real transmitter's identity.
- **E6** Timing copied from the real transmitter where the driver depends on it: the keep-alive, the first data after
  the sign-in, messages in parts.

## S. Scenario suite (the driver against its emulator, through the plugin and the database)

A class `<Cgm>ScenarioTest : AbstractCgmScenarioTest()` runs all of these. A scenario the transmitter cannot do is
`@Ignore`d in that class, with the reason.

- **S1** `fullRun`: pairing, readings every 5 minutes stored and shown on the overview, a calibration, the status read,
  unpairing. No alarm where there is nothing to alarm about.
- **S2** Readings: `aGapIsFilledFromTheHistory`, `aReadingIsStoredOnce`, `aReadingFromTheFutureIsNotStored`,
  `aTransmitterClockOffIsSet`.
- **S3** The sensor: `aNewSensorIsRecordedOnce`, `noSensorRecordsNoSensorChange`.
- **S4** The link: `aLostAnswerKeepsTheLink`, `outOfRangeAndBack`, `bluetoothOffAndOn`,
  `aTransmitterThatForgotThePhoneIsPairedAgain`.
- **S5** The driver itself: `aDriverReloadKeepsTheTransmitter`, `aSourceSwitchedAwayAndBackKeepsTheTransmitter`.
- **S6** Alarms: `aLowGlucoseAlarmRings`, `aDroppedAlarmIsResolved`, `aStopResolvesAllAlarms`.
- **S7** Calibration: `aCalibrationReachesTheTransmitter`, `aRefusedCalibrationIsReportedAndKeepsTheLink`.
- **S8** App restart: the app killed and started again keeps the transmitter and stores no reading twice.
- **S9** Transmitter-specific behaviour has a scenario of its own (Eversense: the placement guide, the 365
  registration with the vendor server).

## U. Screens (one test per driver)

- **U1** Pairing through the driver's own wizard screens, against the emulator.
- **U2** The status screen shows the values read from the transmitter, and "sync now" reads them again.
- **U3** A calibration from the dialog reaches the emulator.
- **U4** The driver's own screens (Eversense: the placement guide) open and close.
- **U5** The system Back on each sub-screen leads to the driver's overview, not out of the page.
- **U6** Unpair, with its confirm dialog.

## T. JVM tests

- **T1** Must: each message seen on a real transmitter is parsed in a unit test with the real bytes, with the date and
  the firmware in a comment.
- **T2** Must: the driver talks through the shared `BleCentral`, and its emulator is an `EmulatedPeripheral` on
  `EmulatedBleCentral`. The driver has no Bluetooth code of its own.
- **T3** Should: the driver against its emulator on the JVM, in virtual time, so the unit run covers the link rules.

## R. Real transmitter (a person runs these)

Each driver change is tested on a real transmitter before it is called done. The full list is run once for a new
driver, and again for what a change touches. The result goes in the PR, with the date, the transmitter and its
firmware.

- **R1** Pairing, the sign-in and the first reading.
- **R2** Readings for an hour: each one stored once, with the values and times the vendor's receiver or app shows.
- **R3** The phone out of range for 30 minutes: the gap is filled from the history.
- **R4** A calibration accepted, and one refused.
- **R5** A new sensor: the sensor change recorded once, with its insertion time, and the sensor age right.
- **R6** A transmitter alarm that can be raised without harm (Eversense: the transmitter off the sensor) rings in AAPS;
  Silence stops the sound, and the alert ends when the transmitter drops it.
- **R7** Bluetooth off and on, and the phone asleep for an hour: the readings go on.
- **R8** The app restarted: the transmitter is kept, no reading is stored twice.
- **R9** Unpair, then pair again, also with the vendor app after AAPS.
- **R10** The transmitter clock off: set at the next sync.
- **R11** The vendor cloud upload, when the user turned it on, received in the vendor's account.

## C. CI and coverage

- **C1** The scenario class runs in a CircleCI shard.
- **C2** The screens test runs in a CircleCI shard.
- **C3** The driver's module has at least 80 % line coverage on Codecov, with the instrumented results.

## Exceptions

A requirement that a driver cannot meet stays visible:

- an `@Ignore` with the reason on the scenario or screen test, or
- a `false` from the emulator hook, with a comment, or
- a line in the PR that names the requirement and the reason.

A requirement left out silently counts as not passed.

Known exceptions on 2026-11-21:

- **Every receiver, B1 and B5:** only Glunovo and Intelligo refuse a reading from the future; storage checks neither
  time. Decided: one check in storage for every source (`CGM_DESIGN.md`, section 3), not built yet.
- **Eversense, A1-A6:** one plain card per alarm, with the alarm sound for the critical ones; nothing removes a card
  when the alarm ends. Waits for `PumpAlerts` for CGM (`CGM_DESIGN.md`, section 5).
- **Eversense, E1 and E4:** the emulator has no `CgmEmulatorControl` and no saved state. E2: it is built from the
  driver's code, so all of it is `UNVERIFIED`.
- **Eversense, S1-S9 and C1:** there is no `AbstractCgmScenarioTest` yet. The host tests cover part of S2, S4, S6 and
  S7 against the emulator, without the database.
- **Eversense, U1-U6 and C2:** screen tests exist for the navigation and the status, not one class per this list.
- **Eversense, T1:** no real frames recorded yet. The golden tests pin the output of the first driver, which proves
  "same as before", not "right".
- **Eversense, R:** only R1 in part, on a test 365 without a sensor (2026-11-21): pairing, sign-in, registration,
  full sync; no readings. Nothing on an E3.
