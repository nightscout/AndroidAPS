# Pump driver acceptance

What a pump driver must pass before it counts as done. A driver is not reviewed against a plan of its own: it passes
these requirements, or a requirement is marked as not possible for that pump, with the reason.

Each requirement has an id (D1, E1, S1, ...) so a review, a PR or a test can name it. **Must** is required. **Should**
is expected; a driver that does not do it says why.

How to build the emulator and the tests is in the skill `.claude/skills/pump-emulator-scenarios/SKILL.md`. This file
says what has to pass, not how.

## D. Driver rules (checked in review)

- **D1 Start and stop.** `onStart` loads everything the driver needs from storage and the pump. `onStop` leaves
  nothing in memory that the next start would trust. A reload, as a settings import does it, gives the same pump.
- **D2 The queue owns the pump.** All talk to the pump goes through the command queue. A screen or a ViewModel does not
  talk to the pump directly.
- **D3 No test code.** Faults, events and speed live in the emulator and its transport. The driver cannot tell a test
  from a real pump.
- **D4 Follow-up commands.** A command the driver queues after a command (a history read after a temp basal) is in the
  queue before the first one ends, so the queue is never empty in between.
- **D5 Amounts.** An amount sent to the pump is rounded to the pump's units, never cut. `1.15 * 100` is
  `114.99999...`, and a cut gave 1.14 U.
- **D6 A setting to write is kept apart.** A value the user wants written is not stored where a status read stores the
  pump's values, or a read in between writes the old value back.
- **D7 The answer of the pump is checked.** A setting is done only when the pump said so. A command whose answer was
  lost is checked on the pump before it is reported as done or failed.
- **D8 What the user is told.** A bolus the pump stopped or did not give in full is reported as not given, with an
  alarm. A refused setting is reported as failed, a done one as done.

## E. Emulator (in the tree, next to the driver)

- **E1** The emulator implements `PumpEmulatorControl`. A hook the pump cannot do returns false (or null), so a missing
  hook never looks like a passed test.
- **E2** Each behaviour copies something seen on a real pump, with the source and the date in a comment. A guess is
  marked `UNVERIFIED`, with what is guessed.
- **E3** FAST speed only makes actions shorter. The pump clock is the phone clock.
- **E4** The emulator saves and restores its state (`saveState`), for the app restart test.
- **E5** An emulated pump never reaches a vendor cloud or a real pump's identity.
- **E6** Timing copied from the real pump where the driver depends on it: a setting the pump carries out later, a
  request the pump holds, a report that comes before the pump is ready.

## S. Scenario suite (the driver against its emulator, through the real queue)

A class `<Pump>ScenarioTest : AbstractPumpScenarioTest()` runs all of these. A scenario the pump cannot do is
`@Ignore`d in that class with the reason (for example: the report of a real empty reservoir was never seen).

- **S1** `fullRun`: pairing, a status read shown on the overview, a bolus, a bolus the user stops, a temp basal and its
  cancel (also in percent where the pump takes one), an extended bolus and its cancel, the daily totals, a profile
  switch the pump gives hour by hour, unpairing. No alarm where there is nothing to alarm about.
- **S2** `theAmountsReachThePumpAsAsked`: odd amounts reach the pump as asked, also as a percent of the base rate.
- **S3** `theLoopsTempBasalsReachThePump`: 12 temp basal steps of the loop in a row.
- **S4** Bolus faults: `aBolusWhoseAnswerIsLostIsCounted`, `aBolusStartThatIsLostGivesNoRecord`,
  `aBolusThePumpStopsCountsWhatItGave`, `aLinkLostDuringABolusIsCountedOnce`, `aStopWhoseAnswerIsLostCountsWhatThePumpGave`,
  `aStopThatIsLostCountsWhatThePumpGave`.
