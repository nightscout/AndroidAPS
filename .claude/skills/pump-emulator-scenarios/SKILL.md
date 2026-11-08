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
- Speed: shorter actions (`EmulatorSpeed.FAST`), never a faster clock. The pump clock is the phone clock, or the
  history has times no real pump gives. Things that wait for a time, as the end of a temp basal, are raised as events.
- The emulator object lives in `PumpEmulators`, a graph input, not in the graph. Bind it with
  `pumpEmulators.getOrCreate(<its key>) { ... }`.

## Adding a pump

1. The emulator transport implements `PumpEmulatorControl`. Start with what the pump already does, and return false
   for the rest.
2. `<Pump>Accessors` in the pump module (as `CarelevoAccessors`), so the test reaches the transport and the plugin.
3. An adapter `<Pump>PumpUnderTest`: how to pair (through the wizard's own ViewModels from
   `testGraphs.viewModelFactory`, or through its queue commands), how to unpair, how to restore a saved emulator.
4. A test class `<Pump>ScenarioTest : AbstractPumpScenarioTest()` that returns the adapter. Add the source dir to
   `pumpTestSources` in `app/build.gradle.kts`.

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

## Traps

- `activePlugin.activePump` is the concentration wrapper. Compare the driver with `activePumpInternal`.
- The queue drops every command while `isConfigured()` is false, also a profile switch. A wizard that needs the
  profile before pairing takes the requested one, `PumpSync.expectedPumpState().profile`, as Equil does.
- `getBolusesFromTime(..., ascending = true)` returns the newest first. Sort by timestamp.
- An Android service of a driver is made once for the process, but each test has a new graph. Left bound, the next
  test's plugin binds the old service, whose objects belong to the old graph: its link runs there and the wizard waits
  for ever. `tearDown` therefore stops the driver as a real stop does and waits until its services are gone.
- The queue drops a status read while another one waits or runs, and reports it as failed. A driver start queues one,
  and during a bolus it waits for the bolus end. The `readStatus` step waits for that one first.
- A driver that keeps no pump state is ready (`isInitialized()`) only after its first status read, as at an app start.
  Right after a reload, check `isConfigured()`.
- A driver that cannot tell at once whether a bolus start reached the pump may count the whole amount and decide later,
  from the pump, once the bolus would have ended. Give such a check a longer limit (`checkBolusRecords(BOLUS_TIMEOUT)`).
  A record it sets to 0 U counts as no record.
- A driver's own database, as the stored pump log of Aaruy, is a file. It outlives the test graph, so a test finds the
  rows of the tests before it. A driver must not take an old row for a new event; Aaruy tells them apart by the row id.
