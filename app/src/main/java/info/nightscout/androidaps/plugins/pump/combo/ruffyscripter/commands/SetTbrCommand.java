package info.nightscout.androidaps.plugins.pump.combo.ruffyscripter.commands;

import android.os.SystemClock;

import org.monkey.d.ruffy.ruffy.driver.display.MenuAttribute;
import org.monkey.d.ruffy.ruffy.driver.display.MenuType;
import org.monkey.d.ruffy.ruffy.driver.display.menu.MenuTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import info.nightscout.androidaps.plugins.pump.combo.ruffyscripter.PumpState;
import info.nightscout.androidaps.plugins.pump.combo.ruffyscripter.PumpWarningCodes;
import info.nightscout.androidaps.plugins.pump.combo.ruffyscripter.WarningOrErrorCode;

public class SetTbrCommand extends BaseCommand {
    private static final Logger log = LoggerFactory.getLogger(SetTbrCommand.class);

    private final long percentage;
    private final long duration;

    public SetTbrCommand(long percentage, long duration) {
        this.percentage = percentage;
        this.duration = duration;
    }

    @Override
    public List<String> validateArguments() {
        List<String> violations = new ArrayList<>();

        if (percentage % 10 != 0) {
            violations.add("TBR percentage must be set in 10% steps");
        }
        if (percentage < 0 || percentage > 500) {
            violations.add("TBR percentage must be within 0-500%");
        }

        if (percentage != 100) {
            if (duration % 15 != 0) {
                violations.add("TBR duration can only be set in 15 minute steps");
            }
            if (duration > 60 * 24) {
                violations.add("Maximum TBR duration is 24 hours");
            }
        }

        return violations;
    }

    @Override
    public Integer getReconnectWarningId() {
        return PumpWarningCodes.TBR_CANCELLED;
    }

    @Override
    public void execute() {
        try {
            if (checkAndWaitIfExistingTbrIsAboutToEnd()) {
                return;
            }

            enterTbrMenu();
            boolean increasingPercentage = inputTbrPercentage();
            verifyDisplayedTbrPercentage(increasingPercentage);

            if (percentage == 100) {
                cancelTbrAndConfirmCancellationWarning();
            } else {
                // switch to TBR_DURATION menu by pressing menu key
                scripter.verifyMenuIsDisplayed(MenuType.TBR_SET);
                scripter.pressMenuKey();
                scripter.verifyMenuIsDisplayed(MenuType.TBR_DURATION);

                boolean increasingDuration = inputTbrDuration();
                verifyDisplayedTbrDuration(increasingDuration);

                // confirm TBR
                scripter.pressCheckKey();
                scripter.waitForMenuToBeLeft(MenuType.TBR_DURATION);
            }
        } catch (CommandException e) {
            if (scripter.getCurrentMenu().getType() == MenuType.WARNING_OR_ERROR) {
                // The pump raises a TBR CANCELLED warning when a running TBR finishes while we're
                // programming a new one (TBR remaining time was last displayed as 0:01, the pump
                // rounds seconds up to full minutes). In that case confirm the alert since we know
                // we caused it (in a way), but still fail the command so the usual cleanups of returning
                // to main menu etc are performed, after which this command can simply be retried.
                // Note that this situation should have been dealt with in
                // #checkAndWaitIfExistingTbrIsAboutToEnd, but still occur if that method runs
                // into a timeout or some other freaky thing happens, so we'll leave it here.
                WarningOrErrorCode warningOrErrorCode = scripter.readWarningOrErrorCode();
                if (Objects.equals(warningOrErrorCode.warningCode, PumpWarningCodes.TBR_CANCELLED)) {
                    scripter.confirmAlert(PumpWarningCodes.TBR_CANCELLED);
                }
            }
            throw e;
        }

        result.success = true;
    }

