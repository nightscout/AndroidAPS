package de.jotomo.ruffyscripter.commands;

import org.monkey.d.ruffy.ruffy.driver.display.MenuAttribute;
import org.monkey.d.ruffy.ruffy.driver.display.MenuType;

import de.jotomo.ruffyscripter.RuffyScripter;

// TODO robustness: can a TBR run out, whilst we're trying to cancel it?
public class CancelTbrCommand implements Command {
    @Override
    public CommandResult execute(RuffyScripter scripter) {
        scripter.verifyMenuIsDisplayed(MenuType.MAIN_MENU);
        Double tbrPercentage = (Double) scripter.currentMenu.getAttribute(MenuAttribute.TBR);
        boolean runtimeDisplayed = scripter.currentMenu.attributes().contains(MenuAttribute.RUNTIME);
        if (tbrPercentage == 100 && !runtimeDisplayed) {
            return new CommandResult().success(true).enacted(false).message("No TBR active");
        }
        return new SetTbrCommand(100, 0).execute(scripter);
    }
}
