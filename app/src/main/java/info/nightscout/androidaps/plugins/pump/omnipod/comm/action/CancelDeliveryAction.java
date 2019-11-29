package info.nightscout.androidaps.plugins.pump.omnipod.comm.action;

import java.util.EnumSet;

import info.nightscout.androidaps.plugins.pump.omnipod.comm.OmnipodCommunicationService;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.message.command.CancelDeliveryCommand;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.message.response.StatusResponse;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.BeepType;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.DeliveryType;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.state.PodSessionState;
import info.nightscout.androidaps.plugins.pump.omnipod.exception.ActionInitializationException;

public class CancelDeliveryAction implements OmnipodAction<StatusResponse> {
    private final PodSessionState podState;
    private final EnumSet<DeliveryType> deliveryTypes;
    private final BeepType beepType;

    public CancelDeliveryAction(PodSessionState podState, EnumSet<DeliveryType> deliveryTypes,
                                boolean acknowledgementBeep) {
        if (podState == null) {
            throw new ActionInitializationException("Pod state cannot be null");
        }
        if (deliveryTypes == null) {
            throw new ActionInitializationException("Delivery types cannot be null");
        }
        this.podState = podState;
        this.deliveryTypes = deliveryTypes;
        if (acknowledgementBeep) {
            beepType = BeepType.BIP_BIP;
        } else {
            beepType = BeepType.NO_BEEP;
        }
    }

    public CancelDeliveryAction(PodSessionState podState, DeliveryType deliveryType,
                                boolean acknowledgementBeep) {
        this(podState, EnumSet.of(deliveryType), acknowledgementBeep);
    }

    @Override
    public StatusResponse execute(OmnipodCommunicationService communicationService) {
        return communicationService.sendCommand(StatusResponse.class, podState,
                new CancelDeliveryCommand(podState.getCurrentNonce(), beepType, deliveryTypes));
    }
}
