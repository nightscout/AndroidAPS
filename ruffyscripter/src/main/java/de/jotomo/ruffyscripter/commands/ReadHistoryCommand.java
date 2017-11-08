package de.jotomo.ruffyscripter.commands;

import android.support.annotation.NonNull;

import org.monkey.d.ruffy.ruffy.driver.display.MenuAttribute;
import org.monkey.d.ruffy.ruffy.driver.display.MenuType;
import org.monkey.d.ruffy.ruffy.driver.display.menu.BolusType;
import org.monkey.d.ruffy.ruffy.driver.display.menu.MenuDate;
import org.monkey.d.ruffy.ruffy.driver.display.menu.MenuTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;

import de.jotomo.ruffy.spi.history.Bolus;
import de.jotomo.ruffy.spi.history.PumpError;
import de.jotomo.ruffy.spi.history.PumpHistory;
import de.jotomo.ruffy.spi.history.PumpHistoryRequest;

// Note: TBRs are added to history only after they've completed running
public class ReadHistoryCommand extends BaseCommand {
    private static Logger log = LoggerFactory.getLogger(ReadHistoryCommand.class);

    private final PumpHistoryRequest request;
    private final PumpHistory history = new PumpHistory();

    public ReadHistoryCommand(PumpHistoryRequest request) {
        this.request = request;
    }

    @Override
    public void execute() {
        if (request.bolusHistory != PumpHistoryRequest.SKIP
                || request.tbrHistory != PumpHistoryRequest.SKIP
                || request.pumpErrorHistory != PumpHistoryRequest.SKIP) {
            scripter.navigateToMenu(MenuType.MY_DATA_MENU);
            scripter.verifyMenuIsDisplayed(MenuType.MY_DATA_MENU);
            scripter.pressCheckKey();

            // TODO see how dana does time mangling for timezones

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
                int totalRecords = (int) scripter.getCurrentMenu().getAttribute(MenuAttribute.TOTAL_RECORD);
                if (totalRecords > 0) {
                    if (request.pumpErrorHistory == PumpHistoryRequest.LAST) {
                        PumpError error = readErrorRecord();
                        history.pumpErrorHistory.add(error);
                    } else {
                        readErrorRecords(request.pumpErrorHistory);
                    }
                }
            }

            // tdd history
            scripter.pressMenuKey();
            scripter.verifyMenuIsDisplayed(MenuType.DAILY_DATA);
/*
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
*/

            scripter.pressBackKey();
            scripter.returnToRootMenu();
            scripter.verifyRootMenuIsDisplayed();
        }

        if (log.isDebugEnabled()) {
            if (!history.bolusHistory.isEmpty()) {
                log.debug("Read bolus history:");
                for (Bolus bolus : history.bolusHistory) {
                    log.debug(new Date(bolus.timestamp) + ": " + bolus.toString());
                }
            }
            if (!history.pumpErrorHistory.isEmpty()) {
                log.debug("Read error history:");
                for (PumpError pumpError : history.pumpErrorHistory) {
                    log.debug(new Date(pumpError.timestamp) + ": " + pumpError.toString());
                }
            }
        }
        result.success(true).history(history);
    }

    private void readBolusRecords(long requestedTime) {
        int record = (int) scripter.getCurrentMenu().getAttribute(MenuAttribute.CURRENT_RECORD);
        int totalRecords = (int) scripter.getCurrentMenu().getAttribute(MenuAttribute.TOTAL_RECORD);
        while (true) {
            log.debug("Reading bolus record #" + record + "/" + totalRecords);
            Bolus bolus = readBolusRecord();
            if (requestedTime != PumpHistoryRequest.FULL && bolus.timestamp <= requestedTime) {
                break;
            }
            history.bolusHistory.add(bolus);
            scripter.pressDownKey();
            scripter.waitForMenuUpdate();
            record = (int) scripter.getCurrentMenu().getAttribute(MenuAttribute.CURRENT_RECORD);
            if (record == totalRecords) {
                break;
            }
        }
    }

    @NonNull
    private Bolus readBolusRecord() {
        scripter.verifyMenuIsDisplayed(MenuType.BOLUS_DATA);
        BolusType bolusType = (BolusType) scripter.getCurrentMenu().getAttribute(MenuAttribute.BOLUS_TYPE);
        boolean isValid =  bolusType == BolusType.NORMAL;
        Double bolus = (Double) scripter.getCurrentMenu().getAttribute(MenuAttribute.BOLUS);
        long recordDate = readRecordDate();
        return new Bolus(recordDate, bolus, isValid);
    }

    private void readErrorRecords(long requestedTime) {
        int record = (int) scripter.getCurrentMenu().getAttribute(MenuAttribute.CURRENT_RECORD);
        int totalRecords = (int) scripter.getCurrentMenu().getAttribute(MenuAttribute.TOTAL_RECORD);
        while (true) {
            log.debug("Reading error record #" + record + "/" + totalRecords);
            PumpError error = readErrorRecord();
            if (requestedTime != PumpHistoryRequest.FULL && error.timestamp <= requestedTime) {
                break;
            }
            history.pumpErrorHistory.add(error);
            scripter.pressDownKey();
            scripter.waitForMenuUpdate();
            record = (int) scripter.getCurrentMenu().getAttribute(MenuAttribute.CURRENT_RECORD);
            if (record == totalRecords) {
                break;
            }
        }
    }

    @NonNull
    private PumpError readErrorRecord() {
        scripter.verifyMenuIsDisplayed(MenuType.ERROR_DATA);

        Integer warningCode = (Integer) scripter.getCurrentMenu().getAttribute(MenuAttribute.WARNING);
        Integer errorCode = (Integer) scripter.getCurrentMenu().getAttribute(MenuAttribute.ERROR);
        String message = (String) scripter.getCurrentMenu().getAttribute(MenuAttribute.MESSAGE);
        long recordDate = readRecordDate();
        return new PumpError(recordDate, warningCode, errorCode, message);
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
    public String toString() {
        return "ReadHistoryCommand{" +
                "request=" + request +
                ", history=" + history +
                '}';
    }
}
