package info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.communication.action;

import java.util.EnumSet;

import info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.communication.message.command.DeactivatePodCommand;
import info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.communication.message.response.StatusResponse;
import info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.definition.DeliveryType;
import info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.exception.PodFaultException;
import info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.manager.ErosPodStateManager;
import info.nightscout.androidaps.plugins.pump.omnipod.eros.rileylink.manager.OmnipodRileyLinkCommunicationManager;

public class DeactivatePodAction implements OmnipodAction<StatusResponse> {
    private final ErosPodStateManager podStateManager;
    private final boolean acknowledgementBeep;

    public DeactivatePodAction(ErosPodStateManager podStateManager, boolean acknowledgementBeep) {
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
