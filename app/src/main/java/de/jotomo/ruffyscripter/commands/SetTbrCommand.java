package de.jotomo.ruffyscripter.commands;

import android.os.SystemClock;
import android.util.Log;

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
            enterTbrMenu(scripter);
            inputTbrPercentage(scripter);
            verifyDisplayedTbrPercentage(scripter);

            if (percentage == 100) {
                cancelTbrAndConfirmCancellationWarning(scripter);
            } else {
                // switch to TBR_DURATION menu by pressing menu key
                scripter.verifyMenuIsDisplayed(MenuType.TBR_SET);
                scripter.pressMenuKey();
                scripter.waitForMenuUpdate();
                scripter.verifyMenuIsDisplayed(MenuType.TBR_DURATION);

                inputTbrDuration(scripter);
                verifyDisplayedTbrDuration(scripter);

                // confirm TBR
                scripter.pressCheckKey();
                scripter.waitForMenuToBeLeft(MenuType.TBR_DURATION);
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
<<<<<<< HEAD

    private void enterTbrMenu(RuffyScripter scripter) {
        scripter.verifyMenuIsDisplayed(MenuType.MAIN_MENU);
        scripter.navigateToMenu(MenuType.TBR_MENU);
        scripter.verifyMenuIsDisplayed(MenuType.TBR_MENU);
        scripter.pressCheckKey();
        scripter.waitForMenuUpdate();
        scripter.verifyMenuIsDisplayed(MenuType.TBR_SET);
    }

    private void inputTbrPercentage(RuffyScripter scripter) {
        scripter.verifyMenuIsDisplayed(MenuType.TBR_SET);
        long currentPercent = readDisplayedTbrPercentage(scripter);
        log.debug("Current TBR %: " + currentPercent);
        long percentageChange = percentage - currentPercent;
        long percentageSteps = percentageChange / 10;
        boolean increasePercentage = true;
        if (percentageSteps < 0) {
            increasePercentage = false;
            percentageSteps = Math.abs(percentageSteps);
=======
    private MenuType lastMenu;
    private int retries = 0;
    private void tick()
    {
        Log.v("SetTbrCommand:tick",state+": "+scripter.currentMenu);
        switch (state)
        {
            case BEFORE:
                if(scripter.currentMenu!=null && scripter.currentMenu.getType()==MenuType.MAIN_MENU)
                {
                    updateState(MAIN,120);
                    lastMenu = MenuType.MAIN_MENU;
                    log.debug("found MAIN_MENU -> state:MAIN");
                    retries=3;
                }
                break;
            case MAIN:
                if(retries>0)
                    if(scripter.goToMainMenuScreen(MenuType.TBR_MENU,30000))
                    {
                        updateState(TBR,30);
                        retries=0;
                    }
                    else
                        retries--;
                else
                    updateState(ERROR,30);
                break;
            case TBR:
                if(scripter.enterMenu(MenuType.TBR_MENU,MenuType.TBR_SET, RuffyScripter.Key.CHECK,20000))
                {
                    updateState(SET_TBR,60);
                    retries = 10;
                }
                else
                    updateState(ERROR,30);
                break;
            case SET_TBR:
                if(scripter.currentMenu!=null && scripter.currentMenu.getType()==MenuType.TBR_SET)
                {
                    Object percentageObj = scripter.currentMenu.getAttribute(MenuAttribute.BASAL_RATE);
                    Log.v("SetTbrCommand:tick",state+": requested basal: "+percentage+" actual percetage: "+percentageObj);
                    if(percentageObj != null && percentageObj instanceof Double)
                    {
                        double currentPercentage = ((Double) percentageObj).doubleValue();

                        if(currentPercentage!=percentage)
                        {
                            if(retries>0) {
                                retries--;
                                int requestedPercentage = (int) percentage;
                                int actualPercentage = (int) currentPercentage;
                                int steps = (requestedPercentage - actualPercentage) / 10;
                                Log.v("SetTbrCommand:tick",state+": adjusting basal("+requestedPercentage+"/"+actualPercentage+") with "+steps+" steps and "+retries+" retries left");
                                scripter.step(steps,(steps<0? RuffyScripter.Key.DOWN: RuffyScripter.Key.UP), 500);
                                scripter.waitScreen(1000);
                            }
                            else
                            {
                                Log.v("SetTbrCommand:tick",state+": no retries left for adjusting basals");
                                updateState(ERROR,30);
                            }
                        }
                        else
                        {
                            if(percentage==100)
                            {
                                Log.v("SetTbrCommand:tick",state+": percentage 100 going to SET");
                                scripter.pressCheckKey();
                                updateState(SET,30);
                            }
                            else {
                                Log.v("SetTbrCommand:tick",state+": basal set, going to TIME");
                                updateState(TIME, 30);
                            }
                            retries=10;
                        }
                    }
                }
                else
                {
                    Log.v("SetTbrCommand:tick",state+": not in correct menu: "+scripter.currentMenu+" expected "+MenuType.TBR_SET);
                    updateState(ERROR,30);
                }
                break;
            case TIME:
                if((scripter.currentMenu!=null && scripter.currentMenu.getType()==MenuType.TBR_DURATION) || scripter.enterMenu(MenuType.TBR_SET,MenuType.TBR_DURATION, RuffyScripter.Key.MENU,20000))
                {
                    Log.v("SetTbrCommand:tick",state+": going to SET_TIME");
                    updateState(SET_TIME,60);
                    retries = 10;
                }
                else if(retries==0)
                {
                    Log.v("SetTbrCommand:tick",state+": no retries left");
                    updateState(ERROR,30);
                }
                else
                {
                    Log.v("SetTbrCommand:tick",state+": next retry "+retries+"left");
                    retries--;
                }
                break;
            case SET_TIME:
                if(scripter.currentMenu!=null && scripter.currentMenu.getType()==MenuType.TBR_DURATION)
                {
                    Object durationObj = scripter.currentMenu.getAttribute(MenuAttribute.RUNTIME);
                    Log.v("SetTbrCommand:tick",state+": requested time: "+duration+" actual time: "+durationObj);
                    if(durationObj != null && durationObj instanceof MenuTime)
                    {
                        MenuTime time = (MenuTime) durationObj;
                        double currentDuration = (time.getHour()*60)+time.getMinute();
                        if(currentDuration!=duration)
                        {
                            if(retries>0) {
                                retries--;
                                int requestedDuration = (int) duration;
                                int actualDuration = (int) currentDuration;
                                int steps = (requestedDuration - actualDuration)/15;
                                if(currentDuration+(steps*15)<requestedDuration)
                                    steps++;
                                else if(currentDuration+(steps*15)>requestedDuration)
                                    steps--;
                                Log.v("SetTbrCommand:tick",state+": adjusting duration("+requestedDuration+"/"+actualDuration+") with "+steps+" steps and "+retries+" retries left");
                                scripter.step(steps,(steps>0? RuffyScripter.Key.UP: RuffyScripter.Key.DOWN), 500);
                                scripter.waitScreen(1000);
                            }
                            else
                            {
                                Log.v("SetTbrCommand:tick",state+": no retry left");
                                updateState(ERROR,30);
                            }
                        }
                        else {
                            Log.v("SetTbrCommand:tick",state+": setting");
                            scripter.pressCheckKey();
                            scripter.waitScreen(500);
                            if(scripter.currentMenu.getType()==MenuType.MAIN_MENU || scripter.currentMenu.getType()==MenuType.WARNING_OR_ERROR)
                            {
                                Log.v("SetTbrCommand:tick",state+": set and going to SET");
                                updateState(SET, 30);
                            }
                            else if(retries>0)
                            {
                                Log.v("SetTbrCommand:tick",state+": not set, wait");
                                retries--;
                            }
                            else
                            {
                                Log.v("SetTbrCommand:tick",state+": no retry left");
                                updateState(ERROR,30);
                            }
                        }
                    }
                }
                else if(scripter.currentMenu.getType()==MenuType.MAIN_MENU || scripter.currentMenu.getType()==MenuType.WARNING_OR_ERROR)
                {
                    Log.v("SetTbrCommand:tick",state+": set and going to SET");
                    updateState(SET, 30);
                }
                else
                {
                    Log.v("SetTbrCommand:tick",state+": not in "+ MenuType.TBR_DURATION+" but in "+scripter.currentMenu);
                    updateState(ERROR,60);
                }
                break;
            case SET:
                if(scripter.currentMenu!=null && scripter.currentMenu.getType()==MenuType.WARNING_OR_ERROR)
                {
                    Log.v("SetTbrCommand:tick",state+": in ERROR_WARNING acking");
                    lastMenu = scripter.currentMenu.getType();
                    scripter.pressCheckKey();
                    updateState(SET, 30);
                }
                else if(scripter.currentMenu!=null && scripter.currentMenu.getType()==MenuType.MAIN_MENU) {
                    Object setPercentage = scripter.currentMenu.getAttribute(MenuAttribute.TBR);
                    Object setDuration = scripter.currentMenu.getAttribute(MenuAttribute.RUNTIME);
                    Log.v("SetTbrCommand:tick",state+": got percentage: "+setPercentage+" requestes: "+percentage+" duration: "+setDuration+" request: "+duration);

                    if (setPercentage== null ||setDuration==null) {
                        if(percentage!=100)
                        {
                            Log.v("SetTbrCommand:tick",state+": fail because nothign set");
                            updateState(ERROR,10);
                        }
                        else
                        {
                            Log.v("SetTbrCommand:tick",state+": finished");
                            if(lastMenu==MenuType.WARNING_OR_ERROR)
                                updateState(AFTER,10);
                            else
                                updateState(SET,10);
                        }
                    }
                    else {
                        double mmTbrPercentage = (Double) setPercentage;
                        MenuTime mmTbrDuration = (MenuTime) setDuration;
                        // ... and be the same as what we set
                        // note that displayed duration might have already counted down, e.g. from 30 minutes to
                        // 29 minutes and 59 seconds, so that 29 minutes are displayed
                        int mmTbrDurationInMinutes = mmTbrDuration.getHour() * 60 + mmTbrDuration.getMinute();
                        if (mmTbrPercentage == percentage && mmTbrDurationInMinutes <= duration) {
                            Log.v("SetTbrCommand:tick",state+": finished, correctly set");
                            updateState(AFTER, 10);
                        } else {
                            Log.v("SetTbrCommand:tick",state+": failed");
                            updateState(ERROR, 10);
                        }
                    }
                }
                break;
            case ERROR:
            case AFTER:
                synchronized(SetTbrCommand.this) {
                    SetTbrCommand.this.notify();
                }
                break;
>>>>>>> add Logs to setTbrCommand
        }
        log.debug("Pressing " + (increasePercentage ? "up" : "down") + " " + percentageSteps + " times");
        for (int i = 0; i < percentageSteps; i++) {
            scripter.verifyMenuIsDisplayed(MenuType.TBR_SET);
            if (increasePercentage) scripter.pressUpKey();
            else scripter.pressDownKey();
            SystemClock.sleep(100);
            log.debug("Push #" + (i + 1));
        }
        // Give the pump time to finish any scrolling that might still be going on, can take
        // up to 1100ms. Plus some extra time to be sure
        SystemClock.sleep(2000);
    }

    private void verifyDisplayedTbrPercentage(RuffyScripter scripter) {
        scripter.verifyMenuIsDisplayed(MenuType.TBR_SET);
        long displayedPercentage = readDisplayedTbrPercentage(scripter);
        if (displayedPercentage != percentage) {
            log.debug("Final displayed TBR percentage: " + displayedPercentage);
            throw new CommandException().message("Failed to set TBR percentage");
        }

        // check again to ensure the displayed value hasn't change due to due scrolling taking extremely long
        SystemClock.sleep(2000);
        long refreshedDisplayedTbrPecentage = readDisplayedTbrPercentage(scripter);
        if (displayedPercentage != refreshedDisplayedTbrPecentage) {
            throw new CommandException().message("Failed to set TBR percentage: " +
                    "percentage changed after input stopped from "
                    + displayedPercentage + " -> " + refreshedDisplayedTbrPecentage);
        }
    }

    private long readDisplayedTbrPercentage(RuffyScripter scripter) {
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

    private void inputTbrDuration(RuffyScripter scripter) {
        scripter.verifyMenuIsDisplayed(MenuType.TBR_DURATION);
        long currentDuration = readDisplayedTbrDuration(scripter);
        if (currentDuration % 15 != 0) {
            // The duration displayed is how long an active TBR will still run,
            // which might be something like 0:13, hence not in 15 minute steps.
            // Pressing up will go to the next higher 15 minute step.
            // Don't press down, from 0:13 it can't go down, so press up.
            // Pressing up from 23:59 works to go to 24:00.
            scripter.verifyMenuIsDisplayed(MenuType.TBR_DURATION);
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

    private void verifyDisplayedTbrDuration(RuffyScripter scripter) {
        scripter.verifyMenuIsDisplayed(MenuType.TBR_DURATION);
        long displayedDuration = readDisplayedTbrDuration(scripter);
        if (displayedDuration != duration) {
            log.debug("Final displayed TBR duration: " + displayedDuration);
            throw new CommandException().message("Failed to set TBR duration");
        }

        // check again to ensure the displayed value hasn't change due to due scrolling taking extremely long
        SystemClock.sleep(2000);
        long refreshedDisplayedTbrDuration = readDisplayedTbrDuration(scripter);
        if (displayedDuration != refreshedDisplayedTbrDuration) {
            throw new CommandException().message("Failed to set TBR duration: " +
                    "duration changed after input stopped from "
                    + displayedDuration + " -> " + refreshedDisplayedTbrDuration);
        }
    }

    private long readDisplayedTbrDuration(RuffyScripter scripter) {
        // TODO v2 add timeout? Currently the command execution timeout would trigger if exceeded
        scripter.verifyMenuIsDisplayed(MenuType.TBR_DURATION);
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
            if (scripter.currentMenu.getType() == MenuType.WARNING_OR_ERROR) {
                // Check the raised alarm is TBR CANCELLED, so we're not accidentally cancelling
                // a different alarm that might be raised at the same time.
                // Note that the message is permanently displayed, while the error code is blinking.
                // A wait till the error code can be read results in the code hanging, despite
                // menu updates coming in, so just check the message.
                // TODO v2 this only works when the pump's language is English
                String errorMsg = (String) scripter.currentMenu.getAttribute(MenuAttribute.MESSAGE);
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

    private void verifyMainMenuShowsNoActiveTbr(RuffyScripter scripter) {
        scripter.verifyMenuIsDisplayed(MenuType.MAIN_MENU);
        Double tbrPercentage = (Double) scripter.currentMenu.getAttribute(MenuAttribute.TBR);
        boolean runtimeDisplayed = scripter.currentMenu.attributes().contains(MenuAttribute.RUNTIME);
        if (tbrPercentage != 100 || runtimeDisplayed) {
            throw new CommandException().message("Cancelling TBR failed, TBR is still set according to MAIN_MENU");
        }
    }

    private void verifyMainMenuShowsExpectedTbrActive(RuffyScripter scripter) {
        scripter.verifyMenuIsDisplayed(MenuType.MAIN_MENU);
        // new TBR set; percentage and duration must be displayed ...
        if (!scripter.currentMenu.attributes().contains(MenuAttribute.TBR) ||
                !scripter.currentMenu.attributes().contains(MenuAttribute.RUNTIME)) {
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
