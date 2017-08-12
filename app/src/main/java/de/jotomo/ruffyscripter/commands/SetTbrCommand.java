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

import de.jotomo.ruffyscripter.PumpState;
import de.jotomo.ruffyscripter.RuffyScripter;

import static org.monkey.d.ruffy.ruffy.driver.display.MenuType.MAIN_MENU;
import static org.monkey.d.ruffy.ruffy.driver.display.MenuType.TBR_DURATION;
import static org.monkey.d.ruffy.ruffy.driver.display.MenuType.TBR_MENU;
import static org.monkey.d.ruffy.ruffy.driver.display.MenuType.TBR_SET;
import static org.monkey.d.ruffy.ruffy.driver.display.MenuType.WARNING_OR_ERROR;

public class SetTbrCommand implements Command {
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
    public CommandResult execute(RuffyScripter scripter, PumpState initialPumpState) {

        try {
            log.debug("1. going from "+scripter.currentMenu+" to TBR_MENU");
            int retries = 5;
            while(!scripter.goToMainTypeScreen(TBR_MENU,3000))
            {
                retries--;
                if(retries==0)
                    throw new CommandException().message("not able to find TBR_MENU: stuck in "+scripter.currentMenu);
                SystemClock.sleep(500);
                if(scripter.currentMenu.getType()== TBR_MENU)
                    break;
            }

            if(scripter.currentMenu.getType()!=TBR_MENU)
                throw new CommandException().message("not able to find TBR_MENU: stuck in "+scripter.currentMenu);

            log.debug("2. entering "+scripter.currentMenu);
            retries = 5;
            while(!scripter.enterMenu(TBR_MENU,MenuType.TBR_SET, RuffyScripter.Key.CHECK,2000))
            {
                retries--;
                if(retries==0)
                    throw new CommandException().message("not able to find TBR_SET: stuck in "+scripter.currentMenu);
                SystemClock.sleep(500);
                if(scripter.currentMenu.getType()== TBR_SET)
                    break;
                if(scripter.currentMenu.getType()== TBR_DURATION)
                {
                    scripter.pressMenuKey();
                    scripter.waitForScreenUpdate(1000);
                }
            }

            log.debug("SetTbrCommand: 3. getting/setting basal in "+scripter.currentMenu);
            retries = 30;

            double currentPercentage = -100;
            while(currentPercentage!=percentage && retries>=0) {
                retries--;
                Object percentageObj = scripter.currentMenu.getAttribute(MenuAttribute.BASAL_RATE);

                if (percentageObj != null && (percentageObj instanceof Double)) {
                    currentPercentage = ((Double) percentageObj).doubleValue();

                    if (currentPercentage != percentage) {
                        int requestedPercentage = (int) percentage;
                        int actualPercentage = (int) currentPercentage;
                        int steps = (requestedPercentage - actualPercentage) / 10;
                        log.debug("Adjusting basal(" + requestedPercentage + "/" + actualPercentage + ") with " + steps + " steps and " + retries + " retries left");
                        scripter.step(steps, (steps < 0 ? RuffyScripter.Key.DOWN : RuffyScripter.Key.UP), 500);
                        scripter.waitForScreenUpdate(1000);
                    }

                }
                else
                    currentPercentage=-100;
                scripter.waitForScreenUpdate(1000);
            }
            if(currentPercentage<0 ||retries < 0)
                 throw new CommandException().message("unable to set basalrate");

            log.debug("4. checking basal in "+scripter.currentMenu);
            scripter.waitForScreenUpdate(1000);
            currentPercentage= -1000;
            retries=10;
            while(currentPercentage<0 && retries>=0) {
                retries--;
                Object percentageObj = scripter.currentMenu.getAttribute(MenuAttribute.BASAL_RATE);

                if (percentageObj != null && (percentageObj instanceof Double)) {
                    currentPercentage = ((Double) percentageObj).doubleValue();
                }
                else
                    scripter.waitForScreenUpdate(1000);
            }

            if(retries<0 ||currentPercentage!=percentage)
                throw new CommandException().message("Unable to set percentage. Desired: " + percentage + ", value displayed on pump: " + currentPercentage);

            if(currentPercentage!=100) {
                log.debug("5. change to TBR_DURATION from " + scripter.currentMenu);
                retries = 5;
                while (retries >=0 && !scripter.enterMenu(TBR_SET, MenuType.TBR_DURATION, RuffyScripter.Key.MENU, 2000)) {
                    retries--;
                    if (retries == 0)
                        throw new CommandException().message("not able to find TBR_SET: stuck in " + scripter.currentMenu);
                    SystemClock.sleep(500);
                    if (scripter.currentMenu.getType() == TBR_DURATION)
                        break;
                    if (scripter.currentMenu.getType() == TBR_SET) {
                        scripter.pressMenuKey();
                        scripter.waitForScreenUpdate(1000);
                    }
                }

                log.debug("6. getting/setting duration in " + scripter.currentMenu);
                retries = 30;

                double currentDuration = -100;
                while (currentDuration != duration && retries >= 0) {
                    retries--;
                    Object durationObj = scripter.currentMenu.getAttribute(MenuAttribute.RUNTIME);
                    log.debug("Requested time: " + duration + " actual time: " + durationObj);
                    if (durationObj != null && durationObj instanceof MenuTime) {
                        MenuTime time = (MenuTime) durationObj;
                        currentDuration = (time.getHour() * 60) + time.getMinute();
                        if (currentDuration != duration) {
                            int requestedDuration = (int) duration;
                            int actualDuration = (int) currentDuration;
                            int steps = (requestedDuration - actualDuration) / 15;
                            if (currentDuration + (steps * 15) < requestedDuration)
                                steps++;
                            else if (currentDuration + (steps * 15) > requestedDuration)
                                steps--;
                            log.debug("Adjusting duration(" + requestedDuration + "/" + actualDuration + ") with " + steps + " steps and " + retries + " retries left");
                            scripter.step(steps, (steps > 0 ? RuffyScripter.Key.UP : RuffyScripter.Key.DOWN), 500);
                            scripter.waitForScreenUpdate(1000);
                        }
                    }
                    scripter.waitForScreenUpdate(1000);
                }
                if (currentDuration < 0 || retries < 0)
                    throw new CommandException().message("unable to set duration, requested:" + duration + ", displayed on pump: " + currentDuration);

                log.debug("7. checking time in " + scripter.currentMenu);
                scripter.waitForScreenUpdate(1000);
                currentDuration = -1000;
                retries = 10;
                while (currentDuration < 0 && retries >= 0) {
                    retries--;
                    Object durationObj = scripter.currentMenu.getAttribute(MenuAttribute.RUNTIME);

                    if (durationObj != null && durationObj instanceof MenuTime) {
                        MenuTime time = (MenuTime) durationObj;
                        currentDuration = (time.getHour() * 60) + time.getMinute();
                    }
                    else
                        scripter.waitForScreenUpdate(1000);
                }
                if (retries < 0 || currentDuration != duration)
                    throw new CommandException().message("wrong time!");
            }

            log.debug("8. setting from " + scripter.currentMenu);
            retries=5;
            while(retries>= 0 && (scripter.currentMenu.getType()==TBR_DURATION ||scripter.currentMenu.getType()==TBR_SET))
            {
                retries--;
                scripter.pressCheckKey();
                scripter.waitForScreenUpdate(1000);
            }
            if(retries<0 || scripter.currentMenu.getType()==TBR_DURATION ||scripter.currentMenu.getType()==TBR_SET)
                throw new CommandException().message("failed setting basal!");
            retries=10;
            boolean canceledError = true;
            if(percentage==100)
                canceledError=false;
            while(retries>=0 && scripter.currentMenu.getType()!=MAIN_MENU )
            {
                if(percentage==100 && scripter.currentMenu.getType()==WARNING_OR_ERROR)
                {
                    scripter.pressCheckKey();
                    retries++;
                    canceledError = true;
                    scripter.waitForScreenUpdate(1000);
                }
                else {
                    retries--;
                    if (scripter.currentMenu.getType() == MAIN_MENU && canceledError)
                        break;
                }
            }

            log.debug("9. verifying the main menu display the TBR we just set/cancelled");
            if(retries<0 || scripter.currentMenu.getType()!=MAIN_MENU )
                throw new CommandException().message("failed going to main!");

            Object percentageObj = scripter.currentMenu.getAttribute(MenuAttribute.TBR);
            Object durationObj = scripter.currentMenu.getAttribute(MenuAttribute.RUNTIME);

            if(percentageObj == null || !(percentageObj instanceof Double))
                throw new CommandException().message("not percentage");

            if(((double)percentageObj)!=percentage)
                throw new CommandException().message("wrong percentage set!");

            if(percentage==100)
                return new CommandResult().success(true).enacted(true).message("TBR was cancelled");

            if(durationObj==null || !(durationObj instanceof MenuTime))
                throw new CommandException().message("not time");

            MenuTime t = (MenuTime) durationObj;
            if(t.getMinute()+(60*t.getHour())> duration || t.getMinute()+(60*t.getHour())< duration-5)
                throw new CommandException().message("wrong time set!");

            return new CommandResult().success(true).enacted(true).message(
                    String.format(Locale.US, "TBR set to %d%% for %d min", percentage, duration));
        } catch (Exception e) {
            log.error("got exception: ", e);
            return new CommandResult().success(false).message("failed to wait: " + e.getMessage());
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
