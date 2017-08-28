**Do not share this code beyond members of this project. This is a preview release with significant limitations.**

Release date: **unreleased, there are still bugs!**

Hardware requirements:
- A Roche Accu-Chek Combo (any firmware, they all work)
- An Android phone with either Android < 4.2 (and possibly >= 8.1 in the future) or a phone running LineageOS 14.1 (formerly CyanogenMod)
- Root rigths on any android phone and use this method: http://github.com/gregorybel/combo-pairing/

Limitations:
- Only the state from the main menu is read from the pump and returned with every command and is displayed on the combo tab, nothing else is read back from the pump at this point. Hence, all treatments must be entered via AAPS, no operations shall be performed on the pump itself.
- Only bolus, TBR set, TBR cancel commands are supported
- Alarms (low cartridge, low battery) need to be dealt with on the pump directly
- AAPS rebinds the ruffy service when it goes away, which restart the service. However, when AAPS crashes hard (and thus didn't unbind from ruffy), ruffy runs into a lot of DeadObjectExceptions and might have to be restarted. Though I've only seen this when stopping the debugger.
- A pump suspend state is detected and attempts to bolus or set TBR in suspend mode will raise an alert.
- No bolus progress is displayed and there's no way to abort bolusing via AndroidAPS at this time.

What works:
- Having OpenAPS (via AAPS) set the _temporary_ basal rate
- Issue bolus when entering treatments on the home screen (bolus and calculator dialog). This displays a _Waiting for pump_ popup, without progress  report, even in the case of a carb-only treatment, due to a current limitation in AAPS.
- Commands controlling the pump check each step to ensure they're executed properly and abort if anything but the expected screen is shown.
- Command execution is monitored, timeouts and errors trigger notification alerts (which are noisy and vibrate and which are re-raised every 5m as long as the problem persists).
- Establishing a connection with ruffy/pump is done as needed, connection is terminated after 5s of inactivity.
- Most errors are transient, failed commands usually run through the next loop iteration, or succeed when they are automatically retried (TBR commands only, bolus commands are _not_ automatically retried). If AAPS recovers, the alarm will only trigger again if there is another error (and of course keep ringing if an error persists)

How to use:
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

Testing:
- When there's an error, check the Combo tab, there should be a clue.
- From /_storage/emulated/0/Android/data/info.nightscout.androidaps/_)
- Try to reproduce and open a ticket, add the hash of the commit used (right-click on the branch name select
 _Copy revision number_ or use _git show_ on the command-line) the branch name. Attach the log to the issue and label it as a bug.
