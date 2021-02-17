package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.command;

import java.nio.ByteBuffer;
import java.util.BitSet;

import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.command.base.CommandType;
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.command.base.NonceEnabledCommand;
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.command.base.builder.NonceEnabledCommandBuilder;
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition.BeepType;
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition.Encodable;

public final class StopDeliveryCommand extends NonceEnabledCommand {
    private static final short LENGTH = 7;
    private static final byte BODY_LENGTH = 5;

    private final DeliveryType deliveryType;
    private final BeepType beepType;

    StopDeliveryCommand(int uniqueId, short sequenceNumber, boolean multiCommandFlag, DeliveryType deliveryType, BeepType beepType, int nonce) {
        super(CommandType.STOP_DELIVERY, uniqueId, sequenceNumber, multiCommandFlag, nonce);
        this.deliveryType = deliveryType;
        this.beepType = beepType;
    }

    @Override public byte[] getEncoded() {
        return appendCrc(ByteBuffer.allocate(LENGTH + HEADER_LENGTH) //
                .put(encodeHeader(uniqueId, sequenceNumber, LENGTH, multiCommandFlag)) //
                .put(commandType.getValue()) //
                .put(BODY_LENGTH) //
                .putInt(nonce) //
                .put((byte) ((beepType.getValue() << 4) | deliveryType.getEncoded()[0])) //
                .array());
    }

    @Override public String toString() {
        return "StopDeliveryCommand{" +
                "deliveryType=" + deliveryType +
                ", beepType=" + beepType +
                ", nonce=" + nonce +
                ", commandType=" + commandType +
                ", uniqueId=" + uniqueId +
                ", sequenceNumber=" + sequenceNumber +
                ", multiCommandFlag=" + multiCommandFlag +
                '}';
    }

    public enum DeliveryType implements Encodable {
        BASAL(true, false, false),
        TEMP_BASAL(false, true, false),
        BOLUS(false, false, true),
        ALL(true, true, true);

        private final boolean basal;
        private final boolean tempBasal;
        private final boolean bolus;

        DeliveryType(boolean basal, boolean tempBasal, boolean bolus) {
            this.basal = basal;
            this.tempBasal = tempBasal;
            this.bolus = bolus;
        }

        @Override public byte[] getEncoded() {
            BitSet bitSet = new BitSet(8);
            bitSet.set(0, this.basal);
            bitSet.set(1, this.tempBasal);
            bitSet.set(2, this.bolus);
            return bitSet.toByteArray();
        }
    }

    public static final class Builder extends NonceEnabledCommandBuilder<Builder, StopDeliveryCommand> {
        private DeliveryType deliveryType;
        private BeepType beepType = BeepType.LONG_SINGLE_BEEP;

        public Builder setDeliveryType(DeliveryType deliveryType) {
            this.deliveryType = deliveryType;
            return this;
        }

        public Builder setBeepType(BeepType beepType) {
            this.beepType = beepType;
            return this;
        }

        @Override protected final StopDeliveryCommand buildCommand() {
            if (deliveryType == null) {
                throw new IllegalArgumentException("deliveryType can not be null");
            }
            if (beepType == null) {
                throw new IllegalArgumentException("beepType can not be null");
            }
            return new StopDeliveryCommand(uniqueId, sequenceNumber, multiCommandFlag, deliveryType, beepType, nonce);
        }
    }

}
