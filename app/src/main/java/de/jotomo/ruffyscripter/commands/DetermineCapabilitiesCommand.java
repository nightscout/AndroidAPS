package de.jotomo.ruffyscripter.commands;

import android.os.SystemClock;

import org.monkey.d.ruffy.ruffy.driver.display.MenuAttribute;
import org.monkey.d.ruffy.ruffy.driver.display.MenuType;
import org.monkey.d.ruffy.ruffy.driver.display.menu.MenuTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;

import de.jotomo.ruffyscripter.PumpCapabilities;


public class DetermineCapabilitiesCommand extends BaseCommand {
    private static final Logger log = LoggerFactory.getLogger(DetermineCapabilitiesCommand.class);
    public static final int UP_STEPS = 75;
    public static final int RETRIES = 5;

    @Override
    public List<String> validateArguments() {
        return Collections.emptyList();
    }

    @Override
    public CommandResult execute() {
        try {

            //read main menu 100% or TBR? Read remaining duration.
            long durationBefore =  readDisplayedTbrDurationMainMenu();
            long percentageBefore = readDisplayedTbrPercentageMainMenu();

            enterTbrMenu();
            long maxTbrPercentage = findMaxTbrPercentage();

            // TODO v2 this can probably be removed by now
            SystemClock.sleep(750);

            scripter.verifyMenuIsDisplayed(MenuType.MAIN_MENU,
                    "Pump did not return to MAIN_MEU after finding max tbr. " +
                            "Check pump manually, the TBR might be wrong.");


            //TODO: check if TBR is still the same or duration was less than 5 minutes
            long durationAfter =  readDisplayedTbrDurationMainMenu();
            long percentageAfter = readDisplayedTbrPercentageMainMenu();

            if(Math.abs(durationBefore-durationAfter) > 5){
                throw new CommandException().message("Duration jump during DetermineCapabilities");
            }
            if(percentageAfter != percentageBefore){
                if(durationBefore<5 && percentageAfter == 100){
                    log.debug("(percentageBefore != percentageAfter) - ignoring as tbr is now 100% and had a very short duration left");
                }
                throw new CommandException().message("TBR changed while determining maxTBR.");
            }

            //TODO return Result
            return new CommandResult().success(true).enacted(false).message("Capablities: {maxTbrPercentage = " + maxTbrPercentage + ", success=" + "success }").capabilities((new PumpCapabilities()).maxTempPercent(maxTbrPercentage));
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

    private long findMaxTbrPercentage() {
        scripter.verifyMenuIsDisplayed(MenuType.TBR_SET);
        long activeTempBasal = readDisplayedTbrPercentage();

        // pretend to increase the TBR to more than 500%
        log.debug("Pressing up " + UP_STEPS + " times to get to maximum");
        for (int i = 0; i < UP_STEPS; i++) {
            scripter.verifyMenuIsDisplayed(MenuType.TBR_SET);
            scripter.pressUpKey();
            SystemClock.sleep(200);
            log.debug("Push #" + (i + 1));
        }

        //read the displayed maximum value
        scripter.verifyMenuIsDisplayed(MenuType.TBR_SET);
        long maximumTempBasal = readDisplayedTbrPercentage();

        //reset the TBR in a controlled manner
        long percentageChange = maximumTempBasal - activeTempBasal;
        long percentageSteps = percentageChange / 10;

        int retries= 0;
        while (percentageSteps > 0 && retries < RETRIES) {
            log.debug("Pressing down " + percentageSteps + " times to get to previous value. Retry " + retries);
            for (int i = 0; i < percentageSteps; i++) {
                scripter.verifyMenuIsDisplayed(MenuType.TBR_SET);
                scripter.pressDownKey();
                SystemClock.sleep(200);
                log.debug("Push #" + (i + 1));
            }
            //do the rest if button-presses failed.
            scripter.verifyMenuIsDisplayed(MenuType.TBR_SET);
            long currentPercentage = readDisplayedTbrPercentage();
            percentageChange = currentPercentage - activeTempBasal;
            percentageSteps = percentageChange / 10;
            retries++;
        }


        //exit menu
        scripter.pressCheckKey();
        scripter.waitForMenuToBeLeft(MenuType.TBR_SET);
        return maximumTempBasal;
    }


    private long readDisplayedTbrPercentage() {
        SystemClock.sleep(1000);
        // TODO v2 add timeout? Currently the command execution timeout would trigger if exceeded
        Object percentageObj = scripter.getCurrentMenu().getAttribute(MenuAttribute.BASAL_RATE);
        // this as a bit hacky, the display value is blinking, so we might catch that, so
        // keep trying till we get the Double we want
        while (!(percentageObj instanceof Double)) {
            scripter.waitForMenuUpdate();
            percentageObj = scripter.getCurrentMenu().getAttribute(MenuAttribute.BASAL_RATE);
        }
        return ((Double) percentageObj).longValue();
    }

    private int readDisplayedTbrDurationMainMenu() {
        scripter.verifyMenuIsDisplayed(MenuType.MAIN_MENU);
        if(scripter.getCurrentMenu().attributes().contains(MenuAttribute.RUNTIME)){
            // TODO v2 add timeout? Currently the command execution timeout would trigger if exceeded
            Object durationObj = scripter.getCurrentMenu().getAttribute(MenuAttribute.RUNTIME);
            MenuTime duration = (MenuTime) durationObj;
            return duration.getHour() * 60 + duration.getMinute();
        } else {
            return 0;
        }
    }

    private int readDisplayedTbrPercentageMainMenu() {
        scripter.verifyMenuIsDisplayed(MenuType.MAIN_MENU);
        if(scripter.getCurrentMenu().attributes().contains(MenuAttribute.TBR)){
            return (int)((Double) scripter.getCurrentMenu().getAttribute(MenuAttribute.TBR)).doubleValue();
        } else {
            return 100;
        }
    }

    @Override
    public String toString() {
        return "DetermineCapabilitiesCommand{}";
    }
}
