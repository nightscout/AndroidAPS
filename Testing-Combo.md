- [ ] Pairing
  - [ ] Pairing works with `combo-scripter-v2` branch
- [ ] Bolusing
  - [ ] Cancelling bolus at various stages and checking error message (if any) and check
        no bolus or a partial bolus was delivered
  - [ ] Cancelling a bolus 0.2 U before its finished (cancelling requires pressing up/down
        3s, so the cancellation will be too late, but the code should handle it and report
        the full bolus being delivered
  - [ ] Low cartridge alarm during bolus
    - [ ] must be confirmed by AAPS
    - [ ] bolus must have been fully delivered by pump
    - [ ] bolus must have been added to DB
    - [ ] the confirmed pump warning must be raised as a notification in AAPS
    - [ ] notification must also appear on smartwatch
  - [ ] An error during bolus must yield an error in AAPS
  - [ ] An error during bolus must yield a notification on a smartwatch
  - [ ] Bolusing e.g. 4 U if reservoir has only 2 U left must be rejected
  - [ ] Pressing a button on the pump before bolus delivery started must be handled gracefully
    - [ ] Same as above, but moving pump out of range
  - [ ] Pressing a button on the pump after bolus delivery has started must be handled
        gracefully (bolus finished and AAPS must correctly add it the the DB)
    - [ ] Same as above, but moving pump out of range
  - [ ] Giving a bolus on the pump and immediately afterwards entering a bolus on AAPS
        must reject the bolus with a warning that IOB is not up to date
  - [ ] A bolus given on the pump must appear in AAPS after the next communication or when
        manually refreshing via the Combo tab
  - [ ] Test the highest bolus you'd ever give yourself (AAPS has a configurable limit and the pump
        has a limit which can be configured with the Config SW)
- [ ] BT disconnect issues
  - [ ] Moving pump out of reach when setting TBR causes "TBR cancelled" alarm on pump.
        When putting pump close to phone, AAPS must confirm the alert and successfully
        retry setting TBR
  - [ ] Same as above while bolusing must report an error and NOT retry the command
- [ ] AAPS start
  - [ ] Starting AAPS without a reachable pump must show something sensible in the Combo tab
        (that is not hang indefinitely with "initializing" activity)
  - [ ] Starting AAPS without a reachable pump must trigger "pump unrechable" alert after
        5 to 10 min.
  - [ ] If 'sync profile to pump' is set the basal rate profile must be synced to pump on start
  - [ ] If 'sync profile to pump' is set NOT set, the pump's profile must not be touched
- [ ] Read history using Smartpix and compare with AAPS' DB (treatment tab)
      Esp. those times we communication was interrupted, boluses were cancelled, ...
  - [ ] Boluses
  - [ ] TBR
  - [ ] Alerts
  - [ ] TDDs
- [ ] Date/Time
  - [ ] If time is off by more than 2m, a warning must be shown on the main screen whenever
        a connection to the pump is established
  - [ ] Since history records store the year, it must be inferred. Set phone and pump to something
        like 31.21.17, 23:00, cause an alert, deliver a bolus, set a TBR, let the new year arrive
        trigger the above events again and verify AAPS has correctly read them.
- [ ] Disconnected pump (pump unreachable)
      - [ ] ...
- [ ] Refilling cartridge
  - [ ] If TBR was cancelled by refilling, AAPS must detect this and update the TBR treatment
        accordingly
- [ ] Stress testing
  - PersistentNotification plugin disabled
  - Lots of comms running, like Wifi, GSM, BT audio
  - AAPS running in background
  - Foreground app stresses the phone's memory, CPU (like a game) (potentially) pushing AAPS out of memory
  - [ ] TBR must still be set/cancelled while running in the background
  - [ ] With the pump powered off or out of reach, the 'pump unreachable alert' must still
        trigger
- [ ] Combo tab
  - [ ] Check displayed data (state, battery, reservoir, last bolus, temp basal) is the same
        as on the pump
  - [ ] Last connection must indicate issues (yellow if recent connections/commands failed),
        red if communications failed for 30 or more minutes
- [ ] Unsafe usage
  - [ ] An extended or multiwave bolus given within the last six hour must raise an alert and
        closed loop functionality (Loop plugin shows "disabled by constrains" when "execute" is pressed)
  - [ ] Closed loop functionality must resume 6 h after the last ext/multiwave bolus
  - [ ] An active ext/multiwave bolus (a history record is created only after the bolus completed)
        must also raise an alert and disable loop
  - [ ] If a basal rate other than profile 1 is activated, this must also raise an alert and disable
        the loop for 6 h
- [ ] Reading/setting basal profile
  - [ ] AAPS reads basal rate properly
  - [ ] AAPS doesn't touch pump basal if "Sync to profile" pref not set
  - [ ] AAPS updates basal rate if "Sync to profile" is enabled
  - [ ] Test profile with 115% (or something like that) change to ask the
        pump for basal rates like 0.812, which should then be set propely
- [ ] Taking over alerts
  - [ ] If an error alert is active on the pump, pressing refresh shall display the error
        in the Combo tab but NOT confirm it. Easiest error to trigger: rewind piston
        and attempt to start the pump, this will trigger E11: Not primed.
  - [ ] Pressing refresh while a low cartridge or low battery alarm is active
        must confirm the alarm, indicate the new status in the Combo tab and
        show a notification on the overview screen
- [ ] Misc
  - [ ] Pump state is correctly uploaded to Nightscout

