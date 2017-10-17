package info.nightscout.androidaps.plugins.PumpCombo.ruffy.spi;

import java.util.Date;

public interface RuffyCommands {
    CommandResult deliverBolus(double amount, BolusProgressReporter bolusProgressReporter);

    void cancelBolus();

    CommandResult setTbr(int percent, int duraton);

    CommandResult cancelTbr();

    boolean isPumpAvailable();

    boolean isPumpBusy();

    CommandResult readReservoirLevel();

    // PumpHistory.fields.*: null=don't care. empty history=we know nothing yet. filled history=this is what we know so far
    CommandResult readHistory(PumpHistory knownHistory);

    CommandResult readBasalProfile();

    CommandResult setBasalProfile(BasalProfile basalProfile);

    CommandResult setDateAndTime(Date date);
}

