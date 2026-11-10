# Eversense in AndroidAPS 4

The Eversense E3/365 plugin receives glucose readings over Bluetooth. It provides transmitter status, calibration submission, a placement guide and optional uploads to Eversense DMS.

Project: [BernhardRo/AndroidAPS](https://github.com/BernhardRo/AndroidAPS/tree/dev_es365). For architecture and implementation details, see [TECHNICAL.md](TECHNICAL.md).

## Setup

Complete sensor setup and alarm configuration in the official Eversense app first. Keep that app installed for functions not provided by AAPS.

1. Enable Bluetooth and select **Eversense** as the active blood glucose source in AAPS. Allow the requested Bluetooth/Nearby devices permissions.
2. Open the Eversense settings and expand **Eversense DMS credentials**.
3. Enter your DMS username and password. Both fields are required for E3 and ES365, even with cloud upload disabled.
4. Enable **European region** for a European/OUS DMS account; leave it off for a US account. The default is off.
5. Choose whether to enable cloud upload. It is on by default.
6. Open **Status & Connection**, press **Connect**, wait for the ten-second scan and select your transmitter. If one is already saved, Connect uses it.
7. Wait for connection setup, then check for a current glucose timestamp in the AAPS overview.

ES365 needs internet for initial registration. It can also need registration again after account or transmitter changes. Verify reception with your actual phone and transmitter before relying on the setup.

## Settings

| Setting | Use |
| --- | --- |
| **Status & Connection** | View battery, sensor and calibration information; connect, disconnect, change transmitter or request a sync. |
| **Enable cloud upload** | Upload data to Eversense DMS at most once per hour. |
| **Diagnostic logging** | Collect extra detail for troubleshooting. Leave off during normal use. |
| **Eversense DMS credentials** | Edit username, password and region, or sign out. |
| **Calibrate transmitter** | Submit a calibration value. |
| **Transmitter Placement** | Check signal feedback while adjusting placement. |

**START SYNC** refreshes transmitter information; it does not force a DMS upload. **Default Sync Days** does not currently control how much glucose history is recovered.

## Daily use

### Connection and transmitter replacement

Unexpected connection loss triggers automatic retries. A manual **Disconnect** pauses them until you press **Connect** again.

To use a replacement transmitter, choose **Change Transmitter**, confirm and select it from the scan results. This does not delete glucose history in AAPS. Sensor linking still belongs in the official app.

### Calibration

Open the Eversense calibration action in the overview or **Calibrate transmitter** in the plugin settings. Check the displayed unit, enter the blood glucose value and submit once. If necessary, AAPS first tries to reconnect.

A warning appears for current vertical or double up/down trend arrows, including stronger trends. A 45-degree arrow does not trigger it. The warning does not block submission. Follow the manufacturer's [calibration guidance](https://www.eversensecgm.com/siteassets/eversense-pwd-resources/eversense-365_fingcalibration_101124_v2-006.pdf); no warning does not prove that glucose is stable.

A successful submission starts a 15-minute countdown in the overview. This is not confirmation that the transmitter has finished processing. If submission fails, check connection and transmitter readiness before trying again.

### Placement

Open **Transmitter Placement**, adjust the transmitter using the signal feedback, then leave the page when finished. Placement signal describes the transmitter-to-sensor connection, not the phone's Bluetooth connection.

### Credentials and sign out

Account or region changes can interrupt reception and require ES365 registration again, so keep internet available.

**Sign out** is inside the expanded credentials group. After confirmation, it deletes the saved username/password and pending cloud uploads and pauses the connection. AAPS glucose history is retained. To resume, enter the credentials again and connect. Use Disconnect, rather than Sign out, for a temporary pause.

## Using the original Eversense app

**The original app can stay installed; uninstalling it is not required.** Keep it for sensor setup, transmitter settings and switching back to the manufacturer workflow.

- **E3:** parallel reception can work. Keep using both only if both receive current readings reliably.
- **ES365:** reliable parallel reception has not been established. Start with one active receiver.

If the apps compete, use Android **Settings > Apps > Eversense / Eversense 365 > Force stop** for the original app, then connect in AAPS. Swiping its window away may not stop its Bluetooth service. A force-stopped app does not provide its normal phone notifications until restarted.

To switch back, disconnect in AAPS first, then open the original app and check reception. Returning to AAPS may require ES365 online registration again. Do not clear app storage, remove Bluetooth pairing or relink the sensor simply to switch receivers.

## Offline use and cloud upload

After initial setup, readings can be received without internet, including in airplane mode with Bluetooth enabled. ES365 needs internet if registration is required again.

DMS upload is optional and does not affect local glucose reception. Uploads are attempted at most once per hour; readings are kept for up to seven days while waiting for connectivity.

## Troubleshooting and limits

| Problem | Check or limitation |
| --- | --- |
| No scan result | Bluetooth, Nearby devices permissions, range and transmitter state. Allow ten seconds for the scan. |
| Connect uses the old transmitter | Use Change Transmitter. |
| ES365 cannot finish registration | Internet, DMS credentials and European region. |
| No readings after Disconnect | Press Connect to resume automatic connection attempts. |
| Connected but no fresh glucose | Check the glucose timestamp, transmitter state, battery and placement. A status sync is not a new reading. |
| Gaps remain in history | E3 has no glucose backfill. ES365 recovery is limited, not a complete archive download. |
| Readings appear in AAPS but not DMS | Check upload, account/region and internet; allow for the hourly limit. Some recovered ES365 history cannot be uploaded. |

A settings export does not replace transmitter setup on a new phone. ES365 registration may be required again.

For a problem report, include the AAPS build, transmitter model, phone/Android version, problem time and whether the original app was running. Enable Diagnostic logging briefly to reproduce the issue, then turn it off. Review logs for private health/device data before sharing.
