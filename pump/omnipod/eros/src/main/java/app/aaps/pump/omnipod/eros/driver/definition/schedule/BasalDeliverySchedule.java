package app.aaps.pump.omnipod.eros.driver.definition.schedule;

import androidx.annotation.NonNull;

import app.aaps.core.utils.pump.ByteUtil;
import app.aaps.pump.omnipod.eros.driver.communication.message.IRawRepresentable;

public class BasalDeliverySchedule extends DeliverySchedule implements IRawRepresentable {

    private final byte currentSegment;
    private final int secondsRemaining;
    private final int pulsesRemaining;
    private final BasalDeliveryTable basalTable;

    public BasalDeliverySchedule(byte currentSegment, int secondsRemaining, int pulsesRemaining,
                                 BasalDeliveryTable basalTable) {
        this.currentSegment = currentSegment;
        this.secondsRemaining = secondsRemaining;
        this.pulsesRemaining = pulsesRemaining;
        this.basalTable = basalTable;
    }

    @Override
    public byte[] getRawData() {
        byte[] rawData = new byte[0];
        rawData = ByteUtil.INSTANCE.concat(rawData, currentSegment);
        rawData = ByteUtil.INSTANCE.concat(rawData, ByteUtil.INSTANCE.getBytesFromInt16(secondsRemaining << 3));
        rawData = ByteUtil.INSTANCE.concat(rawData, ByteUtil.INSTANCE.getBytesFromInt16(pulsesRemaining));
        for (BasalTableEntry entry : basalTable.getEntries()) {
            rawData = ByteUtil.INSTANCE.concat(rawData, entry.getRawData());
        }
        return rawData;
    }

    @NonNull @Override
    public InsulinScheduleType getType() {
        return InsulinScheduleType.BASAL_SCHEDULE;
    }

    @Override
    public int getChecksum() {
        int checksum = 0;
        byte[] rawData = getRawData();
        for (int i = 0; i < rawData.length && i < 5; i++) {
            checksum += ByteUtil.INSTANCE.convertUnsignedByteToInt(rawData[i]);
        }
        for (BasalTableEntry entry : basalTable.getEntries()) {
            checksum += entry.getChecksum();
        }

        return checksum;
    }

    @NonNull @Override
    public String toString() {
        return "BasalDeliverySchedule{" +
                "currentSegment=" + currentSegment +
                ", secondsRemaining=" + secondsRemaining +
                ", pulsesRemaining=" + pulsesRemaining +
                ", basalTable=" + basalTable +
                '}';
    }
}
