package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.command;

import java.nio.ByteBuffer;
import java.util.Collections;

import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.command.base.CommandType;
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.command.base.HeaderEnabledCommand;
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.command.base.builder.NonceEnabledCommandBuilder;
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.command.insulin.program.BolusShortInsulinProgramElement;
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition.ProgramReminder;
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.util.MessageUtil;

// NOT SUPPORTED: extended bolus
public final class ProgramBolusCommand extends HeaderEnabledCommand {
    private static final short LENGTH = 15;
    private static final byte BODY_LENGTH = 13;

    private final ProgramInsulinCommand interlockCommand;
    private final ProgramReminder programReminder;
    private final short numberOfTenthPulses;
    private final int delayUntilFirstTenthPulseInUsec;

    ProgramBolusCommand(ProgramInsulinCommand interlockCommand, int uniqueId, short sequenceNumber, boolean multiCommandFlag, ProgramReminder programReminder, short numberOfTenthPulses, int delayUntilFirstTenthPulseInUsec) {
        super(CommandType.PROGRAM_BOLUS, uniqueId, sequenceNumber, multiCommandFlag);
        this.interlockCommand = interlockCommand;
        this.programReminder = programReminder;
        this.numberOfTenthPulses = numberOfTenthPulses;
        this.delayUntilFirstTenthPulseInUsec = delayUntilFirstTenthPulseInUsec;
    }

    @Override public byte[] getEncoded() {
        byte[] bolusCommand = ByteBuffer.allocate(LENGTH) //
                .put(commandType.getValue()) //
                .put(BODY_LENGTH) //
                .put(programReminder.getEncoded()) //
                .putShort(numberOfTenthPulses) //
                .putInt(delayUntilFirstTenthPulseInUsec) //
                .putShort((short) 0) // Extended bolus pulses
                .putInt(0) // Delay between tenth extended pulses in usec
                .array();

        byte[] interlockCommand = this.interlockCommand.getEncoded();
        byte[] header = encodeHeader(uniqueId, sequenceNumber, (short) (bolusCommand.length + interlockCommand.length), multiCommandFlag);

        return appendCrc(ByteBuffer.allocate(header.length + interlockCommand.length + bolusCommand.length) //
                .put(header) //
                .put(interlockCommand) //
                .put(bolusCommand) //
                .array());
    }

    @Override public String toString() {
        return "ProgramBolusCommand{" +
                "interlockCommand=" + interlockCommand +
                ", programReminder=" + programReminder +
                ", numberOfTenthPulses=" + numberOfTenthPulses +
                ", delayUntilFirstTenthPulseInUsec=" + delayUntilFirstTenthPulseInUsec +
                ", commandType=" + commandType +
                ", uniqueId=" + uniqueId +
                ", sequenceNumber=" + sequenceNumber +
                ", multiCommandFlag=" + multiCommandFlag +
                '}';
    }

    public static final class Builder extends NonceEnabledCommandBuilder<Builder, ProgramBolusCommand> {
        private Double numberOfUnits;
        private Byte delayBetweenPulsesInEighthSeconds;
        private ProgramReminder programReminder;

        public Builder setNumberOfUnits(double numberOfUnits) {
            if (numberOfUnits <= 0.0D) {
                throw new IllegalArgumentException("Number of units should be greater than zero");
            }
            if ((int) (numberOfUnits * 1000) % 50 != 0) {
                throw new IllegalArgumentException("Number of units must be dividable by 0.05");
            }
            this.numberOfUnits = ((int) (numberOfUnits * 100)) / 100.0d;
            return this;
        }

        public Builder setDelayBetweenPulsesInEighthSeconds(byte delayBetweenPulsesInEighthSeconds) {
            this.delayBetweenPulsesInEighthSeconds = delayBetweenPulsesInEighthSeconds;
            return this;
        }

        public Builder setProgramReminder(ProgramReminder programReminder) {
            this.programReminder = programReminder;
            return this;
        }

        @Override protected ProgramBolusCommand buildCommand() {
            if (numberOfUnits == null) {
                throw new IllegalArgumentException("numberOfUnits can not be null");
            }
            if (delayBetweenPulsesInEighthSeconds == null) {
                throw new IllegalArgumentException("delayBetweenPulsesInEighthSeconds can not be null");
            }
            if (programReminder == null) {
                throw new IllegalArgumentException("programReminder can not be null");
            }

            short numberOfPulses = (short) Math.round(numberOfUnits * 20);
            short byte10And11 = (short) (numberOfPulses * delayBetweenPulsesInEighthSeconds);

            ProgramInsulinCommand interlockCommand = new ProgramInsulinCommand(uniqueId, sequenceNumber, multiCommandFlag, nonce,
                    Collections.singletonList(new BolusShortInsulinProgramElement(numberOfPulses)), calculateChecksum((byte) 0x01, byte10And11, numberOfPulses),
                    (byte) 0x01, byte10And11, (short) numberOfPulses, ProgramInsulinCommand.DeliveryType.BOLUS);

            int delayUntilFirstTenthPulseInUsec = delayBetweenPulsesInEighthSeconds / 8 * 100_000;

            return new ProgramBolusCommand(interlockCommand, uniqueId, sequenceNumber, multiCommandFlag, programReminder, (short) (numberOfPulses * 10), delayUntilFirstTenthPulseInUsec);
        }
    }

    private static short calculateChecksum(byte numberOfSlots, short byte10And11, short numberOfPulses) {
        return MessageUtil.calculateChecksum(ByteBuffer.allocate(7) //
                .put(numberOfSlots) //
                .putShort(byte10And11) //
                .putShort(numberOfPulses) //
                .putShort(numberOfPulses) //
                .array());
    }
}
