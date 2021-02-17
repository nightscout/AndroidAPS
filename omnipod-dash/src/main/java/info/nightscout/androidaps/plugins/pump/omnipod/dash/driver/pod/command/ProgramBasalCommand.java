package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.command;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.command.base.CommandType;
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.command.base.HeaderEnabledCommand;
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.command.base.builder.NonceEnabledCommandBuilder;
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.command.insulin.program.CurrentLongInsulinProgramElement;
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.command.insulin.program.CurrentSlot;
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.command.insulin.program.LongInsulinProgramElement;
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.command.insulin.program.ProgramBasalUtil;
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.command.insulin.program.ShortInsulinProgramElement;
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition.BasalProgram;
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition.ProgramReminder;

// Always preceded by 0x1a ProgramInsulinCommand
public final class ProgramBasalCommand extends HeaderEnabledCommand {
    private final ProgramInsulinCommand interlockCommand;
    private final List<LongInsulinProgramElement> insulinProgramElements;
    private final ProgramReminder programReminder;
    private final byte currentInsulinProgramElementIndex;
    private final short remainingTenthPulsesInCurrentInsulinProgramElement;
    private final int delayUntilNextTenthPulseInUsec;

    ProgramBasalCommand(ProgramInsulinCommand interlockCommand, int uniqueId, short sequenceNumber, boolean multiCommandFlag, List<LongInsulinProgramElement> insulinProgramElements, ProgramReminder programReminder, byte currentInsulinProgramElementIndex, short remainingTenthPulsesInCurrentInsulinProgramElement, int delayUntilNextTenthPulseInUsec) {
        super(CommandType.PROGRAM_BASAL, uniqueId, sequenceNumber, multiCommandFlag);

        this.interlockCommand = interlockCommand;
        this.insulinProgramElements = new ArrayList<>(insulinProgramElements);
        this.programReminder = programReminder;
        this.currentInsulinProgramElementIndex = currentInsulinProgramElementIndex;
        this.remainingTenthPulsesInCurrentInsulinProgramElement = remainingTenthPulsesInCurrentInsulinProgramElement;
        this.delayUntilNextTenthPulseInUsec = delayUntilNextTenthPulseInUsec;
    }

    short getLength() {
        return (short) (insulinProgramElements.size() * 6 + 10);
    }

    byte getBodyLength() {
        return (byte) (insulinProgramElements.size() * 6 + 8);
    }

    @Override public byte[] getEncoded() {
        ByteBuffer buffer = ByteBuffer.allocate(getLength()) //
                .put(getCommandType().getValue()) //
                .put(getBodyLength()) //
                .put(programReminder.getEncoded()) //
                .put(currentInsulinProgramElementIndex) //
                .putShort(remainingTenthPulsesInCurrentInsulinProgramElement) //
                .putInt(delayUntilNextTenthPulseInUsec);
        for (LongInsulinProgramElement insulinProgramElement : insulinProgramElements) {
            buffer.put(insulinProgramElement.getEncoded());
        }

        byte[] bolusCommand = buffer.array();
        byte[] interlockCommand = this.interlockCommand.getEncoded();
        byte[] header = encodeHeader(uniqueId, sequenceNumber, (short) (bolusCommand.length + interlockCommand.length), multiCommandFlag);

        return appendCrc(ByteBuffer.allocate(bolusCommand.length + interlockCommand.length + header.length) //
                .put(header) //
                .put(interlockCommand) //
                .put(bolusCommand) //
                .array());
    }

    @Override public String toString() {
        return "ProgramBasalCommand{" +
                "interlockCommand=" + interlockCommand +
                ", insulinProgramElements=" + insulinProgramElements +
                ", programReminder=" + programReminder +
                ", currentInsulinProgramElementIndex=" + currentInsulinProgramElementIndex +
                ", remainingTenthPulsesInCurrentInsulinProgramElement=" + remainingTenthPulsesInCurrentInsulinProgramElement +
                ", delayUntilNextTenthPulseInUsec=" + delayUntilNextTenthPulseInUsec +
                ", commandType=" + commandType +
                ", uniqueId=" + uniqueId +
                ", sequenceNumber=" + sequenceNumber +
                ", multiCommandFlag=" + multiCommandFlag +
                '}';
    }

    public static final class Builder extends NonceEnabledCommandBuilder<Builder, ProgramBasalCommand> {
        private BasalProgram basalProgram;
        private ProgramReminder programReminder;
        private Date currentTime;

        public Builder setBasalProgram(BasalProgram basalProgram) {
            this.basalProgram = basalProgram;
            return this;
        }

        public Builder setProgramReminder(ProgramReminder programReminder) {
            this.programReminder = programReminder;
            return this;
        }

        public Builder setCurrentTime(Date currentTime) {
            this.currentTime = currentTime;
            return this;
        }

        @Override protected ProgramBasalCommand buildCommand() {
            if (basalProgram == null) {
                throw new IllegalArgumentException("basalProgram can not be null");
            }
            if (programReminder == null) {
                throw new IllegalArgumentException("programReminder can not be null");
            }
            if (currentTime == null) {
                throw new IllegalArgumentException("currentTime can not be null");
            }

            short[] pulsesPerSlot = ProgramBasalUtil.mapBasalProgramToPulsesPerSlot(basalProgram);
            CurrentSlot currentSlot = ProgramBasalUtil.calculateCurrentSlot(pulsesPerSlot, currentTime);
            short checksum = ProgramBasalUtil.createChecksum(pulsesPerSlot, currentSlot);
            List<LongInsulinProgramElement> longInsulinProgramElements = ProgramBasalUtil.mapPulsesPerSlotToLongInsulinProgramElements(ProgramBasalUtil.mapBasalProgramToTenthPulsesPerSlot(basalProgram));
            List<ShortInsulinProgramElement> shortInsulinProgramElements = ProgramBasalUtil.mapPulsesPerSlotToShortInsulinProgramElements(pulsesPerSlot);
            CurrentLongInsulinProgramElement currentLongInsulinProgramElement = ProgramBasalUtil.calculateCurrentLongInsulinProgramElement(longInsulinProgramElements, currentTime);

            ProgramInsulinCommand interlockCommand = new ProgramInsulinCommand(uniqueId, sequenceNumber, multiCommandFlag, nonce,
                    shortInsulinProgramElements, currentSlot.getIndex(), checksum, currentSlot.getEighthSecondsRemaining(),
                    currentSlot.getPulsesRemaining(), ProgramInsulinCommand.DeliveryType.BASAL);

            return new ProgramBasalCommand(interlockCommand, uniqueId, sequenceNumber, multiCommandFlag,
                    longInsulinProgramElements, programReminder, currentLongInsulinProgramElement.getIndex(),
                    currentLongInsulinProgramElement.getRemainingTenthPulses(), currentLongInsulinProgramElement.getDelayUntilNextTenthPulseInUsec());
        }
    }
}
