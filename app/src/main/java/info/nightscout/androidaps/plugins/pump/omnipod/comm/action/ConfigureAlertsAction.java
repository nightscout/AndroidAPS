package info.nightscout.androidaps.plugins.pump.omnipod.comm.action;

import java.util.List;

import info.nightscout.androidaps.plugins.pump.omnipod.comm.OmnipodCommunicationService;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.message.command.ConfigureAlertsCommand;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.message.response.StatusResponse;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.AlertConfiguration;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.state.PodSessionState;
import info.nightscout.androidaps.plugins.pump.omnipod.exception.ActionInitializationException;

public class ConfigureAlertsAction implements OmnipodAction<StatusResponse> {
    private final PodSessionState podState;
    private final List<AlertConfiguration> alertConfigurations;

    public ConfigureAlertsAction(PodSessionState podState, List<AlertConfiguration> alertConfigurations) {
        if (podState == null) {
            throw new ActionInitializationException("Pod state cannot be null");
        }
        if (alertConfigurations == null) {
            throw new ActionInitializationException("Alert configurations cannot be null");
        }
        this.podState = podState;
        this.alertConfigurations = alertConfigurations;
    }

    @Override
    public StatusResponse execute(OmnipodCommunicationService communicationService) {
        ConfigureAlertsCommand configureAlertsCommand = new ConfigureAlertsCommand(podState.getCurrentNonce(), alertConfigurations);
        StatusResponse statusResponse = communicationService.sendCommand(StatusResponse.class, podState, configureAlertsCommand);
        for (AlertConfiguration alertConfiguration : alertConfigurations) {
            podState.putConfiguredAlert(alertConfiguration.getAlertSlot(), alertConfiguration.getAlertType());
        }
        return statusResponse;
    }
}
