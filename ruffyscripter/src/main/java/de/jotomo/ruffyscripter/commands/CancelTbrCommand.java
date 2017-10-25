package de.jotomo.ruffyscripter.commands;

import org.monkey.d.ruffy.ruffy.driver.display.MenuType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;

import de.jotomo.ruffy.spi.PumpState;
import de.jotomo.ruffy.spi.CommandResult;

// TODO robustness: can a TBR run out, whilst we're trying to cancel it?
// Hm, we could just ignore TBRs that run out within the next 60s (0:01 or even 0:02
// given we need some time to process the request).
public class CancelTbrCommand extends BaseCommand {
    private static final Logger log = LoggerFactory.getLogger(CancelTbrCommand.class);

    @Override
    public List<String> validateArguments() {
        return Collections.emptyList();
    }

    @Override
    public String reconnectAlarm() {
        return "TBR CANCELLED";
    }

    @Override
    public CommandResult execute() {
        try {
            scripter.verifyMenuIsDisplayed(MenuType.MAIN_MENU);
            PumpState pumpState = scripter.readPumpStateInternal();
            if (!pumpState.tbrActive) {
                log.debug("No TBR active to cancel");
                return new CommandResult()
                        .success(true)
                        // Technically, nothing was enacted, but AAPS needs this to recover
                        // when there was an issue and AAPS thinks a TBR is still active,
                        // so the ComboPlugin can create a TempporaryBasel to mark the TBR
                        // as finished to get in sync with the pump state.
                        .enacted(true)
                        .message("No TBR active");
            }

            log.debug("Cancelling active TBR of " + pumpState.tbrPercent
                    + "% with " + pumpState.tbrRemainingDuration + " min remaining");
            SetTbrCommand setTbrCommand = new SetTbrCommand(100, 0);
            setTbrCommand.setScripter(scripter);
            return setTbrCommand.execute();
        } catch (CommandException e) {
            return e.toCommandResult();
        }
    }

    @Override
    public boolean needsRunMode() {
        return true;
    }

    @Override
    public String toString() {
        return "CancelTbrCommand{}";
    }
}
