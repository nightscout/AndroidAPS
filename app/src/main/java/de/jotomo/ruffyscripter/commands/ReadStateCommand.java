package de.jotomo.ruffyscripter.commands;

import org.monkey.d.ruffy.ruffy.driver.display.Menu;
import org.monkey.d.ruffy.ruffy.driver.display.MenuAttribute;
import org.monkey.d.ruffy.ruffy.driver.display.MenuType;
import org.monkey.d.ruffy.ruffy.driver.display.menu.MenuTime;

import de.jotomo.ruffyscripter.RuffyScripter;

/**
 * Reads the status displayed in the main menu. Usually TBR state or warning/error if any.
 * This command is 'read-only', no buttons are pushed and so no vibrations are caused.
 */
public class ReadStateCommand implements Command {
    @Override
    public CommandResult execute(RuffyScripter scripter) {
        try {
            PumpState state = new PumpState();
            Menu displayedMenu = scripter.currentMenu;
            MenuType displayedMenuType = displayedMenu.getType();
            if (displayedMenuType == MenuType.MAIN_MENU) {
                Double tbrPercentage = (Double) displayedMenu.getAttribute(MenuAttribute.TBR);
                if (tbrPercentage != 100) {
                    state.tbrActive = true;
                    Double displayedTbr = (Double) scripter.currentMenu.getAttribute(MenuAttribute.TBR);
                    state.tbrPercent = displayedTbr.intValue();
                    MenuTime durationMenuTime = ((MenuTime) displayedMenu.getAttribute(MenuAttribute.RUNTIME));
                    state.tbrRemainingDuration = durationMenuTime.getHour() * 60 + durationMenuTime.getMinute();
                }
            } else if (displayedMenuType == MenuType.WARNING_OR_ERROR) {
                state.isErrorOrWarning = true;
                state.errorCode = (int) displayedMenu.getAttribute(MenuAttribute.ERROR);
            } else {
                throw new CommandException().success(false).message("Neither MAIN_MENU nor WARNING_OR_ERROR is displayed, but " + displayedMenuType);
            }

            return new CommandResult().success(true).enacted(false).state(state);
        } catch (CommandException e) {
            return e.toCommandResult();
        }
    }

    @Override
    public String toString() {
        return "ReadStateCommand{}";
    }

}
