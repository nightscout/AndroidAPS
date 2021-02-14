package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.command;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.command.base.Command;
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.command.base.CommandType;
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.command.base.builder.CommandBuilder;
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition.Encodable;
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition.ProgramReminder;

// Always preceded by 0x1a ProgramInsulinCommand
public final class ProgramBasalCommand implements Command {
    private final List<InsulinProgramElement> insulinProgramElements;
    private final ProgramReminder programReminder;
    private final byte currentInsulinProgramElementIndex;
    private final short remainingTenthPulsesInCurrentInsulinProgramElement;
    private final int delayUntilNextTenthPulseInUsec;

    private ProgramBasalCommand(List<InsulinProgramElement> insulinProgramElements, ProgramReminder programReminder, byte currentInsulinProgramElementIndex, short remainingTenthPulsesInCurrentInsulinProgramElement, int delayUntilNextTenthPulseInUsec) {
        this.insulinProgramElements = new ArrayList<>(insulinProgramElements);
        this.programReminder = programReminder;
        this.currentInsulinProgramElementIndex = currentInsulinProgramElementIndex;
        this.remainingTenthPulsesInCurrentInsulinProgramElement = remainingTenthPulsesInCurrentInsulinProgramElement;
        this.delayUntilNextTenthPulseInUsec = delayUntilNextTenthPulseInUsec;
    }

    public short getLength() {
        return (short) (insulinProgramElements.size() * 2 + 14);
    }

    public byte getBodyLength() {
        return (byte) (insulinProgramElements.size() * 2 + 12);
    }

    @Override public byte[] getEncoded() {
        ByteBuffer buffer = ByteBuffer.allocate(getLength()) //
                .put(getCommandType().getValue()) //
                .put(getBodyLength()) //
                .put(programReminder.getEncoded()) //
                .put(currentInsulinProgramElementIndex) //
                .putShort(remainingTenthPulsesInCurrentInsulinProgramElement) //
                .putInt(delayUntilNextTenthPulseInUsec);
        for (InsulinProgramElement insulinProgramElement : insulinProgramElements) {
            buffer.put(insulinProgramElement.getEncoded());
        }
        return buffer.array();
    }

    @Override public CommandType getCommandType() {
        return CommandType.PROGRAM_BASAL;
    }

    @Override public String toString() {
        return "ProgramBasalCommand{" +
                "uniqueInsulinProgramElements=" + insulinProgramElements +
                ", programReminder=" + programReminder +
                ", currentInsulinProgramElementIndex=" + currentInsulinProgramElementIndex +
                ", remainingTenthPulsesInCurrentInsulinProgramElement=" + remainingTenthPulsesInCurrentInsulinProgramElement +
                ", delayUntilNextTenthPulseInUsec=" + delayUntilNextTenthPulseInUsec +
                '}';
    }

    public static class InsulinProgramElement implements Encodable {
        private final short totalTenthPulses;
        private final int delayBetweenTenthPulses;

        public InsulinProgramElement(byte totalTenthPulses, short delayBetweenTenthPulses) {
            this.totalTenthPulses = totalTenthPulses;
            this.delayBetweenTenthPulses = delayBetweenTenthPulses;
        }

        @Override public byte[] getEncoded() {
            return ByteBuffer.allocate(6) //
                    .putShort(totalTenthPulses) //
                    .putInt(delayBetweenTenthPulses) //
                    .array();
        }
    }

    public static final class Builder implements CommandBuilder<ProgramBasalCommand> {
        private List<InsulinProgramElement> insulinProgramElements;
        private ProgramReminder programReminder;
        private Byte currentInsulinProgramElementIndex;
        private Short remainingTenthPulsesInCurrentInsulinProgramElement;
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

        public Builder setCurrentInsulinProgramElementIndex(Byte currentInsulinProgramElementIndex) {
            this.currentInsulinProgramElementIndex = currentInsulinProgramElementIndex;
            return this;
        }

        public Builder setRemainingTenthPulsesInCurrentInsulinProgramElement(Short remainingTenthPulsesInCurrentInsulinProgramElement) {
            this.remainingTenthPulsesInCurrentInsulinProgramElement = remainingTenthPulsesInCurrentInsulinProgramElement;
            return this;
        }

        public Builder setDelayUntilNextTenthPulseInUsec(Integer delayUntilNextTenthPulseInUsec) {
            this.delayUntilNextTenthPulseInUsec = delayUntilNextTenthPulseInUsec;
            return this;
        }

        @Override public ProgramBasalCommand build() {
            if (insulinProgramElements == null) {
                throw new IllegalArgumentException("insulinProgramElements can not be null");
            }
            if (programReminder == null) {
                throw new IllegalArgumentException("programReminder can not be null");
            }
            if (currentInsulinProgramElementIndex == null) {
                throw new IllegalArgumentException("currentInsulinProgramElementIndex can not be null");
            }
            if (remainingTenthPulsesInCurrentInsulinProgramElement == null) {
                throw new IllegalArgumentException("remainingTenthPulsesInCurrentInsulinProgramElement can not be null");
            }
            if (delayUntilNextTenthPulseInUsec == null) {
                throw new IllegalArgumentException("delayUntilNextTenthPulseInUsec can not be null");
            }
            return new ProgramBasalCommand(insulinProgramElements, programReminder, currentInsulinProgramElementIndex, remainingTenthPulsesInCurrentInsulinProgramElement, delayUntilNextTenthPulseInUsec);
        }
    }
}
