package info.nightscout.androidaps.plugins.PumpCombo.scripter.commands;

import android.os.SystemClock;

import org.monkey.d.ruffy.ruffy.driver.display.MenuAttribute;
import org.monkey.d.ruffy.ruffy.driver.display.MenuType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import info.nightscout.androidaps.plugins.PumpCombo.scripter.PumpState;
import info.nightscout.androidaps.plugins.PumpCombo.scripter.RuffyScripter;

import static info.nightscout.androidaps.plugins.PumpCombo.scripter.commands.ExperimentalBolusCommand.ProgressReportCallback.State.DELIVERED;
import static info.nightscout.androidaps.plugins.PumpCombo.scripter.commands.ExperimentalBolusCommand.ProgressReportCallback.State.DELIVERING;
import static info.nightscout.androidaps.plugins.PumpCombo.scripter.commands.ExperimentalBolusCommand.ProgressReportCallback.State.STOPPED;
import static info.nightscout.androidaps.plugins.PumpCombo.scripter.commands.ExperimentalBolusCommand.ProgressReportCallback.State.STOPPING;

public class ExperimentalBolusCommand extends BolusCommand {
    private static final Logger log = LoggerFactory.getLogger(ExperimentalBolusCommand.class);

    private final ProgressReportCallback progressReportCallback;
    private volatile boolean cancelRequested;

    public ExperimentalBolusCommand(double bolus, ProgressReportCallback progressReportCallback) {
        super(bolus);
        this.progressReportCallback = progressReportCallback;
    }

    @Override
    public List<String> validateArguments() {
        List<String> violations = new ArrayList<>();

        if (bolus <= 0 || bolus > 25) {
            violations.add("Requested bolus " + bolus + " out of limits (0-25)");
        }

        return violations;
    }

