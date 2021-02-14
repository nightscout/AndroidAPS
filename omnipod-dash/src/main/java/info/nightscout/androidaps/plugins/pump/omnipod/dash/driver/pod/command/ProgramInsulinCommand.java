package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.command;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.command.base.Command;
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.command.base.CommandType;
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.command.base.HeaderEnabledCommand;
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition.ProgramReminder;
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition.program.InsulinProgramElement;

// Always followed by one of: 0x13, 0x16, 0x17
public final class ProgramInsulinCommand extends HeaderEnabledCommand {
    private final List<InsulinProgramElement> insulinProgramElements;
    private final ProgramReminder programReminder;
    private final byte currentHalfOurEntryIndex;
    private final short remainingPulsesInCurrentHalfHourEntry;
    private final int delayUntilNextTenthPulseInUsec;
    private final Command interlockCommand;

    private static final List<CommandType> ALLOWED_INTERLOCK_COMMANDS = Arrays.asList(
            CommandType.PROGRAM_BASAL,
            CommandType.PROGRAM_TEMP_BASAL,
            CommandType.PROGRAM_BOLUS
    );

    private ProgramInsulinCommand(int address, short sequenceNumber, boolean multiCommandFlag, ProgramReminder programReminder, List<InsulinProgramElement> insulinProgramElements, byte currentHalfOurEntryIndex, short remainingPulsesInCurrentHalfHourEntry, int delayUntilNextTenthPulseInUsec, Command interlockCommand) {
        super(CommandType.PROGRAM_INSULIN, address, sequenceNumber, multiCommandFlag);
        this.insulinProgramElements = new ArrayList<>(insulinProgramElements);
        this.programReminder = programReminder;
        this.currentHalfOurEntryIndex = currentHalfOurEntryIndex;
        this.remainingPulsesInCurrentHalfHourEntry = remainingPulsesInCurrentHalfHourEntry;
        this.delayUntilNextTenthPulseInUsec = delayUntilNextTenthPulseInUsec;
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
                .put(programReminder.getEncoded()) //
                .put(currentHalfOurEntryIndex) //
                .putShort(remainingPulsesInCurrentHalfHourEntry) //
                .putInt(delayUntilNextTenthPulseInUsec);

        for (InsulinProgramElement element : insulinProgramElements) {
            commandBuffer.put(element.getEncoded());
        }

        byte[] command = commandBuffer.array();

        // TODO interlock and header

        return command;
    }

    public static final class ProgramBasalBuilder extends HeaderEnabledBuilder<ProgramBasalBuilder, ProgramInsulinCommand> {
        private List<InsulinProgramElement> insulinProgramElements;
        private ProgramReminder programReminder;
        private Byte currentHalfOurEntryIndex;
        private Short remainingPulsesInCurrentHalfHourEntry;
        private Integer delayUntilNextTenthPulseInUsec;
        private Command interlockCommand;

        public ProgramBasalBuilder setInsulinProgramElements(List<InsulinProgramElement> insulinProgramElements) {
            if (insulinProgramElements == null) {
                throw new IllegalArgumentException("insulinProgramElements can not be null");
            }
            this.insulinProgramElements = new ArrayList<>(insulinProgramElements);
            return this;
        }

        public ProgramBasalBuilder setProgramReminder(ProgramReminder programReminder) {
            this.programReminder = programReminder;
            return this;
        }

        public ProgramBasalBuilder setCurrentHalfOurEntryIndex(byte currentHalfOurEntryIndex) {
            this.currentHalfOurEntryIndex = currentHalfOurEntryIndex;
            return this;
        }

        public ProgramBasalBuilder setRemainingPulsesInCurrentHalfHourEntry(short remainingPulsesInCurrentHalfHourEntry) {
            this.remainingPulsesInCurrentHalfHourEntry = remainingPulsesInCurrentHalfHourEntry;
            return this;
        }

        public ProgramBasalBuilder setDelayUntilNextTenthPulseInUsec(Integer delayUntilNextTenthPulseInUsec) {
            this.delayUntilNextTenthPulseInUsec = delayUntilNextTenthPulseInUsec;
            return this;
        }

        public ProgramBasalBuilder setInterlockCommand(Command interlockCommand) {
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
            if (programReminder == null) {
                throw new IllegalArgumentException("programReminder can not be null");
            }
            if (currentHalfOurEntryIndex == null) {
                throw new IllegalArgumentException("currentHalfOurEntryIndex can not be null");
            }
            if (remainingPulsesInCurrentHalfHourEntry == null) {
                throw new IllegalArgumentException("remainingPulsesInCurrentHalfHourEntry can not be null");
            }
            if (delayUntilNextTenthPulseInUsec == null) {
                throw new IllegalArgumentException("durationUntilNextTenthPulseInUsec can not be null");
            }
            if (interlockCommand == null) {
                throw new IllegalArgumentException("interlockCommand can not be null");
            }
            return new ProgramInsulinCommand(address, sequenceNumber, multiCommandFlag, programReminder, insulinProgramElements, currentHalfOurEntryIndex, remainingPulsesInCurrentHalfHourEntry, delayUntilNextTenthPulseInUsec, interlockCommand);
        }
    }

    @Override public String toString() {
        return "ProgramInsulinCommand{" +
                "insulinProgramElements=" + insulinProgramElements +
                ", programReminder=" + programReminder +
                ", currentHalfOurEntryIndex=" + currentHalfOurEntryIndex +
                ", remainingPulsesInCurrentHalfHourEntry=" + remainingPulsesInCurrentHalfHourEntry +
                ", delayUntilNextTenthPulseInUsec=" + delayUntilNextTenthPulseInUsec +
                ", commandType=" + commandType +
                ", address=" + address +
                ", sequenceNumber=" + sequenceNumber +
                ", multiCommandFlag=" + multiCommandFlag +
                '}';
    }
}
