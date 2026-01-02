package app.aaps.pump.omnipod.eros.driver.definition.schedule;

import androidx.annotation.NonNull;

import org.joda.time.Duration;

import app.aaps.core.utils.pump.ByteUtil;
import app.aaps.pump.omnipod.eros.driver.communication.message.IRawRepresentable;
import app.aaps.pump.omnipod.eros.driver.definition.OmnipodConstants;

public class BolusDeliverySchedule extends DeliverySchedule implements IRawRepresentable {

    private final double units;
    private final Duration timeBetweenPulses;

    public BolusDeliverySchedule(double units, Duration timeBetweenPulses) {
        if (units <= 0D) {
            throw new IllegalArgumentException("Units should be > 0");
        } else if (units > OmnipodConstants.MAX_BOLUS) {
            throw new IllegalArgumentException("Units exceeds max bolus");
        }
        this.units = units;
        this.timeBetweenPulses = timeBetweenPulses;
    }

    @Override
    public byte[] getRawData() {
        byte[] rawData = new byte[]{1}; // Number of half hour segments

        int pulseCount = (int) Math.round(units / OmnipodConstants.POD_PULSE_SIZE);
        int multiplier = (int) timeBetweenPulses.getStandardSeconds() * 8;
        int fieldA = pulseCount * multiplier;

        rawData = ByteUtil.INSTANCE.concat(rawData, ByteUtil.INSTANCE.getBytesFromInt16(fieldA));
        rawData = ByteUtil.INSTANCE.concat(rawData, ByteUtil.INSTANCE.getBytesFromInt16(pulseCount));
        rawData = ByteUtil.INSTANCE.concat(rawData, ByteUtil.INSTANCE.getBytesFromInt16(pulseCount));
        return rawData;
    }

    @Override
    public InsulinScheduleType getType() {
        return InsulinScheduleType.BOLUS;
    }

    @Override
    public int getChecksum() {
        int checksum = 0;
        byte[] rawData = getRawData();
        for (int i = 0; i < rawData.length && i < 7; i++) {
            checksum += ByteUtil.INSTANCE.convertUnsignedByteToInt(rawData[i]);
        }
        return checksum;
    }

    @Override @NonNull
    public String toString() {
        return "BolusDeliverySchedule{" +
                "units=" + units +
                ", timeBetweenPulses=" + timeBetweenPulses +
                '}';
    }
}