- **S5** Temp basal faults: `aTempBasalWhoseAnswerIsLostIsKnown`, `aTempBasalCancelWhoseAnswerIsLostIsKnown`,
  `aTempBasalCancelWhoseCheckIsLostTooIsKnownAfterAStatusRead`, `aLostTempBasalIsKnownAsThePumpRunsIt`,
  `aTempBasalCancelThatIsLostKeepsTheTempBasal`.
- **S6** Other lost answers: `aProfileWhoseAnswerIsLostIsKnown`, `anExtendedBolusWhoseAnswerIsLostIsKnown`,
  `anExtendedBolusCancelWhoseAnswerIsLostIsKnown`.
- **S7** The driver itself: `aDriverReloadKeepsThePump`, `aPumpOutOfRangeFailsCleanly`.
- **S8** What happens on the pump: `aSuspendOnThePumpIsRecorded`, `aBolusGivenOnThePumpIsImported`,
  `aBolusWithThePumpClockOffIsStoredAtItsTime`.
- **S9** Alarms: `anOcclusionRaisesAnAlarm`, `anEmptyReservoirRaisesAnAlarm`.
- **S10** App restart: `appKilledDuringABolus`, in its two runs (`killRestart 1` and `2`).
- **S11** Pump-specific behaviour has a scenario of its own in the pump's class (for example a log that wraps, a
  setting the pump carries out later).

## U. Screens (one test per pump)

A class `<Pump>UiTest : AbstractPumpUiTest()` with the pump's `PumpScreens`. The screens that are the same for every
pump (bolus, temp basal and extended bolus dialogs) are tested once, not per pump.

- **U1** Pairing through the pump's own wizard screens, against the emulator.
- **U2** The pump history screen shows a record read from the pump, and Refresh reads it again.
- **U3** Stop and start of the insulin from the screens, when AAPS offers it for that pump.
- **U4** One pump setting saved to the pump from the pump settings screen, checked on the emulator.
- **U5** The system Back on each pump sub-screen leads to the pump overview, not out of the pump page.
- **U6** Unpair, with its confirm dialog.

## T. JVM tests

- **T1** Must: each packet seen on a real pump is parsed in a unit test with the real bytes, with the date and the
  firmware in a comment.
- **T2** Must, for a Bluetooth pump: the transport passes the shared transport contract test
  (`<Pump>BleTransportContractTest`).
- **T3** Should: the driver's service against the emulator on the JVM (as `DanaRSServiceIntegrationTest`), so the unit
  run covers the service too.

## R. Real pump (a person runs these, the pump not on a body)

Each driver change is tested on a real pump before it is called done. The full list is run once for a new driver, and
again for what a change touches. The result goes in the PR, with the date and the pump firmware.

- **R1** Pairing and a status read.
- **R2** A bolus of an odd amount (1.15 U or the nearest step), given in full.
- **R3** A bolus stopped by the user: AAPS counts what the pump gave.
- **R4** A temp basal start and its cancel, and a temp basal replaced by another.
- **R5** An extended bolus start and its cancel, if the pump has one.
- **R6** A profile switch written to the pump, read back.
- **R7** A history read with new records, and a bolus given on the pump imported.
- **R8** The pump clock checked, and set when off.
- **R9** The insulin stopped and started on the pump: AAPS records both.
- **R10** The link lost and back: the next command works.
- **R11** One pump setting saved from the screens.
- **R12** The app restarted during a bolus: AAPS counts it once.

## C. CI and coverage

- **C1** The scenario class runs in CircleCI shard D (it gets there by `@ShardD` on the base class), and has its time
  in `part_seconds` of `.circleci/scenario-worker.sh`.
- **C2** The screens test runs in a CircleCI shard (C by default).
- **C3** The driver's module has at least 80 % line coverage on Codecov, with the instrumented results.

## Exceptions

A requirement that a pump cannot meet stays visible:

- an `@Ignore` with the reason on the scenario or screen test, or
- a `false` from the emulator hook, with a comment, or
- a line in the PR that names the requirement and the reason.

A requirement left out silently counts as not passed.
