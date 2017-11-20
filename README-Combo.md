**This software is part of a DIY solution. You're solely responsible for everything that might happen**

Hardware requirements:
- A Roche Accu-Chek Combo (any firmware, they all work)
- A Smartpix or Realtyme device together with the 360 Configuration Software to configure the pump.
  Roche sends these out free of charge to their customers.
- A compatible phone:
    - An Android phone with either Android < 4.2 (and possibly >= 8.1 in the future) or a phone running LineageOS 14.1 (formerly CyanogenMod)
    - With a phone running LineageOS a pairing can be created that can then be transfered to a rooted phone which can then be used as a loop
      phone using these instructions:  http://github.com/gregorybel/combo-pairing/

How to use v1:
- Note: Documentation on how to use AndroidAPS is available at https://github.com/MilosKozak/AndroidAPS/wiki
- Configure the pumps settings as described here: https://gitlab.com/jotomo/AndroidAPS/wikis/pump-configuration
- On the pump, go to the _Menu settings_ menu and select _Standard_. This disables extended and multiwave and all but one basal rate. Those aren't supported by AAPS-Combo.
- Clone ruffy from git@gitlab.com:jotomo/ruffy.git and build from the `develop` branch.
- If ruffy crashes during pairing, check out the `pairing` branch, build it, pair the pump and then switch back to `develop` and build and install.
  After that, it's not required to open ruffy, as Android will start ruffy automatically when its services are required.
  Ruffy enables bluetooth discovery for 60s, if that's an issue, start the pairing process in ruffy and on the pump and switch to the bluetooth settings of your phone
  and keep it open. This leaves the bluetoth in discovery mode as long as the screen is on. When pump shows the pairing code switch back
  to ruffy and enter the displayed code.
- Clone AndroidAPS from git@gitlab.com:jotomo/AndroidAPS.git and build from the `develop` branch (see the AndroidAPS wiki for building options.
- Enter all treatments via AndroidAPS, since AAPS will be oblivious to boluses entered on the pump, as the pump history is not read yet
- There are no warnings regarding low cartridge/battery since we're not reading that data from the pump just yet (next version)
- Ensure you set the basal rate on the pump to the values of the profile used in AAPS, the profile isn't synced to the pump yet! Also don't forget to set/create a profile in AAPS **and** trigger a profile switch afterwards to activate it.
- Don't press any buttons while commands are running (BT logo is displayed on pump display), or the command will fail
- Let the ruffy app run in the background, don't interact with it, esp. not while AAPS is issuing commands to the pump.
- To change cartridge: stop the loop, wait for AAPS to cancel any running TBR (check Combo tab), suspend/stop the pump, change cartridge, start the pump again and re-enable the loop. To enable/disable the loop, long press on the "Closed loop" label on the home screen (try long pressing all items next to it too ...).
- AAPS guides first time users through objectives (tab must be set visible in config first, it's enabled irregardless of visibility). This ensures users learn to use AndroidAPS step by step as more and more advanced options are enabled.
- If a running TBR was cancelled on the pump or through the pump: press _Cancel TBR_ in in AAPS, so AAPS knows about this as well. AAPS will issue a _cancel TBR command_ to the pump, which the pump will ignore. AAPS will set a new TBR the next time the loop runs or is manually triggered.
- While all errors are reported, dealing with errors does not yet cover all cases. So after an error, the pump might be running a different TBR than what AAPS thinks the pump does.
  Issue a TBR Cancel in AAPS to get them lined up. In case of boluses, check the pump to be sure (press check twice to enter the bolus history menu).
- Update this page if something is unclear or missing

Testing v1:
- When there's an error, check the Combo tab, there should be a clue.
- Try to reproduce and open a ticket, add tag if any, otherwise add the hash of the commit used (right-click on the branch name select
 _Copy revision number_ or use _git show_ on the command-line) the branch name. Attach the log to the issue and label it as a bug.
  The logs can be found in _/storage/emulated/0/Android/data/info.nightscout.androidaps/_

v2
Limitations
- Extended bolus and multiwave bolus are not supported.
- If multiple boluses are given within a single minute, only one might be recognized.
  This is due to the Combo saving history records with minute-precision only.
  However, this case is only possible for very small boluses and is unlikely to occur
  in non-testing scenarios (e.g. bolusing from the pump and then immediately bolusing
  from AAPS).

Usage
- This is not a product, esp. in the beginning the user needs to monitor and understand the system, its limitations and how it
  can fail. It is strongly advised NOT to use this system when the person using is not able to fully understand the system.
- The integration of the Combo with AndroidAPS is designed with the assumption that all inputs are made via AndroidAPS.
  While there are checks that will detected boluses entered directly on the pump, which will be added to the history and be
  included in IOB calulations, there are delays until AAPS becomes aware of those bolusus. It is therefore strongly adviced
  to only bolus via AndroidAPS (it's also only possible to enter carbs via AndroidAPS, required for advanced loop functionality).
- It's recommended to enable key lock on the pump to prevent bolusing, esp. when the pump was used before and quick bolusing was a habit.
- When a BOLUS/TBR CANCELLED alert starts on the pump during bolusing or setting a TBR, this is caused by disconnect
  between pump and phone. The app will try to reconnect and confirm the alert and then retry the last action. Therefore,
  such an alarm shall be ignored (cancelling it is not a big issue, but will lead to the currently active action to
  have to wait till the pump's display turns off before it can reconnect to the pump).
  If the pump's alarm continues, the last action might have failed, in which case the user needs to confirm the alarm
- If AAPS crashes (or is stopped from the debugger) while pump comms where happening, it might be necessary to force close ruffy.
  Restarting AAPS will also start ruffy again.