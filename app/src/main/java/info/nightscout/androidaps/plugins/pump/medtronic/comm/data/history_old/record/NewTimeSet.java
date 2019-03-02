package info.nightscout.androidaps.plugins.pump.medtronic.comm.data.history_old.record;

import info.nightscout.androidaps.plugins.pump.medtronic.comm.data.history_old.TimeStampedRecord;
import info.nightscout.androidaps.plugins.pump.medtronic.defs.MedtronicDeviceType;

// This event existed as 0x18 in Roundtrip and early Decocare,
// but I don't see a corresponding event in RileyLink_ios.
public class NewTimeSet extends TimeStampedRecord {

    public NewTimeSet() {
    }


    @Override
    public boolean parseFrom(byte[] data, MedtronicDeviceType model) {
        return false;
    }


    @Override
    public boolean isAAPSRelevant() {
        return true;
    }
}
