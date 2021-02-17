package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.command;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.command.base.CommandType;
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.command.base.NonceEnabledCommand;
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.command.insulin.program.ShortInsulinProgramElement;

// Always followed by one of: 0x13, 0x16, 0x17
final class ProgramInsulinCommand extends NonceEnabledCommand {
    private final List<ShortInsulinProgramElement> insulinProgramElements;
    private final byte currentSlot;
    private final short checksum;
    private final short remainingEighthSecondsInCurrentSlot;
    private final short remainingPulsesInCurrentSlot;
    private final DeliveryType deliveryType;

    ProgramInsulinCommand(int uniqueId, short sequenceNumber, boolean multiCommandFlag, int nonce, List<ShortInsulinProgramElement> insulinProgramElements, byte currentSlot, short checksum, short remainingEighthSecondsInCurrentSlot, short remainingPulsesInCurrentSlot, DeliveryType deliveryType) {
        super(CommandType.PROGRAM_INSULIN, uniqueId, sequenceNumber, multiCommandFlag, nonce);
        this.insulinProgramElements = new ArrayList<>(insulinProgramElements);
        this.currentSlot = currentSlot;
        this.checksum = checksum;
        this.remainingEighthSecondsInCurrentSlot = remainingEighthSecondsInCurrentSlot;
        this.remainingPulsesInCurrentSlot = remainingPulsesInCurrentSlot;
        this.deliveryType = deliveryType;
    }

    public short getLength() {
        return (short) (insulinProgramElements.size() * 2 + 14);
    }

    public byte getBodyLength() {
        return (byte) (insulinProgramElements.size() * 2 + 12);
    }

    @Override public byte[] getEncoded() {
        ByteBuffer buffer = ByteBuffer.allocate(this.getLength()) //
                .put(commandType.getValue()) //
                .put(getBodyLength()) //
                .putInt(nonce) //
                .put(deliveryType.getValue()) //
                .putShort(checksum) //
                .put(currentSlot) //
                .putShort(remainingEighthSecondsInCurrentSlot) //
                .putShort(remainingPulsesInCurrentSlot);

        for (ShortInsulinProgramElement element : insulinProgramElements) {
            buffer.put(element.getEncoded());
        }

        return buffer.array();
    }

    enum DeliveryType {
        BASAL((byte) 0x00),
        TEMP_BASAL((byte) 0x01),
        BOLUS((byte) 0x02);

        private final byte value;

        DeliveryType(byte value) {
            this.value = value;
        }

        public byte getValue() {
            return value;
        }
    }

    @Override public String toString() {
        return "ProgramInsulinCommand{" +
                "insulinProgramElements=" + insulinProgramElements +
                ", currentSlot=" + currentSlot +
                ", checksum=" + checksum +
                ", remainingEighthSecondsInCurrentSlot=" + remainingEighthSecondsInCurrentSlot +
                ", remainingPulsesInCurrentSlot=" + remainingPulsesInCurrentSlot +
                ", deliveryType=" + deliveryType +
                ", nonce=" + nonce +
                ", commandType=" + commandType +
                ", uniqueId=" + uniqueId +
                ", sequenceNumber=" + sequenceNumber +
                ", multiCommandFlag=" + multiCommandFlag +
                '}';
    }

}
