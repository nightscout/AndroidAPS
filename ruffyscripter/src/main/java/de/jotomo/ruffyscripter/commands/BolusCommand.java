package de.jotomo.ruffyscripter.commands;

import android.os.SystemClock;

import org.monkey.d.ruffy.ruffy.driver.display.MenuAttribute;
import org.monkey.d.ruffy.ruffy.driver.display.MenuType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import de.jotomo.ruffy.spi.BolusProgressReporter;
import de.jotomo.ruffy.spi.CommandResult;
import de.jotomo.ruffy.spi.PumpWarningCodes;
import de.jotomo.ruffy.spi.history.WarningOrErrorCode;
import de.jotomo.ruffyscripter.RuffyScripter;

import static de.jotomo.ruffy.spi.BolusProgressReporter.State.DELIVERED;
import static de.jotomo.ruffy.spi.BolusProgressReporter.State.DELIVERING;
import static de.jotomo.ruffy.spi.BolusProgressReporter.State.FINISHED;
import static de.jotomo.ruffy.spi.BolusProgressReporter.State.PROGRAMMING;
import static de.jotomo.ruffy.spi.BolusProgressReporter.State.STOPPED;
import static de.jotomo.ruffy.spi.BolusProgressReporter.State.STOPPING;

public class BolusCommand extends BaseCommand {
    private static final Logger log = LoggerFactory.getLogger(BolusCommand.class);

    protected final double bolus;
    private final BolusProgressReporter bolusProgressReporter;
    private volatile boolean cancelRequested;

    public BolusCommand(double bolus, BolusProgressReporter bolusProgressReporter) {
        this.bolus = bolus;
        this.bolusProgressReporter = bolusProgressReporter;
        this.result = new CommandResult();
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
    public Integer getReconnectWarningId() {
        return PumpWarningCodes.BOLUS_CANCELLED;
    }

    @Override
    public void execute() {
        try {
            if (cancelRequested) {
                bolusProgressReporter.report(STOPPED, 0, 0);
                result.success = true;
                return;
            }
            bolusProgressReporter.report(PROGRAMMING, 0, 0);
            enterBolusMenu();
            inputBolusAmount();
            verifyDisplayedBolusAmount();

            // last chance to abort before confirm the bolus
            if (cancelRequested) {
                bolusProgressReporter.report(STOPPING, 0, 0);
                scripter.returnToRootMenu();
                bolusProgressReporter.report(STOPPED, 0, 0);
                result.success = true;
                return;
            }

            // confirm bolus
            scripter.verifyMenuIsDisplayed(MenuType.BOLUS_ENTER);
            scripter.pressCheckKey();
            result.enacted = true;

            // the pump displays the entered bolus and waits a few seconds to let user check and cancel
            while (scripter.getCurrentMenu().getType() == MenuType.BOLUS_ENTER) {
                if (cancelRequested) {
                    bolusProgressReporter.report(STOPPING, 0, 0);
                    scripter.pressUpKey();
                    // wait up to 1s for a BOLUS_CANCELLED alert, if it doesn't happen we missed
                    // the window, simply continue and let the next cancel attempt try its luck
                    boolean alertWasCancelled = scripter.confirmAlert(PumpWarningCodes.BOLUS_CANCELLED, 1000);
                    if (alertWasCancelled) {
                        bolusProgressReporter.report(STOPPED, 0, 0);
                        result.success = true;
                        return;
                    }
                    SystemClock.sleep(10);
                }
            }

            // the bolus progress is displayed on the main menu
            scripter.verifyMenuIsDisplayed(MenuType.MAIN_MENU,
                    "Pump did not return to MAIN_MEU from BOLUS_ENTER to deliver bolus. "
                            + "Check pump manually, the bolus might not have been delivered.");
            bolusProgressReporter.report(DELIVERING, 0, 0);

            // wait for bolus delivery to complete; the remaining units to deliver are counted down
            boolean cancelInProgress = false;
            Double lastBolusReported = 0d;
            Double bolusRemaining = (Double) scripter.getCurrentMenu().getAttribute(MenuAttribute.BOLUS_REMAINING);
            while (bolusRemaining != null || scripter.getCurrentMenu().getType() == MenuType.WARNING_OR_ERROR) {
                if (cancelRequested && !cancelInProgress) {
                    bolusProgressReporter.report(STOPPING, 0, 0);
                    cancelInProgress = true;
                    new Thread(() ->
                            scripter.pressKeyMs(RuffyScripter.Key.UP, 3000), "bolus-canceller").start();
                }
                if (scripter.getCurrentMenu().getType() == MenuType.WARNING_OR_ERROR) {
                    // confirm warning alert and update the result to indicate alerts occurred
                    WarningOrErrorCode warningOrErrorCode = scripter.readWarningOrErrorCode();
                    if (warningOrErrorCode.errorCode != null) {
                        throw new CommandException("Pump is in error state");
                    }
                    int warningCode = warningOrErrorCode.warningCode;
                    if (warningCode == PumpWarningCodes.BOLUS_CANCELLED) {
                        scripter.confirmAlert(PumpWarningCodes.BOLUS_CANCELLED, 2000);
                        bolusProgressReporter.report(STOPPED, 0, 0);
                        result.wasSuccessfullyCancelled = true;
                        result.alertConfirmed = true;
                    } else if (warningCode == PumpWarningCodes.CARTRIDGE_LOW) {
                        scripter.confirmAlert(PumpWarningCodes.CARTRIDGE_LOW, 2000);
                        result.alertConfirmed = true;
                    } else if (warningCode == PumpWarningCodes.BATTERY_LOW) {
                        scripter.confirmAlert(PumpWarningCodes.BATTERY_LOW, 2000);
                        result.alertConfirmed = true;
                    } else {
                        throw new CommandException("Pump is showing exotic warning: " + warningCode);
                    }
                }
                if (bolusRemaining != null && !bolusRemaining.equals(lastBolusReported)) {
                    log.debug("Delivering bolus, remaining: " + bolusRemaining);
                    int percentDelivered = (int) (100 - (bolusRemaining / bolus * 100));
                    bolusProgressReporter.report(DELIVERING, percentDelivered, bolus - bolusRemaining);
                    lastBolusReported = bolusRemaining;
                }
                SystemClock.sleep(50);
                bolusRemaining = (Double) scripter.getCurrentMenu().getAttribute(MenuAttribute.BOLUS_REMAINING);
            }
            bolusProgressReporter.report(DELIVERED, 100, bolus);
            result.success = true;
        } finally {
            bolusProgressReporter.report(FINISHED, 100, 0);
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
            throw new CommandException("Failed to set correct bolus. Expected: " + bolus + ", actual: " + displayedBolus);
        }

        // check again to ensure the displayed value hasn't change due to due scrolling taking extremely long
        SystemClock.sleep(1000);
        scripter.verifyMenuIsDisplayed(MenuType.BOLUS_ENTER);
        double refreshedDisplayedBolus = scripter.readBlinkingValue(Double.class, MenuAttribute.BOLUS);
        if (Math.abs(displayedBolus - refreshedDisplayedBolus) > 0.05) {
            throw new CommandException("Failed to set bolus: bolus changed after input stopped from "
                    + displayedBolus + " -> " + refreshedDisplayedBolus);
        }
    }

    public void requestCancellation() {
        log.debug("Bolus cancellation requested");
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
