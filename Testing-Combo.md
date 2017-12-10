- [ ] Pairing
  - [ ] Pairing works with `combo-scripter-v2` branch
- [ ] Bolusing
  - [ ] Cancelling bolus at various stages and checking error message (if any) and check
        no bolus or a partial bolus was delivered
  - [ ] Enter a bolus of 2 U and press cancel when delivery is at 1.7 (cancelling requires AAAPS
        to press the up button for 3 seconds, so the cancellation attempt will not succeed because delivery
        ends before those 3 seconds are elapsed. The code should handle this without giving an
        error and adding the full bolus to treatments.
  - [ ] Low cartridge alarm during bolus
    - [ ] alarm must be confirmed by AAPS
    - [ ] bolus must have been fully delivered by pump
    - [ ] bolus must have been added to DB
    - [ ] the confirmed pump warning must be raised as a notification in AAPS
    - [ ] notification must also appear on smartwatch
  - [ ] An error during bolus must yield an error in AAPS
  - [ ] An error during bolus must yield a notification on a smartwatch
  - [ ] Test bolusing a bolus bigger than what's left in the reservoir. A message to check what
        was actually delivered must appear (this is a corner-case where when practically can't
        check what was actually delivered).
  - [ ] Pressing a button on the pump before bolus delivery started must be handled gracefully
    - [ ] Same as above, but moving pump out of range
  - [ ] Pressing a button on the pump after bolus delivery has started must be handled
        gracefully (bolus finished and AAPS must correctly add it the the DB)
    - [ ] Same as above, but moving pump out of range
  - [ ] Test the highest bolus you'd ever give yourself (AAPS has a configurable limit and the pump
        has a limit which can be configured with the Config SW), no timeout or other issues must show
  - [ ] Test a bolus exceeding the pumps max bolus; should show a generic error message
- [ ] BT disconnect issues
  - [ ] Moving pump out of reach when setting TBR causes "TBR cancelled" alarm on pump.
        When putting pump close to phone, AAPS must confirm the alert and successfully
        retry setting TBR (reconnects are a best-effort kind of a thing, so this might not always work)
  - [ ] When a disconnect occurs, the pump's screen shows the error and the pump only accepts a connection
        again when a timeout has occurred. A recovery should be quicker if that timeout is decreased.
        It might be interesting to experiment with the Config software to set lower menu or display timeouts
        (or whatever they're called ...) to improve recovery speed.
  - [ ] Same as above while bolusing must report an error and NOT retry the command
- [ ] AAPS start
  - [ ] Starting AAPS without a reachable pump must show something sensible in the Combo tab
        (not hanging indefinitely with "initializing" activity)
  - [ ] Starting AAPS without a reachable pump must trigger "pump unrechable" alert after the configured threshold
  - [ ] If the pump's profile doesn't match AAPS', the pump must be updated when AAPS starts
  - [ ] Doing a profile change (to shift time or increase/decrease insulin), the pump's basal profile must be updated
  - [ ] If a profile change has a duration, the pump's basal profile must be set to the original value again at the end
        (this can vary a few minutes between what the overview screen shows and when the pump is updated, as the check
        whether the pump is up-to-date or not is performed periodically and not at the exact minute a profile change ends)
- [ ] Read history using Smartpix and compare with AAPS' DB (treatment tab)
      Esp. those times we communication was interrupted, boluses were cancelled, ...
  - [ ] Boluses
  - [ ] TBR
  - [ ] Alerts
- [ ] Disconnected pump (pump unreachable)
    - [ ] With local alerts enabled for 'pump unreachable', an alert must be triggered within 5 minutes
          after the configured threshold. (Don't set the threshold too low, e.g. 10 minutes, since
          there might be no need to set a TBR within such a short time).
- [ ] Refilling cartridge
  - [ ] If TBR was cancelled by refilling, AAPS must detect this and create a TBR record in AAPS
        based on what the pump displays (not the full TBR duration, but what is displayed as remaining
        on the main screen.
- [ ] Stress testing
  - PersistentNotification plugin disabled
  - Lots of comms running, like Wifi, GSM, BT audio
  - AAPS running in background
  - Foreground app stresses the phone's memory, CPU (like a game) (potentially) pushing AAPS out of memory
  - [ ] TBR must still be set/cancelled while running in the background
  - [ ] With the pump powered off or out of reach, the 'pump unreachable alert' must still
        trigger
- [ ] Combo tab
  - [ ] Check displayed data (state, battery, reservoir, bolus, temp basal) is the same
        as on the pump
- [ ] Unsafe usage
  - [ ] An extended or multiwave bolus given within the last six hour must raise an alert and
        restrict the loop functionality to low-suspend only (setting maxIOB to zero)
  - [ ] Closed loop functionality must resume 6 h after the last ext/multiwave bolus
  - [ ] An active ext/multiwave bolus (a history record is created only after the bolus completed)
        must also raise an alert and restrict the loop
  - [ ] If a basal rate other than profile 1 is activated, this must also raise an alert and disable
        the restrict the loop
- [ ] Reading/setting basal profile
  - [ ] AAPS reads basal rate properly
  - [ ] AAPS doesn't touch pump basal if "Sync to profile" pref not set
  - [ ] AAPS updates basal rate if "Sync to profile" is enabled
  - [ ] Test profile with 115% (or something like that) change to ask the
        pump for basal rates like 0.812, which should then be set propely
  - [ ] Updating the profile extensively (200%, shifting time) takes up to 6 minutes, but
        should complete without timeout.
- [ ] Taking over alerts
  - [ ] If an error alert is active on the pump, pressing refresh shall display the error
        in the Combo tab but NOT confirm it. Easiest error to trigger: rewind piston
        and attempt to start the pump, this will trigger E11: Not primed.
  - [ ] Pressing refresh while a low cartridge or low battery alarm is active
        must confirm the alarm, indicate the new status in the Combo tab and
        show a notification on the overview screen
- [ ] Misc
  - [ ] Pump state is correctly uploaded to Nightscout

