package info.nightscout.androidaps.plugins.pump.combo.ruffyscripter.commands;

import android.support.annotation.NonNull;

import org.monkey.d.ruffy.ruffy.driver.display.MenuAttribute;
import org.monkey.d.ruffy.ruffy.driver.display.MenuType;
import org.monkey.d.ruffy.ruffy.driver.display.menu.MenuDate;
import org.monkey.d.ruffy.ruffy.driver.display.menu.MenuTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Calendar;
import java.util.Date;

import info.nightscout.androidaps.plugins.pump.combo.ruffyscripter.history.Bolus;
import info.nightscout.androidaps.plugins.pump.combo.ruffyscripter.history.PumpAlert;
import info.nightscout.androidaps.plugins.pump.combo.ruffyscripter.history.PumpHistory;
import info.nightscout.androidaps.plugins.pump.combo.ruffyscripter.history.PumpHistoryRequest;
import info.nightscout.androidaps.plugins.pump.combo.ruffyscripter.history.Tbr;
import info.nightscout.androidaps.plugins.pump.combo.ruffyscripter.history.Tdd;

public class ReadHistoryCommand extends BaseCommand {
    private static Logger log = LoggerFactory.getLogger(ReadHistoryCommand.class);

    private final PumpHistoryRequest request;
    private final PumpHistory history = new PumpHistory();

    public ReadHistoryCommand(PumpHistoryRequest request) {
        this.request = request;
    }

    @Override
    public void execute() {
        if (request.bolusHistory == PumpHistoryRequest.SKIP
                && request.tbrHistory == PumpHistoryRequest.SKIP
                && request.pumpErrorHistory == PumpHistoryRequest.SKIP
                && request.tddHistory == PumpHistoryRequest.SKIP) {
            throw new CommandException("History request but all data types are skipped");
        }

        scripter.verifyMenuIsDisplayed(MenuType.MAIN_MENU);
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

        if (request.pumpErrorHistory != PumpHistoryRequest.SKIP
                || request.tddHistory != PumpHistoryRequest.SKIP
                || request.tbrHistory != PumpHistoryRequest.SKIP) {
            // error history
            scripter.pressMenuKey();
            scripter.verifyMenuIsDisplayed(MenuType.ERROR_DATA);
            if (request.pumpErrorHistory != PumpHistoryRequest.SKIP) {
                int totalRecords = (int) scripter.getCurrentMenu().getAttribute(MenuAttribute.TOTAL_RECORD);
                if (totalRecords > 0) {
                    if (request.pumpErrorHistory == PumpHistoryRequest.LAST) {
                        PumpAlert error = readAlertRecord();
                        history.pumpAlertHistory.add(error);
                    } else {
                        readAlertRecords(request.pumpErrorHistory);
                    }
                }
            }

            // tdd history (TBRs are added to history only after they've completed running)
            scripter.pressMenuKey();
            scripter.verifyMenuIsDisplayed(MenuType.DAILY_DATA);
            if (request.tddHistory != PumpHistoryRequest.SKIP) {
                int totalRecords = (int) scripter.getCurrentMenu().getAttribute(MenuAttribute.TOTAL_RECORD);
                if (totalRecords > 0) {
                    if (request.tddHistory == PumpHistoryRequest.LAST) {
                        Tdd tdd = readTddRecord();
                        history.tddHistory.add(tdd);
                    } else {
                        readTddRecords(request.tbrHistory);
                    }
                }
            }

            // tbr history
            scripter.pressMenuKey();
            scripter.verifyMenuIsDisplayed(MenuType.TBR_DATA);
            if (request.tbrHistory != PumpHistoryRequest.SKIP) {
                int totalRecords = (int) scripter.getCurrentMenu().getAttribute(MenuAttribute.TOTAL_RECORD);
                if (totalRecords > 0) {
                    if (request.tbrHistory == PumpHistoryRequest.LAST) {
                        Tbr tbr = readTbrRecord();
                        history.tbrHistory.add(tbr);
                    } else {
                        readTbrRecords(request.tbrHistory);
                    }
                }
            }
        }

        if (log.isDebugEnabled()) {
            if (!history.bolusHistory.isEmpty()) {
                log.debug("Read bolus history (" + history.bolusHistory.size() + "):");
                for (Bolus bolus : history.bolusHistory) {
                    log.debug(new Date(bolus.timestamp) + ": " + bolus.toString());
                }
            }
            if (!history.pumpAlertHistory.isEmpty()) {
                log.debug("Read error history (" + history.pumpAlertHistory.size() + "):");
                for (PumpAlert pumpAlert : history.pumpAlertHistory) {
                    log.debug(new Date(pumpAlert.timestamp) + ": " + pumpAlert.toString());
                }
            }
            if (!history.tddHistory.isEmpty()) {
                log.debug("Read TDD history (" + history.tddHistory.size() + "):");
                for (Tdd tdd : history.tddHistory) {
                    log.debug(new Date(tdd.timestamp) + ": " + tdd.toString());
                }
            }
            if (!history.tbrHistory.isEmpty()) {
                log.debug("Read TBR history (" + history.tbrHistory.size() + "):");
                for (Tbr tbr : history.tbrHistory) {
                    log.debug(new Date(tbr.timestamp) + ": " + tbr.toString());
                }
            }
        }

        scripter.returnToRootMenu();
        scripter.verifyRootMenuIsDisplayed();

        result.success(true).history(history);
    }

