package info.nightscout.pump.combo.ruffyscripter.commands;

import android.os.SystemClock;

import androidx.annotation.NonNull;

import org.monkey.d.ruffy.ruffy.driver.display.MenuAttribute;
import org.monkey.d.ruffy.ruffy.driver.display.MenuType;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import info.nightscout.pump.combo.ruffyscripter.BolusProgressReporter;
import info.nightscout.pump.combo.ruffyscripter.PumpWarningCodes;
import info.nightscout.pump.combo.ruffyscripter.RuffyScripter;
import info.nightscout.pump.combo.ruffyscripter.WarningOrErrorCode;
import info.nightscout.rx.logging.AAPSLogger;
import info.nightscout.rx.logging.LTag;

public class BolusCommand extends BaseCommand {
    
    private final AAPSLogger aapsLogger;

    protected final double bolus;
    private final BolusProgressReporter bolusProgressReporter;
    private volatile boolean cancelRequested;

    public BolusCommand(double bolus, BolusProgressReporter bolusProgressReporter, AAPSLogger aapsLogger) {
        this.bolus = bolus;
        this.bolusProgressReporter = bolusProgressReporter;
        this.aapsLogger = aapsLogger;
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
            bolusProgressReporter.report(BolusProgressReporter.State.STOPPED, 0, 0);
            result.success = true;
            aapsLogger.debug(LTag.PUMP, "Stage 0: cancelled bolus before programming");
            return;
        }

        bolusProgressReporter.report(BolusProgressReporter.State.PROGRAMMING, 0, 0);
        enterBolusMenu();
        inputBolusAmount();
        verifyDisplayedBolusAmount();

        // last chance to abort before confirming the bolus
        if (cancelRequested) {
            bolusProgressReporter.report(BolusProgressReporter.State.STOPPING, 0, 0);
            scripter.returnToRootMenu();
            bolusProgressReporter.report(BolusProgressReporter.State.STOPPED, 0, 0);
            result.success = true;
            aapsLogger.debug(LTag.PUMP, "Stage 1: cancelled bolus after programming");
            return;
        }

        // confirm bolus
        scripter.verifyMenuIsDisplayed(MenuType.BOLUS_ENTER);
        scripter.pressCheckKey();
        aapsLogger.debug(LTag.PUMP, "Stage 2: bolus confirmed");

        // the pump displays the entered bolus and waits a few seconds to let user check and cancel
        while (scripter.getCurrentMenu().getType() == MenuType.BOLUS_ENTER) {
            if (cancelRequested) {
                aapsLogger.debug(LTag.PUMP, "Stage 2: cancelling during confirmation wait");
                bolusProgressReporter.report(BolusProgressReporter.State.STOPPING, 0, 0);
                scripter.pressUpKey();
                // wait up to 1s for a BOLUS_CANCELLED alert, if it doesn't happen we missed
                // the window, simply continue and let the next cancel attempt try its luck
                boolean alertWasCancelled = scripter.confirmAlert(PumpWarningCodes.BOLUS_CANCELLED, 1000);
                if (alertWasCancelled) {
                    aapsLogger.debug(LTag.PUMP, "Stage 2: successfully cancelled during confirmation wait");
                    bolusProgressReporter.report(BolusProgressReporter.State.STOPPED, 0, 0);
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
        bolusProgressReporter.report(BolusProgressReporter.State.DELIVERING, 0, 0);

        // wait for bolus delivery to complete; the remaining units to deliver are counted down
        boolean cancelInProgress = false;
        Double lastBolusReported = 0d;
        Double bolusRemaining = (Double) scripter.getCurrentMenu().getAttribute(MenuAttribute.BOLUS_REMAINING);
        Thread cancellationThread = null;
        while (bolusRemaining != null || scripter.getCurrentMenu().getType() == MenuType.WARNING_OR_ERROR) {
            if (cancelRequested && !cancelInProgress) {
                aapsLogger.debug(LTag.PUMP, "Stage 3: cancellation while delivering bolus");
                bolusProgressReporter.report(BolusProgressReporter.State.STOPPING, 0, 0);
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
                        } catch (InterruptedException ignored) {
                            // ignore
                        }
                    }
                    scripter.confirmAlert(PumpWarningCodes.BOLUS_CANCELLED, 2000);
                    bolusProgressReporter.report(BolusProgressReporter.State.STOPPED, 0, 0);
                    aapsLogger.debug(LTag.PUMP, "Stage 3: confirmed BOLUS CANCELLED after cancelling bolus during delivery");
                } else if (Objects.equals(warningCode, PumpWarningCodes.CARTRIDGE_LOW)) {
                    scripter.confirmAlert(PumpWarningCodes.CARTRIDGE_LOW, 2000);
                    result.forwardedWarnings.add(PumpWarningCodes.CARTRIDGE_LOW);
                    aapsLogger.debug(LTag.PUMP, "Stage 3: confirmed low cartridge alert and forwarding to AAPS");
                } else if (Objects.equals(warningCode, PumpWarningCodes.BATTERY_LOW)) {
                    scripter.confirmAlert(PumpWarningCodes.BATTERY_LOW, 2000);
                    result.forwardedWarnings.add(PumpWarningCodes.BATTERY_LOW);
                    aapsLogger.debug(LTag.PUMP, "Stage 3: confirmed low battery alert and forwarding to AAPS");
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
                aapsLogger.debug(LTag.PUMP, "Delivering bolus, remaining: " + bolusRemaining);
                int percentDelivered = (int) (100 - (bolusRemaining / bolus * 100));
                bolusProgressReporter.report(BolusProgressReporter.State.DELIVERING, percentDelivered, bolus - bolusRemaining);
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
            } catch (InterruptedException ignored) {
                // ignore
            }
        }

        if (cancelInProgress) {
            aapsLogger.debug(LTag.PUMP, "Stage 4: bolus was cancelled, with unknown amount delivered");
        } else {
            aapsLogger.debug(LTag.PUMP, "Stage 4: full bolus of " + bolus + " U was successfully delivered");
            bolusProgressReporter.report(BolusProgressReporter.State.DELIVERED, 100, bolus);
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
            aapsLogger.debug(LTag.PUMP, "Waiting for pump to process scrolling input for amount, current: " + displayedBolus + ", desired: " + bolus);
            SystemClock.sleep(50);
            displayedBolus = scripter.readBlinkingValue(Double.class, MenuAttribute.BOLUS);
        }

        aapsLogger.debug(LTag.PUMP, "Final bolus: " + displayedBolus);
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
        aapsLogger.debug(LTag.PUMP, "Bolus cancellation requested");
        cancelRequested = true;
        bolusProgressReporter.report(BolusProgressReporter.State.STOPPING, 0, 0);
    }

    @Override @NonNull
    public String toString() {
        return "BolusCommand{" +
                "bolus=" + bolus +
                '}';
    }
}
