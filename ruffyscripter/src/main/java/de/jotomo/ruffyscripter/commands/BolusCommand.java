package de.jotomo.ruffyscripter.commands;

import android.os.SystemClock;

import org.monkey.d.ruffy.ruffy.driver.display.MenuAttribute;
import org.monkey.d.ruffy.ruffy.driver.display.MenuType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import de.jotomo.ruffy.spi.history.Bolus;
import de.jotomo.ruffy.spi.history.PumpHistory;
import de.jotomo.ruffyscripter.RuffyScripter;
import de.jotomo.ruffy.spi.BolusProgressReporter;
import de.jotomo.ruffy.spi.CommandResult;

import static de.jotomo.ruffy.spi.BolusProgressReporter.State.*;

public class BolusCommand extends BaseCommand {
    private static final Logger log = LoggerFactory.getLogger(BolusCommand.class);

    protected final double bolus;
    private final BolusProgressReporter bolusProgressReporter;
    private volatile boolean cancelRequested;

    public BolusCommand(double bolus, BolusProgressReporter bolusProgressReporter) {
        this.bolus = bolus;
        this.bolusProgressReporter = bolusProgressReporter;
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
            // TODO also check if there's a bolus in history we're not aware of
            //      press check twice to get reservoir level and last bolus quickly

            bolusProgressReporter.report(PROGRAMMING, 0, 0);
            enterBolusMenu();
            inputBolusAmount();
            verifyDisplayedBolusAmount();

            if (cancelRequested) {
                bolusProgressReporter.report(STOPPING, 0, 0);
                scripter.returnToRootMenu();
                bolusProgressReporter.report(STOPPED, 0, 0);
                return new CommandResult().success(true).enacted(false)
                        .message("Bolus cancelled as per user request with no insulin delivered");
            }

            // confirm bolus
            scripter.verifyMenuIsDisplayed(MenuType.BOLUS_ENTER);
            scripter.pressCheckKey();

            // the pump displays the entered bolus and waits a few seconds to let user check and cancel
            while (scripter.getCurrentMenu().getType() == MenuType.BOLUS_ENTER) {
                if (cancelRequested) {
                    bolusProgressReporter.report(STOPPING, 0, 0);
                    scripter.pressUpKey();
                    // wait up to 1s for a BOLUS_CANCELLED alert, if it doesn't happen we missed
                    // the window, simply continue and let the next cancel attempt try its luck
                    boolean alertWasCancelled = scripter.confirmAlert("BOLUS CANCELLED", 1000);
                    if (alertWasCancelled) {
                        bolusProgressReporter.report(STOPPED, 0, 0);
                        return new CommandResult().success(true).enacted(false)
                                .message("Bolus cancelled as per user request with no insulin delivered");
                    }
                }
                SystemClock.sleep(10);
            }
            scripter.verifyMenuIsDisplayed(MenuType.MAIN_MENU,
                    "Pump did not return to MAIN_MEU from BOLUS_ENTER to deliver bolus. "
                            + "Check pump manually, the bolus might not have been delivered.");

            bolusProgressReporter.report(DELIVERING, 0, 0);
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
                    bolusProgressReporter.report(STOPPING, 0, 0);
                    scripter.pressKeyMs(RuffyScripter.Key.UP, 3000);
                    bolusProgressReporter.report(STOPPED, 0, 0);
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
                    bolusProgressReporter.report(DELIVERING, percentDelivered, bolus - bolusRemaining);
                    lastBolusReported = bolusRemaining;
                }

                /*
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
                */

