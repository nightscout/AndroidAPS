package de.jotomo.ruffyscripter.commands;

import android.os.SystemClock;

import org.monkey.d.ruffy.ruffy.driver.display.MenuAttribute;
import org.monkey.d.ruffy.ruffy.driver.display.MenuType;
import org.monkey.d.ruffy.ruffy.driver.display.menu.MenuTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;

import de.jotomo.ruffyscripter.RuffyScripter;

public class SetTbrCommand implements Command {
    private static final Logger log = LoggerFactory.getLogger(SetTbrCommand.class);

    private final long percentage;
    private final long duration;

    public SetTbrCommand(long percentage, long duration) {
        this.percentage = percentage;
        this.duration = duration;

        if (percentage % 10 != 0) {
            throw new IllegalArgumentException("TBR percentage must be set in 10% steps");
        }
        if (percentage < 0 || percentage > 500) {
            throw new IllegalArgumentException("TBR percentage must be within 0-500%");
        }

        if (percentage != 100) {
            if (duration % 15 != 0) {
                throw new IllegalArgumentException("TBR duration can only be set in 15 minute steps");
            }
            if (duration > 60 * 24) {
                throw new IllegalArgumentException("Maximum TBR duration is 24 hours");
            }
        }

        if (percentage == 0 && duration > 120) {
            throw new IllegalArgumentException("Max allowed zero-temp duration is 2h");
        }
    }

    @Override
    public CommandResult execute(RuffyScripter scripter) {
        try {
            scripter.verifyMenuIsDisplayed(MenuType.MAIN_MENU);
            enterTbrMenu(scripter);
            inputTbrPercentage(scripter);
            SystemClock.sleep(500);
            verifyDisplayedTbrPercentage(scripter);

            if (percentage == 100) {
                cancelTbrAndConfirmCancellationWarning(scripter);
            } else {
                // switch to TBR_DURATION menu by pressing menu key
                scripter.pressMenuKey();
                scripter.waitForMenuUpdate();
                scripter.verifyMenuIsDisplayed(MenuType.TBR_DURATION);

                inputTbrDuration(scripter);
                SystemClock.sleep(500);
                verifyDisplayedTbrDuration(scripter);

                // confirm TBR
                scripter.pressCheckKey();
                SystemClock.sleep(500);
            }

            scripter.verifyMenuIsDisplayed(MenuType.MAIN_MENU,
                    "Pump did not return to MAIN_MEU after setting TBR. " +
                    "Check pump manually, the TBR might not have been set/cancelled.");

            // check main menu shows the same values we just set
            if (percentage == 100) {
                verifyMainMenuShowsNoActiveTbr(scripter);
                return new CommandResult().success(true).enacted(true).message("TBR was cancelled");
            } else {
                verifyMainMenuShowsExpectedTbrActive(scripter);
                return new CommandResult().success(true).enacted(true).message(
                        String.format(Locale.US, "TBR set to %d%% for %d min", percentage, duration));
            }

        } catch (CommandException e) {
            return e.toCommandResult();
        }
    }

    private void enterTbrMenu(RuffyScripter scripter) {
        scripter.navigateToMenu(MenuType.TBR_MENU);
        scripter.pressCheckKey();
        scripter.waitForMenuUpdate();
        scripter.verifyMenuIsDisplayed(MenuType.TBR_SET);
    }

