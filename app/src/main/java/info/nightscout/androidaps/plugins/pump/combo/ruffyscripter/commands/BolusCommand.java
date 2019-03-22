package info.nightscout.androidaps.plugins.pump.combo.ruffyscripter.commands;

import android.os.SystemClock;

import org.monkey.d.ruffy.ruffy.driver.display.MenuAttribute;
import org.monkey.d.ruffy.ruffy.driver.display.MenuType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import info.nightscout.androidaps.plugins.pump.combo.ruffyscripter.BolusProgressReporter;
import info.nightscout.androidaps.plugins.pump.combo.ruffyscripter.PumpWarningCodes;
import info.nightscout.androidaps.plugins.pump.combo.ruffyscripter.WarningOrErrorCode;
import info.nightscout.androidaps.plugins.pump.combo.ruffyscripter.RuffyScripter;

import static info.nightscout.androidaps.plugins.pump.combo.ruffyscripter.BolusProgressReporter.State.DELIVERED;
import static info.nightscout.androidaps.plugins.pump.combo.ruffyscripter.BolusProgressReporter.State.DELIVERING;
import static info.nightscout.androidaps.plugins.pump.combo.ruffyscripter.BolusProgressReporter.State.PROGRAMMING;
import static info.nightscout.androidaps.plugins.pump.combo.ruffyscripter.BolusProgressReporter.State.STOPPED;
import static info.nightscout.androidaps.plugins.pump.combo.ruffyscripter.BolusProgressReporter.State.STOPPING;

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

        if (bolus <= 0) {
            violations.add("Requested bolus non-positive: " + bolus);
        }

        return violations;
    }

    @Override
    public Integer getReconnectWarningId() {
        return PumpWarningCodes.BOLUS_CANCELLED;
    }

    @Override
    public void execute() {
        if (cancelRequested) {
            bolusProgressReporter.report(STOPPED, 0, 0);
            result.success = true;
            log.debug("Stage 0: cancelled bolus before programming");
            return;
        }

        bolusProgressReporter.report(PROGRAMMING, 0, 0);
        enterBolusMenu();
        inputBolusAmount();
        verifyDisplayedBolusAmount();

        // last chance to abort before confirming the bolus
        if (cancelRequested) {
            bolusProgressReporter.report(STOPPING, 0, 0);
            scripter.returnToRootMenu();
            bolusProgressReporter.report(STOPPED, 0, 0);
            result.success = true;
            log.debug("Stage 1: cancelled bolus after programming");
            return;
        }

        // confirm bolus
        scripter.verifyMenuIsDisplayed(MenuType.BOLUS_ENTER);
        scripter.pressCheckKey();
        log.debug("Stage 2: bolus confirmed");

        // the pump displays the entered bolus and waits a few seconds to let user check and cancel
        while (scripter.getCurrentMenu().getType() == MenuType.BOLUS_ENTER) {
            if (cancelRequested) {
                log.debug("Stage 2: cancelling during confirmation wait");
                bolusProgressReporter.report(STOPPING, 0, 0);
                scripter.pressUpKey();
                // wait up to 1s for a BOLUS_CANCELLED alert, if it doesn't happen we missed
                // the window, simply continue and let the next cancel attempt try its luck
                boolean alertWasCancelled = scripter.confirmAlert(PumpWarningCodes.BOLUS_CANCELLED, 1000);
                if (alertWasCancelled) {
                    log.debug("Stage 2: successfully cancelled during confirmation wait");
                    bolusProgressReporter.report(STOPPED, 0, 0);
                    result.success = true;
                    return;
                }
            }
            SystemClock.sleep(10);
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
        Thread cancellationThread = null;
        while (bolusRemaining != null || scripter.getCurrentMenu().getType() == MenuType.WARNING_OR_ERROR) {
            if (cancelRequested && !cancelInProgress) {
                log.debug("Stage 3: cancellation while delivering bolus");
                bolusProgressReporter.report(STOPPING, 0, 0);
                cancelInProgress = true;
                cancellationThread = new Thread(() ->
                        scripter.pressKeyMs(RuffyScripter.Key.UP, 3000), "bolus-canceller");
                cancellationThread.start();
            }
            if (scripter.getCurrentMenu().getType() == MenuType.WARNING_OR_ERROR) {
                // confirm warning alert and update the result to indicate alerts occurred
                WarningOrErrorCode warningOrErrorCode = scripter.readWarningOrErrorCode();
                if (warningOrErrorCode.errorCode != null) {
                    throw new CommandException("Pump is in error state");
                }
                Integer warningCode = warningOrErrorCode.warningCode;
                if (Objects.equals(warningCode, PumpWarningCodes.BOLUS_CANCELLED)) {
                    // wait until cancellation thread releases the up button, otherwise we won't
                    // be able to confirm anything
                    if (cancellationThread != null) {
                        try {
                            cancellationThread.join(3500);
                        } catch (InterruptedException e) {
                            // ignore
                        }
                    }
                    scripter.confirmAlert(PumpWarningCodes.BOLUS_CANCELLED, 2000);
                    bolusProgressReporter.report(STOPPED, 0, 0);
                    log.debug("Stage 3: confirmed BOLUS CANCELLED after cancelling bolus during delivery");
                } else if (Objects.equals(warningCode, PumpWarningCodes.CARTRIDGE_LOW)) {
                    scripter.confirmAlert(PumpWarningCodes.CARTRIDGE_LOW, 2000);
                    result.forwardedWarnings.add(PumpWarningCodes.CARTRIDGE_LOW);
                    log.debug("Stage 3: confirmed low cartridge alert and forwarding to AAPS");
                } else if (Objects.equals(warningCode, PumpWarningCodes.BATTERY_LOW)) {
                    scripter.confirmAlert(PumpWarningCodes.BATTERY_LOW, 2000);
                    result.forwardedWarnings.add(PumpWarningCodes.BATTERY_LOW);
                    log.debug("Stage 3: confirmed low battery alert and forwarding to AAPS");
                } else {
                    // all other warnings or errors;
                    // An occlusion error can also occur during bolus. To read the partially delivered
                    // bolus, we'd have to first confirm the error. But an (occlusion) **error** shall not
                    // be confirmed and potentially be swallowed by a bug or shaky comms, so we let
                    // the pump be noisy (which the user will have to interact with anyway).
                    // Thus, this method will terminate with an exception and display an error message.
                    // Ideally, sometime after the user has dealt with the situation, the partially
                    // delivered bolus should be read. However, ready history is tricky at this point.
                    // Also: with an occlusion, the amount of insulin active is in question.
                    // It would be safer to assume the delivered bolus results in IOB, but there's
                    // only so much we can do at this point, so the user shall take over here and
                    // add a bolus record as and if needed.
                    throw new CommandException("Pump is showing exotic warning/error: " + warningOrErrorCode);
                }
            }
            if (bolusRemaining != null && !Objects.equals(bolusRemaining, lastBolusReported)) {
                log.debug("Delivering bolus, remaining: " + bolusRemaining);
                int percentDelivered = (int) (100 - (bolusRemaining / bolus * 100));
                bolusProgressReporter.report(DELIVERING, percentDelivered, bolus - bolusRemaining);
                lastBolusReported = bolusRemaining;
            }
            SystemClock.sleep(50);
            bolusRemaining = (Double) scripter.getCurrentMenu().getAttribute(MenuAttribute.BOLUS_REMAINING);
        }
        // if a cancellation was started by pressing up for 3 seconds but the bolus has finished during those
        // three seconds, must wait until the button is unpressed again so that follow up commands
        // work properly.
        if (cancellationThread != null) {
            try {
                cancellationThread.join();
            } catch (InterruptedException e) {
                // ignore
            }
        }

        if (cancelInProgress) {
            log.debug("Stage 4: bolus was cancelled, with unknown amount delivered");
        } else {
            log.debug("Stage 4: full bolus of " + bolus + " U was successfully delivered");
            bolusProgressReporter.report(DELIVERED, 100, bolus);
        }
        result.success = true;
    }

    private void enterBolusMenu() {
        scripter.verifyMenuIsDisplayed(MenuType.MAIN_MENU);
        scripter.navigateToMenu(MenuType.BOLUS_MENU);
        scripter.verifyMenuIsDisplayed(MenuType.BOLUS_MENU);
        scripter.pressCheckKey();
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
        if (Math.abs(displayedBolus - bolus) > 0.01) {
            throw new CommandException("Failed to set correct bolus. Expected: " + bolus + ", actual: " + displayedBolus);
        }

        // check again to ensure the displayed value hasn't change due to due scrolling taking extremely long
        SystemClock.sleep(1000);
        scripter.verifyMenuIsDisplayed(MenuType.BOLUS_ENTER);
        double refreshedDisplayedBolus = scripter.readBlinkingValue(Double.class, MenuAttribute.BOLUS);
        if (Math.abs(displayedBolus - refreshedDisplayedBolus) > 0.01) {
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
    public String toString() {
        return "BolusCommand{" +
                "bolus=" + bolus +
                '}';
    }
}
