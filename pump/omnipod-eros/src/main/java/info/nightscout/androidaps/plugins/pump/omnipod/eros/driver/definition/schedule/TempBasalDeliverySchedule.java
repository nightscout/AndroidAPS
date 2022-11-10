package info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.definition.schedule;

import info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.communication.message.IRawRepresentable;
import info.nightscout.pump.core.utils.ByteUtil;

public class TempBasalDeliverySchedule extends DeliverySchedule implements IRawRepresentable {

    private final int secondsRemaining;
    private final int firstSegmentPulses;
    private final BasalDeliveryTable basalTable;

    public TempBasalDeliverySchedule(int secondsRemaining, int firstSegmentPulses, BasalDeliveryTable basalTable) {
        this.secondsRemaining = secondsRemaining;
        this.firstSegmentPulses = firstSegmentPulses;
        this.basalTable = basalTable;
    }

    @Override
    public byte[] getRawData() {
        byte[] rawData = new byte[0];
        rawData = ByteUtil.concat(rawData, basalTable.numSegments());
        rawData = ByteUtil.concat(rawData, ByteUtil.getBytesFromInt16(secondsRemaining << 3));
        rawData = ByteUtil.concat(rawData, ByteUtil.getBytesFromInt16(firstSegmentPulses));
        for (BasalTableEntry entry : basalTable.getEntries()) {
            rawData = ByteUtil.concat(rawData, entry.getRawData());
        }
        return rawData;
    }

    @Override
    public InsulinScheduleType getType() {
        return InsulinScheduleType.TEMP_BASAL_SCHEDULE;
    }

    @Override
    public int getChecksum() {
        int checksum = 0;
        byte[] rawData = getRawData();
        for (int i = 0; i < rawData.length && i < 5; i++) {
            checksum += ByteUtil.convertUnsignedByteToInt(rawData[i]);
        }
        for (BasalTableEntry entry : basalTable.getEntries()) {
            checksum += entry.getChecksum();
        }

        return checksum;
    }

    public int getSecondsRemaining() {
        return secondsRemaining;
    }

    public int getFirstSegmentPulses() {
        return firstSegmentPulses;
    }

    public BasalDeliveryTable getBasalTable() {
        return basalTable;
    }

    @Override
    public String toString() {
        return "TempBasalDeliverySchedule{" +
                "secondsRemaining=" + secondsRemaining +
                ", firstSegmentPulses=" + firstSegmentPulses +
                ", basalTable=" + basalTable +
                '}';
    }
}
