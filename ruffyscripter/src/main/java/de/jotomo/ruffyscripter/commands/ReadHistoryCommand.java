package de.jotomo.ruffyscripter.commands;

import org.monkey.d.ruffy.ruffy.driver.display.MenuAttribute;
import org.monkey.d.ruffy.ruffy.driver.display.MenuType;
import org.monkey.d.ruffy.ruffy.driver.display.menu.BolusType;
import org.monkey.d.ruffy.ruffy.driver.display.menu.MenuDate;
import org.monkey.d.ruffy.ruffy.driver.display.menu.MenuTime;

import java.util.Collections;
import java.util.Date;
import java.util.List;

import de.jotomo.ruffy.spi.CommandResult;
import de.jotomo.ruffy.spi.history.Bolus;
import de.jotomo.ruffy.spi.history.PumpHistory;
import de.jotomo.ruffy.spi.history.PumpHistoryRequest;

public class ReadHistoryCommand extends BaseCommand {
    private final PumpHistoryRequest request;
    private final PumpHistory history = new PumpHistory();

    public ReadHistoryCommand(PumpHistoryRequest request) {
        this.request = request;
    }

    @Override
    public CommandResult execute() {
        if (request.reservoirLevel) {
            readReservoirLevel();
        }
        if (request.bolusHistory != PumpHistoryRequest.SKIP
                || request.tbrHistory != PumpHistoryRequest.SKIP
                || request.errorHistory != PumpHistoryRequest.SKIP
                || request.tddHistory != PumpHistoryRequest.SKIP) {
            scripter.navigateToMenu(MenuType.MY_DATA_MENU);
            scripter.verifyMenuIsDisplayed(MenuType.MY_DATA_MENU);
            scripter.pressCheckKey();
            scripter.verifyMenuIsDisplayed(MenuType.BOLUS_DATA);
            if (request.bolusHistory != PumpHistoryRequest.SKIP) {
                if (request.bolusHistory == PumpHistoryRequest.LAST) {
                    // Could also be extended, multiwave:
                    BolusType bolusType = (BolusType) scripter.getCurrentMenu().getAttribute(MenuAttribute.BOLUS_TYPE);
                    if (!bolusType.equals(BolusType.NORMAL)) {
                        throw new CommandException().success(false).enacted(false).message("Unsupported bolus type encountered: " + bolusType);
                    }
                    Double bolus = (Double) scripter.getCurrentMenu().getAttribute(MenuAttribute.BOLUS);
                    MenuDate date = (MenuDate) scripter.getCurrentMenu().getAttribute(MenuAttribute.DATE);
                    MenuTime time = (MenuTime) scripter.getCurrentMenu().getAttribute(MenuAttribute.TIME);
                    // TODO handle year changes; if current month == 1 and record date == 12, use $YEAR-1
                    int currentMonth = new Date().getMonth() + 1;
                    int currentYear = new Date().getYear() + 1900;
                    if (currentMonth == 1 && date.getMonth() == 12) {
                        currentYear -= 1;
                    }
                    long recordDate = new Date(currentYear - 1900, date.getMonth() - 1, date.getDay(), time.getHour(), time.getMinute()).getTime();
                    history.bolusHistory.add(new Bolus(recordDate, bolus));

//                int record = (int) scripter.getCurrentMenu().getAttribute(MenuAttribute.CURRENT_RECORD);
//                int totalRecords = (int) scripter.getCurrentMenu().getAttribute(MenuAttribute.TOTAL_RECORD);

                /*
                read displayed date, bolus, add to history
                while data > last known, press up to go through history
                 */

                }
            }
            scripter.pressMenuKey();
            scripter.verifyMenuIsDisplayed(MenuType.ERROR_DATA);
            if (request.errorHistory != PumpHistoryRequest.SKIP) {
                int code = (int) scripter.getCurrentMenu().getAttribute(MenuAttribute.WARNING);
                String message = (String) scripter.getCurrentMenu().getAttribute(MenuAttribute.MESSAGE);
                MenuDate date = (MenuDate) scripter.getCurrentMenu().getAttribute(MenuAttribute.DATE);
                MenuTime time = (MenuTime) scripter.getCurrentMenu().getAttribute(MenuAttribute.TIME);

            }
            scripter.pressMenuKey();
            scripter.verifyMenuIsDisplayed(MenuType.DAILY_DATA);
            if (request.tddHistory != PumpHistoryRequest.SKIP) {
                Double total = (Double) scripter.getCurrentMenu().getAttribute(MenuAttribute.DAILY_TOTAL);
                MenuDate date = (MenuDate) scripter.getCurrentMenu().getAttribute(MenuAttribute.DATE);
                MenuTime time = (MenuTime) scripter.getCurrentMenu().getAttribute(MenuAttribute.TIME);
            }
            scripter.pressMenuKey();
            scripter.verifyMenuIsDisplayed(MenuType.TBR_DATA);
            if (request.tbrHistory != PumpHistoryRequest.SKIP) {
                Double percentage = (Double) scripter.getCurrentMenu().getAttribute(MenuAttribute.TBR);
                MenuTime duration = (MenuTime) scripter.getCurrentMenu().getAttribute(MenuAttribute.RUNTIME);
                MenuDate date = (MenuDate) scripter.getCurrentMenu().getAttribute(MenuAttribute.DATE);
                // TODO start or end time?
                MenuTime time = (MenuTime) scripter.getCurrentMenu().getAttribute(MenuAttribute.TIME);
            }
            scripter.pressBackKey();
            scripter.returnToRootMenu();
        }
        scripter.verifyRootMenuIsDisplayed();
        return new CommandResult().success(true).enacted(false).history(history);
    }

    private void readReservoirLevel() {
        scripter.verifyRootMenuIsDisplayed();
        scripter.pressCheckKey();
        scripter.waitForMenuToBeLeft(MenuType.MAIN_MENU);
        scripter.waitForMenuToBeLeft(MenuType.STOP);
        scripter.verifyMenuIsDisplayed(MenuType.QUICK_INFO);
        int remainingInsulin = ((Double) scripter.getCurrentMenu().getAttribute(MenuAttribute.REMAINING_INSULIN)).intValue();
        scripter.returnToRootMenu();
        history.reservoirLevel = remainingInsulin;
    }

    @Override
    public List<String> validateArguments() {
        return Collections.emptyList();
    }
}
