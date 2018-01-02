**This software is part of a DIY solution and is not a product, but
requires YOU to read, learn and understand the system and how to use it.
It is not something that does all your diabetes management for you, but
allows you to improve your diabetes and quality of life significantly
if you're willing to put in the time required.
You alone are responsible for what you do with it.**

Hardware requirements:
- A Roche Accu-Chek Combo (any firmware, they all work)
- A Smartpix or Realtyme device together with the 360 Configuration
  Software to configure the pump.
  Roche sends out Smartpix devices and the configuration software
  free of charge to their customers upon request.
- A compatible phone: An Android phone with a phone running LineageOS 14.1 (formerly CyanogenMod)
- To build AndroidAPS with Combo support you need the latest Android Studio 3 version

Limitations:
- Extended bolus and multiwave bolus are not supported.
- Only one basal profile is supported.
- Setting a basal profile other than 1 on the pump, or delivering extended boluses or multiwave
  boluses from the pump interferes with TBRs and forces the loop into low-suspend only mode for 6 hours
  as the the loop can't run safely under those conditions.
- It's currently not possible to set the time and date on the pump, so daylight saving times
  changes have to be performed manually.
- There's a bug in the pump's firmware that's triggered when "too much" communication happens
  with the pump. Specifically, this issue occurs when going from just issuing commands to the pump
  to reading the pump's data and history. For that reason, a minimal amount of data is read from
  the pump.
  The bug might still rarely occur and causes the pump to not accept any connection
  unless a button is physically pressed on the pump (make sure the 'pump unreachable' alert is enabled
  so you'll get an alarm if the bug occurs.
  Therefore, the pump's reservoir level is not read and the pump status information uploaded to Nightscout
  shows fake numbers of 150 (above low threshold - which can be configured via the configuration
  tool), 8 (below low threshold, triggers NS alarm since < 10) and 0 if the reservoir is empty.
  Furthermore, no history (from the My Data menu) is read unless absolutely required.
  Reading all pump data can be forced through the Combo tab, to import
  events that happened solely on the pump, but that code has been tested less, might cause duplicates and may
  trigger the bug, so it's strongly recommended to stick to the usage scenario of controlling the
  pump solely through AAPS.
  Checking history, reservoir level etc on the pump causes no issues but should be avoided
  when the Bluetooth icon is displayed on the display, indicating that AAPS is communicating with the pump.
- Currently only basal rates in the range of 0.05 to 10 U/h are supported (this also applies when modifying
  a profile, e.g. when increasing to 200%, the highest basal rate must not exceed 5 U/h since it will be
  doubled. Similarly, when reducing to 50%, the lowest basal rate must be at least 0.10 U/h).

Setup:
- Configure pump using 360 config software.
  - Set/leave the menu configuration as "Standard", this will show only the supported
    menus/actions on the pump and hide those which are unsupported (extended/multiwave bolus,
    multiple basal rates), which cause the loop functionality to be restricted when used because
    it's not possible to run the loop in a safe manner when used.
  - Set maximum TBR to 500%
  - Disable end of TBR alert
  - Set TBR duration step-size to 15 min
  - Set low cartridge alarm to your liking
  - Enable keylock (can also be set on the pump directly, see usage section on reasoning)
- Get Android Studio 3 https://developer.android.com/studio/index.html
- Follow the link http://ruffy.AndroidAPS.org and clone via git (branch `combo-scripter-v2`)
- Pair the pump using ruffy, if it doesn't work after multiple attempts, switch to the `pairing` branch, pair,
  then switch back the original branch. If the pump is already paired and
  can be controlled via ruffy, installing the above version is sufficient.
  If AAPS is already installed, switch to the MDI plugin to avoid the Combo
  plugin from interfering with ruffy during the pairing process.
  Note that the pairing processing is somewhat fragile (but only has to be done once)
  and may need a few attempts;
  quickly acknowledge prompts and when starting over, remove the pump device
  from the bluetooth settings beforehand.
  When AAPS is using ruffy, the ruffy app can't be used. The easiest way is to just
  reboot the phone after the pairing process and let AAPS start ruffy in the background.
- Clone AndroidAPS from https://github.com/jotomo/AndroidAPS (branch `combo-scripter-v2`)
  and build AAPS using the instructions in the wiki http://wiki.AndroidAPS.org
- Before enabling the Combo plugin in AAPS make sure your profile is set up
  correctly and your basal profile is up to date as AAPS will sync the basal profile
  to the pump.

Usage:
- Keep in mind that this is not a product, esp. in the beginning the user needs to monitor and understand the system,
  its limitations and how it can fail. It is strongly advised NOT to use this system when the person
  using is not able to fully understand the system.
- This integration uses the same functionality which the meter provides that comes with the Combo.
  The meter allows to mirror the pump screen and forwards button presses to the pump. The connection
  to the pump and this forwarding is what the ruffy app does. A `scripter` components reads the screen
  and automates inputing boluses, TBRs etc and making sure inputs are processed correctly (that's what
  the scripter-part in the branch name stands for).
  AAPS then interacts with the scripter to apply loop commands and to administer boluses.
  This mode has some restrictions: it's comparatively slow (but well fast enough for what it is used for),
  doesn't support reading history continuously and setting a TBR or giving a bolus causes the pump to
  vibrate.
- The integration of the Combo with AndroidAPS is designed with the assumption that all inputs are
  made via AndroidAPS. Boluses entered on the pump will NOT be detected by AAPS and may therefore
  result in too much insulin being delivered.
- The pump's first basal rate profile is read on app start and is updated by AAPS. Manually changing
  the pump's basal rate profile will lead to wrong basals being delivered and is NOT supported.
- It's recommended to enable key lock on the pump to prevent bolusing from the pump, esp. when the
  pump was used before and quick bolusing was a habit.
  Also, with keylock enabled, accidentally pressing a key will NOT interrupt a running command
- When a BOLUS/TBR CANCELLED alert starts on the pump during bolusing or setting a TBR, this is
  caused by a disconnect between pump and phone. The app will try to reconnect and confirm the alert
  and then retry the last action (boluses are NOT retried for safety reasons). Therefore,
  such an alarm shall be ignored (cancelling it is not a big issue, but will lead to the currently
  active action to have to wait till the pump's display turns off before it can reconnect to the
  pump). If the pump's alarm continues, the last action might have failed, in which case the user
  needs to confirm the alarm.
- When a low cartridge or low battery alarm is raised during a bolus, they are confirmed and shown
  as a notification in AAPS. If they occur while no connection is open to the pump, going to the
  combo tab and hitting the Refresh button will take over those alerts by confirming them and
  showing a notification in AAPS.
- When AAPS fails to confirm a TBR CANCELLED alert, or one is raised for a different reason,
  hitting Refresh in the Combo tab establishes a connection, confirms the alert and shows
  a notification for it in AAPS. This can safely be done, since those alerts are benign - an
  appropriate TBR will be set again during the next loop iteration.
- For all other alerts raised by the pump: connecting to the pump will show the alert message in
  the Combo tab, e.g. "State: E4: Occlusion" as well as showing a notification on the main screen.
  An error will raise an urgent notification.
- After pairing, ruffy should not be used directly (AAPS will start in the background as needed),
  since using ruffy at AAPS at the same time is not supported.
- If AAPS crashes (or is stopped from the debugger) while AAPS and the pump were comunicating (using
  ruffy), it might be necessary to force close ruffy. Restarting AAPS will start ruffy again.
- Read the documentation on the wiki as well as the docs at https://openaps.org
- Don't press any buttons on the pump while AAPS communicates with the pump (Bluetooth logo is
  shown on the pump).
