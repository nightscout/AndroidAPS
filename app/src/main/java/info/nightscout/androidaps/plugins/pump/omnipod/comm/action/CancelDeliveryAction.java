package info.nightscout.androidaps.plugins.pump.omnipod.comm.action;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import info.nightscout.androidaps.plugins.pump.omnipod.comm.OmnipodCommunicationManager;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.message.MessageBlock;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.message.OmnipodMessage;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.message.command.CancelDeliveryCommand;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.message.response.StatusResponse;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.BeepType;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.DeliveryType;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.state.PodSessionState;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.exception.ActionInitializationException;

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

    @Override
    public StatusResponse execute(OmnipodCommunicationManager communicationService) {
        List<MessageBlock> messageBlocks = new ArrayList<>();

        if (acknowledgementBeep && deliveryTypes.size() > 1) {
            // Workaround for strange beep behaviour when cancelling multiple delivery types
            List<DeliveryType> deliveryTypeList = new ArrayList<>(deliveryTypes);

            EnumSet<DeliveryType> deliveryTypeWithBeep = EnumSet.of(deliveryTypeList.remove(deliveryTypeList.size() - 1));
            EnumSet<DeliveryType> deliveryTypesWithoutBeep = EnumSet.copyOf(deliveryTypeList);

            messageBlocks.add(new CancelDeliveryCommand(podState.getCurrentNonce(), BeepType.NO_BEEP, deliveryTypesWithoutBeep));
            messageBlocks.add(new CancelDeliveryCommand(podState.getCurrentNonce(), BeepType.BEEP, deliveryTypeWithBeep));
        } else {
            messageBlocks.add(new CancelDeliveryCommand(podState.getCurrentNonce(),
                    acknowledgementBeep && deliveryTypes.size() == 1 ? BeepType.BEEP : BeepType.NO_BEEP, deliveryTypes));
        }

        return communicationService.exchangeMessages(StatusResponse.class, podState,
                new OmnipodMessage(podState.getAddress(), messageBlocks, podState.getMessageNumber()));
    }
}
