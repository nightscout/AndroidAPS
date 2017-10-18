package info.nightscout.androidaps.plugins.PumpCombo.ruffy.spi;

import java.util.Date;

import info.nightscout.androidaps.plugins.PumpCombo.ruffy.spi.history.PumpHistoryRequest;

public interface RuffyCommands {
    CommandResult deliverBolus(double amount, BolusProgressReporter bolusProgressReporter);

    void cancelBolus();

    CommandResult setTbr(int percent, int duration);

    CommandResult cancelTbr();

    boolean isPumpAvailable();

    boolean isPumpBusy();

    CommandResult readHistory(PumpHistoryRequest request);

    CommandResult readBasalProfile(int number);

    CommandResult setBasalProfile(BasalProfile basalProfile);

    CommandResult setDateAndTime(Date date);
}

