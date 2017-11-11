package de.jotomo.ruffyscripter.commands;

import android.os.SystemClock;

import org.monkey.d.ruffy.ruffy.driver.display.MenuType;

import de.jotomo.ruffy.spi.CommandResult;

/**
 * Created by joe on 08/11/17.
 */

public class SetDateAndTimeCommand extends BaseCommand {
    @Override
    public void execute() {
/*        scripter.navigateToMenu(MenuType.DATE_AND_TIME_MENU);
        scripter.pressCheckKey();
        // TODO ruffy does'n support date/time menu yet*/
        result.success = true;
    }
}
