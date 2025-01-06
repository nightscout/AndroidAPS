package app.aaps.pump.omnipod.eros.driver.communication.action;

import java.util.List;

import app.aaps.pump.omnipod.eros.driver.communication.message.command.ConfigureAlertsCommand;
import app.aaps.pump.omnipod.eros.driver.communication.message.response.StatusResponse;
import app.aaps.pump.omnipod.eros.driver.definition.AlertConfiguration;
import app.aaps.pump.omnipod.eros.driver.manager.ErosPodStateManager;
import app.aaps.pump.omnipod.eros.rileylink.manager.OmnipodRileyLinkCommunicationManager;

public class ConfigureAlertsAction implements OmnipodAction<StatusResponse> {
    private final ErosPodStateManager podStateManager;
    private final List<AlertConfiguration> alertConfigurations;

    public ConfigureAlertsAction(ErosPodStateManager podStateManager, List<AlertConfiguration> alertConfigurations) {
        if (podStateManager == null) {
            throw new IllegalArgumentException("Pod state manager cannot be null");
        }
        if (alertConfigurations == null) {
            throw new IllegalArgumentException("Alert configurations cannot be null");
        }
        this.podStateManager = podStateManager;
        this.alertConfigurations = alertConfigurations;
    }

    @Override
    public StatusResponse execute(OmnipodRileyLinkCommunicationManager communicationService) {
        ConfigureAlertsCommand configureAlertsCommand = new ConfigureAlertsCommand(podStateManager.getCurrentNonce(), alertConfigurations);
        StatusResponse statusResponse = communicationService.sendCommand(StatusResponse.class, podStateManager, configureAlertsCommand);
        updateConfiguredAlerts(podStateManager, alertConfigurations);
        return statusResponse;
    }

    public static void updateConfiguredAlerts(ErosPodStateManager podStateManager, List<AlertConfiguration> alertConfigurations) {
        for (AlertConfiguration alertConfiguration : alertConfigurations) {
            if (alertConfiguration.isActive()) {
                podStateManager.putConfiguredAlert(alertConfiguration.getAlertSlot(), alertConfiguration.getAlertType());
            } else {
                podStateManager.removeConfiguredAlert(alertConfiguration.getAlertSlot());
            }
        }
    }
}
