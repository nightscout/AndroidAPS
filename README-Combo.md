**This software is part of a DIY solution and is not a product.
You alone are responsible for what you do with it**

Hardware requirements:
- A Roche Accu-Chek Combo (any firmware, they all work)
- A Smartpix or Realtyme device together with the 360 Configuration
  Software to configure the pump.
  Roche sends these out free of charge to their customers upon request.
- A compatible phone:
    - An Android phone with either Android < 4.2 (and possibly >= 8.1
      in the future) or a phone running LineageOS 14.1
      (formerly CyanogenMod)
    - With a phone running LineageOS a pairing can be created that can
      then be transfered to a rooted phone which can then be used as a
      loop phone using these instructions:
      http://github.com/gregorybel/combo-pairing/

Limitations:
- Extended bolus and multiwave bolus are not supported.
- Only one basal profile is supported.
- Setting a basal profile other than 1 on the pump, or delivering extended boluses or multiwave
  boluses from the pump will disable the loop functionality for 6h as the the loop can't run
  safely under those conditions.
- If multiple boluses are given within a single minute, only one might
  be recognized. This is due to the Combo saving history records with
  minute-precision only. However, this case is only possible for very
  small boluses and is unlikely to occur in non-testing scenarios
  (e.g. bolusing from the pump and then immediately bolusing from AAPS).
- If a TBR is set on the pump, AAPS will cancel it. This is because it's not possible to determine
  the start point of a TBR until it is finished or was cancelled at which point a record in the
  pumps history is created. Before that, there is none and it's simply not possible to determine
  the TBRs influence on IOB. Set TBR using AAPS instead.
- It's currently not possible to set the time and date on the pump (only reading the time - but
  not the date - is supported raises a warning in AAPS to update the pump clock manually).
  Thus, the pump's clock must be updated manually when the clocks are turned forward/backward
  for daylight saving time.

Setup v2:
- Configure pump using 360 config software.
  - Set/leave the menu configuration as "Standard"
  - Set maximum TBR to 500%
  - Disable end of TBR alert
  - Set low cartridge alarm to your licking
- Get ruffy from https://github.com/jotomo/ruffy (branch `combo-scripter-v2`)
- Pair the pump, if it doesn't work, switch to the `pairing` branch, pair,
  then switch back the original branch
- Get AndroidAPS from https://gitlab.com/jotomo/KEF (Branch `combo-scripter-v2`)
- Make sure you're basal rate is properly set on the pump

Usage:
- This is not a product, esp. in the beginning the user needs to monitor and understand the system,
  its limitations and how it can fail. It is strongly advised NOT to use this system when the person
  using is not able to fully understand the system.
- The integration of the Combo with AndroidAPS is designed with the assumption that all inputs are
  made via AndroidAPS. While there are checks that will detected boluses entered directly on the
  pump, which will be added to the history and be included in IOB calulations, there are delays
  until AAPS becomes aware of those bolusus (up to 15m). It is therefore strongly adviced
  to only bolus via AndroidAPS (it's also only possible to enter carbs via AndroidAPS, required for
  advanced loop functionality).
- It's recommended to enable key lock on the pump to prevent bolusing from the pump, esp. when the
  pump was used before and quick bolusing was a habit.
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