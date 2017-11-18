- [x] Bugs
  - [x] ruffy: Accessing the quick info menu yields noMenu when cartridge is low
  - [x] ruffy: Multi-digit error codes in error history aren't supported
  - [-] No connection can be established anymore; ruffy issue i can't solve
    - Removing the BT device's bonding (!=pairing) fixes it; nope it doesn't
    - Ruffy logs in BTConnection:163  handler.fail("no connection possible: " + e.getMessage());
    - When developing (and thus killing/restarting AAPS often) this is trigger more frequently, leaving
      some ruffy-releated (BT) cache in disarray? Immune to wiping, only repairing seems to work so far
  - [x] Timeout connecting to pump -> crash (result.state wasn't filled in case of timeout)
  - [x] Bolus deleted in treatments  (marked invalid?!) is re-added when pump reads history
        Probably fixed through other bugfixes, irrelevant though as "pump history records" can't
        be deleted in AAPS
  - [x] Issue of creating TBR start date from main menu time, which might be off by a minute
        when we read it as a history record. End date time might be slightly off, unless
         CancelTempBasal is updated to read from history (probably not worth it).
         What would happen if while setting the TBR the start time was 12:01 but then
         we read a history record with a start time of 12:02, both running 30min.
         Would the former be trimmed to 1m? That'd be acceptable (this edge case occurs
         if between confirm the TBR and reading the main menu date, the second goes
         from 59.9999 to 0)
        Only in issue if TBR was set on pump. In that case the TBR is cancelled and the
        resulting history record is read
- [ ] Tasks
  - [x] Main
    - [x] On command error: recover by returning to main menu
          check entry and exit points of commands
    - [x] Taking over alerts
      - [x] On connect
      - [x] During bolusing
        - Can the low warning be set as high as 280 or so? To be able to trigger it with a quick refill? yup.
      - [x] Check for errors first thing in runCommand? Whenever analysing a CommandResult?
      - [x] forward all warnings and errors encountered, but only confirm benign ones
    - [-] Properly reporting back failures in UI, maybe warn if lots of errors (unreachable alert might
          already be enough, since it's based on 'lastSuccessfulConnection', where a connection is
          considered successful if the command during that connection succeeded.
          KeepAlive triggered check suffices.
    - [x] Finish ComboPlugin structure to only have to plug date setting and pump setting in later
          (actually, just use stub methods)
    - [-] Updating time on pump
      - [x] Raise a warning if time clock is off
      - [-] Ruffy: support reading date/time menus
    - [-] Setting pump basal profile (20h)
    - [-] Pairing (and sourcing ruffy) (20h)
    - [x] Run readReservoirAndBolusLevel after SetTbr too so boluses on the pump are caught sooner?
          Currently the pump gets to know such a record when bolusing or when refresh() is called
          after 15m of no other command taking place. IOB will then be current with next loop
          checkPumpHistory is now called every 15m the least after executing a command
    - [x] Reading history
      - [x] Bolus
        - [x] Read
        - [x] Update DB
      - [x] TBRs
        - [x] Read
        - [x] Update DB
      - [x] Alerts
        - [x] Read
        - [x] Update DB? No, but raise an alert if new ones are found beyond those read at startup
              (Those that occurred while AAPS was not active needn't be turned into alarms,
               the user had to deal with them on the pump already). (Done via "Taking over alerts")
        - [x] Display in UI
      - [x] TDD
        - [x] Read
        - [x] Update DB? No, just write to plugin cache
        - [x] Display in UI
    - [x] Optimize reading full history to pass timestamps of last known records to avoid reading known records
          iteration.
  - [x] Cleanups
    - [x] TBR cancel logic
    - [x] Check dynamic timeout logic in RuffyScripter.runCommand
    - [x] Finish 'enacted' removal rewrite (esp. cancel tbr)
    - [x] ComboPlugin, commands invocation, checks, upadting combo store/cache
    - [x] Finish reconnect
  - [x] Adrian says: when changing time; last treatments timestamp is  updated??
    - Nope, at least not with a 2014 pump (SW1.06?)
  - [x] Reconnect and auto-retry for commands
  - [x] Empty battery state
  - [ ] Integrate alarms
    - [x] Remove combo alerter thread
    - [ ] Fix display of alarms on mainscreen (increase height if needed)
    - [-] Display errors in combo tab(?), nope notifications are better suited; also there's the alerts thing already
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
