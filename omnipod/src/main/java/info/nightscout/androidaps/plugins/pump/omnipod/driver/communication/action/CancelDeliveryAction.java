package info.nightscout.androidaps.plugins.pump.omnipod.driver.communication.action;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import info.nightscout.androidaps.plugins.pump.omnipod.driver.communication.message.MessageBlock;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.communication.message.OmnipodMessage;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.communication.message.command.CancelDeliveryCommand;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.communication.message.response.StatusResponse;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.definition.BeepType;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.definition.DeliveryType;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.manager.PodStateManager;
import info.nightscout.androidaps.plugins.pump.omnipod.rileylink.manager.OmnipodRileyLinkCommunicationManager;

public class CancelDeliveryAction implements OmnipodAction<StatusResponse> {
    private final PodStateManager podStateManager;
    private final EnumSet<DeliveryType> deliveryTypes;
    private final boolean acknowledgementBeep;

    public CancelDeliveryAction(PodStateManager podStateManager, EnumSet<DeliveryType> deliveryTypes,
                                boolean acknowledgementBeep) {
        if (podStateManager == null) {
            throw new IllegalArgumentException("Pod state manager cannot be null");
        }
        if (deliveryTypes == null) {
            throw new IllegalArgumentException("Delivery types cannot be null");
        }
        this.podStateManager = podStateManager;
        this.deliveryTypes = deliveryTypes;
        this.acknowledgementBeep = acknowledgementBeep;
    }

    @Override
    public StatusResponse execute(OmnipodRileyLinkCommunicationManager communicationService) {
        List<MessageBlock> messageBlocks = new ArrayList<>();

        if (acknowledgementBeep && deliveryTypes.size() > 1) {
            // Workaround for strange beep behaviour when cancelling multiple delivery types
            List<DeliveryType> deliveryTypeList = new ArrayList<>(deliveryTypes);

            EnumSet<DeliveryType> deliveryTypeWithBeep = EnumSet.of(deliveryTypeList.remove(deliveryTypeList.size() - 1));
            EnumSet<DeliveryType> deliveryTypesWithoutBeep = EnumSet.copyOf(deliveryTypeList);

            messageBlocks.add(new CancelDeliveryCommand(podStateManager.getCurrentNonce(), BeepType.NO_BEEP, deliveryTypesWithoutBeep));
            messageBlocks.add(new CancelDeliveryCommand(podStateManager.getCurrentNonce(), BeepType.BEEP, deliveryTypeWithBeep));
        } else {
            messageBlocks.add(new CancelDeliveryCommand(podStateManager.getCurrentNonce(),
                    acknowledgementBeep && deliveryTypes.size() == 1 ? BeepType.BEEP : BeepType.NO_BEEP, deliveryTypes));
        }

        StatusResponse statusResponse = communicationService.exchangeMessages(StatusResponse.class, podStateManager,
                new OmnipodMessage(podStateManager.getAddress(), messageBlocks, podStateManager.getMessageNumber()));

        return statusResponse;
    }
}
