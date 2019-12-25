package info.nightscout.androidaps.plugins.pump.omnipod.comm.action;

import java.util.EnumSet;

import info.nightscout.androidaps.plugins.pump.omnipod.comm.OmnipodCommunicationService;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.message.command.DeactivatePodCommand;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.message.response.StatusResponse;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.DeliveryType;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.state.PodSessionState;
import info.nightscout.androidaps.plugins.pump.omnipod.exception.ActionInitializationException;

public class DeactivatePodAction implements OmnipodAction<StatusResponse> {
    private final PodSessionState podState;
    private final boolean acknowledgementBeep;

    public DeactivatePodAction(PodSessionState podState, boolean acknowledgementBeep) {
        if (podState == null) {
            throw new ActionInitializationException("Pod state cannot be null");
        }
        this.podState = podState;
        this.acknowledgementBeep = acknowledgementBeep;
    }

    @Override
    public StatusResponse execute(OmnipodCommunicationService communicationService) {
        if (!podState.isSuspended() && !podState.hasFaultEvent()) {
            communicationService.executeAction(new CancelDeliveryAction(podState,
                    EnumSet.allOf(DeliveryType.class), acknowledgementBeep));
        }

        return communicationService.sendCommand(StatusResponse.class, podState, new DeactivatePodCommand(podState.getCurrentNonce()));
    }
}
