package info.nightscout.androidaps.plugins.pump.omnipod.defs.schedule;

import org.joda.time.Duration;

import info.nightscout.androidaps.plugins.pump.common.utils.ByteUtil;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.message.IRawRepresentable;
import info.nightscout.androidaps.plugins.pump.omnipod.util.OmnipodConst;

public class BolusDeliverySchedule extends DeliverySchedule implements IRawRepresentable {

    private final double units;
    private final Duration timeBetweenPulses;

    public BolusDeliverySchedule(double units, Duration timeBetweenPulses) {
        if (units <= 0D) {
            throw new IllegalArgumentException("Units should be > 0");
        } else if (units > OmnipodConst.MAX_BOLUS) {
            throw new IllegalArgumentException("Units exceeds max bolus");
        }
        this.units = units;
        this.timeBetweenPulses = timeBetweenPulses;
    }

    @Override
    public byte[] getRawData() {
        byte[] rawData = new byte[]{1}; // Number of half hour segments

        int pulseCount = (int) Math.round(units / OmnipodConst.POD_PULSE_SIZE);
        int multiplier = (int) timeBetweenPulses.getStandardSeconds() * 8;
        int fieldA = pulseCount * multiplier;

        rawData = ByteUtil.concat(rawData, ByteUtil.getBytesFromInt16(fieldA));
        rawData = ByteUtil.concat(rawData, ByteUtil.getBytesFromInt16(pulseCount));
        rawData = ByteUtil.concat(rawData, ByteUtil.getBytesFromInt16(pulseCount));
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
            checksum += ByteUtil.convertUnsignedByteToInt(rawData[i]);
        }
        return checksum;
    }

    @Override
    public String toString() {
        return "BolusDeliverySchedule{" +
                "units=" + units +
                ", timeBetweenPulses=" + timeBetweenPulses +
                '}';
    }
}
