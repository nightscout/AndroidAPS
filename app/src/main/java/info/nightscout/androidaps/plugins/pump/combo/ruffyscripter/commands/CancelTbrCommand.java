package info.nightscout.androidaps.plugins.pump.combo.ruffyscripter.commands;

import org.monkey.d.ruffy.ruffy.driver.display.MenuType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.plugins.pump.combo.ruffyscripter.PumpState;
import info.nightscout.androidaps.plugins.pump.combo.ruffyscripter.PumpWarningCodes;

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
    public String toString() {
        return "CancelTbrCommand{}";
    }
}
