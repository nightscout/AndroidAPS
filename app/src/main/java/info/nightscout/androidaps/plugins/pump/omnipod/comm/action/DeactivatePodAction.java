package info.nightscout.androidaps.plugins.pump.omnipod.comm.action;

import java.util.EnumSet;

import info.nightscout.androidaps.plugins.pump.omnipod.comm.OmnipodCommunicationManager;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.exception.ActionInitializationException;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.exception.PodFaultException;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.message.command.DeactivatePodCommand;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.message.response.StatusResponse;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.DeliveryType;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.state.PodStateManager;

public class DeactivatePodAction implements OmnipodAction<StatusResponse> {
    private final PodStateManager podStateManager;
    private final boolean acknowledgementBeep;

    public DeactivatePodAction(PodStateManager podStateManager, boolean acknowledgementBeep) {
        if (podStateManager == null) {
            throw new ActionInitializationException("Pod state manager cannot be null");
        }
        this.podStateManager = podStateManager;
        this.acknowledgementBeep = acknowledgementBeep;
    }

    @Override
    public StatusResponse execute(OmnipodCommunicationManager communicationService) {
        if (!podStateManager.isSuspended() && !podStateManager.hasFaultEvent()) {
            try {
                communicationService.executeAction(new CancelDeliveryAction(podStateManager,
                        EnumSet.allOf(DeliveryType.class), acknowledgementBeep));
            } catch (PodFaultException ex) {
                // Ignore
            }
        }

        return communicationService.sendCommand(StatusResponse.class, podStateManager, new DeactivatePodCommand(podStateManager.getCurrentNonce()));
    }
}
