package de.jotomo.ruffyscripter.commands;

import android.os.SystemClock;

import org.monkey.d.ruffy.ruffy.driver.display.MenuAttribute;
import org.monkey.d.ruffy.ruffy.driver.display.MenuType;
import org.monkey.d.ruffy.ruffy.driver.display.menu.MenuTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import de.jotomo.ruffy.spi.PumpWarningCodes;

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

        if (percentage == 0 && duration > 120) {
            violations.add("Max allowed zero-temp duration is 2h");
        }

        return violations;
    }

    @Override
    public String getReconnectAlarm() {
        return "TBR CANCELLED";
    }

    @Override
    public void execute() {
        boolean cancellingTbr = percentage == 100;

        enterTbrMenu();
        boolean increasingPercentage = inputTbrPercentage();
        verifyDisplayedTbrPercentage(increasingPercentage);

        if (cancellingTbr) {
            cancelTbrAndConfirmCancellationWarning();
        } else {
            // switch to TBR_DURATION menu by pressing menu key
            scripter.verifyMenuIsDisplayed(MenuType.TBR_SET);
            scripter.pressMenuKey();
            scripter.waitForMenuUpdate();
            scripter.verifyMenuIsDisplayed(MenuType.TBR_DURATION);

            boolean increasingDuration = inputTbrDuration();
            verifyDisplayedTbrDuration(increasingDuration);

            // confirm TBR
            scripter.pressCheckKey();
            scripter.waitForMenuToBeLeft(MenuType.TBR_DURATION);
        }

        scripter.verifyMenuIsDisplayed(MenuType.MAIN_MENU,
                "Pump did not return to MAIN_MEU after setting TBR. " +
                        "Check pump manually, the TBR might not have been set/cancelled.");

        // check main menu shows the same values we just set
        if (cancellingTbr) {
            verifyMainMenuShowsNoActiveTbr();
            result.success(true).enacted(true).message("TBR was cancelled");
        } else {
            verifyMainMenuShowsExpectedTbrActive();
            result.success(true).enacted(true)
                    .message(String.format(Locale.US, "TBR set to %d%% for %d min", percentage, duration));
        }
    }

    private void enterTbrMenu() {
        scripter.verifyMenuIsDisplayed(MenuType.MAIN_MENU);
        scripter.navigateToMenu(MenuType.TBR_MENU);
        scripter.verifyMenuIsDisplayed(MenuType.TBR_MENU);
        scripter.pressCheckKey();
        scripter.waitForMenuUpdate();
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
            log.debug("Push #" + (i + 1));
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

    private void verifyMainMenuShowsNoActiveTbr() {
        scripter.verifyMenuIsDisplayed(MenuType.MAIN_MENU);
        Double tbrPercentage = (Double) scripter.getCurrentMenu().getAttribute(MenuAttribute.TBR);
        boolean runtimeDisplayed = scripter.getCurrentMenu().attributes().contains(MenuAttribute.RUNTIME);
        if (tbrPercentage != 100 || runtimeDisplayed) {
            throw new CommandException("Cancelling TBR failed, TBR is still set according to MAIN_MENU");
        }
    }

    private void verifyMainMenuShowsExpectedTbrActive() {
        scripter.verifyMenuIsDisplayed(MenuType.MAIN_MENU);
        // new TBR set; percentage and duration must be displayed ...
        if (!scripter.getCurrentMenu().attributes().contains(MenuAttribute.TBR) ||
                !scripter.getCurrentMenu().attributes().contains(MenuAttribute.RUNTIME)) {
            throw new CommandException("Setting TBR failed, according to MAIN_MENU no TBR is active");
        }
        Double mmTbrPercentage = (Double) scripter.getCurrentMenu().getAttribute(MenuAttribute.TBR);
        MenuTime mmTbrDuration = (MenuTime) scripter.getCurrentMenu().getAttribute(MenuAttribute.RUNTIME);
        // ... and be the same as what we set
        // note that displayed duration might have already counted down, e.g. from 30 minutes to
        // 29 minutes and 59 seconds, so that 29 minutes are displayed
        int mmTbrDurationInMinutes = mmTbrDuration.getHour() * 60 + mmTbrDuration.getMinute();
        if (mmTbrPercentage != percentage || (mmTbrDurationInMinutes != duration && mmTbrDurationInMinutes + 1 != duration)) {
            throw new CommandException("Setting TBR failed, TBR in MAIN_MENU differs from expected");
        }
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
