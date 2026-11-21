# Eversense driver (PR #5536): review and plan

Working notes for bringing the Eversense E3 / 365 driver from PR #5536 into AAPS. Written 2026-11-20, brought up to
date 2026-11-21.

## Where it is

- Local branch `eversense` = `dev` + PR #5536 (author BernhardRo, fork branch `dev_es365`) + the work below. The PR on
  GitHub targets the branch `eversense`, which on GitHub is still a plain copy of `dev`.
- Everything Eversense is in one module, `:plugins:source:eversense`, as a pump driver has one. All of it is in
  `commonMain` and runs on Android and iOS; `androidMain` and `iosMain` hold only small ports. How it works:
  `plugins/source/eversense/TECHNICAL.md`.
- Tried on a Pixel 9a with a test 365 transmitter without a sensor (2026-11-21): scan, pairing, sign-in, registration
  with the Eversense server, full sync. No readings yet.

## Findings of the first review, and where they are

### Glucose data

All fixed:

- The 365 decoder had no lower bound. Now 20..449 for the reading and the history, and a reading with a "glucose
  unavailable" reason is not taken (`DecodingFixesTest`).
- A reading dated in the future was taken and then refused every real reading as "not newer" until the clock passed
  it. Now a reading more than 2 minutes ahead of the phone is refused, and such a time stored before is dropped
  (`FutureReadingTest`).
- `recentGlucoseDatetime` was saved before the reading was delivered, and the state was written from several threads.
  The driver now does all of it on one thread, in one step.
- The data watchdog counted only new readings. Keep-alives count too, so a 365 without a new value is not reconnected
  every cycle.

Open decision: `SourceSensorExtensions.ADVANCED_FILTERING_SENSORS` contains `EVERSENSE_E3` and `EVERSENSE_365`, with
no reason given in the PR. It decides which SMB options Eversense users get.

### Effects on other features

- Fixed: the user's sensor age limits were overwritten (with values above their maximum) and stayed after a change of
  CGM. Now the CGM source reports its sensor's lifetime, the master syncs it to the clients, and every phone colours
  the sensor age by one rule (`SensorLifetime`); the manual limits are hidden while it is known.
- Fixed: battery, expiry and calibration notices have their own ids; the quick-launch calibration action for every
  user is gone; the password-confirmation exception is gone with the login settings.
- Open: nothing removes a transmitter alarm card when the alarm ends.
- Open: `EVERSENSE_PLACEMENT` is posted as URGENT but declared NORMAL.
- Open: `LongNonKey.EversenseLastCalibrationSubmittedAt` lives in `core:keys` only so that `ui` can read it.

### Project rules

Fixed: only the English `strings.xml` is edited, unused strings are gone, no fully qualified names or
`System.currentTimeMillis()`, the help link is gone, and the extra dependencies (BouncyCastle, logback, slf4j) are gone.
`:plugins:source` does not depend on the driver.

### Architecture

Fixed: no own Activities. The pairing wizard, the status tab (in the pumps' layout) and the placement guide are inside
the one Eversense screen; calibration goes through the AAPS dialog. The driver and its session run on one thread with
coroutines and end with the link; the Eversense server is reached with ktor, with timeouts.

### Tests

There are tests for the driver, the session, the login, pairing and its scan, the status tab, the placement guide,
calibration, the future-reading check and the server requests. The golden tests pin the protocol output of the first
driver (requests, framing, decoding, dates, cloud records, the 365 channel) to recorded digests; the frozen sources
they were recorded from are gone (see `TECHNICAL.md`).

Still missing: the other communicator rules (duplicate readings, backfill window, full-sync rules),
`RangeCalculator`, the 365 message counter, and the notices of `EversensePlugin.onStateChanged`.

## Steps

1. Module move to `:plugins:source:eversense`, with the plugin, screens, keys and strings. Done.
2. Test with a real transmitter. Started: a test 365 without a sensor. Readings, calibration and an E3 are to come.
3. Cleanup and alignment with AAPS: lifecycle (a start loads everything, a stop drops everything), a pairing wizard
   like a pump's, dead code and settings out, the review fixes above, and what was learned from PR #4869. Done.
4. Shared code: all in `commonMain` on `BleCentral`, cryptography-kotlin with AES-CCM built here, ktor and
   `AAPSLogger`. Done.
5. Emulator, fake Eversense server and scenario tests. Done in host tests (E3 and 365 against the real driver, a
   first registration against a fake server); the `emulate_eversense` option file runs an emulated E3 in the app. Not
   done: an instrumented scenario class in the app tests, and the scenarios "transmitter forgot the client" and "app
   restart".
6. iOS: registered (`EversenseIosRegistration`). A device test and the tests in `ios-ci.yml` are to come.

## Emulator

Built (see "Emulator" in `TECHNICAL.md`): an emulated E3 and 365 for the shared emulated Bluetooth, switched on in
the app by the `emulate_eversense` option file (E3 only, as a 365's first registration needs the real server). Built
from the code and not from captures, so it is `UNVERIFIED` until real frames are recorded.

Scenarios covered by host tests: sign-in, a reading every 5 minutes, the keep-alive, a 365 gap filled from the
history, the clock set, calibration, the signal read, a pushed alarm, Bluetooth off and on, a first registration
against a fake server, a refused login. Still to add: the transmitter forgot the client, a lost answer, app restart.

## To check on a device

- Readings and backfill with a sensor; the 365 "glucose unavailable" field.
- Reconnect with the phone asleep. The 365 Bluetooth disconnect timeout: the test transmitter accepted 300 s; 0 was
  not tried.
- The E3 calibration time zone (needs an E3).
- Unpair and pair again: the phone's bond is removed now.
- iOS, all of it; an E3 lifted off the sensor might reconnect in a loop there, as iOS has no timer to slow it.

## Open decisions

- Advanced filtering for Eversense: keep or remove.
