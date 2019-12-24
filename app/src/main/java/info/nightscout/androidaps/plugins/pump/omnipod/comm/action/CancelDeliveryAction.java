package info.nightscout.androidaps.plugins.pump.omnipod.comm.action;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import info.nightscout.androidaps.plugins.pump.omnipod.comm.OmnipodCommunicationService;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.message.MessageBlock;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.message.OmnipodMessage;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.message.command.BeepConfigCommand;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.message.command.CancelDeliveryCommand;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.message.response.StatusResponse;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.BeepConfigType;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.BeepType;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.DeliveryType;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.state.PodSessionState;
import info.nightscout.androidaps.plugins.pump.omnipod.exception.ActionInitializationException;

public class CancelDeliveryAction implements OmnipodAction<StatusResponse> {
    private final PodSessionState podState;
    private final EnumSet<DeliveryType> deliveryTypes;
    private final boolean acknowledgementBeep;

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
        this.acknowledgementBeep = acknowledgementBeep;
    }

    public CancelDeliveryAction(PodSessionState podState, DeliveryType deliveryType,
                                boolean acknowledgementBeep) {
        this(podState, EnumSet.of(deliveryType), acknowledgementBeep);
    }

    @Override
    public StatusResponse execute(OmnipodCommunicationService communicationService) {
        List<MessageBlock> messageBlocks = new ArrayList<>();

        messageBlocks.add(new CancelDeliveryCommand(podState.getCurrentNonce(),
                acknowledgementBeep && deliveryTypes.size() == 1 ? BeepType.BIP_BIP : BeepType.NO_BEEP, deliveryTypes));

        if (acknowledgementBeep && deliveryTypes.size() > 1) {
            // Workaround for strange beep behaviour when cancelling multiple delivery types at the same time

            // FIXME we should use other constructor with all beep configs.
            //  Theoretically, if we would cancel multiple delivery types but not all,
            //  we should keep the beep config for delivery types that we're not cancelling.
            //  We currently have no use case that though,
            //  as we either cancel 1 type or all types,
            messageBlocks.add(new BeepConfigCommand(BeepConfigType.BIP_BIP));
        }

        return communicationService.exchangeMessages(StatusResponse.class, podState,
                new OmnipodMessage(podState.getAddress(), messageBlocks, podState.getMessageNumber()));
    }
}
