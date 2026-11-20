# Eversense README screenshots

These PNGs are unmodified Android screenshots from AAPS 4.0.0, commit `5d85b5094e`, captured on a Pixel 6a emulator running Android 16 (API 36.1), at 1080 × 2400 pixels. The app uses English, dark theme and the default color scheme. No DMS credentials, transmitter identifiers or patient readings were entered.

| File | Page/state |
| --- | --- |
| `bg-source.png` | Configuration > BG Source, Eversense selected |
| `settings.png` | Eversense settings, credentials collapsed |
| `credentials.png` | Eversense settings, credentials expanded and empty |
| `status-connection.png` | Status & Connection, disconnected and never synchronized |
| `calibration.png` | Calibrate transmitter, empty field focused, keyboard dismissed |
| `placement.png` | Transmitter Placement, disconnected |
| `overview-navigation.png` | Overview with navigation menu, Settings gear and configured Eversense Calibration QuickLaunch action |
| `settings-list.png` | Settings opened from the overview gear, Simple mode off, Eversense section visible |
| `navigation-menu.png` | Left navigation menu with Configuration |
| `plugin-navigation.png` | Eversense plugin page with its own preferences gear, no readings |
| `quicklaunch-configuration.png` | Configure QuickLaunch before adding Eversense Calibration using its plus button |
| `quicklaunch-calibration.png` | Eversense Calibration opened from QuickLaunch, empty BG field, no submission |

The overview uses the default virtual pump with no profile or glucose readings. Its pump indicators are emulator defaults. For the Settings list capture, Simple mode was turned off and the settings page was reopened. Eversense Calibration was added to QuickLaunch through its configuration screen; no transmitter connection or calibration submission was performed.

To update an image, install the matching build on an emulator, navigate to the corresponding page, wait for transitions to finish and capture the screen:

```sh
adb -s <emulator-serial> shell screencap -p /sdcard/eversense-screen.png
adb -s <emulator-serial> pull /sdcard/eversense-screen.png <filename>.png
```

Check the image against the current UI before replacing it. Keep credentials empty and use a disconnected emulator to avoid publishing private device or health data. The parent README displays images at 320 pixels wide and explains controls using their visible labels.
