package info.nightscout.androidaps.plugins.pump.combo.ruffyscripter.commands;

import android.support.annotation.NonNull;

import org.monkey.d.ruffy.ruffy.driver.display.MenuAttribute;
import org.monkey.d.ruffy.ruffy.driver.display.MenuType;
import org.monkey.d.ruffy.ruffy.driver.display.menu.BolusType;
import org.monkey.d.ruffy.ruffy.driver.display.menu.MenuDate;
import org.monkey.d.ruffy.ruffy.driver.display.menu.MenuTime;

import java.util.Calendar;
import java.util.Collections;
import java.util.List;

import info.nightscout.androidaps.plugins.pump.combo.ruffyscripter.CommandResult;
import info.nightscout.androidaps.plugins.pump.combo.ruffyscripter.PumpWarningCodes;
import info.nightscout.androidaps.plugins.pump.combo.ruffyscripter.RuffyScripter;
import info.nightscout.androidaps.plugins.pump.combo.ruffyscripter.history.Bolus;

public abstract class BaseCommand implements Command {
    // RS will inject itself here
    protected RuffyScripter scripter;

    protected CommandResult result;

    public BaseCommand() {
        result = new CommandResult();
    }

    @Override
    public void setScripter(RuffyScripter scripter) {
        this.scripter = scripter;
    }

    @Override
    public boolean needsRunMode() {
        return true;
    }

    /**
     * A warning id (or null) caused by a disconnect we can safely confirm on reconnect,
     * knowing it's not severe as it was caused by this command.
     * @see PumpWarningCodes
     */
    @Override
    public Integer getReconnectWarningId() {
        return null;
    }

    @Override
    public List<String> validateArguments() {
        return Collections.emptyList();
    }

    @Override
    public CommandResult getResult() {
        return result;
    }

    @NonNull
    protected Bolus readBolusRecord() {
        scripter.verifyMenuIsDisplayed(MenuType.BOLUS_DATA);
        BolusType bolusType = (BolusType) scripter.getCurrentMenu().getAttribute(MenuAttribute.BOLUS_TYPE);
        boolean isValid = bolusType == BolusType.NORMAL;
        Double bolus = (Double) scripter.getCurrentMenu().getAttribute(MenuAttribute.BOLUS);
        long recordDate = readRecordDate();
        return new Bolus(recordDate, bolus, isValid);
    }

    protected long readRecordDate() {
        MenuDate date = (MenuDate) scripter.getCurrentMenu().getAttribute(MenuAttribute.DATE);
        MenuTime time = (MenuTime) scripter.getCurrentMenu().getAttribute(MenuAttribute.TIME);

        int year = Calendar.getInstance().get(Calendar.YEAR);
        if (date.getMonth() > Calendar.getInstance().get(Calendar.MONTH) + 1) {
            year -= 1;
        }
        Calendar calendar = Calendar.getInstance();
        calendar.set(year, date.getMonth() - 1, date.getDay(), time.getHour(), time.getMinute(), 0);

        // round to second
        return calendar.getTimeInMillis() - calendar.getTimeInMillis() % 1000;

    }
}
