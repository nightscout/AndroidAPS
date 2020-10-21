package info.nightscout.androidaps.plugins.pump.omnipod.driver.communication.action;

import java.util.EnumSet;

import info.nightscout.androidaps.plugins.pump.omnipod.driver.communication.message.command.DeactivatePodCommand;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.communication.message.response.StatusResponse;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.definition.DeliveryType;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.exception.PodFaultException;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.manager.PodStateManager;
import info.nightscout.androidaps.plugins.pump.omnipod.rileylink.manager.OmnipodRileyLinkCommunicationManager;

public class DeactivatePodAction implements OmnipodAction<StatusResponse> {
    private final PodStateManager podStateManager;
    private final boolean acknowledgementBeep;

    public DeactivatePodAction(PodStateManager podStateManager, boolean acknowledgementBeep) {
        if (podStateManager == null) {
            throw new IllegalArgumentException("Pod state manager cannot be null");
        }
        this.podStateManager = podStateManager;
        this.acknowledgementBeep = acknowledgementBeep;
    }

    @Override
    public StatusResponse execute(OmnipodRileyLinkCommunicationManager communicationService) {
        if (!podStateManager.isSuspended() && !podStateManager.isPodFaulted()) {
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
