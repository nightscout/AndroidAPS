package app.aaps.pump.omnipod.eros.driver.communication.message.command;

import androidx.annotation.NonNull;

import org.joda.time.Duration;

import app.aaps.core.utils.pump.ByteUtil;
import app.aaps.pump.omnipod.eros.driver.communication.message.MessageBlock;
import app.aaps.pump.omnipod.eros.driver.definition.MessageBlockType;
import app.aaps.pump.omnipod.eros.driver.definition.OmnipodConstants;

public class BolusExtraCommand extends MessageBlock {
    private final boolean acknowledgementBeep;
    private final boolean completionBeep;
    private final Duration programReminderInterval;
    private final double units;
    private final Duration timeBetweenPulses;
    private final double squareWaveUnits;
    private final Duration squareWaveDuration;

    public BolusExtraCommand(double units, boolean acknowledgementBeep, boolean completionBeep) {
        this(units, Duration.standardSeconds(2), acknowledgementBeep, completionBeep);
    }

    public BolusExtraCommand(double units, Duration timeBetweenPulses, boolean acknowledgementBeep, boolean completionBeep) {
        this(units, 0.0, Duration.ZERO, acknowledgementBeep, completionBeep, Duration.ZERO, timeBetweenPulses);
    }

    public BolusExtraCommand(double units, double squareWaveUnits, Duration squareWaveDuration,
                             boolean acknowledgementBeep, boolean completionBeep,
                             Duration programReminderInterval, Duration timeBetweenPulses) {
        if (units <= 0D) {
            throw new IllegalArgumentException("Units should be > 0");
        } else if (units > OmnipodConstants.MAX_BOLUS) {
            throw new IllegalArgumentException("Units exceeds max bolus");
        }
        this.units = units;
        this.squareWaveUnits = squareWaveUnits;
        this.squareWaveDuration = squareWaveDuration;
        this.acknowledgementBeep = acknowledgementBeep;
        this.completionBeep = completionBeep;
        this.programReminderInterval = programReminderInterval;
        this.timeBetweenPulses = timeBetweenPulses;
        encode();
    }

    private void encode() {
        byte beepOptions = (byte) ((programReminderInterval.getStandardMinutes() & 0x3f) + (completionBeep ? 1 << 6 : 0) + (acknowledgementBeep ? 1 << 7 : 0));

        int squareWavePulseCountCountX10 = (int) Math.round(squareWaveUnits * 200);
        int timeBetweenExtendedPulses = squareWavePulseCountCountX10 > 0 ? (int) squareWaveDuration.getMillis() * 100 / squareWavePulseCountCountX10 : 0;

        encodedData = ByteUtil.INSTANCE.concat(encodedData, beepOptions);
        encodedData = ByteUtil.INSTANCE.concat(encodedData, ByteUtil.INSTANCE.getBytesFromInt16((int) Math.round(units * 200)));
        encodedData = ByteUtil.INSTANCE.concat(encodedData, ByteUtil.INSTANCE.getBytesFromInt((int) timeBetweenPulses.getMillis() * 100));
        encodedData = ByteUtil.INSTANCE.concat(encodedData, ByteUtil.INSTANCE.getBytesFromInt16(squareWavePulseCountCountX10));
        encodedData = ByteUtil.INSTANCE.concat(encodedData, ByteUtil.INSTANCE.getBytesFromInt(timeBetweenExtendedPulses));
    }

    @Override
    public MessageBlockType getType() {
        return MessageBlockType.BOLUS_EXTRA;
    }

    @NonNull @Override
    public String toString() {
        return "BolusExtraCommand{" +
                "acknowledgementBeep=" + acknowledgementBeep +
                ", completionBeep=" + completionBeep +
                ", programReminderInterval=" + programReminderInterval +
                ", units=" + units +
                ", timeBetweenPulses=" + timeBetweenPulses +
                ", squareWaveUnits=" + squareWaveUnits +
                ", squareWaveDuration=" + squareWaveDuration +
                '}';
    }
}
