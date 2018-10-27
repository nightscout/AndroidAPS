package info.nightscout.androidaps.plugins.PumpCommon.utils;

/**
 * Created by andy on 10/25/18.
 */

import org.joda.time.LocalDateTime;

/**
 * This is simple version of ATechDate, limited only to one format (yyyymmddHHMIss)
 */
public class DateTimeUtil {

    /**
     * DateTime is packed as long: yyyymmddHHMMss
     * 
     * @param atechDateTime
     * @return
     */
    public static LocalDateTime toLocalDateTime(long atechDateTime) {
        int year = (int)(atechDateTime / 10000000000L);
        atechDateTime -= year * 10000000000L;

        int month = (int)(atechDateTime / 100000000L);
        atechDateTime -= month * 100000000L;

        int dayOfMonth = (int)(atechDateTime / 1000000L);
        atechDateTime -= dayOfMonth * 1000000L;

        int hourOfDay = (int)(atechDateTime / 10000L);
        atechDateTime -= hourOfDay * 10000L;

        int minute = (int)(atechDateTime / 100L);
        atechDateTime -= minute * 100L;

        int second = (int)atechDateTime;

        return new LocalDateTime(year, month, dayOfMonth, minute, second);
    }


    public static long toATechDate(LocalDateTime ldt) {
        long atechDateTime = 0L;

        atechDateTime += ldt.getYear() * 10000000000L;
        atechDateTime += ldt.getMonthOfYear() * 100000000L;
        atechDateTime += ldt.getDayOfMonth() * 1000000L;
        atechDateTime += ldt.getHourOfDay() * 10000L;
        atechDateTime += ldt.getMinuteOfHour() * 100L;
        atechDateTime += ldt.getSecondOfMinute();

        return atechDateTime;
    }

}
