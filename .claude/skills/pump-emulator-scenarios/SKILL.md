---
name: pump-emulator-scenarios
description: Test a pump driver against its emulator through the real command queue - pairing, bolus, temp basal, lost commands and answers, driver reload, app restart. Use when adding a pump to the scenario tests, when a pump scenario or CI shard D fails, or when changing a driver or its emulator in a way these tests cover.
---

# Pump emulator scenario tests

Instrumented tests that run a real pump driver against its in-tree emulator, through the real command queue,
PumpSync and database, with no screen. After each step they compare what AAPS stored with what the emulated pump did.
They test the core of AAPS and the driver; the screens have their own tests.

Code: `app/src/androidTest/kotlin/app/aaps/e2e/scenario/` (engine `PumpScenario`, shared scenarios
`AbstractPumpScenarioTest`) and one adapter per pump in `app/src/androidTestPumps/<pump>/`. The test hooks of an
emulator are `PumpEmulatorControl` in `:core:interfaces`.

## Rules for drivers

- **The start loads everything, the stop drops everything.** `onStart` reads all the driver needs from storage and
  the pump, and `onStop` leaves nothing in memory that the next start would trust. Then a driver reload, as a settings
  import does it, gives the same pump as before. `aDriverReloadKeepsThePump` checks it.
- **No test code in drivers.** Faults, events and speed live in the emulator and its transport only, so the driver
  cannot tell a test from a real pump.

## Rules for emulators

- Each hook copies something seen on a real pump. Mark a guess `UNVERIFIED`, with what is guessed.
- A hook the pump cannot do returns false (or null for `saveState`). The scenario then fails with that, so a missing
  hook never looks like a passed test.
- A case no real pump has shown, and that cannot be made on one to look, is not guessed when a scenario judges the
  driver by it: a wrong guess would show as a driver bug. The hook returns false, says why in its KDoc, and the pump's
  test class skips that scenario with an `@Ignore("<why>")` override (the shared scenario is `open` for that). Example:
  a blockage during an Equil bolus.
- Speed: shorter actions (`EmulatorSpeed.FAST`), never a faster clock. The pump clock is the phone clock, or the
  history has times no real pump gives. Things that wait for a time, as the end of a temp basal, are raised as events.
- The emulator object lives in `PumpEmulators`, a graph input, not in the graph. Bind it with
  `pumpEmulators.getOrCreate(<its key>) { ... }`.