    /**
     * When programming a new TBR while an existing TBR runs out, a TBR CANCELLED
     * alert is raised (failing the command, requiring a reconnect and confirming alert
     * and all). To avoid this, wait until the active TBR runs out if the active TBR
     * is about to end.
     *
     * @return true if we waited till the TBR ended and cancellation was request so all work is done.
     */
    private boolean checkAndWaitIfExistingTbrIsAboutToEnd() {
        scripter.verifyMenuIsDisplayed(MenuType.MAIN_MENU);
        long timeout = System.currentTimeMillis() + 65 * 1000;
        PumpState state = scripter.readPumpStateInternal();
        if (state.tbrRemainingDuration == 1) {
            while (state.tbrActive && System.currentTimeMillis() < timeout) {
                log.debug("Waiting for existing TBR to run out to avoid alert while setting TBR");
                scripter.waitForScreenUpdate();
                state = scripter.readPumpStateInternal();
            }
            // if we waited above and a cancellation was requested, we already completed the request
            if (!state.tbrActive && percentage == 100) {
                result.success = true;
                return true;
            }
        }
        return false;
    }

    private void enterTbrMenu() {
        scripter.verifyMenuIsDisplayed(MenuType.MAIN_MENU);
        scripter.navigateToMenu(MenuType.TBR_MENU);
        scripter.verifyMenuIsDisplayed(MenuType.TBR_MENU);
        scripter.pressCheckKey();
        scripter.verifyMenuIsDisplayed(MenuType.TBR_SET);
    }

    private boolean inputTbrPercentage() {
        scripter.verifyMenuIsDisplayed(MenuType.TBR_SET);
        long currentPercent = readDisplayedPercentage();
        log.debug("Current TBR %: " + currentPercent);
        long percentageChange = percentage - currentPercent;
        long percentageSteps = percentageChange / 10;
        boolean increasePercentage = percentageSteps > 0;
        log.debug("Pressing " + (increasePercentage ? "up" : "down") + " " + percentageSteps + " times");
        for (int i = 0; i < Math.abs(percentageSteps); i++) {
            scripter.verifyMenuIsDisplayed(MenuType.TBR_SET);
            log.debug("Push #" + (i + 1) + "/" + Math.abs(percentageSteps));
            if (increasePercentage) scripter.pressUpKey();
            else scripter.pressDownKey();
            SystemClock.sleep(50);
        }
        return increasePercentage;
    }

    private void verifyDisplayedTbrPercentage(boolean increasingPercentage) {
        scripter.verifyMenuIsDisplayed(MenuType.TBR_SET);
        // wait up to 5s for any scrolling to finish
        long displayedPercentage = readDisplayedPercentage();
        long timeout = System.currentTimeMillis() + 10 * 1000;
        while (timeout > System.currentTimeMillis()
                && ((increasingPercentage && displayedPercentage < percentage)
                || (!increasingPercentage && displayedPercentage > percentage))) {
            log.debug("Waiting for pump to process scrolling input for percentage, current: "
                    + displayedPercentage + ", desired: " + percentage + ", scrolling "
                    + (increasingPercentage ? "up" : "down"));
            SystemClock.sleep(50);
            displayedPercentage = readDisplayedPercentage();
        }
        log.debug("Final displayed TBR percentage: " + displayedPercentage);
        if (displayedPercentage != percentage) {
            throw new CommandException("Failed to set TBR percentage, requested: "
                    + percentage + ", actual: " + displayedPercentage);
        }

        // check again to ensure the displayed value hasn't change and scrolled past the desired
        // value due to due scrolling taking extremely long
        SystemClock.sleep(1000);
        scripter.verifyMenuIsDisplayed(MenuType.TBR_SET);
        long refreshedDisplayedTbrPecentage = readDisplayedPercentage();
        if (displayedPercentage != refreshedDisplayedTbrPecentage) {
            throw new CommandException("Failed to set TBR percentage: " +
                    "percentage changed after input stopped from "
                    + displayedPercentage + " -> " + refreshedDisplayedTbrPecentage);
        }
    }

