package info.nightscout.androidaps.plugins.PumpCommon.hw.rileylink.data;

import java.util.Calendar;
import java.util.GregorianCalendar;

import info.nightscout.androidaps.plugins.PumpCommon.hw.rileylink.service.RileyLinkErrorCode;
import info.nightscout.androidaps.plugins.PumpCommon.hw.rileylink.service.RileyLinkServiceState;

/**
 * Created by andy on 5/19/18.
 */

public class RLHistoryItem {

    String dateTimeString;
    long dateTime;
    GregorianCalendar gregorianCalendar;
    RileyLinkServiceState serviceState;
    RileyLinkErrorCode errorCode;

    // 2010 10 11 12 30 00

    public RLHistoryItem(GregorianCalendar gregorianCalendar, RileyLinkServiceState serviceState, RileyLinkErrorCode errorCode) {
        this.dateTime = gregorianCalendar.get(Calendar.SECOND) //
                + gregorianCalendar.get(Calendar.MINUTE) * 100 //
                + gregorianCalendar.get(Calendar.HOUR_OF_DAY) * 10000
                + gregorianCalendar.get(Calendar.DAY_OF_MONTH) * 1000000
                + (gregorianCalendar.get(Calendar.MONTH) + 1) * 100000000
                + gregorianCalendar.get(Calendar.YEAR) * 10000000000L;

        this.dateTimeString = "" + getNumber(gregorianCalendar.get(Calendar.DAY_OF_MONTH)) + "." + //
                getNumber(gregorianCalendar.get(Calendar.MONTH) + 1) + "." + //
                gregorianCalendar.get(Calendar.YEAR) + " " + //
                getNumber(gregorianCalendar.get(Calendar.HOUR_OF_DAY)) + ":" + //
                getNumber(gregorianCalendar.get(Calendar.MINUTE)) + ":" + //
                getNumber(gregorianCalendar.get(Calendar.SECOND)); //

        this.serviceState = serviceState;
        this.errorCode = errorCode;
    }


    public String getNumber(int number) {
        if (number > 9)
            return "" + number;
        else
            return "0" + number;
    }
}
