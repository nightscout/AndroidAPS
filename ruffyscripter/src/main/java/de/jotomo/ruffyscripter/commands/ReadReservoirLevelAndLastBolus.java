package de.jotomo.ruffyscripter.commands;

import android.support.annotation.NonNull;

import org.monkey.d.ruffy.ruffy.driver.display.MenuAttribute;
import org.monkey.d.ruffy.ruffy.driver.display.MenuType;
import org.monkey.d.ruffy.ruffy.driver.display.menu.BolusType;
import org.monkey.d.ruffy.ruffy.driver.display.menu.MenuDate;
import org.monkey.d.ruffy.ruffy.driver.display.menu.MenuTime;

import java.util.Date;

import de.jotomo.ruffy.spi.history.Bolus;

public class ReadReservoirLevelAndLastBolus extends BaseCommand {
    @Override
    public void execute() {
        scripter.verifyRootMenuIsDisplayed();
        scripter.pressCheckKey();
        scripter.waitForMenuToBeLeft(MenuType.MAIN_MENU);
        scripter.waitForMenuToBeLeft(MenuType.STOP);
        scripter.verifyMenuIsDisplayed(MenuType.QUICK_INFO);
        result.reservoirLevel = ((Double) scripter.getCurrentMenu().getAttribute(MenuAttribute.REMAINING_INSULIN)).intValue();
        scripter.pressCheckKey();
        result.lastBolus = readBolusRecord();
        scripter.returnToRootMenu();
        result.success = true;
    }

    // TODO deduplicate -> ReadHistoryCommand
    @NonNull
    private Bolus readBolusRecord() {
        scripter.verifyMenuIsDisplayed(MenuType.BOLUS_DATA);
        BolusType bolusType = (BolusType) scripter.getCurrentMenu().getAttribute(MenuAttribute.BOLUS_TYPE);
        boolean isValid =  bolusType == BolusType.NORMAL;
        Double bolus = (Double) scripter.getCurrentMenu().getAttribute(MenuAttribute.BOLUS);
        long recordDate = readRecordDate();
        return new Bolus(recordDate, bolus, isValid);
    }

    private long readRecordDate() {
        MenuDate date = (MenuDate) scripter.getCurrentMenu().getAttribute(MenuAttribute.DATE);
        MenuTime time = (MenuTime) scripter.getCurrentMenu().getAttribute(MenuAttribute.TIME);

        int currentMonth = new Date().getMonth() + 1;
        int currentYear = new Date().getYear() + 1900;
        if (currentMonth == 1 && date.getMonth() == 12) {
            currentYear -= 1;
        }
        return new Date(currentYear - 1900, date.getMonth() - 1, date.getDay(), time.getHour(), time.getMinute()).getTime();
    }

    @Override
    public boolean needsRunMode() {
        return false;
    }

    @Override
    public String toString() {
        return "ReadReservoirLevelAndLastBolus{}";
    }
}
