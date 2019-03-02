package info.nightscout.androidaps.plugins.pump.medtronic.comm.data.history_old.record;

import info.nightscout.androidaps.plugins.pump.medtronic.comm.data.history_old.Record;
import info.nightscout.androidaps.plugins.pump.medtronic.comm.data.history_old.RecordTypeEnum;
import info.nightscout.androidaps.plugins.pump.medtronic.defs.MedtronicDeviceType;

/**
 * Created by andy on 6/1/18.
 */

public class IgnoredHistoryEntry extends Record {

    // public int lngth = 7;
    private RecordTypeEnum typeEnum = RecordTypeEnum.Null;


    public IgnoredHistoryEntry() {
    }


    public void init(RecordTypeEnum typeEnum) {
        this.typeEnum = typeEnum;
    }


    @Override
    public int getLength() {
        return this.typeEnum.getLength();
    }


    @Override
    public String getRecordTypeName() {
        return typeEnum.name();
    }


    @Override
    public String getShortTypeName() {
        return typeEnum.name();
    }


    @Override
    public boolean parseFrom(byte[] data, MedtronicDeviceType model) {
        return true;
    }


    @Override
    public boolean isAAPSRelevant() {
        return false;
    }
}
