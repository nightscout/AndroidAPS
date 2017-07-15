package de.jotomo.ruffyscripter.commands;

import org.monkey.d.ruffy.ruffy.driver.display.Menu;
import org.monkey.d.ruffy.ruffy.driver.display.MenuAttribute;
import org.monkey.d.ruffy.ruffy.driver.display.MenuType;
import org.monkey.d.ruffy.ruffy.driver.display.menu.MenuTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.jotomo.ruffyscripter.RuffyScripter;

/**
 * Reads the status displayed in the main menu. Usually TBR state or warning/error if any.
 * This command is 'read-only', no buttons are pushed and so no vibrations are caused.
 */
public class ReadStateCommand implements Command {
    private static final Logger log = LoggerFactory.getLogger(ReadStateCommand.class);

    @Override
    public CommandResult execute(RuffyScripter scripter) {
        try {
            State state = new State();
            Menu displayedMenu = scripter.currentMenu;
            MenuType displayedMenuType = displayedMenu.getType();
            if (displayedMenuType == MenuType.MAIN_MENU) {
                Double tbrPercentage = (Double) displayedMenu.getAttribute(MenuAttribute.TBR);
                if (tbrPercentage != 100) {
                    state.tbrActive = true;
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
}
