- [ ] Bugs
  - [ ] No connection can be established anymore
    - Removing the BT device's bonding (!=pairing) fixes it; nope it doesn't
    - Ruffy logs in BTConnection:163  handler.fail("no connection possible: " + e.getMessage());
    - When developing (and thus killing/restarting AAPS often) this is trigger more frequently, leaving
      some ruffy-releated (BT) cache in disarray? Immune to wiping, only repairing seems to work so far
  - [x] Timeout connecting to pump -> crash
  - [ ] Bolus deleted in treatments  (marked invalid?!) is re-added when pump reads history
- [ ] Tasks
  - [ ] Main
    - [ ] on command error: recover by returning to main menu
    - [ ] Reading history
      - [x] Bolus
        - [x] Read
        - [x] Sync DB
      - [ ] TBRs
        - [x] Read
        - [ ] Sync DB
      - [ ] Alerts
        - [x] Read
        - [ ] Sync DB?
        - [x] Display in UI
      - [ ] TDD
        - [x] Read
        - [ ] Sync DB?
        - [x] Display in UI
    - [ ] Taking over alerts
      - [ ] On connect
      - [ ] During bolusing
      - [ ] Check for errors first thing in runCommand? Whenever analysing a CommandResult?
    - [ ] Updating time on pump
      - [ ] Ruffy: support reading date/time menus
    - [ ] Setting pump basal profile
    - [ ] Pairing
    - [ ] Run readReservoirAndBolusLevel after SetTbr too so boluses on the pump are caught sooner?
          Currently the pump gets to know such a record when bolusing or when refresh() is called
          after 15m of no other command taking place. IOB will then be current with next loop
    - [ ] Optimize reading full history to pass timestamps of last known records to avoid reading known records
          iteration.
  - [ ] Cleanups
    - [ ] Finish 'enacted' removal rewrite (esp. cancel tbr)
    - [ ] ComboPlugin, commands invocation, checks, upadting combo store/cache
    - [ ] Finish reconnect, then start testing regular actions are still stable?
  - [x] Adrian says: when changing time; last treatments timestamp is  updated??
    - Nope, at least not with a 2014 pump (SW1.06?)
  - [x] Reconnect and auto-retry for commands
  - [x] Empty battery state
  - [ ] Integrate alarms
    - [x] Remove combo alerter thread
    - [ ] Fix display of alarms on mainscreen (increase height if needed)
    - [ ] Display errors in combo tap
    - [x] Option to raise overview notifications as android notification with noise (for urgent ones?)
  - [ ] Low prio
    - [ ] Naming is messed up: pump has warnings and errors, which cause alerts; W+E are thus alerts,
          e.g. pumpErrorHistory should be renamed to alertHistory
    - [ ] Enable BT if disabled? does dana does this?
    - [ ] Finish and test German translation
    - [ ] No clean startup/shutdown; RuffyScripter is instanciated once, idle disconnect thread never killed
        - Application shut down is broken with PersistentNotification (never shut down) and WearPlugin -
          Android logs it as crashed and restarts it, thereby restarting the app (or just keeping it alive,
          also causes errors with the DB as there were attemtps to open a closed DB instance/ref.

Inbox
  - [ ] Where/when to call checkTbrMisMatch?
  - [ ] pairing: just start SetupFragment?
  - [ ] Read history, change time, check if bolus records changed
  - [ ] Updating clock on pump; see how danar does it
    - [ ] Update if mins are of by >=2m, also check/update if history has no bolus within the last 24h
  - [ ] Wakelocks? Never needded them so far ...
  - [ ] Finish Ruffyscripter simplification
    - [x] Only reconnect on interruption, return after confirm error.
    - [x] Bolus: do not check anything. Just bolus and abort. Checking history is done by CP
    - [ ] TBR: check in command or CP as well?
    - [ ] Generally, check the command was executed to the end and the MAINMENU was shown afterwards
  - [ ] General error handling: have RS report back an alert if not confirmed within 10s (since commands can confirm them)
  - [ ] forced sync after error
  - [ ] Comm errors
    - [ ] Retry? The occassional error, but have a treshold to alarm when e.g. 4 of 5 actions fail?
  - [ ] Reading history, updating DB from it
    - [ ] Detecting out of sync
  - [ ] Pump unreachable alert
    - [ ] Add option "Raise as android notification as well" like the "Forward overview screen messages to wear"?
  - [ ] Pump warnings
  - [ ] When suspended, AAPS won't try to read state again? (not tried 30m) should it? currently user must unsuspend in AAPS
  - [ ] 'last error' in fragment? warning if error rate > 50%?
  - [ ] How much noise to make when there are errors? Try to kill errors on the pump. If there are persistent issues, alert only after 20m, like xdrip does with missed readings?
