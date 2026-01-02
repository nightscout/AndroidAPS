package app.aaps.pump.omnipod.eros.driver.communication.action;

import androidx.annotation.NonNull;

import org.joda.time.Duration;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import app.aaps.pump.omnipod.eros.driver.communication.message.MessageBlock;
import app.aaps.pump.omnipod.eros.driver.communication.message.OmnipodMessage;
import app.aaps.pump.omnipod.eros.driver.communication.message.command.BeepConfigCommand;
import app.aaps.pump.omnipod.eros.driver.communication.message.command.CancelDeliveryCommand;
import app.aaps.pump.omnipod.eros.driver.communication.message.response.StatusResponse;
import app.aaps.pump.omnipod.eros.driver.definition.BeepConfigType;
import app.aaps.pump.omnipod.eros.driver.definition.BeepType;
import app.aaps.pump.omnipod.eros.driver.definition.DeliveryType;
import app.aaps.pump.omnipod.eros.driver.manager.ErosPodStateManager;
import app.aaps.pump.omnipod.eros.rileylink.manager.OmnipodRileyLinkCommunicationManager;

public class CancelDeliveryAction implements OmnipodAction<StatusResponse> {
    private final ErosPodStateManager podStateManager;
    private final EnumSet<DeliveryType> deliveryTypes;
    private final boolean acknowledgementBeep;

    public CancelDeliveryAction(ErosPodStateManager podStateManager, EnumSet<DeliveryType> deliveryTypes,
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
    public StatusResponse execute(@NonNull OmnipodRileyLinkCommunicationManager communicationService) {
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
