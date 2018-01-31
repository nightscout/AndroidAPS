package info.nightscout.androidaps.plugins.PumpCombo.ruffyscripter.commands;

import org.monkey.d.ruffy.ruffy.driver.display.MenuAttribute;
import org.monkey.d.ruffy.ruffy.driver.display.MenuType;

import java.util.ArrayList;
import java.util.List;

import info.nightscout.androidaps.plugins.PumpCombo.ruffyscripter.history.Bolus;
import info.nightscout.androidaps.plugins.PumpCombo.ruffyscripter.history.PumpHistory;

public class ReadQuickInfoCommand extends BaseCommand {
    @Override
    public void execute() {
        scripter.verifyRootMenuIsDisplayed();
        scripter.pressCheckKey();
        scripter.waitForMenuToBeLeft(MenuType.MAIN_MENU);
        scripter.waitForMenuToBeLeft(MenuType.STOP);
        scripter.verifyMenuIsDisplayed(MenuType.QUICK_INFO);
        result.reservoirLevel = ((Double) scripter.getCurrentMenu().getAttribute(MenuAttribute.REMAINING_INSULIN)).intValue();
        scripter.pressCheckKey();
        List<Bolus> bolusHistory = new ArrayList<>(1);
        bolusHistory.add(readBolusRecord());
        result.history = new PumpHistory().bolusHistory(bolusHistory);
        scripter.returnToRootMenu();
        result.success = true;
    }

    @Override
    public boolean needsRunMode() {
        return false;
    }

    @Override
    public String toString() {
        return "ReadQuickInfoCommand{}";
    }
}
