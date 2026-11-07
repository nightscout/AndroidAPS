# Eversense in AndroidAPS 4

This guide explains how to set up and use the Eversense E3/365 plugin, including offline use and its current limits.

Project: [BernhardRo/AndroidAPS, dev_es365](https://github.com/BernhardRo/AndroidAPS/tree/dev_es365). Maintainers can read the separate [technical documentation](TECHNICAL.md).

## What you can do

The plugin receives glucose readings directly from the transmitter over Bluetooth. AAPS stores these readings locally. You can also view transmitter status, submit a calibration, check placement signal and optionally upload data to Eversense DMS.

DMS is the Eversense online account and data service. Bluetooth reception and DMS upload are separate: an established setup can receive local readings without reaching DMS.

| Feature | Eversense E3 | Eversense 365 |
| --- | --- | --- |
| Bluetooth glucose readings and trends | Supported | Supported |
| Status, calibration submission and placement guide | Implemented | Implemented |
| Recover gaps from transmitter history | Not supported | Limited recovery |
| Optional DMS upload | Supported | Supported |
| Internet for initial identity registration | No ES365-style registration step | Required |
| Later connections offline | Designed to work with saved setup | Designed to work with a valid saved identity |

Implemented features and automated tests do not establish compatibility with every phone or firmware. Check behavior with your actual transmitter. Real-device connection, calibration, offline operation and battery use still need validation.

## Before you start

You need this AAPS build on Android, a working sensor and transmitter, Bluetooth enabled, and your DMS username and password. Allow the Bluetooth permissions Android requests. Android 12 and later use Nearby devices permissions.

Complete sensor setup and alarm-threshold configuration in the official Eversense app first. This plugin does not provide those procedures or replace the rest of your AAPS pump, profile and loop configuration.

Keep internet available for first ES365 registration. Both transmitter types currently require non-empty DMS username and password fields in AAPS, even when cloud upload is off.

## First setup

1. Select **Eversense** as the active blood glucose source in AAPS.
2. Open its settings and expand **Eversense DMS credentials**.
3. Enter the username and password. Enter the password once; the settings keep it hidden.
4. Enable **European region** for the European/OUS DMS service. Leave it off for the US service. The default is off, so check this explicitly.
5. Choose whether to keep **Enable cloud upload** on or off. It is on by default.
6. Open **Status & Connection** and press **Connect**.
7. If no transmitter is saved, wait for the ten-second scan and select yours. The list includes supported names beginning with T followed by digits.
8. Wait for connection setup and synchronization. ES365 can also need online registration.
9. Check the latest glucose timestamp in the AAPS overview.

Connect uses a saved transmitter when one exists. Use Change Transmitter to select another. Repeated Connect or START SYNC presses are usually unnecessary while setup is in progress.

Before testing two receivers together, read [Using the original app](#using-the-original-app).

## Settings and screens

Labels may be translated by the AAPS language setting.

| Setting or screen | Purpose |
| --- | --- |
| **Status & Connection** | Read status, connect, disconnect, change transmitter or request a sync. |
| **Enable cloud upload** | Upload eligible data to DMS, at most once per hour. |
| **Diagnostic logging** | Temporarily collect more detail for a problem report. Off by default. |
| **Eversense DMS credentials** | Expand to edit username, password and region, or sign out. |
| **Documentation & Setup Guide** | Open this guide. |
| **Calibrate transmitter** | Enter and submit a calibration value. |
| **Transmitter Placement** | See signal feedback while adjusting placement. |

The three device pages use the same AAPS theme as the other settings pages. They follow changes to the Light, Dark or System setting automatically. The top-left arrow returns to the previous screen.

### Read the status page

Status includes connection, battery, insertion date, last sync, calibration phase and last/next calibration information. Some values can be cached or unavailable until the transmitter has been read. Check the glucose timestamp in the overview to confirm current reception.

Connected means connection setup has finished, rather than merely opening a Bluetooth link. A status sync is not itself a new glucose reading.

**START SYNC** requests fresh transmitter information while connected. It does not force a DMS upload or bypass the hourly upload limit.

**Default Sync Days** is saved, but does not currently control the history request. Selecting 14 or 30 days does not guarantee that much recovered history.

## Daily tasks

### Disconnect and reconnect

Press Disconnect on the status page and confirm to pause reception. Automatic retries stay paused until you press Connect again. Disabling the source also stops its connection work.

Unexpected connection loss triggers automatic retries with increasing delays, up to about a minute. Check Bluetooth, battery, placement and any manual pause if readings stop. Sign out is not a routine reconnect step.

### Change transmitter

Open Status & Connection, press Change Transmitter and confirm. Wait for the scan, select the replacement and check for fresh readings. Keep internet available if ES365 needs registration.

This forgets the saved Bluetooth address; it does not delete AAPS glucose history. Sensor linking and initial setup still belong in the official app.

### Submit a calibration

Use the Eversense calibration action in the overview when available, or open Calibrate transmitter in the plugin settings.

1. Check the displayed unit: mg/dL or mmol/L.
2. Enter the intended blood glucose value in that unit.
3. Submit once and wait. If disconnected, AAPS first tries to reconnect.
4. If submission fails, check connection and transmitter status before trying again.

A warning appears for a recent vertical, double or stronger up/down trend arrow. A 45-degree arrow does not trigger it, and a calculated delta alone does not trigger it. The warning does not disable submission: you can still send the value. It refers to sensor glucose, not a direct measurement of how your fingerstick value is changing. Missing or old data can prevent an assessment; no warning does not prove stability.

Follow the manufacturer's [calibration guidance](https://www.eversensecgm.com/siteassets/eversense-pwd-resources/eversense-365_fingcalibration_101124_v2-006.pdf), which advises against calibrating during rapid change. The software warning does not replace that guidance.

Success requests a sync and starts a 15-minute countdown in the overview. The countdown is a local display, not a new reading or proof that the transmitter has finished processing. Follow the Eversense instructions for when and how to calibrate.

### Check placement

Open Transmitter Placement for signal feedback while adjusting placement. Leave the page when finished. Polling stops when it is no longer active, and the plugin attempts to leave transmitter placement mode.

Placement signal differs from phone-to-transmitter Bluetooth signal. Diagnostic logging is unrelated to placement mode.

### Change credentials or sign out

Expand Eversense DMS credentials to edit the account or region. Changes can interrupt reception and require registration again. Keep internet available for such changes with ES365.

Sign out appears only inside the expanded group. A confirmation warns that the saved username and password will be deleted. Cancel keeps them unchanged. Confirming pauses the connection, clears the cached DMS token and removes pending uploads. It does not delete AAPS glucose history or completely erase all transmitter state.

To resume, enter credentials and region again and check the connection. Use Change Transmitter if you also need a different device.

## Using the original app

### Must it be deleted?

**No. This plugin does not require uninstalling the official Eversense app. Keep it installed** for sensor setup, transmitter settings and returning to the manufacturer workflow. Installed and actively connected are different things.

Do not delete it, clear its storage or remove Android Bluetooth pairing as a first troubleshooting step. These actions can make returning to your original setup harder. AAPS Sign out and Change Transmitter are separate from the original app's account and setup controls.

### Can it keep running?

| Situation | Practical guidance |
| --- | --- |
| E3 already receiving in both apps | You can retain that arrangement if both continue to receive current readings reliably. Parallel E3 use has been reported in this project context, but is not guaranteed for every setup. |
| ES365 with AAPS as direct receiver | Keep the original app installed, but do not assume both can remain active receivers. Establish a reliable connection with one active receiver. |
| App installed without an active connection | No uninstall requirement is enforced by the plugin. Merely having the app on the phone does not reserve a connection. |
| Repeated competing reconnects or missing readings | Test one active receiver at a time and check timestamps in the receiver you use. |

Reliable ES365 parallel reception has not been established for this project. The plugin does not coordinate Bluetooth ownership with the original app. A pairing restriction between phones would not, by itself, establish whether two apps on the same phone can share reception.

### Keep it installed while testing AAPS

1. Complete needed sensor setup in the official app first.
2. If connections compete, use Android **Settings > Apps > Eversense / Eversense 365 > Force stop** for the official app. Phone menu names vary. Swiping away its window may leave a connection service running.
3. Keep phone Bluetooth on and retain AAPS Nearby devices permissions.
4. Connect from AAPS and check that glucose timestamps advance.

A force-stopped app does not provide its normal phone notifications or live display until it runs again. Do not assume its phone alerts remain available while testing AAPS. This guide does not establish equivalence between AAPS notifications and the manufacturer's alerts.

### Switch back to the official app

1. Disconnect in AAPS so its automatic retries stay paused.
2. Open the official app and follow its normal connection procedure.
3. Confirm fresh readings and the required functions there.

To return to AAPS, stop any competing original-app connection, then press Connect in AAPS. Switching clients may require ES365 online registration again; keep internet available. Do not relink the sensor or reset the transmitter simply to change receiver.

See the manufacturer's [ES365 pairing and sensor-linking guide](https://www.eversensecgm.com/siteassets/e3-resources/eversense-365_howtopair_365_102524_v22.pdf) for official setup. It describes the original app, not an AAPS parallel-reception guarantee.

## Offline use and airplane mode

An established setup is designed to receive Bluetooth readings without DMS access. Cloud failures do not stop local storage or request a Bluetooth reconnect. Local AAPS processing does not wait for DMS; other AAPS functions retain their own internet requirements.

1. Complete setup and confirm current readings online.
2. Enable airplane mode, then turn Bluetooth back on.
3. Keep the saved account and transmitter setup.
4. Check that glucose timestamps continue to advance.

ES365 needs internet for initial registration, missing identity material, or registration no longer accepted by the transmitter. A valid saved identity can work offline even with an expired DMS upload token. Signing out or reselecting the transmitter is not a useful offline test.

Check airplane-mode behavior with the actual setup. No new readings can arrive while Bluetooth itself is off.

## Optional cloud upload

AAPS stores readings locally before queuing DMS work. At most one upload cycle starts per hour, including failed attempts. A cycle can make several requests for readings, events and status; it is not a one-request limit.

Turning upload off leaves Bluetooth reception enabled. It does not remove ES365's internet requirement for initial registration.

Without validated internet, eligible readings stay queued. A later reading can trigger a due upload when connectivity returns. There is no exact clock-time guarantee, and START SYNC does not bypass the limit.

The pending buffer retains up to seven days and 2016 readings. Older entries can be dropped at these limits; AAPS history is unaffected. ES365 history without required raw transmitter data cannot use the raw-data upload endpoint, even if it appears in AAPS.

After a failure, correct the account, region or connection issue and allow the next eligible cycle. DMS can lag behind local reception.

## Troubleshooting

| Problem | Check or action |
| --- | --- |
| No scan result | Bluetooth, Nearby devices permissions, range and transmitter state. Allow ten seconds for the scan. |
| Connect uses the old transmitter | Use Change Transmitter. |
| First ES365 connection fails | Internet, credentials and European region. Registration cannot complete offline. |
| No reconnect after manual Disconnect | Press Connect; manual pause disables retries. |
| Connected but no fresh timestamp | Transmitter state, battery and placement. Status sync is not a new reading. |
| Apps compete | Keep both installed, but test one active receiver as described above. |
| E3 history gap remains | E3 glucose backfill is not implemented. |
| ES365 fills only part of a gap | Recovery is limited; Default Sync Days does not expand the request. |
| Calibration fails | Displayed unit, input, connection and transmitter readiness. Failure does not start the success countdown. |
| AAPS has readings but DMS does not | Upload setting, account/region, network, hourly limit and raw-data restrictions. |
| Wrong subpage colors | Check the AAPS theme setting and that this build includes the shared-theme pages. |

### Report a problem

Record the AAPS build, transmitter model, phone/Android version, time, last glucose timestamp and reproduction steps. State whether Bluetooth and internet were available, whether the official app was running, and whether local readings or only DMS were affected.

Enable Diagnostic logging shortly before reproducing the issue, collect relevant logs, then turn it off. Logs can contain health and device data; review them before sharing. Session keys are not logged, but this does not make logs anonymous. Switching logging off does not delete old files.

## Other limits

- Bluetooth and DMS support are Android-only.
- Sensor setup and alarm thresholds still need the official app. Legacy stored target/alert values are not transmitter controls in this settings page.
- E3 has no glucose backfill; ES365 recovery is not a complete archive download.
- Cached status and model-based reminders do not establish current readiness.
- A settings export is not a complete transmitter backup. Account settings can be exported; live identity/state and pending uploads are excluded. A new phone may need setup and ES365 registration again.
