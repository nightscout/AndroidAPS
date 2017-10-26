package de.jotomo.ruffy.spi;

import java.util.Date;

import de.jotomo.ruffy.spi.history.PumpHistoryRequest;

public interface RuffyCommands {
    CommandResult deliverBolus(double amount, BolusProgressReporter bolusProgressReporter);

    void cancelBolus();

    CommandResult setTbr(int percent, int duration);

    CommandResult cancelTbr();

    // TODO read Dana code wrt to syncing and such

    /** Confirms an active alarm on the pump. The state returned is the state after the alarm
     * has been confirmed. Confirmed alerts are returned in history.pumpErrorHistory. */
    @Deprecated
    // TODO rename to confirmActiveAlarm (single)
    // add a field activeAlarm to PumpState and put logic in ComboPlugin.
    CommandResult takeOverAlarms();
    // let this be an actual command in RS? or extend readPumpState wit ha flag to return confirmed errors?

    /* plan:

    let errors ring on the pump (all require interacting with the pump anyways and they
    should not be subject to errors in AAPS/ruffy.

    When connecting to the pump, read the current state. If a warning(s) is ongoing
    confirm it and forward it and let AAPS display it.
    Check if a previous command failed (tbr/bolus) and if so, DON'T report a warning
    caused by an interrupted command.
    Put the logic in AAPS: don't have readPumpState automatically confirm anything,
    but read state, call takeOverAlarm(alarm)

    concrete warnings???
    tbr cancelled => we can almost always just confirm this, we sync aaps if neded
    bolus => basically the same with SMBs, for user-initiated boluses and error should be reported
             properly, but that should work since it's interactive and PumpEneact result is
             or should be checked (verify it is)
             deliwerTreatment knows if it's SMB or not, whether it's okay to dismiss bolus cancelled alarm.

    battery low => always forward (not configurable, whole point is to make AAPS master)
    cartridge low => always forward

    when sync detects a significant mismatch it should alert?
      big bolus on pump aaps didn't knew about???
      removing a big bolus in aaps db since it's not in pump history? warn? aaps bug.

      ==> think this whole comm errors thing through (incl. auto/reconnect, auto-confirm
          ), on LoD - 1 and go back to coding after that

      open: dealing with low cartridge/battery during bolus.
            also: dealing with empty cartridge/battery alarms we let ring (force resync
            when 'next possible'. force resync when coming from a suspended state, or any
            error state in general.
            if cartridge is low, check reservoir before each bolus and don't attempt
            to bolus if not enough in reservoir for bolus?
            (testers: bolus with cartridge very low to run in such scenarios - forget the
            cartridge is low).

      so: * confirm pump warnings; for tbr/bolus cancelled caused by connection loss don't
            report them to the user (on disconnect set a flag 'error caused by us' or use.
            also note connection lose and force history resync if appropriate(?) (LAST).
          * stuff low warnings: confirm, show in combo tab and give overview notification
          * keepaliver receiver raises an urgent error if there have been no pump comms in
            > 25m
          * errors always ring on the pump, are critical and require the pump to be interacted
            with, so they're not confirmed. if encountered, they're read and also reported as
            urgent on the phone. cancel again if we see the user has confirmed them on the pump?
            (parent monitoring, needing to see an error).
            make Combo->Status red and "Occlusion" for errors?
     */

    boolean isPumpAvailable();

    boolean isPumpBusy();

    // start everything with this: read pump state.
    // see if there's an error active.
    CommandResult readPumpState();

    CommandResult readHistory(PumpHistoryRequest request);

    CommandResult readBasalProfile(int number);

    CommandResult setBasalProfile(BasalProfile basalProfile);

    CommandResult setDateAndTime(Date date);

    void requestPairing();

    void sendAuthKey(String key);

    void unpair();
}

