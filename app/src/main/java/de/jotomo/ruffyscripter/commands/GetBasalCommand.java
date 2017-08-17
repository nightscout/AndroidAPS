package de.jotomo.ruffyscripter.commands;

import android.util.Log;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.jotomo.ruffyscripter.PumpState;
import de.jotomo.ruffyscripter.RuffyScripter;

public class GetBasalCommand implements Command {
    private static final Logger log = LoggerFactory.getLogger(GetBasalCommand.class);

    private RuffyScripter scripter;

    public GetBasalCommand() {
    }

    @Override
    public List<String> validateArguments() {
        List<String> violations = new ArrayList<>();

        return violations;
    }

    //    private void tick()
//    {
//        switch (state)
//        {
//            case BEFORE:
//                if(scripter.currentMenu!=null && scripter.currentMenu.getType()==MenuType.MAIN_MENU)
//                {
//                    updateState(MAIN,120);
//                    lastMenu = MenuType.MAIN_MENU;
//                    log.debug("found MAIN_MENU -> state:MAIN");
//                    retries=3;
//                }
//                break;
//            case MAIN:
//                if(retries>0)
//                    if(scripter.goToMainTypeScreen(MenuType.BASAL_1_MENU,30000))
//                    {
//                        if(scripter.enterMenu(MenuType.BASAL_1_MENU,MenuType.BASAL_TOTAL, RuffyScripter.Key.CHECK,2000))
//                        {
//                            updateState(BASAL_OVERVIEW, 30);
//                            retries=0;
//                        }
//                    }
//                    else
//                        retries--;
//                else
//                    updateState(ERROR,30);
//                break;
//            case BASAL_OVERVIEW:
//                if(scripter.currentMenu.getType()==MenuType.BASAL_TOTAL && scripter.currentMenu.getAttribute(MenuAttribute.BASAL_TOTAL)!=null && (Integer)scripter.currentMenu.getAttribute(MenuAttribute.BASAL_SELECTED)==1)
//                {
//                    basalTotal = (Double)scripter.currentMenu.getAttribute(MenuAttribute.BASAL_TOTAL);
//                    if(scripter.enterMenu(MenuType.BASAL_TOTAL,MenuType.BASAL_SET, RuffyScripter.Key.MENU,3000))
//                    {
//                        updateState(READ_BASAL,30);
//                        retries = 96;
//                    }
//                }
//                break;
//            case READ_BASAL:
//                if(scripter.currentMenu.getType()==MenuType.BASAL_SET && scripter.currentMenu.getAttribute(MenuAttribute.BASAL_START)!=null) {
//                    Object rateObj = scripter.currentMenu.getAttribute(MenuAttribute.BASAL_RATE);
//                    MenuTime time = (MenuTime) scripter.currentMenu.getAttribute(MenuAttribute.BASAL_START);
//                    if (rateObj instanceof Double) {
//                        rate.put(time.getHour(), (Double) rateObj);
//                    }
//                    boolean complete = true;
//                    for (int t = 0; t < 24; t++) {
//                        if (rate.get(t) == null)
//                            complete = false;
//                    }
//                    if (retries > 0) {
//                        if (complete) {
//                            scripter.pressBackKey();
//                            updateState(AFTER, 30);
//                        } else {
//                            retries--;
//                            scripter.pressMenuKey();
//                            scripter.waitForScreenUpdate(250);
//                        }
//                    } else {
//                        updateState(ERROR, 30);
//                    }
//                }
//                break;
//            case ERROR:
//            case AFTER:
//                scripter.goToMainMenuScreen(MenuType.MAIN_MENU,2000);
//                synchronized(GetBasalCommand.this) {
//                    GetBasalCommand.this.notify();
//                }
//                break;
//        }
//    }
    @Override
    public CommandResult execute(RuffyScripter scripter, PumpState initialPumpState) {
        try {
            Map<Integer, Double> rate = new HashMap<>();

            for (int i = 0; i < 24; i++) {
                Log.v("BASAL_RATE", "BASAL_RATE from " + String.format("%02d", i) + ":00 = " + rate.get(i));
            }
        } catch (Exception e) {
            log.error("failed to get basal", e);
            return new CommandResult().success(false).message("failed to get basal: " + e.getMessage());
        }
        return new CommandResult().success(true).enacted(true).message("Basal Rate was read");
    }

    @Override
    public String toString() {
        return "GetTbrCommand{}";
    }
}