    private void inputTbrPercentage(RuffyScripter scripter) {
        long currentPercent = readDisplayedTbrPercentage(scripter);
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
            if (increasePercentage) scripter.pressUpKey();
            else scripter.pressDownKey();
            // TODO waitForMenuChange instead // or have key press method handle that??
            SystemClock.sleep(100);
            log.debug("Push #" + (i + 1));
        }
    }

    private void verifyDisplayedTbrPercentage(RuffyScripter scripter) {
        long displayedPercentage = readDisplayedTbrPercentage(scripter);
        if (displayedPercentage != this.percentage) {
            log.debug("Final displayed TBR percentage: " + displayedPercentage);
            throw new CommandException().message("Failed to set TBR percentage");
        }
    }

    private long readDisplayedTbrPercentage(RuffyScripter scripter) {
        Object percentageObj = scripter.currentMenu.getAttribute(MenuAttribute.BASAL_RATE);
        // this as a bit hacky, the display value is blinking, so we might catch that, so
        // keep trying till we get the Double we want
        while (!(percentageObj instanceof Double)) {
            scripter.waitForMenuUpdate();
            percentageObj = scripter.currentMenu.getAttribute(MenuAttribute.BASAL_RATE);
        }
        return ((Double) percentageObj).longValue();
    }

    private void inputTbrDuration(RuffyScripter scripter) {
        long currentDuration = readDisplayedTbrDuration(scripter);
        if (currentDuration % 15 != 0) {
            // The duration displayed is how long an active TBR will still run,
            // which might be something like 0:13, hence not in 15 minute steps.
            // Pressing up will go to the next higher 15 minute step.
            // Don't press down, from 0:13 it can't go down, so press up.
            // Pressing up from 23:59 works to go to 24:00.
            scripter.pressUpKey();
            scripter.waitForMenuUpdate();
            currentDuration = readDisplayedTbrDuration(scripter);
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
            if (increaseDuration) scripter.pressUpKey();
            else scripter.pressDownKey();
            // TODO waitForMenuChange instead // or have key press method handle that??
            SystemClock.sleep(100);
            log.debug("Push #" + (i + 1));
        }
    }

    private void verifyDisplayedTbrDuration(RuffyScripter scripter) {
        long displayedDuration = readDisplayedTbrDuration(scripter);
        if (displayedDuration != duration) {
            log.debug("Final displayed TBR duration: " + displayedDuration);
            throw new CommandException().message("Failed to set TBR duration");
        }
    }

    private long readDisplayedTbrDuration(RuffyScripter scripter) {
        Object durationObj = scripter.currentMenu.getAttribute(MenuAttribute.RUNTIME);
        // this as a bit hacky, the display value is blinking, so we might catch that, so
        // keep trying till we get the Double we want
        while (!(durationObj instanceof MenuTime)) {
            scripter.waitForMenuUpdate();
            durationObj = scripter.currentMenu.getAttribute(MenuAttribute.RUNTIME);
        }
        MenuTime duration = (MenuTime) durationObj;
        return duration.getHour() * 60 + duration.getMinute();
    }

    private void cancelTbrAndConfirmCancellationWarning(RuffyScripter scripter) {
        // TODO this will fail if no TBR is running; detect and just throw CE(success=true, msg="nothing to do")?

        // confirm entered TBR
        scripter.pressCheckKey();
        scripter.waitForMenuUpdate();

        // hm, waiting here (more) makes things worse, if we don't press the alert away quickly,
        // the pump exits BT mode ... so I guess we'll live with the checks below,
        // verifying we made it back to the main menu and the displayed TBR data
        // corresponds to what we set. Hope the timing is stable enough ...

/*                scripter.waitForMenuToBeLeft(MenuType.TBR_SET);
                if (scripter.currentMenu.getType() != MenuType.MAIN_MENU) {
                    // pump shortly enters the main menu before raising the alert
                    // TODO is this always entered?
                    log.debug("TBR cancelled, going over main menu");
                    scripter.waitForMenuToBeLeft(MenuType.MAIN_MENU);
                }
                if (scripter.currentMenu.getType() != MenuType.WARNING_OR_ERROR) {
                    throw new CommandException(false, null, "Expected WARNING_OR_ERROR menu was not shown when cancelling TBR");
                }*/
        // confirm "TBR cancelled alert"
        scripter.pressCheckKey();
        SystemClock.sleep(200);
        // dismiss "TBR cancelled alert"
        scripter.pressCheckKey();
        scripter.waitForMenuToBeLeft(MenuType.WARNING_OR_ERROR);
    }

    private void verifyMainMenuShowsNoActiveTbr(RuffyScripter scripter) {
        Double tbrPercentage = (Double) scripter.currentMenu.getAttribute(MenuAttribute.TBR);
        boolean runtimeDisplayed = scripter.currentMenu.attributes().contains(MenuAttribute.RUNTIME);
        if (tbrPercentage != 100 || runtimeDisplayed) {
            throw new CommandException().message("Cancelling TBR failed, TBR is still set according to MAIN_MENU");
        }
    }

    private void verifyMainMenuShowsExpectedTbrActive(RuffyScripter scripter) {
        // new TBR set; percentage and duration must be displayed ...
        if (!scripter.currentMenu.attributes().contains(MenuAttribute.TBR) ||
                !scripter.currentMenu.attributes().contains(MenuAttribute.TBR)) {
            throw new CommandException().message("Setting TBR failed, according to MAIN_MENU no TBR is active");
        }
        Double mmTbrPercentage = (Double) scripter.currentMenu.getAttribute(MenuAttribute.TBR);
        MenuTime mmTbrDuration = (MenuTime) scripter.currentMenu.getAttribute(MenuAttribute.RUNTIME);
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
        return "SetTbrCommand{" +
                "percentage=" + percentage +
                ", duration=" + duration +
                '}';
    }
}
