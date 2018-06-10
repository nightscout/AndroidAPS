package info.nightscout.androidaps.plugins.PumpMedtronic.comm.data.history.record;

import info.nightscout.androidaps.plugins.PumpMedtronic.comm.data.history.PumpTimeStamp;
import info.nightscout.androidaps.plugins.PumpMedtronic.comm.data.history.TimeFormat;
import info.nightscout.androidaps.plugins.PumpMedtronic.comm.data.history.TimeStampedRecord;

public class ResultDailyTotalPumpEvent extends TimeStampedRecord {
    private static final String TAG = "ResultDailyTotalPumpEvent";


    public ResultDailyTotalPumpEvent() {
    }


    @Override
    public int getDatestampOffset() {
        return 5;
    }


    @Override
    public int getLength() {
        return isLargerFormat() ? 10 : 7;
    }


    @Override
    protected boolean collectTimeStamp(byte[] data, int offset) {
        try {
            // This might be a 5 byte date on largerFormat
            timestamp = new PumpTimeStamp(TimeFormat.parse2ByteDate(data, offset));
        } catch (org.joda.time.IllegalFieldValueException e) {
            return false;
        }
        return true;
    }


    @Override
    public String getShortTypeName() {
        return "Result Daily Total";
    }


    @Override
    public boolean isAAPSRelevant() {
        return false;
    }

}