- Lost commands and lost answers are each used in the order the test set them, so a test can name a sequence ("the
  cancel answer is lost, then the answer to the read after it"). Only the first fault can fire.
- `EmulatedCommand.STATUS` is every read of what the pump runs now (its status, its temp basal), not its history. Map
  each such read, or a STATUS fault never fires. Then look in the log that a fault did fire: a scenario whose fault
  never fired passes and tests nothing. The Equil emulator once named no message under 10 bytes, and its reads are 6.
- The emulator reports as its base rate the rate of its active profile. Dana RS takes a temp basal at the base rate as
  no temp basal; while its emulator said 1.0 U/h always, every Dana temp basal scenario passed without a temp basal.
- A scanner hands out only what the pump advertises now, as the real one does. A `SharedFlow` with `replay` kept an old
  advertisement for new listeners: Equil's wizard paired with a serial the pump no longer had, and the driver read an
  old log index. Advertise again and again while a scan runs instead, until `stopScan`.

## Adding a pump

1. The emulator transport implements `PumpEmulatorControl`. Start with what the pump already does, and return false
   for the rest.
2. `<Pump>Accessors` in the pump module (as `CarelevoAccessors`), so the test reaches the transport and the plugin.
3. An adapter `<Pump>PumpUnderTest`: how to pair (through the wizard's own ViewModels from
   `testGraphs.viewModelFactory`, or through its queue commands), how to unpair, how to restore a saved emulator.
4. A test class `<Pump>ScenarioTest : AbstractPumpScenarioTest()` that returns the adapter. Add the source dir to
   `pumpTestSources` in `app/build.gradle.kts`.
5. The real radio code: the emulator replaces the transport, so the scenarios never run it. A unit test
   `<Pump>BleTransportContractTest : BleTransportContract()` (`:shared:tests`, package `app.aaps.shared.tests.ble`)
   runs it on Robolectric's Bluetooth: say how to build the transport and give the pump's service and characteristics.
   The module needs `testImplementation(project(":shared:tests"))`, `testImplementation(libs.org.robolectric)` and
   `testRuntimeOnly(libs.org.junit.vintage.engine)`. A transport that reports through its own callback sets
   `reportsThroughListener = false`, and only the shared part runs.

## Running

Only on a temporary emulator: the tests clear the app's settings. For example
`emulator -avd <avd> -read-only -no-snapshot -port 5590`, then point every `adb` call at that serial.

- Build: `./gradlew.bat :app:assembleFullDebug :app:assembleFullDebugAndroidTest` (a 4.0.0 tree must be committed to
  build the app).
- Run: `adb -s <serial> shell am instrument -w -r -e class app.aaps.e2e.<Pump>ScenarioTest
  info.nightscout.androidaps.test/app.aaps.runners.AapsTestRunner`
- At real speed: add `-e emulatorSpeed real`. Use it to look for timing faults.

### App restart (`appKilledDuringABolus`)

A driver reload is not an app restart: other objects keep their memory, a running command goes on, and the app start
order is not run. So a real restart takes two runs, and a normal run skips the test:

```
adb -s <serial> shell am instrument -w -r -e killRestart 1 -e class app.aaps.e2e.<Pump>ScenarioTest#appKilledDuringABolus <runner>
adb -s <serial> shell am instrument -w -r -e killRestart 2 -e class app.aaps.e2e.<Pump>ScenarioTest#appKilledDuringABolus <runner>
```

Run 1 starts a bolus at real speed, saves the emulator to a file and ends with no cleanup; its process ends with the
run. Run 2 is a new process on the same database file and settings, with the emulator as saved.

### CI

CircleCI shard D (fourth emulator, `.circleci/config.yml`) runs `-e annotation app.aaps.testcategories.ShardD`, then
the two restart runs for each class it lists with `-e log true`. `@ShardD` is `@Inherited` and set on
`AbstractPumpScenarioTest`, so a new `<Pump>ScenarioTest` lands there with no CI change. Shard C leaves ShardD out.

The pump UI tests (`AbstractPumpUiTest`, one `PumpScreens` per pump) have no shard annotation, so they run in shard C.
CI makes its AVDs without a device, which gives the emulator default screen: **320 x 640 at 160 dpi**, on API 31. A
test that passes on a phone-sized emulator can fail there: Compose does not lay out what is below the screen, so
uiautomator cannot find it. `ScreenDriver` scrolls for a text to tap. To run a test on the same screen, make the AVD as
CI does (on Windows through `cmd`: PowerShell puts a byte-order mark before the answer, which avdmanager refuses):

```
cmd /c "echo no| <sdk>\cmdline-tools\latest\bin\avdmanager.bat create avd -n citest_local -k system-images;android-31;google_apis_playstore;x86_64"
```

Shard C's logcat can rotate before CI reads it, so the UI base adds the screen to the error, which the test output keeps.

## Traps

- `activePlugin.activePump` is the concentration wrapper. Compare the driver with `activePumpInternal`.
- Only `MasterActionExecutor` raises the BOLUS_DELIVERY_FAILED alarm, when the queue reports a bolus as not given. A
  bolus straight through the queue (`bolus`, `startBolus`) never alarms, so a check of that alarm needs `userBolus`,
  the insulin dialog's path. A bolus the pump cut short must come back as not given, or the user hears nothing.
- The queue drops every command while `isConfigured()` is false, also a profile switch. A wizard that needs the
  profile before pairing takes the requested one, `PumpSync.expectedPumpState().profile`, as Equil does.
- `getBolusesFromTime(..., ascending = true)` returns the newest first. Sort by timestamp.
- An Android service of a driver is made once for the process, but each test has a new graph. Left bound, the next
  test's plugin binds the old service, whose objects belong to the old graph: its link runs there and the wizard waits
  for ever. `tearDown` therefore stops the driver as a real stop does and waits until its services are gone.
- `commandQueue.clear()` leaves the running command running. A driver stopped under it connects again for it and
  writes its pump into the settings, which the next test has already cleared: that test starts as paired, and its
  pairing hung. `tearDown` waits for the queue before it stops the driver.
- `RetryRule` runs a test once more after a timeout, never after a wrong result (an `AssertionError`). A flake shows
  only in the log: grep the logcat for `RetryRule: `. So a scenario check reports a wrong result with an assertion,
  not with `check`/`checkNotNull`, which throw `IllegalStateException` and would be retried.
- The queue drops a status read while another one waits or runs, and reports it as failed. A driver start queues one,
  and during a bolus it waits for the bolus end. The `readStatus` step waits for that one first.
- A driver that keeps no pump state is ready (`isInitialized()`) only after its first status read, as at an app start.
  Right after a reload, check `isConfigured()`.
- A driver that cannot tell at once whether a bolus start reached the pump may count the whole amount and decide later,
  from the pump, once the bolus would have ended. Give such a check a longer limit (`checkBolusRecords(BOLUS_TIMEOUT)`).
  A record it sets to 0 U counts as no record.
- A driver's own database, as the stored pump log of Aaruy, is a file. It outlives the test graph, so a test finds the
  rows of the tests before it. A driver must not take an old row for a new event; Aaruy tells them apart by the row id.
