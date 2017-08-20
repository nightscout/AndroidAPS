package de.jotomo.ruffyscripter.commands;

import android.os.SystemClock;

import org.monkey.d.ruffy.ruffy.driver.display.MenuAttribute;
import org.monkey.d.ruffy.ruffy.driver.display.MenuType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import de.jotomo.ruffyscripter.PumpState;
import de.jotomo.ruffyscripter.RuffyScripter;

import static de.jotomo.ruffyscripter.commands.BolusCommand.ProgressReportCallback.State.DELIVERING;
import static de.jotomo.ruffyscripter.commands.BolusCommand.ProgressReportCallback.State.STOPPED;
import static de.jotomo.ruffyscripter.commands.BolusCommand.ProgressReportCallback.State.STOPPING;
import static de.jotomo.ruffyscripter.commands.BolusCommand.ProgressReportCallback.State.DELIVERED;

public class BolusCommand implements Command {
    private static final Logger log = LoggerFactory.getLogger(BolusCommand.class);

    private final double bolus;
    private final ProgressReportCallback progressReportCallback;
    private volatile boolean cancelRequested;

    public BolusCommand(double bolus, ProgressReportCallback progressReportCallback) {
        this.progressReportCallback = progressReportCallback;
        this.bolus = bolus;
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
    public CommandResult execute(RuffyScripter scripter, PumpState initialPumpState) {
        try {
            enterBolusMenu(scripter);
            inputBolusAmount(scripter);
            verifyDisplayedBolusAmount(scripter);

            if (cancelRequested) {
                progressReportCallback.report(STOPPING, 0, 0);
                scripter.goToMainTypeScreen(MenuType.MAIN_MENU, 30 * 1000);
                progressReportCallback.report(STOPPED, 0, 0);
                return new CommandResult().success(true).enacted(false)
                        .message("Bolus cancelled as per user request with no insulin delivered");
            }

            // confirm bolus
            scripter.verifyMenuIsDisplayed(MenuType.BOLUS_ENTER);
            scripter.pressCheckKey();

            // the pump displays the entered bolus and waits a few seconds to let user check and cancel
            while (scripter.currentMenu.getType() == MenuType.BOLUS_ENTER) {
                if (cancelRequested) {
                    progressReportCallback.report(STOPPING, 0, 0);
                    scripter.pressUpKey();
                    // wait up to 1s for a BOLUS_CANCELLED alert, if it doesn't happen we missed
                    // the window, simply continue and let the next cancel attempt try its luck
                    boolean alertWasCancelled = confirmAlert("BOLUS CANCELLED", 1000);
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
            Double bolusRemaining = (Double) scripter.currentMenu.getAttribute(MenuAttribute.BOLUS_REMAINING);
            double lastBolusReported = 0;
            List<String> alarmsRaised = new ArrayList<>();
            // wait for bolus delivery to complete; the remaining units to deliver are counted
            // down and are displayed on the main menu.
            // TODO extract into method

            // TODO 'low cartrdige' alarm must be handled inside, since the bolus continues regardless;
            // it must be claread so we can see the remaining bolus again;
            while (bolusRemaining != null) {
                if (cancelRequested) {
                    progressReportCallback.report(STOPPING, 0, 0);
                    // TODO just press up 3s in a separated thread and let this loop run
                    // and at the end handle the outcome and returned raise alarms, whether cancel was reuqested etc

                    // TODO press up 3s, deal with bolus cancelled error, retrieved amount actually delivered from history and return it
                    // since the cancellation takes three seconds some insulin will have definitely been delivered (delivery speed is roughly 0.1U/s),
                    // but the pump may also finish delivering the bolus while we try to cancel it
                    // so, press a button, keep it press and deal with three outcomes:
                    // * delivery finished (no more remaining bolus displayed)
                    // * bolus was cancelled (warning raised)
                    // * any other error (low cartridge, occlusion, both will also trigger 'bolus cancelled' errors)
                    // cancelBolusInDelivery()
                    // TODO new thread to press button and then deal with outcomes below, since all errors can occur at all time, pressing
                    // abort just forces an error (if keyrpess is in time)
                    progressReportCallback.report(STOPPED, 0, 0);
                }
                if (lastBolusReported != bolusRemaining) {
                    log.debug("Delivering bolus, remaining: " + bolusRemaining);
                    int percentDelivered = (int) (100 - (bolusRemaining / bolus * 100));
                    progressReportCallback.report(DELIVERING, percentDelivered, bolus - bolusRemaining);
                    lastBolusReported = bolusRemaining;
                }
                // TODO deal with alarms that can arise; an oclussion with raise an oclussion alert as well as a bolus cancelled alert
                // occlusion cancels the bolus -> abort routine to report back delivered bolus;
                // low cartridge alert lets bolus run out
                // also, any other error or warning can occur and we should return in a controlled fashion -
                // communicating back what was actually delivered.
                // generally: cancel an alert on the pump and raise the error in AAPS?
                // letting the alert go off disrupts comms if the user interacts with the pump,
                // then we need to schedule a history read in the near future, let thee user know
                // the data will be out of sync for a bit.
                // how does the dana handle pump errors? has no vibration, but sound i guess
                // should this be configurabe? initially?

                if (scripter.currentMenu.getType() == MenuType.WARNING_OR_ERROR) {
                    String message = (String) scripter.currentMenu.getAttribute(MenuAttribute.MESSAGE);
                    if (message.equals("LOW CARTRIDGE")) {
                        alarmsRaised.add(message);
                        // confirm, note alert was raised and continue bolusing)
                    } else {
                        // any other alert
                        break;
                    }
                }
                SystemClock.sleep(50);
                bolusRemaining = (Double) scripter.currentMenu.getAttribute(MenuAttribute.BOLUS_REMAINING);
            }

            // wait up to 2s for any possible warning to be raised
            long minWait = System.currentTimeMillis() + 2 * 1000;
            while (scripter.currentMenu.getType() != MenuType.WARNING_OR_ERROR || System.currentTimeMillis() < minWait) {
                SystemClock.sleep(50);
            }

            // process warnings (confirm them, report back to AAPS about them)
            while (scripter.currentMenu.getType() == MenuType.WARNING_OR_ERROR || System.currentTimeMillis() < minWait) {
               // TODO
            }

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
            if (scripter.currentMenu.getType() != MenuType.BOLUS_DATA) {
                scripter.waitForMenuUpdate();
            }

            if (!scripter.currentMenu.attributes().contains(MenuAttribute.BOLUS)) {
                throw new CommandException().success(false).enacted(true)
                        .message("Bolus was delivered, but unable to confirm it with history record");
            }

            double lastBolusInHistory = (double) scripter.currentMenu.getAttribute(MenuAttribute.BOLUS);
            if (Math.abs(bolus - lastBolusInHistory) > 0.05) {
                throw new CommandException().success(false).enacted(true)
                        .message("Last bolus shows " + lastBolusInHistory
                                + " U delievered, but " + bolus + " U were requested");
            }
            log.debug("Bolus record in history confirms delivered bolus");

            if (!scripter.goToMainTypeScreen(MenuType.MAIN_MENU, 15 * 1000)) {
                throw new CommandException().success(false).enacted(true)
                        .message("Bolus was correctly delivered and checked against history, but we "
                                + "did not return the main menu successfully.");
            }

            progressReportCallback.report(DELIVERED, 100, bolus);

            return new CommandResult().success(true).enacted(true)
                    .message(String.format(Locale.US, "Delivered %02.1f U", bolus));
        } catch (CommandException e) {
            return e.toCommandResult();
        }
    }

    // TODO confirmAlarms? and report back which were cancelled?

    private boolean confirmAlert(String alertText, int maxWaitTillExpectedAlert) {
        // TODO
        return false;
    }

    private void enterBolusMenu(RuffyScripter scripter) {
        scripter.verifyMenuIsDisplayed(MenuType.MAIN_MENU);
        scripter.navigateToMenu(MenuType.BOLUS_MENU);
        scripter.verifyMenuIsDisplayed(MenuType.BOLUS_MENU);
        scripter.pressCheckKey();
        scripter.waitForMenuUpdate();
        scripter.verifyMenuIsDisplayed(MenuType.BOLUS_ENTER);
    }

    private void inputBolusAmount(RuffyScripter scripter) {
        scripter.verifyMenuIsDisplayed(MenuType.BOLUS_ENTER);
        // press 'up' once for each 0.1 U increment
        long steps = Math.round(bolus * 10);
        for (int i = 0; i < steps; i++) {
            scripter.verifyMenuIsDisplayed(MenuType.BOLUS_ENTER);
            scripter.pressUpKey();
            SystemClock.sleep(100);
        }
        // Give the pump time to finish any scrolling that might still be going on, can take
        // up to 1100ms. Plus some extra time to be sure
        SystemClock.sleep(2000);
    }

    private void verifyDisplayedBolusAmount(RuffyScripter scripter) {
        scripter.verifyMenuIsDisplayed(MenuType.BOLUS_ENTER);
        double displayedBolus = readDisplayedBolusAmount(scripter);
        log.debug("Final bolus: " + displayedBolus);
        if (Math.abs(displayedBolus - bolus) > 0.05) {
            throw new CommandException().message("Failed to set correct bolus. Expected: " + bolus + ", actual: " + displayedBolus);
        }

        // check again to ensure the displayed value hasn't change due to due scrolling taking extremely long
        SystemClock.sleep(2000);
        double refreshedDisplayedBolus = readDisplayedBolusAmount(scripter);
        if (Math.abs(displayedBolus - refreshedDisplayedBolus) > 0.05) {
            throw new CommandException().message("Failed to set bolus: bolus changed after input stopped from "
                    + displayedBolus + " -> " + refreshedDisplayedBolus);
        }
    }

    private double readDisplayedBolusAmount(RuffyScripter scripter) {
        // TODO v2 add timeout? Currently the command execution timeout would trigger if exceeded
        scripter.verifyMenuIsDisplayed(MenuType.BOLUS_ENTER);
        // bolus amount is blinking, so we need to make sure we catch it at the right moment
        Object amountObj = scripter.currentMenu.getAttribute(MenuAttribute.BOLUS);
        while (!(amountObj instanceof Double)) {
            scripter.waitForMenuUpdate();
            amountObj = scripter.currentMenu.getAttribute(MenuAttribute.BOLUS);
        }
        return (double) amountObj;
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
