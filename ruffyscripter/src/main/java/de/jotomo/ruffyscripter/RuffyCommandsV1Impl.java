package de.jotomo.ruffyscripter;

import android.content.Context;
import android.support.annotation.NonNull;

import java.util.Date;

import de.jotomo.ruffy.spi.BasalProfile;
import de.jotomo.ruffy.spi.BolusProgressReporter;
import de.jotomo.ruffy.spi.CommandResult;
import de.jotomo.ruffy.spi.RuffyCommands;
import de.jotomo.ruffy.spi.history.PumpHistoryRequest;


public class RuffyCommandsV1Impl implements RuffyCommands {
    private static RuffyCommands delegate;

    @NonNull
    public static RuffyCommands getInstance(Context context) {
        if (delegate == null) delegate = new RuffyScripter(context);
        return delegate;
    }

    private RuffyCommandsV1Impl() {}

    @Override
    public CommandResult getDateAndTime() {
        return delegate.getDateAndTime();
    }

    @Override
    public CommandResult readReservoirLevelAndLastBolus() {
        return delegate.readReservoirLevelAndLastBolus();
    }

    @Override
    public CommandResult confirmAlert(int warningCode) {
        return delegate.confirmAlert(warningCode);
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
    public boolean isConnected() {
        return delegate.isConnected();
    }

    @Override
    public CommandResult readPumpState() {
        return delegate.readPumpState();
    }

    @Override
    public CommandResult readHistory(PumpHistoryRequest request) {
        return delegate.readHistory(request);
    }

    /** Not supported by RuffyScripter */
    @Override
    public CommandResult readBasalProfile() {
        return new CommandResult().success(false);
    }

    /** Not supported by RuffyScripter */
    @Override
    public CommandResult setBasalProfile(BasalProfile basalProfile) {
        return new CommandResult().success(false);
    }

    /** Not supported by RuffyScripter */
    @Override
    public CommandResult setDateAndTime() {
        return new CommandResult().success(false);
    }

    @Override
    public void requestPairing() {
        delegate.requestPairing();
    }

    @Override
    public void sendAuthKey(String key) {
        delegate.sendAuthKey(key);
    }

    @Override
    public void unpair() {
        delegate.unpair();
    }
}
