package de.jotomo.ruffyscripter.commands;

import org.monkey.d.ruffy.ruffy.driver.display.MenuAttribute;
import org.monkey.d.ruffy.ruffy.driver.display.MenuType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.jotomo.ruffyscripter.RuffyScripter;

// TODO robustness: can a TBR run out, whilst we're trying to cancel it?
public class CancelTbrCommand implements Command {
    private static final Logger log = LoggerFactory.getLogger(CancelTbrCommand.class);

    @Override
    public CommandResult execute(RuffyScripter scripter) {
        try {
            scripter.verifyMenuIsDisplayed(MenuType.MAIN_MENU);
            Double tbrPercentage = (Double) scripter.currentMenu.getAttribute(MenuAttribute.TBR);
            boolean runtimeDisplayed = scripter.currentMenu.attributes().contains(MenuAttribute.RUNTIME);
            if (tbrPercentage == 100 && !runtimeDisplayed) {
                return new CommandResult()
                        .success(true)
                        .enacted(true) // technically, nothing was enacted, but AAPS needs this to recover
                        // when there was an issue and AAPS thinks a TBR is still active
                        .message("No TBR active");
            }
            log.debug("Cancelling active TBR of " + tbrPercentage + "% with " + runtimeDisplayed + "min remaining");
        } catch (CommandException e) {
            return e.toCommandResult();
        }
        return new SetTbrCommand(100, 0).execute(scripter);
    }

    @Override
    public String toString() {
        return "CancelTbrCommand{}";
    }
}
