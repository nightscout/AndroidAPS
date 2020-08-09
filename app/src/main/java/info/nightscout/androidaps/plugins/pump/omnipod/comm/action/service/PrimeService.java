package info.nightscout.androidaps.plugins.pump.omnipod.comm.action.service;

import org.joda.time.Duration;

import java.util.Collections;

import info.nightscout.androidaps.plugins.pump.omnipod.comm.OmnipodCommunicationManager;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.action.BolusAction;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.action.ConfigureAlertsAction;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.message.OmnipodMessage;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.message.command.FaultConfigCommand;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.message.response.StatusResponse;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.AlertConfiguration;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.AlertConfigurationFactory;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.state.PodStateManager;
import info.nightscout.androidaps.plugins.pump.omnipod.util.OmnipodConst;

public class PrimeService {

    public StatusResponse executeDisableTab5Sub16FaultConfigCommand(OmnipodCommunicationManager communicationService, PodStateManager podStateManager) {
        FaultConfigCommand faultConfigCommand = new FaultConfigCommand(podStateManager.getCurrentNonce(), (byte) 0x00, (byte) 0x00);
        OmnipodMessage faultConfigMessage = new OmnipodMessage(podStateManager.getAddress(),
                Collections.singletonList(faultConfigCommand), podStateManager.getMessageNumber());
        return communicationService.exchangeMessages(StatusResponse.class, podStateManager, faultConfigMessage);
    }

    public StatusResponse executeFinishSetupReminderAlertCommand(OmnipodCommunicationManager communicationService, PodStateManager podStateManager) {
        AlertConfiguration finishSetupReminderAlertConfiguration = AlertConfigurationFactory.createFinishSetupReminderAlertConfiguration();
        return communicationService.executeAction(new ConfigureAlertsAction(podStateManager,
                Collections.singletonList(finishSetupReminderAlertConfiguration)));
    }

    public StatusResponse executePrimeBolusCommand(OmnipodCommunicationManager communicationService, PodStateManager podStateManager) {
        return communicationService.executeAction(new BolusAction(podStateManager, OmnipodConst.POD_PRIME_BOLUS_UNITS,
                Duration.standardSeconds(1), false, false));
    }
}
