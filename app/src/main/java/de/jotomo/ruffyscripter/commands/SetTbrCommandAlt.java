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

public class SetTbrCommandAlt extends BaseCommand {
    private static final Logger log = LoggerFactory.getLogger(SetTbrCommand.class);

    private final long percentage;
    private final long duration;

    public SetTbrCommandAlt(long percentage, long duration) {
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
    public CommandResult execute() {
        try {
            enterTbrMenu();
            inputTbrPercentage();
            verifyDisplayedTbrPercentage();

            if (percentage == 100) {
                cancelTbrAndConfirmCancellationWarning();
            } else {
                // switch to TBR_DURATION menu by pressing menu key
                scripter.verifyMenuIsDisplayed(MenuType.TBR_SET);
                scripter.pressMenuKey();
                scripter.waitForMenuUpdate();
                scripter.verifyMenuIsDisplayed(MenuType.TBR_DURATION);

                inputTbrDuration();
                verifyDisplayedTbrDuration();

                // confirm TBR
                scripter.pressCheckKey();
                scripter.waitForMenuToBeLeft(MenuType.TBR_DURATION);
            }

            scripter.verifyMenuIsDisplayed(MenuType.MAIN_MENU,
                    "Pump did not return to MAIN_MEU after setting TBR. " +
                            "Check pump manually, the TBR might not have been set/cancelled.");

            // check main menu shows the same values we just set
            if (percentage == 100) {
                verifyMainMenuShowsNoActiveTbr();
                return new CommandResult().success(true).enacted(true).message("TBR was cancelled");
            } else {
                verifyMainMenuShowsExpectedTbrActive();
                return new CommandResult().success(true).enacted(true).message(
                        String.format(Locale.US, "TBR set to %d%% for %d min", percentage, duration));
            }

        } catch (CommandException e) {
            return e.toCommandResult();
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

    private void inputTbrPercentage() {
        scripter.verifyMenuIsDisplayed(MenuType.TBR_SET);
        long currentPercent = scripter.readBlinkingValue(Double.class, MenuAttribute.BASAL_RATE).longValue();
        log.debug("Current TBR %: " + currentPercent);
        long percentageChange = percentage - currentPercent;
        long percentageSteps = percentageChange / 10;
        boolean increasePercentage = true;
        if (percentageSteps < 0) {
            increasePercentage = false;
            percentageSteps = Math.abs(percentageSteps);
        }
        log.debug("Pressing " + (increasePercentage ? "up" : "down") + " " + percentageSteps + " times");
        for (int i = 0; i < percentageSteps; i++) {
            scripter.verifyMenuIsDisplayed(MenuType.TBR_SET);
            log.debug("Push #" + (i + 1));
            if (increasePercentage) scripter.pressUpKey();
            else scripter.pressDownKey();
            SystemClock.sleep(100);
        }
        // Give the pump time to finish any scrolling that might still be going on, can take
        // up to 1100ms. Plus some extra time to be sure
        SystemClock.sleep(2000);
    }

    private void verifyDisplayedTbrPercentage() {
        scripter.verifyMenuIsDisplayed(MenuType.TBR_SET);
        long displayedPercentage = scripter.readBlinkingValue(Double.class, MenuAttribute.BASAL_RATE).longValue();
        if (displayedPercentage != percentage) {
            log.debug("Final displayed TBR percentage: " + displayedPercentage);
            throw new CommandException().message("Failed to set TBR percentage, requested: " + percentage + ", actual: " + displayedPercentage);
        }

        // check again to ensure the displayed value hasn't change due to due scrolling taking extremely long
        SystemClock.sleep(2000);
        scripter.verifyMenuIsDisplayed(MenuType.TBR_SET);
        long refreshedDisplayedTbrPecentage = scripter.readBlinkingValue(Double.class, MenuAttribute.BASAL_RATE).longValue();
        if (displayedPercentage != refreshedDisplayedTbrPecentage) {
            throw new CommandException().message("Failed to set TBR percentage: " +
                    "percentage changed after input stopped from "
                    + displayedPercentage + " -> " + refreshedDisplayedTbrPecentage);
        }
    }

    private void inputTbrDuration() {
        scripter.verifyMenuIsDisplayed(MenuType.TBR_DURATION);
        long currentDuration = scripter.readDisplayedDuration();
        if (currentDuration % 15 != 0) {
            // The duration displayed is how long an active TBR will still run,
            // which might be something like 0:13, hence not in 15 minute steps.
            // Pressing up will go to the next higher 15 minute step.
            // Don't press down, from 0:13 it can't go down, so press up.
            // Pressing up from 23:59 works to go to 24:00.
            scripter.verifyMenuIsDisplayed(MenuType.TBR_DURATION);
            scripter.pressUpKey();
            scripter.waitForMenuUpdate();
            currentDuration = scripter.readDisplayedDuration();
        }
        log.debug("Current TBR duration: " + currentDuration);
        long durationChange = duration - currentDuration;
        long durationSteps = durationChange / 15;
        boolean increaseDuration = true;
        if (durationSteps < 0) {
            increaseDuration = false;
            durationSteps = Math.abs(durationSteps);
        }
        log.debug("Pressing " + (increaseDuration ? "up" : "down") + " " + durationSteps + " times");
        for (int i = 0; i < durationSteps; i++) {
            scripter.verifyMenuIsDisplayed(MenuType.TBR_DURATION);
            if (increaseDuration) scripter.pressUpKey();
            else scripter.pressDownKey();
            SystemClock.sleep(100);
            log.debug("Push #" + (i + 1));
        }
        // Give the pump time to finish any scrolling that might still be going on, can take
        // up to 1100ms. Plus some extra time to be sure
        SystemClock.sleep(2000);
    }

    private void verifyDisplayedTbrDuration() {
        scripter.verifyMenuIsDisplayed(MenuType.TBR_DURATION);
        long displayedDuration = scripter.readDisplayedDuration();
        if (displayedDuration != duration) {
            log.debug("Final displayed TBR duration: " + displayedDuration);
            throw new CommandException().message("Failed to set TBR duration");
        }

        // check again to ensure the displayed value hasn't change due to due scrolling taking extremely long
        SystemClock.sleep(2000);
        scripter.verifyMenuIsDisplayed(MenuType.TBR_DURATION);
        long refreshedDisplayedTbrDuration = scripter.readDisplayedDuration();
        if (displayedDuration != refreshedDisplayedTbrDuration) {
            throw new CommandException().message("Failed to set TBR duration: " +
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
        // the pumup could have moved from 0:02 to 0:01, so instead, check if a "TBR CANCELLED" alert
        // is raised and if so dismiss it
        long inFiveSeconds = System.currentTimeMillis() + 5 * 1000;
        boolean alertProcessed = false;
        while (System.currentTimeMillis() < inFiveSeconds && !alertProcessed) {
            if (scripter.getCurrentMenu().getType() == MenuType.WARNING_OR_ERROR) {
                // Check the raised alarm is TBR CANCELLED, so we're not accidentally cancelling
                // a different alarm that might be raised at the same time.
                // Note that the message is permanently displayed, while the error code is blinking.
                // A wait till the error code can be read results in the code hanging, despite
                // menu updates coming in, so just check the message.
                // TODO v2 this only works when the pump's language is English
                String errorMsg = (String) scripter.getCurrentMenu().getAttribute(MenuAttribute.MESSAGE);
                if (!errorMsg.equals("TBR CANCELLED")) {
                    throw new CommandException().success(false).enacted(false)
                            .message("An alert other than the expected TBR CANCELLED was raised by the pump: "
                                    + errorMsg + ". Please check the pump.");
                }
                // confirm "TBR CANCELLED" alert
                scripter.verifyMenuIsDisplayed(MenuType.WARNING_OR_ERROR);
                scripter.pressCheckKey();
                // dismiss "TBR CANCELLED" alert
                scripter.verifyMenuIsDisplayed(MenuType.WARNING_OR_ERROR);
                scripter.pressCheckKey();
                scripter.waitForMenuToBeLeft(MenuType.WARNING_OR_ERROR);
                alertProcessed = true;
            }
            SystemClock.sleep(10);
        }
    }

    private void verifyMainMenuShowsNoActiveTbr() {
        scripter.verifyMenuIsDisplayed(MenuType.MAIN_MENU);
        Double tbrPercentage = (Double) scripter.getCurrentMenu().getAttribute(MenuAttribute.TBR);
        boolean runtimeDisplayed = scripter.getCurrentMenu().attributes().contains(MenuAttribute.RUNTIME);
        if (tbrPercentage != 100 || runtimeDisplayed) {
            throw new CommandException().message("Cancelling TBR failed, TBR is still set according to MAIN_MENU");
        }
    }

    private void verifyMainMenuShowsExpectedTbrActive() {
        scripter.verifyMenuIsDisplayed(MenuType.MAIN_MENU);
        // new TBR set; percentage and duration must be displayed ...
        if (!scripter.getCurrentMenu().attributes().contains(MenuAttribute.TBR) ||
                !scripter.getCurrentMenu().attributes().contains(MenuAttribute.RUNTIME)) {
            throw new CommandException().message("Setting TBR failed, according to MAIN_MENU no TBR is active");
        }
        Double mmTbrPercentage = (Double) scripter.getCurrentMenu().getAttribute(MenuAttribute.TBR);
        MenuTime mmTbrDuration = (MenuTime) scripter.getCurrentMenu().getAttribute(MenuAttribute.RUNTIME);
        // ... and be the same as what we set
        // note that displayed duration might have already counted down, e.g. from 30 minutes to
        // 29 minutes and 59 seconds, so that 29 minutes are displayed
        int mmTbrDurationInMinutes = mmTbrDuration.getHour() * 60 + mmTbrDuration.getMinute();
        if (mmTbrPercentage != percentage || (mmTbrDurationInMinutes != duration && mmTbrDurationInMinutes + 1 != duration)) {
            throw new CommandException().message("Setting TBR failed, TBR in MAIN_MENU differs from expected");
        }
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{" +
                "percentage=" + percentage +
                ", duration=" + duration +
                '}';
    }
}