                SystemClock.sleep(50);
                bolusRemaining = (Double) scripter.getCurrentMenu().getAttribute(MenuAttribute.BOLUS_REMAINING);
            }
            bolusProgressReporter.report(DELIVERED, 100, bolus);

            /*
            // wait up to 2s for any possible warning to be raised, if not raised already
            // TODO what could be raised here, other than those alarms than can ring at any time anyways?
            long timeout = System.currentTimeMillis() + 2 * 1000;
            while (scripter.getCurrentMenu().getType() != MenuType.WARNING_OR_ERROR && System.currentTimeMillis() < timeout) {
                SystemClock.sleep(50);
            }

            // process warnings (confirm them, report back to AAPS about them)
//            while (scripter.getCurrentMenu().getType() == MenuType.WARNING_OR_ERROR || System.currentTimeMillis() < timeout) {
            if (scripter.getCurrentMenu().getType() == MenuType.WARNING_OR_ERROR) {
                scripter.confirmAlert(((String) scripter.getCurrentMenu().getAttribute(MenuAttribute.MESSAGE)), 1000);
            }
//                SystemClock.sleep(50);
//            }
             */

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
            scripter.returnToRootMenu();
            if (scripter.getCurrentMenu().getType() != MenuType.MAIN_MENU) {
                throw new CommandException().success(false).enacted(true)
                        .message("Bolus was correctly delivered and checked against history, but we "
                                + "did not return the main menu successfully.");
            }

            ArrayList<Bolus> boluses = new ArrayList<>();
            boluses.add(new Bolus(System.currentTimeMillis(), bolus));
            return new CommandResult().success(true).enacted(true)
                    .message(String.format(Locale.US, "Delivered %02.1f U", bolus))
                    .history(new PumpHistory().bolusHistory(boluses));
        } catch (CommandException e) {
            return e.toCommandResult();
        } finally {
            bolusProgressReporter.report(null, 100, 0);
        }
    }

    private void enterBolusMenu() {
        scripter.verifyMenuIsDisplayed(MenuType.MAIN_MENU);
        scripter.navigateToMenu(MenuType.BOLUS_MENU);
        scripter.verifyMenuIsDisplayed(MenuType.BOLUS_MENU);
        scripter.pressCheckKey();
        scripter.waitForMenuUpdate();
        scripter.verifyMenuIsDisplayed(MenuType.BOLUS_ENTER);
    }

    private void inputBolusAmount() {
        scripter.verifyMenuIsDisplayed(MenuType.BOLUS_ENTER);
        // press 'up' once for each 0.1 U increment
        long steps = Math.round(bolus * 10);
        for (int i = 0; i < steps; i++) {
            scripter.verifyMenuIsDisplayed(MenuType.BOLUS_ENTER);
            scripter.pressUpKey();
            SystemClock.sleep(50);
        }
    }

    private void verifyDisplayedBolusAmount() {
        scripter.verifyMenuIsDisplayed(MenuType.BOLUS_ENTER);

        // wait up to 10s for any scrolling to finish
        double displayedBolus = scripter.readBlinkingValue(Double.class, MenuAttribute.BOLUS);
        long timeout = System.currentTimeMillis() + 10 * 1000;
        while (timeout > System.currentTimeMillis() && bolus - displayedBolus > 0.05) {
            log.debug("Waiting for pump to process scrolling input for amount, current: " + displayedBolus + ", desired: " + bolus);
            SystemClock.sleep(50);
            displayedBolus = scripter.readBlinkingValue(Double.class, MenuAttribute.BOLUS);
        }

        log.debug("Final bolus: " + displayedBolus);
        if (Math.abs(displayedBolus - bolus) > 0.05) {
            throw new CommandException().message("Failed to set correct bolus. Expected: " + bolus + ", actual: " + displayedBolus);
        }

        // check again to ensure the displayed value hasn't change due to due scrolling taking extremely long
        SystemClock.sleep(1000);
        scripter.verifyMenuIsDisplayed(MenuType.BOLUS_ENTER);
        double refreshedDisplayedBolus = scripter.readBlinkingValue(Double.class, MenuAttribute.BOLUS);
        if (Math.abs(displayedBolus - refreshedDisplayedBolus) > 0.05) {
            throw new CommandException().message("Failed to set bolus: bolus changed after input stopped from "
                    + displayedBolus + " -> " + refreshedDisplayedBolus);
        }
    }

    public void requestCancellation() {
        cancelRequested = true;
        bolusProgressReporter.report(STOPPING, 0, 0);
    }

    @Override
    public boolean needsRunMode() {
        return true;
    }

    @Override
    public String toString() {
        return "BolusCommand{" +
                "bolus=" + bolus +
                '}';
    }
}
