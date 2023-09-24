package info.nightscout.pump.combo.ruffyscripter.commands;

import androidx.annotation.NonNull;

import org.monkey.d.ruffy.ruffy.driver.display.MenuAttribute;
import org.monkey.d.ruffy.ruffy.driver.display.MenuType;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import app.aaps.core.interfaces.logging.AAPSLogger;
import app.aaps.core.interfaces.logging.LTag;
import info.nightscout.pump.combo.ruffyscripter.history.Bolus;
import info.nightscout.pump.combo.ruffyscripter.history.PumpHistory;

public class ReadQuickInfoCommand extends BaseCommand {
    private final AAPSLogger aapsLogger;

    private final int numberOfBolusRecordsToRetrieve;

    public ReadQuickInfoCommand(int numberOfBolusRecordsToRetrieve, AAPSLogger aapsLogger) {
        this.numberOfBolusRecordsToRetrieve = numberOfBolusRecordsToRetrieve;
        this.aapsLogger = aapsLogger;
    }

    @Override
    public void execute() {
        scripter.verifyRootMenuIsDisplayed();
        // navigate to reservoir menu
        scripter.pressCheckKey();
        scripter.waitForMenuToBeLeft(MenuType.MAIN_MENU);
        scripter.waitForMenuToBeLeft(MenuType.STOP);
        scripter.verifyMenuIsDisplayed(MenuType.QUICK_INFO);
        result.reservoirLevel = ((Double) scripter.getCurrentMenu().getAttribute(MenuAttribute.REMAINING_INSULIN)).intValue();
        if (numberOfBolusRecordsToRetrieve > 0) {
            // navigate to bolus data menu
            scripter.pressCheckKey();
            scripter.verifyMenuIsDisplayed(MenuType.BOLUS_DATA);
            List<Bolus> bolusHistory = new ArrayList<>(numberOfBolusRecordsToRetrieve);
            result.history = new PumpHistory().bolusHistory(bolusHistory);
            // read bolus records
            int totalRecords = (int) scripter.getCurrentMenu().getAttribute(MenuAttribute.TOTAL_RECORD);
            int record = (int) scripter.getCurrentMenu().getAttribute(MenuAttribute.CURRENT_RECORD);
            if (record > 0) {
                while (true) {
                    bolusHistory.add(readBolusRecord());
                    if (bolusHistory.size() == numberOfBolusRecordsToRetrieve || record == totalRecords) {
                        break;
                    }
                    // advance to next record
                    scripter.pressDownKey();
                    while (record == (int) scripter.getCurrentMenu().getAttribute(MenuAttribute.CURRENT_RECORD)) {
                        scripter.waitForScreenUpdate();
                    }
                    record = (int) scripter.getCurrentMenu().getAttribute(MenuAttribute.CURRENT_RECORD);
                }
            }
            if (!result.history.bolusHistory.isEmpty()) {
                aapsLogger.debug(LTag.PUMP, "Read bolus history (" + result.history.bolusHistory.size() + "):");
                for (Bolus bolus : result.history.bolusHistory) {
                    aapsLogger.debug(LTag.PUMP, new Date(bolus.timestamp) + ": " + bolus);
                }
            }
        }
        scripter.returnToRootMenu();
        result.success = true;
    }

    @Override
    public boolean needsRunMode() {
        return false;
    }

    @NonNull @Override
    public String toString() {
        return "ReadQuickInfoCommand{}";
    }
}
