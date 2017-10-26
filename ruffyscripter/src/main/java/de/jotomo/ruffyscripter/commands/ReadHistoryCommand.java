package de.jotomo.ruffyscripter.commands;

import android.support.annotation.NonNull;

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
                || request.pumpErrorHistory != PumpHistoryRequest.SKIP
                || request.tddHistory != PumpHistoryRequest.SKIP) {
            scripter.navigateToMenu(MenuType.MY_DATA_MENU);
            scripter.verifyMenuIsDisplayed(MenuType.MY_DATA_MENU);
            scripter.pressCheckKey();

            // bolus history
            scripter.verifyMenuIsDisplayed(MenuType.BOLUS_DATA);
            if (request.bolusHistory != PumpHistoryRequest.SKIP) {
                int totalRecords = (int) scripter.getCurrentMenu().getAttribute(MenuAttribute.TOTAL_RECORD);
                if (totalRecords > 0) {
                    if (request.bolusHistory == PumpHistoryRequest.LAST) {
                        Bolus bolus = readBolusRecord();
                        history.bolusHistory.add(bolus);
                    } else {
                        readBolusRecords(request.bolusHistory);
                    }
                }
            }

            // error history
            scripter.pressMenuKey();
            scripter.verifyMenuIsDisplayed(MenuType.ERROR_DATA);
            if (request.pumpErrorHistory != PumpHistoryRequest.SKIP) {
                int code = (int) scripter.getCurrentMenu().getAttribute(MenuAttribute.WARNING);
                String message = (String) scripter.getCurrentMenu().getAttribute(MenuAttribute.MESSAGE);
                MenuDate date = (MenuDate) scripter.getCurrentMenu().getAttribute(MenuAttribute.DATE);
                MenuTime time = (MenuTime) scripter.getCurrentMenu().getAttribute(MenuAttribute.TIME);

            }

            // tdd history
            scripter.pressMenuKey();
            scripter.verifyMenuIsDisplayed(MenuType.DAILY_DATA);
            if (request.tddHistory != PumpHistoryRequest.SKIP) {
                Double total = (Double) scripter.getCurrentMenu().getAttribute(MenuAttribute.DAILY_TOTAL);
                MenuDate date = (MenuDate) scripter.getCurrentMenu().getAttribute(MenuAttribute.DATE);
                MenuTime time = (MenuTime) scripter.getCurrentMenu().getAttribute(MenuAttribute.TIME);
            }

            // tbr history
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
            scripter.verifyRootMenuIsDisplayed();
        }

        return new CommandResult().success(true).enacted(false).history(history);
    }

    private void readBolusRecords(long requestedTime) {
        int record = (int) scripter.getCurrentMenu().getAttribute(MenuAttribute.CURRENT_RECORD);
        int totalRecords = (int) scripter.getCurrentMenu().getAttribute(MenuAttribute.TOTAL_RECORD);
        while (record <= totalRecords) {
            Bolus bolus = readBolusRecord();
            if (requestedTime != PumpHistoryRequest.FULL && bolus.timestamp < requestedTime) {
                break;
            }
            history.bolusHistory.add(bolus);
            record = (int) scripter.getCurrentMenu().getAttribute(MenuAttribute.CURRENT_RECORD);
        }
    }

    @NonNull
    private Bolus readBolusRecord() {
        // Could also be extended, multiwave
        BolusType bolusType = (BolusType) scripter.getCurrentMenu().getAttribute(MenuAttribute.BOLUS_TYPE);
        if (!bolusType.equals(BolusType.NORMAL)) {
            throw new CommandException().success(false).enacted(false).message("Unsupported bolus type encountered: " + bolusType);
        }
        // TODO no bolus etc yet? How would that ever look?
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
        return new Bolus(recordDate, bolus);
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

    @Override
    public String toString() {
        return "ReadHistoryCommand{" +
                "request=" + request +
                ", history=" + history +
                '}';
    }

}