    private boolean inputTbrDuration() {
        scripter.verifyMenuIsDisplayed(MenuType.TBR_DURATION);
        long durationSteps = calculateDurationSteps();
        boolean increaseDuration = durationSteps > 0;
        log.debug("Pressing " + (increaseDuration ? "up" : "down") + " " + durationSteps + " times");
        for (int i = 0; i < Math.abs(durationSteps); i++) {
            scripter.verifyMenuIsDisplayed(MenuType.TBR_DURATION);
            log.debug("Push #" + (i + 1) + "/" + Math.abs(durationSteps));
            if (increaseDuration) scripter.pressUpKey();
            else scripter.pressDownKey();
            SystemClock.sleep(50);
        }
        return increaseDuration;
    }

    private long calculateDurationSteps() {
        long currentDuration = readDisplayedDuration();
        log.debug("Initial TBR duration: " + currentDuration);

        long difference = duration - currentDuration;
        long durationSteps = difference / 15;
        long durationAfterInitialSteps = currentDuration + (durationSteps * 15);

        if (durationAfterInitialSteps < duration) return durationSteps + 1;
        else if (durationAfterInitialSteps > duration) return durationSteps - 1;
        else return durationSteps;
    }

    private void verifyDisplayedTbrDuration(boolean increasingPercentage) {
        scripter.verifyMenuIsDisplayed(MenuType.TBR_DURATION);

        // wait up to 5s for any scrolling to finish
        long displayedDuration = readDisplayedDuration();
        long timeout = System.currentTimeMillis() + 10 * 1000;
        while (timeout > System.currentTimeMillis()
                && ((increasingPercentage && displayedDuration < duration)
                || (!increasingPercentage && displayedDuration > duration))) {
            log.debug("Waiting for pump to process scrolling input for duration, current: "
                    + displayedDuration + ", desired: " + duration
                    + ", scrolling " + (increasingPercentage ? "up" : "down"));
            SystemClock.sleep(50);
            displayedDuration = readDisplayedDuration();
        }

        log.debug("Final displayed TBR duration: " + displayedDuration);
        if (displayedDuration != duration) {
            throw new CommandException("Failed to set TBR duration, requested: "
                    + duration + ", actual: " + displayedDuration);
        }

        // check again to ensure the displayed value hasn't change and scrolled past the desired
        // value due to due scrolling taking extremely long
        SystemClock.sleep(1000);
        scripter.verifyMenuIsDisplayed(MenuType.TBR_DURATION);
        long refreshedDisplayedTbrDuration = readDisplayedDuration();
        if (displayedDuration != refreshedDisplayedTbrDuration) {
            throw new CommandException("Failed to set TBR duration: " +
                    "duration changed after input stopped from "
                    + displayedDuration + " -> " + refreshedDisplayedTbrDuration);
        }
    }

    private void cancelTbrAndConfirmCancellationWarning() {
        // confirm entered TBR
        scripter.verifyMenuIsDisplayed(MenuType.TBR_SET);
        scripter.pressCheckKey();

        // A "TBR CANCELLED alert" is only raised by the pump when the remaining time is
        // greater than 60s (displayed as 0:01, the pump goes from there to finished.
        // We could read the remaining duration from MAIN_MENU, but by the time we're here,
        // the pump could have moved from 0:02 to 0:01, so instead, check if a "TBR CANCELLED" alert
        // is raised and if so dismiss it
        scripter.confirmAlert(PumpWarningCodes.TBR_CANCELLED, 2000);
    }

    private long readDisplayedDuration() {
        MenuTime duration = scripter.readBlinkingValue(MenuTime.class, MenuAttribute.RUNTIME);
        return duration.getHour() * 60 + duration.getMinute();
    }

    private long readDisplayedPercentage() {
        return scripter.readBlinkingValue(Double.class, MenuAttribute.BASAL_RATE).longValue();
    }

    @Override
    public boolean needsRunMode() {
        return true;
    }

    @Override
    public String toString() {
        return "SetTbrCommand{" +
                "percentage=" + percentage +
                ", duration=" + duration +
                '}';
    }
}
