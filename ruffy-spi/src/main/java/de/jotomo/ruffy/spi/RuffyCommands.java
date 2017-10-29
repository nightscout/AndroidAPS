package de.jotomo.ruffy.spi;

import java.util.Date;

import de.jotomo.ruffy.spi.history.PumpHistoryRequest;

public interface RuffyCommands {
    CommandResult deliverBolus(double amount, BolusProgressReporter bolusProgressReporter);

    void cancelBolus();

    CommandResult setTbr(int percent, int duration);

    CommandResult cancelTbr();

    CommandResult confirmAlert(int warningCode);

    boolean isPumpAvailable();

    boolean isPumpBusy();

    CommandResult readPumpState();

    CommandResult readReservoirLevelAndLastBolus();

    CommandResult readHistory(PumpHistoryRequest request);

    CommandResult readBasalProfile(int number);

    CommandResult setBasalProfile(BasalProfile basalProfile);

    CommandResult getDateAndTime();

    CommandResult setDateAndTime(Date date);

    void requestPairing();

    void sendAuthKey(String key);

    void unpair();
}

