package de.jotomo.ruffyscripter.commands;

import android.os.SystemClock;

import com.j256.ormlite.stmt.query.In;

import org.monkey.d.ruffy.ruffy.driver.display.MenuAttribute;
import org.monkey.d.ruffy.ruffy.driver.display.MenuType;
import org.monkey.d.ruffy.ruffy.driver.display.menu.MenuTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

import de.jotomo.ruffyscripter.PumpCapabilities;
import de.jotomo.ruffyscripter.PumpState;
import de.jotomo.ruffyscripter.RuffyScripter;


public class DetermineCapabilitiesCommand implements Command {
    private static final Logger log = LoggerFactory.getLogger(DetermineCapabilitiesCommand.class);
    public static final int UP_STEPS = 75;

    @Override
    public List<String> validateArguments() {
        return Collections.emptyList();
    }

    @Override
    public CommandResult execute(RuffyScripter scripter, PumpState initialPumpState) {
        try {

            //read main menu 100% or TBR? Read remaining duration.
            long durationBefore =  readDisplayedTbrDurationMainMenu(scripter);
            long percentageBefore = readDisplayedTbrPercentageMainMenu(scripter);

            enterTbrMenu(scripter);
            long maxTbrPercentage = findMaxTbrPercentage(scripter);

            // TODO v2 this can probably be removed by now
            SystemClock.sleep(750);

            scripter.verifyMenuIsDisplayed(MenuType.MAIN_MENU,
                    "Pump did not return to MAIN_MEU after finding max tbr. " +
                            "Check pump manually, the TBR might be wrong.");


            //TODO: check if TBR is still the same or duration was less than 5 minutes
            long durationAfter =  readDisplayedTbrDurationMainMenu(scripter);
            long percentageAfter = readDisplayedTbrPercentageMainMenu(scripter);

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

    private void enterTbrMenu(RuffyScripter scripter) {
        scripter.verifyMenuIsDisplayed(MenuType.MAIN_MENU);
        scripter.navigateToMenu(MenuType.TBR_MENU);
        scripter.verifyMenuIsDisplayed(MenuType.TBR_MENU);
        scripter.pressCheckKey();
        scripter.waitForMenuUpdate();
        scripter.verifyMenuIsDisplayed(MenuType.TBR_SET);
    }

    private long findMaxTbrPercentage(RuffyScripter scripter) {
        scripter.verifyMenuIsDisplayed(MenuType.TBR_SET);
        long activeTempBasal = readDisplayedTbrPercentage(scripter);

        // pretend to increase the TBR to more than 500%
        log.debug("Pressing up " + UP_STEPS + " times to get to maximum");
        for (int i = 0; i < UP_STEPS; i++) {
            scripter.verifyMenuIsDisplayed(MenuType.TBR_SET);
            scripter.pressUpKey();
            SystemClock.sleep(100);
            log.debug("Push #" + (i + 1));
        }

        //read the displayed maximum value
        scripter.verifyMenuIsDisplayed(MenuType.TBR_SET);
        long maximumTempBasal = readDisplayedTbrPercentage(scripter);

        //reset the TBR in a controlled manner
        long percentageChange = maximumTempBasal - activeTempBasal;
        long percentageSteps = percentageChange / 10;
        log.debug("Pressing down " + percentageSteps + " times to get to previous value");
        for (int i = 0; i < percentageSteps; i++) {
            scripter.verifyMenuIsDisplayed(MenuType.TBR_SET);
            scripter.pressDownKey();
            SystemClock.sleep(100);
            log.debug("Push #" + (i + 1));
        }


        //do the rest if button-presses failed.
        scripter.verifyMenuIsDisplayed(MenuType.TBR_SET);
        int i= 0;
        while (readDisplayedTbrPercentage(scripter) > activeTempBasal && i < percentageSteps) {
            scripter.verifyMenuIsDisplayed(MenuType.TBR_SET);
            scripter.pressDownKey();
            SystemClock.sleep(100);
            log.debug("Push again as previous buttons failed: #" + i);
            i++;
        }

        //exit menu
        scripter.pressCheckKey();
        scripter.waitForMenuToBeLeft(MenuType.TBR_SET);
        return maximumTempBasal;
    }


    private long readDisplayedTbrPercentage(RuffyScripter scripter) {
        SystemClock.sleep(250);
        // TODO v2 add timeout? Currently the command execution timeout would trigger if exceeded
        Object percentageObj = scripter.currentMenu.getAttribute(MenuAttribute.BASAL_RATE);
        // this as a bit hacky, the display value is blinking, so we might catch that, so
        // keep trying till we get the Double we want
        while (!(percentageObj instanceof Double)) {
            scripter.waitForMenuUpdate();
            percentageObj = scripter.currentMenu.getAttribute(MenuAttribute.BASAL_RATE);
        }
        return ((Double) percentageObj).longValue();
    }

    private int readDisplayedTbrDurationMainMenu(RuffyScripter scripter) {
        scripter.verifyMenuIsDisplayed(MenuType.MAIN_MENU);
        if(scripter.currentMenu.attributes().contains(MenuAttribute.RUNTIME)){
            // TODO v2 add timeout? Currently the command execution timeout would trigger if exceeded
            Object durationObj = scripter.currentMenu.getAttribute(MenuAttribute.RUNTIME);
            MenuTime duration = (MenuTime) durationObj;
            return duration.getHour() * 60 + duration.getMinute();
        } else {
            return 0;
        }
    }

    private int readDisplayedTbrPercentageMainMenu(RuffyScripter scripter) {
        scripter.verifyMenuIsDisplayed(MenuType.MAIN_MENU);
        if(scripter.currentMenu.attributes().contains(MenuAttribute.TBR)){
            return (int)((Double) scripter.currentMenu.getAttribute(MenuAttribute.TBR)).doubleValue();
        } else {
            return 100;
        }
    }

    @Override
    public String toString() {
        return "DetermineCapabilitiesCommand{}";
    }
}
