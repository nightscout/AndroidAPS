package info.nightscout.androidaps.plugins.pump.omnipod.comm.action;

import java.util.Collections;

import info.nightscout.androidaps.plugins.pump.omnipod.comm.OmnipodCommunicationManager;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.exception.ActionInitializationException;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.message.command.AcknowledgeAlertsCommand;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.message.response.StatusResponse;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.AlertSet;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.AlertSlot;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.state.PodStateManager;

public class AcknowledgeAlertsAction implements OmnipodAction<StatusResponse> {
    private final PodStateManager podStateManager;
    private final AlertSet alerts;

    public AcknowledgeAlertsAction(PodStateManager podStateManager, AlertSet alerts) {
        if (podStateManager == null) {
            throw new ActionInitializationException("Pod state manager cannot be null");
        }
        if (alerts == null) {
            throw new ActionInitializationException("Alert set can not be null");
        } else if (alerts.size() == 0) {
            throw new ActionInitializationException("Alert set can not be empty");
        }
        this.podStateManager = podStateManager;
        this.alerts = alerts;
    }

    public AcknowledgeAlertsAction(PodStateManager podStateManager, AlertSlot alertSlot) {
        this(podStateManager, new AlertSet(Collections.singletonList(alertSlot)));
    }

    @Override
    public StatusResponse execute(OmnipodCommunicationManager communicationService) {
        return communicationService.sendCommand(StatusResponse.class, podStateManager,
                new AcknowledgeAlertsCommand(podStateManager.getCurrentNonce(), alerts));
    }
}
