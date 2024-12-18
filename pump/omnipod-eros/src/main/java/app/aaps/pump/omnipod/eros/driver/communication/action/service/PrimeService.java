package app.aaps.pump.omnipod.eros.driver.communication.action.service;

import androidx.annotation.NonNull;

import org.joda.time.Duration;

import java.util.Collections;

import app.aaps.pump.omnipod.eros.driver.communication.action.BolusAction;
import app.aaps.pump.omnipod.eros.driver.communication.action.ConfigureAlertsAction;
import app.aaps.pump.omnipod.eros.driver.communication.message.OmnipodMessage;
import app.aaps.pump.omnipod.eros.driver.communication.message.command.FaultConfigCommand;
import app.aaps.pump.omnipod.eros.driver.communication.message.response.StatusResponse;
import app.aaps.pump.omnipod.eros.driver.definition.AlertConfiguration;
import app.aaps.pump.omnipod.eros.driver.definition.OmnipodConstants;
import app.aaps.pump.omnipod.eros.driver.manager.ErosPodStateManager;
import app.aaps.pump.omnipod.eros.driver.util.AlertConfigurationUtil;
import app.aaps.pump.omnipod.eros.rileylink.manager.OmnipodRileyLinkCommunicationManager;

public class PrimeService {

    public StatusResponse executeDisableTab5Sub16And17FaultConfigCommand(OmnipodRileyLinkCommunicationManager communicationService, @NonNull ErosPodStateManager podStateManager) {
        FaultConfigCommand faultConfigCommand = new FaultConfigCommand(podStateManager.getCurrentNonce(), (byte) 0x00, (byte) 0x00);
        OmnipodMessage faultConfigMessage = new OmnipodMessage(podStateManager.getAddress(),
                Collections.singletonList(faultConfigCommand), podStateManager.getMessageNumber());
        return communicationService.exchangeMessages(StatusResponse.class, podStateManager, faultConfigMessage);
    }

    public StatusResponse executeFinishSetupReminderAlertCommand(OmnipodRileyLinkCommunicationManager communicationService, ErosPodStateManager podStateManager) {
        AlertConfiguration finishSetupReminderAlertConfiguration = AlertConfigurationUtil.createFinishSetupReminderAlertConfiguration();
        return communicationService.executeAction(new ConfigureAlertsAction(podStateManager,
                Collections.singletonList(finishSetupReminderAlertConfiguration)));
    }

    public StatusResponse executePrimeBolusCommand(@NonNull OmnipodRileyLinkCommunicationManager communicationService, ErosPodStateManager podStateManager) {
        return communicationService.executeAction(new BolusAction(podStateManager, OmnipodConstants.POD_PRIME_BOLUS_UNITS,
                Duration.standardSeconds(1), false, true));
    }
}
