package info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.communication.action.service;

import org.joda.time.Duration;

import java.util.Collections;

import info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.communication.action.BolusAction;
import info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.communication.action.ConfigureAlertsAction;
import info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.communication.message.OmnipodMessage;
import info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.communication.message.command.FaultConfigCommand;
import info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.communication.message.response.StatusResponse;
import info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.definition.AlertConfiguration;
import info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.definition.OmnipodConstants;
import info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.manager.ErosPodStateManager;
import info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.util.AlertConfigurationUtil;
import info.nightscout.androidaps.plugins.pump.omnipod.eros.rileylink.manager.OmnipodRileyLinkCommunicationManager;

public class PrimeService {

    public StatusResponse executeDisableTab5Sub16And17FaultConfigCommand(OmnipodRileyLinkCommunicationManager communicationService, ErosPodStateManager podStateManager) {
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

    public StatusResponse executePrimeBolusCommand(OmnipodRileyLinkCommunicationManager communicationService, ErosPodStateManager podStateManager) {
        return communicationService.executeAction(new BolusAction(podStateManager, OmnipodConstants.POD_PRIME_BOLUS_UNITS,
                Duration.standardSeconds(1), false, true));
    }
}