    private void readTddRecords(long requestedTime) {
        int record = (int) scripter.getCurrentMenu().getAttribute(MenuAttribute.CURRENT_RECORD);
        int totalRecords = (int) scripter.getCurrentMenu().getAttribute(MenuAttribute.TOTAL_RECORD);
        while (true) {
            Tdd tdd = readTddRecord();
            if (requestedTime != PumpHistoryRequest.FULL && tdd.timestamp < requestedTime) {
                break;
            }
            log.debug("Read TDD record #" + record + "/" + totalRecords);
            history.tddHistory.add(tdd);
            log.debug("Parsed " + scripter.getCurrentMenu().toString() + " => " + tdd);
            if (record == totalRecords) {
                break;
            }
            scripter.pressDownKey();
            while (record == (int) scripter.getCurrentMenu().getAttribute(MenuAttribute.CURRENT_RECORD)) {
                scripter.waitForScreenUpdate();
            }
            record = (int) scripter.getCurrentMenu().getAttribute(MenuAttribute.CURRENT_RECORD);
        }
    }

    @NonNull
    private Tdd readTddRecord() {
        scripter.verifyMenuIsDisplayed(MenuType.DAILY_DATA);
        Double dailyTotal = (Double) scripter.getCurrentMenu().getAttribute(MenuAttribute.DAILY_TOTAL);
        MenuDate date = (MenuDate) scripter.getCurrentMenu().getAttribute(MenuAttribute.DATE);

        int year = Calendar.getInstance().get(Calendar.YEAR);
        if (date.getMonth() > Calendar.getInstance().get(Calendar.MONTH) + 1) {
            year -= 1;
        }
        Calendar calendar = Calendar.getInstance();
        calendar.set(year, date.getMonth() - 1, date.getDay(), 0, 0, 0);
        long time = calendar.getTimeInMillis();
        time = time - time%1000;
        return new Tdd(time, dailyTotal);
    }

    private void readTbrRecords(long requestedTime) {
        int record = (int) scripter.getCurrentMenu().getAttribute(MenuAttribute.CURRENT_RECORD);
        int totalRecords = (int) scripter.getCurrentMenu().getAttribute(MenuAttribute.TOTAL_RECORD);
        while (true) {
            Tbr tbr = readTbrRecord();
            if (requestedTime != PumpHistoryRequest.FULL && tbr.timestamp < requestedTime) {
                break;
            }
            log.debug("Read TBR record #" + record + "/" + totalRecords);
            history.tbrHistory.add(tbr);
            log.debug("Parsed " + scripter.getCurrentMenu().toString() + " => " + tbr);
            if (record == totalRecords) {
                break;
            }
            scripter.pressDownKey();
            while (record == (int) scripter.getCurrentMenu().getAttribute(MenuAttribute.CURRENT_RECORD)) {
                scripter.waitForScreenUpdate();
            }
            record = (int) scripter.getCurrentMenu().getAttribute(MenuAttribute.CURRENT_RECORD);
        }
    }

