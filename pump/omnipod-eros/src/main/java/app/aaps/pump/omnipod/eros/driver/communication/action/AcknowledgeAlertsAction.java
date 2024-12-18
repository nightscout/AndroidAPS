package app.aaps.pump.omnipod.eros.driver.communication.action;

import androidx.annotation.NonNull;

import java.util.Collections;

import app.aaps.pump.omnipod.eros.driver.communication.message.command.AcknowledgeAlertsCommand;
import app.aaps.pump.omnipod.eros.driver.communication.message.response.StatusResponse;
import app.aaps.pump.omnipod.eros.driver.definition.AlertSet;
import app.aaps.pump.omnipod.eros.driver.definition.AlertSlot;
import app.aaps.pump.omnipod.eros.driver.manager.ErosPodStateManager;
import app.aaps.pump.omnipod.eros.rileylink.manager.OmnipodRileyLinkCommunicationManager;

public class AcknowledgeAlertsAction implements OmnipodAction<StatusResponse> {
    private final ErosPodStateManager podStateManager;
    private final AlertSet alerts;

    public AcknowledgeAlertsAction(ErosPodStateManager podStateManager, AlertSet alerts) {
        if (podStateManager == null) {
            throw new IllegalArgumentException("Pod state manager cannot be null");
        }
        if (alerts == null) {
            throw new IllegalArgumentException("Alert set can not be null");
        } else if (alerts.size() == 0) {
            throw new IllegalArgumentException("Alert set can not be empty");
        }
        this.podStateManager = podStateManager;
        this.alerts = alerts;
    }

    public AcknowledgeAlertsAction(@NonNull ErosPodStateManager podStateManager, AlertSlot alertSlot) {
        this(podStateManager, new AlertSet(Collections.singletonList(alertSlot)));
    }

    @Override
    public StatusResponse execute(OmnipodRileyLinkCommunicationManager communicationService) {
        return communicationService.sendCommand(StatusResponse.class, podStateManager,
                new AcknowledgeAlertsCommand(podStateManager.getCurrentNonce(), alerts));
    }
}