- If the loop requests a running TBR to be cancelled the Combo will set a TBR of 90% or 110%
  for 15 minutes instead. This is because cancelling a TBR causes an alert on the pump which
  causes a lot of vibrations.
- Due to the bug (which causes the pump to become unreachable when reading history regularly),
  a delivered bolus will NOT be added to treatments when the connection was lost during bolusing
  or when a pump error occurs (e.g. occlusion). This will raise a message (and play an annoying
  sound) and the user will have a create a bolus record via Careportal/NS manually. Currently
  this requires an available NS installation and being online (for a short time). Hopefully
  both of these issues can be resolved in future versions.

Known issues:
- Occasionally (every couple of days or so) AAPS might fail to automatically cancel
  a TBR CANCELLED alert the user then needs to deal with (press the refresh button in AAPS
  to transfer the warning to AAPS or confirm the alert on the pump).
- Similarly, the 'pump unreachable' bug may occur from time to time, which requires confirming
  the alert on the pump to get the pump to accept connections again.
- Overall the integration seems rather robust, but there are limits to the way the
  pump is controlled and how stable BT is, so there will be minor issues like the above
  from time to time, though they're small compared to what works well.
- AAPS might be unresponsive for 10-30s or so when starting and calculating sensitivity.
  AAPS might also be unresponsive when doing background work, e.g. after receiving a new
  glucose reading.

Reporting bugs:
- Note the precise time the problem occurred and describe the circumstances and steps that caused
  the problem
- Note the Build version (found in the About dialog in the app, when pressing the three dots in the
  upper-right corner).
- Attach the app's log files, which can be found on the phone in
  _/storage/emulated/0/Android/data/info.nightscout.androidaps/_

Components/Layers (developers):
- AndroidAPS
- ComboPlugin
- ruffy-spi (RuffyCommands interface to plug in lower layer)
- Scripting layer (ruffyscripter) / Command layer
- Driver layer (ruffy)