    @NonNull
    private Tbr readTbrRecord() {
        scripter.verifyMenuIsDisplayed(MenuType.TBR_DATA);
        Double percentage = (Double) scripter.getCurrentMenu().getAttribute(MenuAttribute.TBR);
        MenuTime durationTime = (MenuTime) scripter.getCurrentMenu().getAttribute(MenuAttribute.RUNTIME);
        int duration = durationTime.getHour() * 60 + durationTime.getMinute();
        long tbrStartDate = readRecordDate() - duration * 60 * 1000;
        return new Tbr(tbrStartDate, duration, percentage.intValue());
    }

    private void readBolusRecords(long requestedTime) {
        int record = (int) scripter.getCurrentMenu().getAttribute(MenuAttribute.CURRENT_RECORD);
        int totalRecords = (int) scripter.getCurrentMenu().getAttribute(MenuAttribute.TOTAL_RECORD);
        while (true) {
            Bolus bolus = readBolusRecord();
            if (requestedTime != PumpHistoryRequest.FULL && bolus.timestamp < requestedTime) {
                break;
            }
            log.debug("Read bolus record #" + record + "/" + totalRecords);
            history.bolusHistory.add(bolus);
            log.debug("Parsed " + scripter.getCurrentMenu().toString() + " => " + bolus);
            if (record == totalRecords) {
                break;
            }
            scripter.pressDownKey();
            while (record == (int) scripter.getCurrentMenu().getAttribute(MenuAttribute.CURRENT_RECORD)) {
                scripter.waitForScreenUpdate();
            }
            record = (int) scripter.getCurrentMenu().getAttribute(MenuAttribute.CURRENT_RECORD);
        }
    }

    private void readAlertRecords(long requestedTime) {
        int record = (int) scripter.getCurrentMenu().getAttribute(MenuAttribute.CURRENT_RECORD);
        int totalRecords = (int) scripter.getCurrentMenu().getAttribute(MenuAttribute.TOTAL_RECORD);
        while (true) {
            PumpAlert error = readAlertRecord();
            if (requestedTime != PumpHistoryRequest.FULL && error.timestamp < requestedTime) {
                break;
            }
            log.debug("Read alert record #" + record + "/" + totalRecords);
            history.pumpAlertHistory.add(error);
            log.debug("Parsed " + scripter.getCurrentMenu().toString() + " => " + error);
            if (record == totalRecords) {
                break;
            }
            scripter.pressDownKey();
            while (record == (int) scripter.getCurrentMenu().getAttribute(MenuAttribute.CURRENT_RECORD)) {
                scripter.waitForScreenUpdate();
            }
            record = (int) scripter.getCurrentMenu().getAttribute(MenuAttribute.CURRENT_RECORD);
        }
    }

    @NonNull
    private PumpAlert readAlertRecord() {
        scripter.verifyMenuIsDisplayed(MenuType.ERROR_DATA);

        Integer warningCode = (Integer) scripter.getCurrentMenu().getAttribute(MenuAttribute.WARNING);
        Integer errorCode = (Integer) scripter.getCurrentMenu().getAttribute(MenuAttribute.ERROR);
        String message = (String) scripter.getCurrentMenu().getAttribute(MenuAttribute.MESSAGE);
        long recordDate = readRecordDate();
        return new PumpAlert(recordDate, warningCode, errorCode, message);
    }

    @Override
    public String toString() {
        return "ReadHistoryCommand{" +
                "request=" + request +
                ", history=" + history +
                '}';
    }
}
