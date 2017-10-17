package info.nightscout.androidaps.plugins.PumpCombo.ruffy.internal.scripter.commands;

import org.monkey.d.ruffy.ruffy.driver.display.MenuType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;

import info.nightscout.androidaps.plugins.PumpCombo.ruffy.spi.PumpState;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.plugins.PumpCombo.ruffy.spi.CommandResult;

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
    public CommandResult execute() {
        try {
            scripter.verifyMenuIsDisplayed(MenuType.MAIN_MENU);
            PumpState pumpState = scripter.readPumpState();
            if (!pumpState.tbrActive) {
                log.debug("active temp basal 90s ago: " +
                        MainApp.getConfigBuilder().getTempBasalFromHistory(System.currentTimeMillis() - 90 * 1000));
                log.debug("active temp basal 60s ago: " +
                        MainApp.getConfigBuilder().getTempBasalFromHistory(System.currentTimeMillis() - 30 * 1000));
                log.debug("active temp basal 30s ago: " +
                        MainApp.getConfigBuilder().getTempBasalFromHistory(System.currentTimeMillis() - 30 * 1000));
                log.debug("active temp basal now:: " +
                        MainApp.getConfigBuilder().getTempBasalFromHistory(System.currentTimeMillis()));
                // TODO keep checking logs to ensure this case only happens because CancelTbrCommand was called
                // twice by AAPS
                log.warn("No TBR active to cancel");
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
    public String toString() {
        return "CancelTbrCommand{}";
    }
}
