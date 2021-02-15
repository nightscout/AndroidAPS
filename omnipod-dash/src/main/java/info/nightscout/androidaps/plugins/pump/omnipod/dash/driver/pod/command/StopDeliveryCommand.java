package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.command;

import java.nio.ByteBuffer;
import java.util.BitSet;

import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition.BeepType;

public class StopDeliveryCommand extends CommandBase {
    private static final short LENGTH = 7;
    private static final byte BODY_LENGTH = 5;

    private final DeliveryType deliveryType;
    private final BeepType beepType;

    public StopDeliveryCommand(int address, short sequenceNumber, boolean unknown, DeliveryType deliveryType, BeepType beepType) {
        super(CommandType.STOP_DELIVERY, address, sequenceNumber, unknown);
        this.deliveryType = deliveryType;
        this.beepType = beepType;
    }

    @Override public byte[] getEncoded() {
        return appendCrc(ByteBuffer.allocate(LENGTH + HEADER_LENGTH) //
                .put(encodeHeader(address, sequenceNumber, LENGTH, unknown)) //
                .put(commandType.getValue()) //
                .put(BODY_LENGTH) //
                .putInt(1229869870) // FIXME ?? was: byte array of int 777211465 converted to little endian
                .put((byte) ((beepType.getValue() << 4) | deliveryType.getEncoded())) //
                .array());
    }

    public enum DeliveryType {
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

        public byte getEncoded() {
            BitSet bitSet = new BitSet(8);
            bitSet.set(0, this.basal);
            bitSet.set(1, this.tempBasal);
            bitSet.set(2, this.bolus);
            return bitSet.toByteArray()[0];
        }
    }
}
