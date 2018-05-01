package com.gxwtech.roundtrip2.RoundtripService.medtronic.PumpData.records;

import android.os.Bundle;

import com.gxwtech.roundtrip2.RoundtripService.medtronic.PumpModel;
import com.gxwtech.roundtrip2.RoundtripService.medtronic.PumpTimeStamp;
import com.gxwtech.roundtrip2.RoundtripService.medtronic.TimeFormat;
import com.gxwtech.roundtrip2.util.ByteUtil;

/*
 *  Many events in the history only consist of a single opcode and a datestamp.
 *  This serves to record that a particular event happened at a particular date.
 *  Many of the subclasses of this class only override the opcode.
 */
abstract public class TimeStampedRecord extends Record {
    private final static String TAG = "TimeStampedRecord";
    private final static boolean DEBUG_TIMESTAMPEDRECORD = false;

    @Override
    public int getLength() { return 7; }

    public int getDatestampOffset() { return 2; }

    protected PumpTimeStamp timestamp;

    public TimeStampedRecord() {
        timestamp = new PumpTimeStamp();
    }

    @Override
    public PumpTimeStamp getTimestamp() {
        return timestamp;
    }

    @Override
    public boolean parseFrom(byte[] data, PumpModel model) {
        return simpleParse(data,getDatestampOffset());
    }

    // This is useful if there is no data inside, or we don't care about the data.
    public boolean simpleParse(byte[] data, int fiveByteDateOffset) {
        if (getLength() > data.length) {
            return false;
        }
        if (!collectTimeStamp(data,fiveByteDateOffset)) {
            return false;
        }
        rawbytes = ByteUtil.substring(data,0,getLength());
        return true;
    }

    protected boolean collectTimeStamp(byte[] data, int offset) {
        try {
            timestamp = new PumpTimeStamp(TimeFormat.parse5ByteDate(data, offset));
        } catch (org.joda.time.IllegalFieldValueException e) {
            return false;
        }
        return true;
    }

    @Override
    public boolean readFromBundle(Bundle in) {
        String timestampString = in.getString("timestamp");
        timestamp = new PumpTimeStamp(timestampString);
        return super.readFromBundle(in);
    }

    @Override
    public void writeToBundle(Bundle in) {
        super.writeToBundle(in);
        in.putString("timestamp",timestamp.toString());

    }
}
