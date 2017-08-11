package de.jotomo.ruffyscripter.commands;

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

import static de.jotomo.ruffyscripter.commands.SetTbrCommand.State.AFTER;
import static de.jotomo.ruffyscripter.commands.SetTbrCommand.State.BEFORE;
import static de.jotomo.ruffyscripter.commands.SetTbrCommand.State.ERROR;
import static de.jotomo.ruffyscripter.commands.SetTbrCommand.State.MAIN;
import static de.jotomo.ruffyscripter.commands.SetTbrCommand.State.SET;
import static de.jotomo.ruffyscripter.commands.SetTbrCommand.State.SET_TBR;
import static de.jotomo.ruffyscripter.commands.SetTbrCommand.State.SET_TIME;
import static de.jotomo.ruffyscripter.commands.SetTbrCommand.State.TBR;
import static de.jotomo.ruffyscripter.commands.SetTbrCommand.State.TIME;

public class SetTbrCommand implements Command {
    private static final Logger log = LoggerFactory.getLogger(SetTbrCommand.class);

    private final long percentage;
    private final long duration;
    private RuffyScripter scripter;

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

    enum State {
        BEFORE,
        MAIN,
        TBR,
        SET_TBR,
        TIME,
        SET_TIME,
        SET,
        AFTER,
        ERROR
    };
    private State lastState,state;
    private long last;
    private long timeout;
    private Thread timeoutThread = new Thread()
    {
        @Override
        public void run() {
            while(state != ERROR && state!=AFTER) {
                if (timeout + last < System.currentTimeMillis()) {
                    lastState = state;
                    state = ERROR;
                    log.debug("timeout reached -> state:ERROR");
                }
                tick();
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            tick();
        }
    };
    private void updateState(State newState,long timeoutSec)
    {
        lastState = state;
        state = newState;
        last = System.currentTimeMillis();
        timeout = timeoutSec*1000;
    }
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
                {
                    Log.v("SetTbrCommand:tick",state+": no retries left for adjusting basals");
                    updateState(ERROR,30);
                }
                break;
            case TBR:
                if(scripter.enterMenu(MenuType.TBR_MENU,MenuType.TBR_SET, RuffyScripter.Key.CHECK,20000))
                {
                    updateState(SET_TBR,60);
                    retries = 10;
                }
                else
                {
                    Log.v("SetTbrCommand:tick",state+": no retries left for adjusting basals");
                    updateState(ERROR,30);
                }
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
                    retries--;
                    Log.v("SetTbrCommand:tick",state+": next retry "+retries+"left");
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
        }
    }
    @Override
    public CommandResult execute(RuffyScripter scripter, PumpState initialPumpState) {
        state = BEFORE;
        this.scripter = scripter;
        updateState(BEFORE,120);
        timeoutThread.start();

        try {
            synchronized (this) {
                this.wait();
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
            return new CommandResult().success(false).message("failed to wait: "+e.getMessage());
        }
        if(state==AFTER)
        {
            if(percentage==100)
                return new CommandResult().success(true).enacted(true).message("TBR was cancelled");

            return new CommandResult().success(true).enacted(true).message(
                    String.format(Locale.US, "TBR set to %d%% for %d min", percentage, duration));
        }
        return new CommandResult().success(false).message("failed with state: "+state+" from: "+lastState);
    }

    @Override
    public String toString() {
        return "SetTbrCommand{" +
                "percentage=" + percentage +
                ", duration=" + duration +
                '}';
    }
}
