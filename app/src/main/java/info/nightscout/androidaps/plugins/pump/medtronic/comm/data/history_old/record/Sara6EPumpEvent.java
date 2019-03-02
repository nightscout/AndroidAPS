package info.nightscout.androidaps.plugins.pump.medtronic.comm.data.history_old.record;

import info.nightscout.androidaps.plugins.pump.medtronic.comm.data.history_old.PumpTimeStamp;
import info.nightscout.androidaps.plugins.pump.medtronic.comm.data.history_old.TimeFormat;
import info.nightscout.androidaps.plugins.pump.medtronic.comm.data.history_old.TimeStampedRecord;
import info.nightscout.androidaps.plugins.pump.medtronic.defs.MedtronicDeviceType;

public class Sara6EPumpEvent extends TimeStampedRecord {

    public Sara6EPumpEvent() {
    }


    @Override
    public int getLength() {
        return 52;
    }


    @Override
    public String getShortTypeName() {
        return "Sara6E";
    }


    @Override
    public boolean parseFrom(byte[] data, MedtronicDeviceType model) {
        // We don't understand this event...
        // Minimum 16 characters? date components?
        if (16 > data.length) {
            return false;
        }
        try {
            timestamp = new PumpTimeStamp(TimeFormat.parse2ByteDate(data, 1));
        } catch (org.joda.time.IllegalFieldValueException e) {
            return false;
        }
        return true;
    }


    @Override
    public boolean isAAPSRelevant() {
        return false;
    }

}
