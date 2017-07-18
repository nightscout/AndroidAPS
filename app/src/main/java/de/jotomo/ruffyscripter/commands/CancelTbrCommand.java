package de.jotomo.ruffyscripter.commands;

import org.monkey.d.ruffy.ruffy.driver.display.MenuAttribute;
import org.monkey.d.ruffy.ruffy.driver.display.MenuType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;

import de.jotomo.ruffyscripter.PumpState;
import de.jotomo.ruffyscripter.RuffyScripter;

// TODO robustness: can a TBR run out, whilst we're trying to cancel it?
// Hm, we could just ignore TBRs that run out within the next 60s (0:01 or even 0:02
// given we need some time to process the request).
public class CancelTbrCommand implements Command {
    private static final Logger log = LoggerFactory.getLogger(CancelTbrCommand.class);

    @Override
    public List<String> validateArguments() {
        return Collections.emptyList();
    }

    @Override
    public CommandResult execute(RuffyScripter scripter) {
        try {
            scripter.verifyMenuIsDisplayed(MenuType.MAIN_MENU);
            Double tbrPercentage = (Double) scripter.currentMenu.getAttribute(MenuAttribute.TBR);
            boolean runtimeDisplayed = scripter.currentMenu.attributes().contains(MenuAttribute.RUNTIME);
            if (tbrPercentage == 100 && !runtimeDisplayed) {
                // this is likely a relatively harmless error like AAPS trying to cancel a TBR
                // that has run out in the last minute or so, but for debugging lets raise an error
                // to make sure we can inspect this situation.
                // Set enacted=true, so I record is created and AAPS stops thinking a TBR still
                // running and trying again to cancel it.
                return new CommandResult().success(false).enacted(true).message("No TBR active");
                  /*
                        .success(true)
                        .enacted(true) // technically, nothing was enacted, but AAPS needs this to recover
                        // when there was an issue and AAPS thinks a TBR is still active
                        .message("No TBR active");
                  */
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
