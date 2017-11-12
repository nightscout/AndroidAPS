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
  - [ ] Time must be set properly upon app start if pump time is off
  - [ ] Daylight saving time changes must be detected and pump time updated.
        Basically any time change greater than a minute on the phone must be picked up
        by AAPS and result in the pump's time being updated the next time communication
        with the pump takes place, so disabling automatic time updates on the phone and
        changing the phone clock manually should allow testing this
- [ ] Disconnected pump
  ...
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

