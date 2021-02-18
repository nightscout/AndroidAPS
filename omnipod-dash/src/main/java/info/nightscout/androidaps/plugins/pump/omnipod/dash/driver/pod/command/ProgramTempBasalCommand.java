package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.command;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.command.base.CommandType;
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.command.base.HeaderEnabledCommand;
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.command.base.builder.NonceEnabledCommandBuilder;
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.command.insulin.program.BasalInsulinProgramElement;
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.command.insulin.program.ShortInsulinProgramElement;
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.command.insulin.program.util.ProgramBasalUtil;
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.command.insulin.program.util.ProgramTempBasalUtil;
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition.ProgramReminder;

// NOT SUPPORTED: percentage temp basal
public final class ProgramTempBasalCommand extends HeaderEnabledCommand {
    private final ProgramInsulinCommand interlockCommand;
    private final ProgramReminder programReminder;
    private final List<BasalInsulinProgramElement> insulinProgramElements;

    protected ProgramTempBasalCommand(ProgramInsulinCommand interlockCommand, int uniqueId, short sequenceNumber, boolean multiCommandFlag,
                                      ProgramReminder programReminder, List<BasalInsulinProgramElement> insulinProgramElements) {
        super(CommandType.PROGRAM_TEMP_BASAL, uniqueId, sequenceNumber, multiCommandFlag);
        this.interlockCommand = interlockCommand;
        this.programReminder = programReminder;
        this.insulinProgramElements = new ArrayList<>(insulinProgramElements);
    }

    public byte getBodyLength() {
        return (byte) (insulinProgramElements.size() * 6 + 8);
    }

    public short getLength() {
        return (short) (getBodyLength() + 2);
    }

    @Override public byte[] getEncoded() {
        BasalInsulinProgramElement firstProgramElement = insulinProgramElements.get(0);

        short remainingTenthPulsesInFirstElement;
        int delayUntilNextTenthPulseInUsec;

        if (firstProgramElement.getTotalTenthPulses() == 0) {
            remainingTenthPulsesInFirstElement = firstProgramElement.getNumberOfSlots();
            delayUntilNextTenthPulseInUsec = ProgramBasalUtil.MAX_DELAY_BETWEEN_TENTH_PULSES_IN_USEC_AND_USECS_IN_BASAL_SLOT;
        } else {
            remainingTenthPulsesInFirstElement = firstProgramElement.getTotalTenthPulses();
            delayUntilNextTenthPulseInUsec = (int) ((long) firstProgramElement.getNumberOfSlots() * 1_800.0d / remainingTenthPulsesInFirstElement * 1_000_000);
        }

        ByteBuffer buffer = ByteBuffer.allocate(getLength()) //
                .put(commandType.getValue()) //
                .put(getBodyLength()) //
                .put(programReminder.getEncoded()) //
                .put((byte) 0x00) // Current slot index
                .putShort(remainingTenthPulsesInFirstElement) //
                .putInt(delayUntilNextTenthPulseInUsec);

        for (BasalInsulinProgramElement element : insulinProgramElements) {
            buffer.put(element.getEncoded());
        }

        byte[] tempBasalCommand = buffer.array();
        byte[] interlockCommand = this.interlockCommand.getEncoded();
        byte[] header = encodeHeader(uniqueId, sequenceNumber, (short) (tempBasalCommand.length + interlockCommand.length), multiCommandFlag);

        return appendCrc(ByteBuffer.allocate(header.length + interlockCommand.length + tempBasalCommand.length) //
                .put(header) //
                .put(interlockCommand) //
                .put(tempBasalCommand) //
                .array());
    }

    public static class Builder extends NonceEnabledCommandBuilder<Builder, ProgramTempBasalCommand> {
        private ProgramReminder programReminder;
        private Double rateInUnitsPerHour;
        private Short durationInMinutes;

        public Builder setProgramReminder(ProgramReminder programReminder) {
            this.programReminder = programReminder;
            return this;
        }

        public Builder setRateInUnitsPerHour(double rateInUnitsPerHour) {
            this.rateInUnitsPerHour = rateInUnitsPerHour;
            return this;
        }

        public Builder setDurationInMinutes(short durationInMinutes) {
            if (durationInMinutes % 30 != 0) {
                throw new IllegalArgumentException("durationInMinutes must be dividable by 30");
            }
            this.durationInMinutes = durationInMinutes;
            return this;
        }

        @Override protected ProgramTempBasalCommand buildCommand() {
            if (programReminder == null) {
                throw new IllegalArgumentException("programReminder can not be null");
            }
            if (rateInUnitsPerHour == null) {
                throw new IllegalArgumentException("rateInUnitsPerHour can not be null");
            }
            if (durationInMinutes == null) {
                throw new IllegalArgumentException("durationInMinutes can not be null");
            }

            byte durationInSlots = (byte) (durationInMinutes / 30);
            short[] pulsesPerSlot = ProgramTempBasalUtil.mapTempBasalToPulsesPerSlot(durationInSlots, rateInUnitsPerHour);
            short[] tenthPulsesPerSlot = ProgramTempBasalUtil.mapTempBasalToTenthPulsesPerSlot(durationInSlots, rateInUnitsPerHour);

            List<ShortInsulinProgramElement> shortInsulinProgramElements = ProgramTempBasalUtil.mapPulsesPerSlotToShortInsulinProgramElements(pulsesPerSlot);
            List<BasalInsulinProgramElement> insulinProgramElements = ProgramTempBasalUtil.mapTenthPulsesPerSlotToLongInsulinProgramElements(tenthPulsesPerSlot);

            ProgramInsulinCommand interlockCommand = new ProgramInsulinCommand(uniqueId, sequenceNumber, multiCommandFlag, nonce, shortInsulinProgramElements,
                    ProgramTempBasalUtil.calculateChecksum(durationInSlots, pulsesPerSlot[0], pulsesPerSlot), durationInSlots,
                    (short) 0x3840, pulsesPerSlot[0], ProgramInsulinCommand.DeliveryType.TEMP_BASAL);

            return new ProgramTempBasalCommand(interlockCommand, uniqueId, sequenceNumber, multiCommandFlag, programReminder, insulinProgramElements);
        }
    }
}
