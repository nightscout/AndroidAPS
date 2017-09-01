package info.nightscout.androidaps.plugins.PumpCombo.scripter.commands;

import android.os.SystemClock;

import org.monkey.d.ruffy.ruffy.driver.display.MenuAttribute;
import org.monkey.d.ruffy.ruffy.driver.display.MenuType;

import info.nightscout.androidaps.plugins.PumpCombo.scripter.RuffyScripter;

public abstract class BaseCommand implements Command {
    // RS will inject itself here
    protected RuffyScripter scripter;

    @Override
    public void setScripter(RuffyScripter scripter) {
        this.scripter = scripter;
    }

    // TODO upcoming
    protected final boolean canBeCancelled = true;
    protected volatile boolean cancelRequested = false;

    public void requestCancellation() {
        cancelRequested = true;
    }

    public boolean isCancellable() {
        return canBeCancelled;
    }
}
