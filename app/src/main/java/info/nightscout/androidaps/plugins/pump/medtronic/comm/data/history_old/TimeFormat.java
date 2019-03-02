package info.nightscout.androidaps.plugins.pump.medtronic.comm.data.history_old;

import org.joda.time.LocalDate;
import org.joda.time.LocalDateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import android.util.Log;

import info.nightscout.androidaps.plugins.pump.common.utils.ByteUtil;

/**
 * Created by geoff on 6/4/16.
 */
public class TimeFormat {

    public static final String standardFormatString = "YYYY-MM-dd HH:mm:ss";
    private static final boolean DEBUG_TIMEFORMAT = false;
    private static final String TAG = "TimeFormat";


    public TimeFormat() {
    }


    public static DateTimeFormatter standardFormatter() {
        return DateTimeFormat.forPattern(standardFormatString);
    }


    public static LocalDate parse2ByteDate(byte[] data, int offset) throws org.joda.time.IllegalFieldValueException {
        int low = ByteUtil.asUINT8(data[0 + offset]) & 0x1F;
        int mhigh = (ByteUtil.asUINT8(data[0 + offset]) & 0xE0) >> 4;
        int mlow = (ByteUtil.asUINT8(data[1 + offset]) & 0x80) >> 7;
        int month = mhigh + mlow;
        int dayOfMonth = low + 1;
        int year = 2000 + (ByteUtil.asUINT8(data[offset + 1]) & 0x7F);
        /*
         * Log.w(TAG, String.format("Attempting to create DateTime from: %04d-%02d-%02d %02d:%02d:%02d",
         * year + 2000, month, dayOfMonth, hour, minutes, seconds));
         */
        // try {
        LocalDate rval = new LocalDate(year, month, dayOfMonth);
        return rval;
        /*
         * } catch (org.joda.time.IllegalFieldValueException e) {
         * Log.e(TAG,"Illegal DateTime field");
         * //e.printStackTrace();
         * return new LocalDate(1973,3,3);
         * }
         */
    }


    // for relation to old code, replace offset with headerSize

    public static LocalDateTime parse5ByteDate(byte[] data, int offset) throws org.joda.time.IllegalFieldValueException {
        // offset = headerSize;
        if (DEBUG_TIMEFORMAT) {
            Log.w(TAG, String.format("bytes to parse: 0x%02X, 0x%02X, 0x%02X, 0x%02X, 0x%02X", data[offset],
                data[offset + 1], data[offset + 2], data[offset + 3], data[offset + 4]));
        }
        int seconds = data[offset] & 0x3F;
        int minutes = data[offset + 1] & 0x3F;
        int hour = data[offset + 2] & 0x1F;
        int dayOfMonth = data[offset + 3] & 0x1F;
        // Yes, the month bits are stored in the high bits above seconds and minutes!!
        int month = ((data[offset] >> 4) & 0x0c) + ((data[offset + 1] >> 6) & 0x03);
        int year = data[offset + 4] & 0x3F; // Assuming this is correct, need to verify. Otherwise this will be a
                                            // problem in 2016.
        /*
         * Log.w(TAG,String.format("Attempting to create DateTime from: %04d-%02d-%02d %02d:%02d:%02d",
         * year+2000,month,dayOfMonth,hour,minutes,seconds));
         */
        // try {
        LocalDateTime timeStamp = new LocalDateTime(year + 2000, month, dayOfMonth, hour, minutes, seconds);
        return timeStamp;
        /*
         * } catch (org.joda.time.IllegalFieldValueException e) {
         * Log.e(TAG, "Illegal DateTime field");
         * //e.printStackTrace();
         * return new LocalDateTime(1973,2,2,2,2);
         * }
         */
    }

}
