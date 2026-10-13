package app.aaps.pump.omnipod.eros.driver.communication.message.command;

import androidx.annotation.NonNull;

import java.util.EnumSet;

import app.aaps.core.utils.pump.ByteUtil;
import app.aaps.pump.omnipod.eros.driver.communication.message.NonceResyncableMessageBlock;
import app.aaps.pump.omnipod.eros.driver.definition.BeepType;
import app.aaps.pump.omnipod.eros.driver.definition.DeliveryType;
import app.aaps.pump.omnipod.eros.driver.definition.MessageBlockType;

public class CancelDeliveryCommand extends NonceResyncableMessageBlock {

    private final BeepType beepType;
    private final EnumSet<DeliveryType> deliveryTypes;
    private int nonce;

    public CancelDeliveryCommand(int nonce, BeepType beepType, EnumSet<DeliveryType> deliveryTypes) {
        this.nonce = nonce;
        this.beepType = beepType;
        this.deliveryTypes = deliveryTypes;
        encode();
    }

    public CancelDeliveryCommand(int nonce, BeepType beepType, DeliveryType deliveryType) {
        this(nonce, beepType, EnumSet.of(deliveryType));
    }

    @Override
    public MessageBlockType getType() {
        return MessageBlockType.CANCEL_DELIVERY;
    }

    private void encode() {
        encodedData = new byte[5];
        System.arraycopy(ByteUtil.INSTANCE.getBytesFromInt(nonce), 0, encodedData, 0, 4);
        byte beepTypeValue = beepType.getValue();
        if (beepTypeValue > 8) {
            beepTypeValue = 0;
        }
        encodedData[4] = (byte) ((beepTypeValue & 0x0F) << 4);
        if (deliveryTypes.contains(DeliveryType.BASAL)) {
            encodedData[4] |= 1;
        }
        if (deliveryTypes.contains(DeliveryType.TEMP_BASAL)) {
            encodedData[4] |= 2;
        }
        if (deliveryTypes.contains(DeliveryType.BOLUS)) {
            encodedData[4] |= 4;
        }
    }

    @Override
    public int getNonce() {
        return nonce;
    }

    @Override
    public void setNonce(int nonce) {
        this.nonce = nonce;
        encode();
    }

    public EnumSet<DeliveryType> getDeliveryTypes() {
        return deliveryTypes.clone();
    }

    @NonNull @Override
    public String toString() {
        return "CancelDeliveryCommand{" +
                "beepType=" + beepType +
                ", deliveryTypes=" + deliveryTypes +
                ", nonce=" + nonce +
                '}';
    }
}
