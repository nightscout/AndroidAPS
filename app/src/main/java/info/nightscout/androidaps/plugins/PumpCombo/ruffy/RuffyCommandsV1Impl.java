package info.nightscout.androidaps.plugins.PumpCombo.ruffy;

import java.util.Date;

import info.nightscout.androidaps.plugins.PumpCombo.ruffy.internal.scripter.RuffyScripter;
import info.nightscout.androidaps.plugins.PumpCombo.ruffy.spi.BasalProfile;
import info.nightscout.androidaps.plugins.PumpCombo.ruffy.spi.BolusProgressReporter;
import info.nightscout.androidaps.plugins.PumpCombo.ruffy.spi.CommandResult;
import info.nightscout.androidaps.plugins.PumpCombo.ruffy.spi.history.PumpHistory;
import info.nightscout.androidaps.plugins.PumpCombo.ruffy.spi.RuffyCommands;
import info.nightscout.androidaps.plugins.PumpCombo.ruffy.spi.history.PumpHistoryRequest;

public class RuffyCommandsV1Impl implements RuffyCommands {
    private static RuffyCommands delegate;

    public static RuffyCommands getInstance() {
        if (delegate == null) delegate = new RuffyScripter();
        return delegate;
    }

    private RuffyCommandsV1Impl() {
    }

    @Override
    public CommandResult deliverBolus(double amount, BolusProgressReporter bolusProgressReporter) {
        return delegate.deliverBolus(amount, bolusProgressReporter);
    }

    @Override
    public void cancelBolus() {
        delegate.cancelBolus();
    }

    @Override
    public CommandResult setTbr(int percent, int duration) {
        return delegate.setTbr(percent, duration);
    }

    @Override
    public CommandResult cancelTbr() {
        return delegate.cancelTbr();
    }

    @Override
    public boolean isPumpAvailable() {
        return delegate.isPumpAvailable();
    }

    @Override
    public boolean isPumpBusy() {
        return delegate.isPumpBusy();
    }

    @Override
    public CommandResult readHistory(PumpHistoryRequest request) {
        return delegate.readHistory(request);
    }

    @Override
    public CommandResult readBasalProfile(int number) {
        return delegate.readBasalProfile(number);
    }

    @Override
    public CommandResult setBasalProfile(BasalProfile basalProfile) {
        return delegate.setBasalProfile(basalProfile);
    }

    @Override
    public CommandResult setDateAndTime(Date date) {
        return delegate.setDateAndTime(date);
    }
}
