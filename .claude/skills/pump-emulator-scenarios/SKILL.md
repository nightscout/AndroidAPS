---
name: pump-emulator-scenarios
description: Test a pump driver against its emulator through the real command queue - pairing, bolus, temp basal, lost commands and answers, driver reload, app restart. Use when adding a pump to the scenario tests, when a pump scenario fails locally or in the CI work list, or when changing a driver or its emulator in a way these tests cover.
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
- An answer goes out before anything the pump sends after it. The Dana RS emulator answered a history read with its
  first event and sent the rest and the "done" from its own thread; under CI load the "done" came first, the driver
  ended the read with nothing, and a bolus had no record now and then (CircleCI 42425, 42438, 42448). Its transport now holds
  back what the pump sends by itself until the answers are out.
- A status that says how long something runs must count it from its start. The DanaR emulator said 0 s for every
  extended bolus, so each status read moved its start to "now", and 10 s later the driver took it for another one,
  warned "unsupported action in pump" and stored it again. The scenarios checked within 10 s and passed.
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
   `pumpTestSources` in `app/build.gradle.kts`. What only a family of drivers does goes in an abstract class between
   the two, as `AbstractDanaRScenarioTest` for the three DanaR drivers (a wrong driver picked, a temp basal set on the
   pump, the history of every record type, the pump clock, the daily limit, the user options).
5. The real radio code: the emulator replaces the transport, so the scenarios never run it. A unit test
   `<Pump>BleTransportContractTest : BleTransportContract()` (`:shared:tests`, package `app.aaps.shared.tests.ble`)
   runs it on Robolectric's Bluetooth: say how to build the transport and give the pump's service and characteristics.
   The module needs `testImplementation(project(":shared:tests"))`, `testImplementation(libs.org.robolectric)` and
   `testRuntimeOnly(libs.org.junit.vintage.engine)`. A transport that reports through its own callback sets
   `reportsThroughListener = false`, and only the shared part runs.
6. Before the push, run the class and then the two restart runs on a temporary emulator. CI runs the restart pair for
   every class, and a normal local run skips it. The saved emulator must hold the bolus that runs and go on with it
   when it is read back (the DanaR emulator once lost it, so the bolus never ended after the restart).

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

The scenarios (`@ShardD`) are a work list over the CircleCI emulators, `.circleci/scenario-worker.sh`. Its header
says how it works: each class is split in parts (the runner's `numShards`/`shardIndex`), plus one item for the two
restart runs, and every emulator with nothing else to do takes the next item. `@ShardD` is `@Inherited` and set on
`AbstractPumpScenarioTest`, so a new `<Pump>ScenarioTest` lands there with no CI change. Shard C leaves ShardD out.
The Collect step prints one line per item (emulator, result, seconds); a failed item's output has the driver log of
the failed test. The longest parts go first, by the seconds in `part_seconds` of the script. A class not in it counts
as the longest; once it has run on CI, add its seconds from the Collect lines, or it keeps going first. An emulator
whose system dies ("Can't connect to activity manager") takes no more items and gives its item back; a summary line
"the emulator is lost" says so, and the item's result comes from another emulator.

Shared UI gets one pass. The treatment dialogs run once, in `DanaRsEmulatorUiTest` on BLE5; a driver's commands are
tested through the queue by its scenario class, not again through the dialogs.

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
- The loop sends its temp basals with `enforceNew = false` (`loopTempBasal`, `loopCancelTempBasal`), the dialog with
  `true` (`tempBasal`). Only the first reaches the branches where a driver keeps what already runs.
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
- A pump clock can be a second or two behind the phone, so the record of the first command can be timed just before the
  first profile. AAPS once dropped it ("No profile running") and the first Dana temp basal failed now and then in CI;
  now PumpSync gives such a record the first profile. `pair()` does not wait, so the scenarios keep checking that.
- A scenario checks the records as soon as the queue is idle. A driver that stores what a status read found from a
  launched coroutine can store it after the check: the Equil decision on an extended bolus whose answer was lost was
  right in the log and still failed the check. Store it before the read ends.
- After a real-pump test, copy what the pump did into the emulator (mark the source and the date). The DanaR emulator
  gives progress in 0.05 U steps, one more step after a stop, writes the bolus to its history 2 s after the end, and
  refuses two connects after a broken link, all as a real DanaRv2 did. Its Korean variant steps 0.1 U and stops at its
  last progress, starts a link with 0x0301, 0x0302, 0x0303, and ends a history read with no "done", as a real Korean
  DanaR did (the driver ends the read after 10 s with nothing from the pump).
- A stop of the insulin on the pump is a 0 U/h temp basal of type PUMP_SUSPEND in AAPS. `checkSuspend` checks it, and a
  temp basal check while the pump is stopped would compare the wrong things.
- A driver may send a command again after no answer (the DanaR sends a temp basal cancel again). Check the end state
  after a status read, not the state between the two sends.
- A pump that gives high temp basals as extended boluses (the DanaR with "Use extended boluses for >200%", on by
  default) stores every extended bolus as one that stands for a temp basal. `checkExtendedBolus` expects what
  `isFakingTempsByExtendedBoluses` says.
- An alarm reaches the user in two ways: from a background thread as the full-screen alarm notification, from the main
  thread as the alarm screen. `checkUrgentAlarmSince` accepts either.
- A driver's own database, as the stored pump log of Aaruy, is a file. It outlives the test graph, so a test finds the
  rows of the tests before it. A driver must not take an old row for a new event; Aaruy tells them apart by the row id.
