package info.nightscout.androidaps.plugins.pump.omnipod.driver.communication.action;

import org.joda.time.Duration;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import info.nightscout.androidaps.plugins.pump.omnipod.driver.communication.message.MessageBlock;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.communication.message.OmnipodMessage;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.communication.message.command.BeepConfigCommand;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.communication.message.command.CancelDeliveryCommand;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.communication.message.response.StatusResponse;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.definition.BeepConfigType;
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

        messageBlocks.add(new CancelDeliveryCommand(podStateManager.getCurrentNonce(), BeepType.NO_BEEP, deliveryTypes));

        // Workaround for strange behavior where the Pod beeps for each specified delivery type
        if (acknowledgementBeep) {
            messageBlocks.add(new BeepConfigCommand(BeepConfigType.BEEP, false, Duration.ZERO, false, Duration.ZERO, false, Duration.ZERO));
        }

        return communicationService.exchangeMessages(StatusResponse.class, podStateManager,
                new OmnipodMessage(podStateManager.getAddress(), messageBlocks, podStateManager.getMessageNumber()));
    }
}
