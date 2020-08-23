package info.nightscout.androidaps.plugins.pump.omnipod.driver.communication.action;

import java.util.List;

import info.nightscout.androidaps.plugins.pump.omnipod.rileylink.OmnipodRileyLinkCommunicationManager;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.exception.ActionInitializationException;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.communication.message.command.ConfigureAlertsCommand;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.communication.message.response.StatusResponse;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.definition.AlertConfiguration;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.manager.PodStateManager;

public class ConfigureAlertsAction implements OmnipodAction<StatusResponse> {
    private final PodStateManager podStateManager;
    private final List<AlertConfiguration> alertConfigurations;

    public ConfigureAlertsAction(PodStateManager podStateManager, List<AlertConfiguration> alertConfigurations) {
        if (podStateManager == null) {
            throw new ActionInitializationException("Pod state manager cannot be null");
        }
        if (alertConfigurations == null) {
            throw new ActionInitializationException("Alert configurations cannot be null");
        }
        this.podStateManager = podStateManager;
        this.alertConfigurations = alertConfigurations;
    }

    @Override
    public StatusResponse execute(OmnipodRileyLinkCommunicationManager communicationService) {
        ConfigureAlertsCommand configureAlertsCommand = new ConfigureAlertsCommand(podStateManager.getCurrentNonce(), alertConfigurations);
        StatusResponse statusResponse = communicationService.sendCommand(StatusResponse.class, podStateManager, configureAlertsCommand);
        for (AlertConfiguration alertConfiguration : alertConfigurations) {
            podStateManager.putConfiguredAlert(alertConfiguration.getAlertSlot(), alertConfiguration.getAlertType());
        }
        return statusResponse;
    }
}
