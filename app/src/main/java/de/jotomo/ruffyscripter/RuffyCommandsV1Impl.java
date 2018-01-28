package de.jotomo.ruffyscripter;

import android.content.Context;
import android.support.annotation.NonNull;

import java.util.Date;

import de.jotomo.ruffy.spi.BasalProfile;
import de.jotomo.ruffy.spi.BolusProgressReporter;
import de.jotomo.ruffy.spi.CommandResult;
import de.jotomo.ruffy.spi.RuffyCommands;
import de.jotomo.ruffy.spi.history.PumpHistory;
import de.jotomo.ruffy.spi.history.PumpHistoryRequest;


public class RuffyCommandsV1Impl implements RuffyCommands {
    private static RuffyCommandsV1Impl instance;
    private static RuffyCommands delegate;

    @NonNull
    public static RuffyCommands getInstance(Context context) {
        if (instance == null) {
            instance = new RuffyCommandsV1Impl();
            delegate = new RuffyScripter(context);
        }
        return instance;
    }

    private RuffyCommandsV1Impl() {}

    /** Not supported by RuffyScripter */
    @Override
    public CommandResult getDateAndTime() {
        return new CommandResult().success(false);
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
    public void disconnect() {
        delegate.disconnect();
    }

    @Override
    public CommandResult readPumpState() {
        return delegate.readPumpState();
    }

    @Override
    public CommandResult readHistory(PumpHistoryRequest request) {
        return delegate.readHistory(request);
    }

    @Override
    public CommandResult readBasalProfile() {
        return delegate.readBasalProfile();
    }

    @Override
    public CommandResult setBasalProfile(BasalProfile basalProfile) {
        return delegate.setBasalProfile(basalProfile);
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
