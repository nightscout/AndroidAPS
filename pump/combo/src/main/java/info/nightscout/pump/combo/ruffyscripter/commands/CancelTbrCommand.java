package info.nightscout.pump.combo.ruffyscripter.commands;

import androidx.annotation.NonNull;

import org.monkey.d.ruffy.ruffy.driver.display.MenuType;

import info.nightscout.pump.combo.ruffyscripter.PumpState;
import info.nightscout.pump.combo.ruffyscripter.PumpWarningCodes;
import info.nightscout.rx.logging.AAPSLogger;
import info.nightscout.rx.logging.LTag;

public class CancelTbrCommand extends BaseCommand {
    private final AAPSLogger aapsLogger;

    public CancelTbrCommand(AAPSLogger aapsLogger) {
        this.aapsLogger = aapsLogger;
    }
    
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

        aapsLogger.debug(LTag.PUMP, "Cancelling active TBR of " + pumpState.tbrPercent
                + "% with " + pumpState.tbrRemainingDuration + " min remaining");
        SetTbrCommand setTbrCommand = new SetTbrCommand(100, 0, aapsLogger);
        setTbrCommand.setScripter(scripter);
        setTbrCommand.execute();
        result = setTbrCommand.result;
    }

    @Override @NonNull
    public String toString() {
        return "CancelTbrCommand{}";
    }
}
