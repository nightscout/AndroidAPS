package de.jotomo.ruffyscripter.commands;

import org.monkey.d.ruffy.ruffy.driver.display.MenuType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.jotomo.ruffy.spi.PumpState;
import de.jotomo.ruffy.spi.PumpWarningCodes;

// TODO robustness: can a TBR run out, whilst we're trying to cancel it?
// Hm, we could just ignore TBRs that run out within the next 60s (0:01 or even 0:02
// given we need some time to process the request).
public class CancelTbrCommand extends BaseCommand {
    private static final Logger log = LoggerFactory.getLogger(CancelTbrCommand.class);

    @Override
    public Integer getReconnectWarningId() {
        return PumpWarningCodes.TBR_CANCELLED;
    }

    @Override
    public void execute() {
        scripter.verifyMenuIsDisplayed(MenuType.MAIN_MENU);
        PumpState pumpState = scripter.readPumpStateInternal();
        if (!pumpState.tbrActive) {
            // This is non-critical; when cancelling a TBR and the connection was interrupted
            // the TBR was cancelled by that. In that case not cancelling anything is fine.
            result.success = true;
            result.enacted = false;
            return;
        }

        log.debug("Cancelling active TBR of " + pumpState.tbrPercent
                + "% with " + pumpState.tbrRemainingDuration + " min remaining");
        SetTbrCommand setTbrCommand = new SetTbrCommand(100, 0);
        setTbrCommand.setScripter(scripter);
        setTbrCommand.execute();
        result = setTbrCommand.result;
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
