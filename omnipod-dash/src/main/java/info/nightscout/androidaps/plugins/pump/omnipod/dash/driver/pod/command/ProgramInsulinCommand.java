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
    private final short checksum;
    private final byte byte9;
    private final short byte10And11;
    private final short byte12And13;
    private final DeliveryType deliveryType;

    ProgramInsulinCommand(int uniqueId, short sequenceNumber, boolean multiCommandFlag, int nonce, List<ShortInsulinProgramElement> insulinProgramElements, short checksum, byte byte9, short byte10And11, short byte12And13, DeliveryType deliveryType) {
        super(CommandType.PROGRAM_INSULIN, uniqueId, sequenceNumber, multiCommandFlag, nonce);
        this.insulinProgramElements = new ArrayList<>(insulinProgramElements);
        this.checksum = checksum;
        this.byte9 = byte9;
        this.byte10And11 = byte10And11;
        this.byte12And13 = byte12And13;
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
                .put(byte9) // BASAL: currentSlot // BOLUS: number of ShortInsulinProgramElements
                .putShort(byte10And11) // BASAL: remainingEighthSecondsInCurrentSlot // BOLUS: immediate pulses multiplied by delay between pulses in eighth seconds
                .putShort(byte12And13); // BASAL: remainingPulsesInCurrentSlot // BOLUS: immediate pulses

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

    public short calculateChecksum(byte[] bytes) {
        short sum = 0;
        for (byte b : bytes) {
            sum += (short) (b & 0xff);
        }
        return sum;
    }

    @Override public String toString() {
        return "ProgramInsulinCommand{" +
                "insulinProgramElements=" + insulinProgramElements +
                ", checksum=" + checksum +
                ", byte9=" + byte9 +
                ", byte10And11=" + byte10And11 +
                ", byte12And13=" + byte12And13 +
                ", deliveryType=" + deliveryType +
                ", nonce=" + nonce +
                ", commandType=" + commandType +
                ", uniqueId=" + uniqueId +
                ", sequenceNumber=" + sequenceNumber +
                ", multiCommandFlag=" + multiCommandFlag +
                '}';
    }

}