    @Override
    public CommandResult execute() {
        try {
            // TODO read reservoir level and reject request if reservoir < bolus
            enterBolusMenu();

            inputBolusAmount();
            verifyDisplayedBolusAmount();

            if (cancelRequested) {
                progressReportCallback.report(STOPPING, 0, 0);
                scripter.returnToMainMenu();
                progressReportCallback.report(STOPPED, 0, 0);
                return new CommandResult().success(true).enacted(false)
                        .message("Bolus cancelled as per user request with no insulin delivered");
            }

            // confirm bolus
            scripter.verifyMenuIsDisplayed(MenuType.BOLUS_ENTER);
            scripter.pressCheckKey();

            // the pump displays the entered bolus and waits a few seconds to let user check and cancel
            while (scripter.getCurrentMenu().getType() == MenuType.BOLUS_ENTER) {
                if (cancelRequested) {
                    progressReportCallback.report(STOPPING, 0, 0);
                    scripter.pressUpKey();
                    // wait up to 1s for a BOLUS_CANCELLED alert, if it doesn't happen we missed
                    // the window, simply continue and let the next cancel attempt try its luck
                    boolean alertWasCancelled = scripter.confirmAlert("BOLUS CANCELLED", 1000);
                    if (alertWasCancelled) {
                        progressReportCallback.report(STOPPED, 0, 0);
                        return new CommandResult().success(true).enacted(false)
                                .message("Bolus cancelled as per user request with no insulin delivered");
                    }
                }
                SystemClock.sleep(10);
            }
            scripter.verifyMenuIsDisplayed(MenuType.MAIN_MENU,
                    "Pump did not return to MAIN_MEU from BOLUS_ENTER to deliver bolus. "
                            + "Check pump manually, the bolus might not have been delivered.");

            progressReportCallback.report(DELIVERING, 0, 0);
            Double bolusRemaining = (Double) scripter.getCurrentMenu().getAttribute(MenuAttribute.BOLUS_REMAINING);
            double lastBolusReported = 0;
            boolean lowCartdrigeAlarmTriggered = false;
            // wait for bolus delivery to complete; the remaining units to deliver are counted
            // down and are displayed on the main menu.
            // TODO extract into method

            // TODO 'low cartrdige' alarm must be handled inside, since the bolus continues regardless;
            // it must be cleared so we can see the remaining bolus again;
            while (bolusRemaining != null) {
                if (cancelRequested) {
                    // cancel running bolus by pressing up for 3s, while raise a BOLUS CANCELLED
                    // alert, unless the bolus finished within those 3s.
                    progressReportCallback.report(STOPPING, 0, 0);
                    scripter.pressKeyMs(RuffyScripter.Key.UP, 3000);
                    progressReportCallback.report(STOPPED, 0, 0);
                    // if the bolus finished while we attempted to cancel it, there'll be no alarm
                    long timeout = System.currentTimeMillis() + 2000;
                    while (scripter.getCurrentMenu().getType() != MenuType.WARNING_OR_ERROR && System.currentTimeMillis() < timeout) {
                        SystemClock.sleep(10);
                    }
                    while (scripter.getCurrentMenu().getType() == MenuType.WARNING_OR_ERROR) {
                        // TODO make this cleaner, extract method, needed below too
                        scripter.pressCheckKey();
                        SystemClock.sleep(200);
                    }
                    break;
                }
                if (lastBolusReported != bolusRemaining) {
                    log.debug("Delivering bolus, remaining: " + bolusRemaining);
                    int percentDelivered = (int) (100 - (bolusRemaining / bolus * 100));
                    progressReportCallback.report(DELIVERING, percentDelivered, bolus - bolusRemaining);
                    lastBolusReported = bolusRemaining;
                }

                // TODO think through situatiotns where an alarm can be raised, not just when pressing a button,
                // but a 'low battery' alarm can trigger at any time ...
                if (scripter.getCurrentMenu().getType() == MenuType.WARNING_OR_ERROR) {
                    String message = (String) scripter.getCurrentMenu().getAttribute(MenuAttribute.MESSAGE);
                    if (message.equals("LOW CARTRIDGE")) {
                        lowCartdrigeAlarmTriggered = true;
                        scripter.confirmAlert("LOW CARTRIDGE", 2000);
                    } else {
                        // any other alert
                        break;
                    }
                }
                SystemClock.sleep(50);
                bolusRemaining = (Double) scripter.getCurrentMenu().getAttribute(MenuAttribute.BOLUS_REMAINING);
            }
            progressReportCallback.report(DELIVERED, 100, bolus);

            // wait up to 2s for any possible warning to be raised, if not raised already
            // TODO what could be raised here, other than those alarms than can ring at any time anyways?
            long timeout = System.currentTimeMillis() + 2 * 1000;
            while (scripter.getCurrentMenu().getType() != MenuType.WARNING_OR_ERROR && System.currentTimeMillis() < timeout) {
                SystemClock.sleep(50);
            }

            // process warnings (confirm them, report back to AAPS about them)
//            while (scripter.getCurrentMenu().getType() == MenuType.WARNING_OR_ERROR || System.currentTimeMillis() < timeout) {
            // TODO brute-force hack
            if (scripter.getCurrentMenu().getType() == MenuType.WARNING_OR_ERROR) {
                scripter.confirmAlert(((String) scripter.getCurrentMenu().getAttribute(MenuAttribute.MESSAGE)), 1000);
            }
//                SystemClock.sleep(50);
//            }

            // TODO what if we hit 'cartridge low' alert here? is it immediately displayed or after the bolus?
            // TODO how are error states reported back to the caller that occur outside of calls in genal? Low battery, low cartridge?

            // make sure no alert (occlusion, cartridge empty) has occurred.
            scripter.verifyMenuIsDisplayed(MenuType.MAIN_MENU,
                    "Bolus delivery did not complete as expected. "
                            + "Check pump manually, the bolus might not have been delivered.");

            // TODO report back what was read from history

            // read last bolus record; those menus display static data and therefore
            // only a single menu update is sent
            scripter.navigateToMenu(MenuType.MY_DATA_MENU);
            scripter.verifyMenuIsDisplayed(MenuType.MY_DATA_MENU);
            scripter.pressCheckKey();
            if (scripter.getCurrentMenu().getType() != MenuType.BOLUS_DATA) {
                scripter.waitForMenuUpdate();
            }

            if (!scripter.getCurrentMenu().attributes().contains(MenuAttribute.BOLUS)) {
                throw new CommandException().success(false).enacted(true)
                        .message("Bolus was delivered, but unable to confirm it with history record");
            }

            // TODO check date so we don't pick a false record if the previous bolus had the same amount;
            // also, report back partial bolus. Just call ReadHsstory(timestamp, boluses=true) cmd ...
            double lastBolusInHistory = (double) scripter.getCurrentMenu().getAttribute(MenuAttribute.BOLUS);
            if (Math.abs(bolus - lastBolusInHistory) > 0.05) {
                throw new CommandException().success(false).enacted(true)
                        .message("Last bolus shows " + lastBolusInHistory
                                + " U delievered, but " + bolus + " U were requested");
            }
            log.debug("Bolus record in history confirms delivered bolus");

            // TODO how would this call fail? more generally ......
            scripter.returnToMainMenu();
            if (scripter.getCurrentMenu().getType() != MenuType.MAIN_MENU) {
                throw new CommandException().success(false).enacted(true)
                        .message("Bolus was correctly delivered and checked against history, but we "
                                + "did not return the main menu successfully.");
            }


            return new CommandResult().success(true).enacted(true)
                    .message(String.format(Locale.US, "Delivered %02.1f U", bolus));
        } catch (CommandException e) {
            return e.toCommandResult();
        }
    }

    public void requestCancellation() {
        cancelRequested = true;
        progressReportCallback.report(STOPPING, 0, 0);
    }

    @Override
    public String toString() {
        return "BolusCommand{" +
                "bolus=" + bolus +
                '}';
    }

    public interface ProgressReportCallback {
        enum State {
            DELIVERING,
            DELIVERED,
            STOPPING,
            STOPPED
        }

        void report(State state, int percent, double delivered);
    }
}
