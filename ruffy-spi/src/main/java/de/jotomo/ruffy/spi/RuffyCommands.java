package de.jotomo.ruffy.spi;

import java.util.Date;

import de.jotomo.ruffy.spi.history.PumpHistoryRequest;

public interface RuffyCommands {
    CommandResult deliverBolus(double amount, BolusProgressReporter bolusProgressReporter);

    void cancelBolus();

    CommandResult setTbr(int percent, int duration);

    CommandResult cancelTbr();

    /** Confirms an active alarm on the pump. The state returned is the state after the alarm
     * has been confirmed. Confirmed alerts are returned in history.errorHistory. */
    CommandResult takeOverAlarms();

    boolean isPumpAvailable();

    boolean isPumpBusy();

    CommandResult readPumpState();

    CommandResult readHistory(PumpHistoryRequest request);

    CommandResult readBasalProfile(int number);

    CommandResult setBasalProfile(BasalProfile basalProfile);

    CommandResult setDateAndTime(Date date);

    void requestPairing();

    void sendAuthKey(String key);

    void unpair();
}

