package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.command;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.command.base.Command;
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.command.base.CommandType;
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.command.base.NonceEnabledCommand;
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition.program.InsulinProgramElement;

// Always followed by one of: 0x13, 0x16, 0x17
public final class ProgramInsulinCommand extends NonceEnabledCommand {
    private final List<InsulinProgramElement> max8HourInsulinProgramElements;
    private final byte currentHalfHourEntryIndex;
    private final short remainingEighthSecondsInCurrentHalfHourEntry;
    private final short remainingPulsesInCurrentHalfHourEntry;
    private final DeliveryType deliveryType;
    private final Command interlockCommand;

    private static final List<CommandType> ALLOWED_INTERLOCK_COMMANDS = Arrays.asList(
            CommandType.PROGRAM_BASAL,
            CommandType.PROGRAM_TEMP_BASAL,
            CommandType.PROGRAM_BOLUS
    );

    private ProgramInsulinCommand(int address, short sequenceNumber, boolean multiCommandFlag, int nonce, List<InsulinProgramElement> max8HourInsulinProgramElements, byte currentHalfHourEntryIndex, short remainingEighthSecondsInCurrentHalfHourEntry, short remainingPulsesInCurrentHalfHourEntry, DeliveryType deliveryType, Command interlockCommand) {
        super(CommandType.PROGRAM_INSULIN, address, sequenceNumber, multiCommandFlag, nonce);
        this.max8HourInsulinProgramElements = new ArrayList<>(max8HourInsulinProgramElements);
        this.currentHalfHourEntryIndex = currentHalfHourEntryIndex;
        this.remainingEighthSecondsInCurrentHalfHourEntry = remainingEighthSecondsInCurrentHalfHourEntry;
        this.remainingPulsesInCurrentHalfHourEntry = remainingPulsesInCurrentHalfHourEntry;
        this.deliveryType = deliveryType;
        this.interlockCommand = interlockCommand;
    }

    public short getLength() {
        return (short) (max8HourInsulinProgramElements.size() * 6 + 10);
    }

    public byte getBodyLength() {
        return (byte) (max8HourInsulinProgramElements.size() * 6 + 8);
    }

    @Override public byte[] getEncoded() {
        ByteBuffer commandBuffer = ByteBuffer.allocate(this.getLength()) //
                .put(commandType.getValue()) //
                .put(getBodyLength()) //
                .putInt(nonce) //
                .put(deliveryType.getValue()) //
                .putShort(createChecksum()) //
                .put(currentHalfHourEntryIndex) //
                .putShort(remainingEighthSecondsInCurrentHalfHourEntry) //
                .putShort(remainingPulsesInCurrentHalfHourEntry);

        for (InsulinProgramElement element : max8HourInsulinProgramElements) {
            commandBuffer.put(element.getEncoded());
        }

        byte[] command = commandBuffer.array();

        // TODO interlock and header

        return command;
    }

    private short createChecksum() {
        return 0; // TODO
    }

    public static final class Builder extends NonceEnabledBuilder<Builder, ProgramInsulinCommand> {
        private List<InsulinProgramElement> max8HourInsulinProgramElements;
        private Byte currentHalfOurEntryIndex;
        private Short remainingEighthSecondsInCurrentHalfHourEntry;
        private Short remainingPulsesInCurrentHalfHourEntry;
        private DeliveryType deliveryType;
        private Command interlockCommand;

        public Builder setMax8HourInsulinProgramElements(List<InsulinProgramElement> max8HourInsulinProgramElements) {
            if (max8HourInsulinProgramElements == null) {
                throw new IllegalArgumentException("max8HourInsulinProgramElements can not be null");
            }
            this.max8HourInsulinProgramElements = new ArrayList<>(max8HourInsulinProgramElements);
            return this;
        }

        public Builder setCurrentHalfOurEntryIndex(byte currentHalfOurEntryIndex) {
            this.currentHalfOurEntryIndex = currentHalfOurEntryIndex;
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
            if (max8HourInsulinProgramElements == null) {
                throw new IllegalArgumentException("insulinProgramElements can not be null");
            }
            if (currentHalfOurEntryIndex == null) {
                throw new IllegalArgumentException("currentHalfOurEntryIndex can not be null");
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
            return new ProgramInsulinCommand(address, sequenceNumber, multiCommandFlag, nonce, max8HourInsulinProgramElements, currentHalfOurEntryIndex, remainingEighthSecondsInCurrentHalfHourEntry, remainingPulsesInCurrentHalfHourEntry, deliveryType, interlockCommand);
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

}
