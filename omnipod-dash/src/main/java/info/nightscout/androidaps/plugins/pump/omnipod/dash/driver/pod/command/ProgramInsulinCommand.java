package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.command;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.command.base.Command;
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.command.base.CommandType;
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.command.base.NonceEnabledCommand;
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.command.base.builder.NonceEnabledCommandBuilder;
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition.Encodable;

// Always followed by one of: 0x13, 0x16, 0x17
public final class ProgramInsulinCommand extends NonceEnabledCommand {
    private final List<InsulinProgramElement> insulinProgramElements;
    private final byte currentHalfHourEntryIndex;
    private final short checksum;
    private final short remainingEighthSecondsInCurrentHalfHourEntry;
    private final short remainingPulsesInCurrentHalfHourEntry;
    private final DeliveryType deliveryType;
    private final Command interlockCommand;

    private static final List<CommandType> ALLOWED_INTERLOCK_COMMANDS = Arrays.asList(
            CommandType.PROGRAM_BASAL,
            CommandType.PROGRAM_TEMP_BASAL,
            CommandType.PROGRAM_BOLUS
    );

    private ProgramInsulinCommand(int address, short sequenceNumber, boolean multiCommandFlag, int nonce, List<InsulinProgramElement> insulinProgramElements, byte currentHalfHourEntryIndex, short checksum, short remainingEighthSecondsInCurrentHalfHourEntry, short remainingPulsesInCurrentHalfHourEntry, DeliveryType deliveryType, Command interlockCommand) {
        super(CommandType.PROGRAM_INSULIN, address, sequenceNumber, multiCommandFlag, nonce);
        this.insulinProgramElements = new ArrayList<>(insulinProgramElements);
        this.currentHalfHourEntryIndex = currentHalfHourEntryIndex;
        this.checksum = checksum;
        this.remainingEighthSecondsInCurrentHalfHourEntry = remainingEighthSecondsInCurrentHalfHourEntry;
        this.remainingPulsesInCurrentHalfHourEntry = remainingPulsesInCurrentHalfHourEntry;
        this.deliveryType = deliveryType;
        this.interlockCommand = interlockCommand;
    }

    public short getLength() {
        return (short) (insulinProgramElements.size() * 6 + 10);
    }

    public byte getBodyLength() {
        return (byte) (insulinProgramElements.size() * 6 + 8);
    }

    @Override public byte[] getEncoded() {
        ByteBuffer commandBuffer = ByteBuffer.allocate(this.getLength()) //
                .put(commandType.getValue()) //
                .put(getBodyLength()) //
                .putInt(nonce) //
                .put(deliveryType.getValue()) //
                .putShort(checksum) //
                .put(currentHalfHourEntryIndex) //
                .putShort(remainingEighthSecondsInCurrentHalfHourEntry) //
                .putShort(remainingPulsesInCurrentHalfHourEntry);

        for (InsulinProgramElement element : insulinProgramElements) {
            commandBuffer.put(element.getEncoded());
        }

        byte[] command = commandBuffer.array();
        byte[] interlock = interlockCommand.getEncoded();
        short totalLength = (short) (command.length + interlock.length + HEADER_LENGTH);

        return ByteBuffer.allocate(totalLength) //
                .put(encodeHeader(address, sequenceNumber, totalLength, multiCommandFlag)) //
                .put(command) //
                .put(interlock) //
                .array();
    }

    public static final class Builder extends NonceEnabledCommandBuilder<Builder, ProgramInsulinCommand> {
        private List<InsulinProgramElement> insulinProgramElements;
        private Byte currentHalfOurEntryIndex;
        private Short checksum;
        private Short remainingEighthSecondsInCurrentHalfHourEntry;
        private Short remainingPulsesInCurrentHalfHourEntry;
        private DeliveryType deliveryType;
        private Command interlockCommand;

        public Builder setInsulinProgramElements(List<InsulinProgramElement> insulinProgramElements) {
            if (insulinProgramElements == null) {
                throw new IllegalArgumentException("insulinProgramElements can not be null");
            }
            this.insulinProgramElements = new ArrayList<>(insulinProgramElements);
            return this;
        }

        public Builder setCurrentHalfOurEntryIndex(byte currentHalfOurEntryIndex) {
            this.currentHalfOurEntryIndex = currentHalfOurEntryIndex;
            return this;
        }

