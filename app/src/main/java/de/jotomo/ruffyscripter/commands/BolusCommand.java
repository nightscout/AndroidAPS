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

import static de.jotomo.ruffyscripter.commands.ProgressReportCallback.State.BOLUSING;
import static de.jotomo.ruffyscripter.commands.ProgressReportCallback.State.CANCELLED;
import static de.jotomo.ruffyscripter.commands.ProgressReportCallback.State.CANCELLING;
import static de.jotomo.ruffyscripter.commands.ProgressReportCallback.State.FINISHED;
import static de.jotomo.ruffyscripter.commands.ProgressReportCallback.State.PREPARING;

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
        progressReportCallback.progress(PREPARING, 0, 0);
        try {
            enterBolusMenu(scripter);

            inputBolusAmount(scripter);
            verifyDisplayedBolusAmount(scripter);

            // confirm bolus
            scripter.verifyMenuIsDisplayed(MenuType.BOLUS_ENTER);
            scripter.pressCheckKey();
            if (cancelRequested) {
                scripter.goToMainTypeScreen(MenuType.MAIN_MENU, 30_000);
                progressReportCallback.progress(CANCELLED, 0, 0);
                return new CommandResult().success(true).enacted(false).message("Bolus cancelled as per user request with no insulin delivered");
            }
            progressReportCallback.progress(BOLUSING, 0, 0);

            // the pump displays the entered bolus and waits a bit to let user check and cancel
            // TODO pressing up (and possible other keys) cancels the bolus
            scripter.waitForMenuToBeLeft(MenuType.BOLUS_ENTER);
            while (scripter.currentMenu.getType() == MenuType.BOLUS_ENTER) {
                if (cancelRequested) {
                    scripter.pressUpKey();
                    // TODO deal with error; write a method to wait for and cancel a specific alarm
                    // wait happens if the keypress comes too late? just try agoin below?
                }
                SystemClock.sleep(50);
            }

            scripter.verifyMenuIsDisplayed(MenuType.MAIN_MENU,
                    "Pump did not return to MAIN_MEU from BOLUS_ENTER to deliver bolus. "
                            + "Check pump manually, the bolus might not have been delivered.");

            // wait for bolus delivery to complete; the remaining units to deliver are counted
            // down and are displayed on the main menu.
            Double bolusRemaining = (Double) scripter.currentMenu.getAttribute(MenuAttribute.BOLUS_REMAINING);
            double lastBolusReported = 0;
            while (bolusRemaining != null) {
                if (cancelRequested) {
                    // TODO press up 3s, deal with bolus cancelled error, retrieved amount actually delivered from history and return it
                    // since the cancellation takes three seconds some insulin will have definately been delivered (delivery speed is roughly 0.1U/s)
                }
                if (lastBolusReported != bolusRemaining) {
                    log.debug("Delivering bolus, remaining: " + bolusRemaining);
                    int percentDelivered = (int) (100 - (bolusRemaining / bolus * 100));
                    progressReportCallback.progress(BOLUSING, percentDelivered, bolus - bolusRemaining);
                    lastBolusReported = bolusRemaining;
                }
                // TODO deal with alarms that can arise; an oclussion with raise an oclussion alert as well as a bolus cancelled alert
                SystemClock.sleep(50);
                bolusRemaining = (Double) scripter.currentMenu.getAttribute(MenuAttribute.BOLUS_REMAINING);
            }

            // TODO what if we hit 'cartridge low' alert here? is it immediately displayed or after the bolus?
            // TODO how are error states reported back to the caller that occur outside of calls in genal? Low battery, low cartridge?

            // make sure no alert (occlusion, cartridge empty) has occurred.
            scripter.verifyMenuIsDisplayed(MenuType.MAIN_MENU,
                    "Bolus delivery did not complete as expected. "
                            + "Check pump manually, the bolus might not have been delivered.");

            progressReportCallback.progress(FINISHED, 100, bolus);

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

            // leave menu to go back to main menu
            scripter.pressCheckKey();
            scripter.waitForMenuToBeLeft(MenuType.BOLUS_DATA);
            scripter.verifyMenuIsDisplayed(MenuType.MAIN_MENU,
                    "Bolus was correctly delivered and checked against history, but we "
                            + "did not return the main menu successfully.");

            return new CommandResult().success(true).enacted(true)
                    .message(String.format(Locale.US, "Delivered %02.1f U", bolus));
        } catch (CommandException e) {
            return e.toCommandResult();
        }
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
        progressReportCallback.progress(CANCELLING, 0, 0);
    }

    @Override
    public String toString() {
        return "BolusCommand{" +
                "bolus=" + bolus +
                '}';
    }
}
