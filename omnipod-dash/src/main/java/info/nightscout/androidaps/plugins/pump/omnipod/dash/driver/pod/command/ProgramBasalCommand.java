package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.command;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition.ProgramReminder;
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition.program.InsulinProgramElement;

public final class ProgramBasalCommand extends CommandBase {
    private final List<InsulinProgramElement> insulinProgramElements;
    private final ProgramReminder programReminder;
    private final byte currentHalfOurEntryIndex;
    private final short remainingPulsesInCurrentHalfHourEntry;
    private final int delayUntilNextTenthPulseInUsec;

    private ProgramBasalCommand(int address, short sequenceNumber, boolean multiCommandFlag, ProgramReminder programReminder, List<InsulinProgramElement> insulinProgramElements, byte currentHalfOurEntryIndex, short remainingPulsesInCurrentHalfHourEntry, int delayUntilNextTenthPulseInUsec) {
        super(CommandType.PROGRAM_BASAL, address, sequenceNumber, multiCommandFlag);
        this.insulinProgramElements = new ArrayList<>(insulinProgramElements);
        this.programReminder = programReminder;
        this.currentHalfOurEntryIndex = currentHalfOurEntryIndex;
        this.remainingPulsesInCurrentHalfHourEntry = remainingPulsesInCurrentHalfHourEntry;
        this.delayUntilNextTenthPulseInUsec = delayUntilNextTenthPulseInUsec;
    }

    public byte getBodyLength() {
        return (byte) (insulinProgramElements.size() * 6 + 8);
    }

    @Override public byte[] getEncoded() {
        ByteBuffer basalCommandBuffer = ByteBuffer.allocate(this.getBodyLength()) //
                .put(commandType.getValue()) //
                .put(getBodyLength()) //
                .put(programReminder.getEncoded()) //
                .put(currentHalfOurEntryIndex) //
                .putShort(remainingPulsesInCurrentHalfHourEntry) //
                .putInt(delayUntilNextTenthPulseInUsec);

        for (InsulinProgramElement element : insulinProgramElements) {
            basalCommandBuffer.put(element.getEncoded());
        }

        byte[] basalCommand = basalCommandBuffer.array();

        // TODO basal interlock and header

        return basalCommand;
    }

    public static final class Builder extends CommandBase.Builder<Builder, ProgramBasalCommand> {
        private List<InsulinProgramElement> insulinProgramElements;
        private ProgramReminder programReminder;
        private Byte currentHalfOurEntryIndex;
        private Short remainingPulsesInCurrentHalfHourEntry;
        private Integer delayUntilNextTenthPulseInUsec;

        public Builder setInsulinProgramElements(List<InsulinProgramElement> insulinProgramElements) {
            if (insulinProgramElements == null) {
                throw new IllegalArgumentException("insulinProgramElements can not be null");
            }
            this.insulinProgramElements = new ArrayList<>(insulinProgramElements);
            return this;
        }

        public Builder setProgramReminder(ProgramReminder programReminder) {
            this.programReminder = programReminder;
            return this;
        }

        public Builder setCurrentHalfOurEntryIndex(byte currentHalfOurEntryIndex) {
            this.currentHalfOurEntryIndex = currentHalfOurEntryIndex;
            return this;
        }

        public Builder setRemainingPulsesInCurrentHalfHourEntry(short remainingPulsesInCurrentHalfHourEntry) {
            this.remainingPulsesInCurrentHalfHourEntry = remainingPulsesInCurrentHalfHourEntry;
            return this;
        }

        public Builder setDelayUntilNextTenthPulseInUsec(Integer delayUntilNextTenthPulseInUsec) {
            this.delayUntilNextTenthPulseInUsec = delayUntilNextTenthPulseInUsec;
            return this;
        }

        @Override final ProgramBasalCommand buildCommand() {
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
            return new ProgramBasalCommand(address, sequenceNumber, multiCommandFlag, programReminder, insulinProgramElements, currentHalfOurEntryIndex, remainingPulsesInCurrentHalfHourEntry, delayUntilNextTenthPulseInUsec);
        }
    }

}