        public Builder setChecksum(short checksum) {
            this.checksum = checksum;
            return this;
        }

        public Builder setRemainingEighthSecondsInCurrentHalfHourEntryIndex(short remainingEighthSecondsInCurrentHalfHourEntry) {
            this.remainingEighthSecondsInCurrentHalfHourEntry = remainingEighthSecondsInCurrentHalfHourEntry;
            return this;
        }

        public Builder setRemainingPulsesInCurrentHalfHourEntry(short remainingPulsesInCurrentHalfHourEntry) {
            this.remainingPulsesInCurrentHalfHourEntry = remainingPulsesInCurrentHalfHourEntry;
            return this;
        }

        public Builder setDeliveryType(DeliveryType deliveryType) {
            this.deliveryType = deliveryType;
            return this;
        }

        public Builder setInterlockCommand(Command interlockCommand) {
            if (!ALLOWED_INTERLOCK_COMMANDS.contains(interlockCommand.getCommandType())) {
                throw new IllegalArgumentException("Illegal interlock command type");
            }
            this.interlockCommand = interlockCommand;
            return this;
        }

        @Override protected final ProgramInsulinCommand buildCommand() {
            if (insulinProgramElements == null) {
                throw new IllegalArgumentException("insulinProgramElements can not be null");
            }
            if (currentHalfOurEntryIndex == null) {
                throw new IllegalArgumentException("currentHalfOurEntryIndex can not be null");
            }
            if (checksum == null) {
                throw new IllegalArgumentException("checksum can not be null");
            }
            if (remainingEighthSecondsInCurrentHalfHourEntry == null) {
                throw new IllegalArgumentException("remainingEighthSecondsInCurrentHalfHourEntry can not be null");
            }
            if (remainingPulsesInCurrentHalfHourEntry == null) {
                throw new IllegalArgumentException("remainingPulsesInCurrentHalfHourEntry can not be null");
            }
            if (deliveryType == null) {
                throw new IllegalArgumentException("deliveryType can not be null");
            }
            if (interlockCommand == null) {
                throw new IllegalArgumentException("interlockCommand can not be null");
            }
            return new ProgramInsulinCommand(address, sequenceNumber, multiCommandFlag, nonce, insulinProgramElements, currentHalfOurEntryIndex, checksum, remainingEighthSecondsInCurrentHalfHourEntry, remainingPulsesInCurrentHalfHourEntry, deliveryType, interlockCommand);
        }
    }

    public enum DeliveryType {
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
                ", currentHalfHourEntryIndex=" + currentHalfHourEntryIndex +
                ", checksum=" + checksum +
                ", remainingEighthSecondsInCurrentHalfHourEntry=" + remainingEighthSecondsInCurrentHalfHourEntry +
                ", remainingPulsesInCurrentHalfHourEntry=" + remainingPulsesInCurrentHalfHourEntry +
                ", deliveryType=" + deliveryType +
                ", interlockCommand=" + interlockCommand +
                ", nonce=" + nonce +
                ", commandType=" + commandType +
                ", address=" + address +
                ", sequenceNumber=" + sequenceNumber +
                ", multiCommandFlag=" + multiCommandFlag +
                '}';
    }

    public static class InsulinProgramElement implements Encodable {
        private final byte numberOfHalfOurEntries; // 4 bits
        private final short numberOfPulsesPerHalfOurEntry; // 10 bits
        private final boolean extraAlternatePulse;

        public InsulinProgramElement(byte numberOfHalfOurEntries, short numberOfPulsesPerHalfOurEntry, boolean extraAlternatePulse) {
            this.numberOfHalfOurEntries = numberOfHalfOurEntries;
            this.numberOfPulsesPerHalfOurEntry = numberOfPulsesPerHalfOurEntry;
            this.extraAlternatePulse = extraAlternatePulse;
        }

        @Override public byte[] getEncoded() {
            byte firstByte = (byte) ((((numberOfHalfOurEntries - 1) & 0x0f) << 4) //
                    | ((extraAlternatePulse ? 1 : 0) << 3) //
                    | ((numberOfPulsesPerHalfOurEntry >>> 8) & 0x03));

            return ByteBuffer.allocate(2) //
                    .put(firstByte) //
                    .put((byte) (numberOfPulsesPerHalfOurEntry & 0xff)) //
                    .array();
        }
    }
}
