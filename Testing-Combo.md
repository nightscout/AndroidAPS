- [ ] Pairing
  - [ ] Pairing works with `combo-scripter-v2` branch
- [ ] Bolusing
  - [ ] Cancelling bolus at various stages shall not yield an error but cancel the bolus. If
        cancelled before delivery started, no treatment must have been added, if cancel after delivery
        started, the partially delivered bolus must have been added to treatments
  - [ ] Enter a bolus of 2 U and press cancel when delivery is at 1.7 (cancelling requires AAAPS
        to press the up button for 3 seconds, so the cancellation attempt will not succeed because delivery
        ends before those 3 seconds are elapsed). The code should handle this without giving an
        error and add the full bolus to treatments.
  - [ ] Recovery from connection issues during bolusing
    - [ ] Pressing a button on the pump during delivery => Progress dialog freezes, then states that recovery
          is in process and then closes; no error dialog, record correctly added to treatments
    - [ ] Breaking the connection e.g. by moving the pump away from phone for up to a minute => same as above
    - [ ] Same as above but put pump out of reach for 5 minutes => Error dialog, no record in treatments
    - [ ] Starting a bolus bigger than what's left in the reservoir => Error dialog and a record in treatments with the partially delivered bolus
    - [ ] When the connection breaks during bolusing, pressing the cancel button should not interfere with recovery and
          the delivered bolus should be added to treatments
  - [ ] Low cartridge alarm during bolus
    - [ ] alarm must be confirmed by AAPS
    - [ ] bolus must have been fully delivered by pump
    - [ ] bolus must have been added to DB
    - [ ] the confirmed pump warning must be raised as a notification in AAPS
          (or as android notification on watch/smartphone if setting "use system notifications ..." is enabled
  - [ ] Pressing a button on the pump, or moving the pump away from the phone to break connection
        must confirm the pump alert and recover to finish the bolusing.
  - [ ] If recovery fails, an error popup must be displayed
  - [ ] Test bolusing a bolus bigger than what's left in the reservoir. A message to check what
        was actually delivered must appear (this is a corner-case where we practically can't
        check what was actually delivered).
  - [ ] Pressing a button on the pump before bolus delivery started must be handled gracefully
    - [ ] Same as above, but moving pump out of range
  - [ ] Pressing a button on the pump after bolus delivery has started will freeze the progress bar,
        initiate recovery and add the delivered bolus the treatments.
    - [ ] Same as above, but moving pump out of range
  - [ ] Test the highest bolus you'd ever give yourself (AAPS has a configurable limit and the pump
        has a limit which can be configured with the Config SW), no timeout or other issues must show
- [ ] BT disconnect issues
  - [ ] Moving pump out of reach when setting TBR causes "TBR cancelled" alarm on pump.
        When putting pump close to phone, AAPS must confirm the alert and successfully
        retry setting TBR (reconnects are a best-effort kind of a thing, so this might not always work)
  - [ ] When a disconnect occurs, the pump's screen shows the error and the pump only accepts a connection
        again when the pump's menu(?) timeout has occurred. A recovery should be quicker if that timeout is decreased.
        It might be interesting to experiment with the Config software to set lower menu or display timeouts
        (or whatever they're called ...) to improve recovery speed.
- [ ] AAPS start
  - [ ] Starting AAPS without a reachable pump must show something sensible in the Combo tab
        (not hanging indefinitely with "initializing" activity)
  - [ ] Starting AAPS without a reachable pump must trigger "pump unrechable" alert after the configured threshold
  - [ ] If the pump's basal profile doesn't match AAPS', the pump must be updated when AAPS starts
- [ ] Read history using Smartpix and compare with AAPS' DB (treatment tab)
      Esp. those times we communication was interrupted, boluses were cancelled, ...
  - [ ] Boluses
  - [ ] TBR
  - [ ] Alerts
- [ ] Disconnected pump (pump unreachable)
    - [ ] With local alerts enabled for 'pump unreachable', an alert must be triggered within 5 minutes
          after the configured threshold. (Don't set the threshold too low, e.g. 10 minutes, since
          there might be no need to set a TBR within such a short time, but since there was no pump connection
          within that time, the alarm would be triggered).
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
  - [ ] Check displayed data (state, battery, reservoir, temp basal) is the same
        as on the pump
- [ ] Unsafe usage
  - [ ] An active extended or multiwave bolus must raise an alert and
        restrict the loop functionality to low-suspend only for the next 6h (setting maxIOB to zero)
        and cancel an active TBR.
  - [ ] Closed loop functionality must resume 6 h after the last ext/multiwave bolus
  - [ ] If a basal rate other than profile 1 is active on start, the pump must refuse to finish
        initialization and disable the loop. When setting the profile to 1 and refreshing,
        the pump must finish initialization and enable the loop (the overview screen will
        still show "closed loop", but the Combo and Loop tabs will say the loop is disabled
        due to a constraint violation).
  - [ ] When changing profile to one other than the first after AAPS has started and read the first
        basal profile, a warning must be shown, the loop must be disabled and the active TBR be cancelled.
  - [ ] A request to change the AAPS profil (e.g. increase to 110%) must be rejected if the pump
        doesn't have profile one active.
- [ ] Reading/setting basal profile
  - [ ] AAPS reads basal rate properly
  - [ ] Test profile with 115% (or something like that) change to ask the
        pump for basal rates like 0.812, which should then be set propely
  - [ ] Updating the profile extensively (200%, shifting time) takes up to 6 minutes, but
        should complete without timeout.
  - [ ] Doing a profile change (to shift time or increase/decrease insulin), the pump's basal profile must be updated
  - [ ] If a profile change has a duration, the pump's basal profile must be set to the original value again at the end
        (this can vary a few minutes between what the overview screen shows and when the pump is updated, as the check
        whether the pump is up-to-date or not is performed periodically and not at the exact minute a profile change ends)
- [ ] Taking over alerts
  - [ ] If an error alert is active on the pump, pressing refresh shall display the error
        in the Combo tab but NOT confirm it. Easiest error to trigger: rewind piston
        and attempt to start the pump, this will trigger E11: Not primed.
  - [ ] Pressing refresh while a low cartridge or low battery alarm is active
        must confirm the alarm, indicate the new status in the Combo tab and
        show a notification on the overview screen
  - [ ] A TBR CANCELLED is now also taken over when refreshing (since it's a benign error the loop will correct
        during the next iteration).
- [ ] Misc
  - [ ] Pump state is correctly uploaded to Nightscout (note that reservoir level are fake numbers representing
        norma/low/empty).
