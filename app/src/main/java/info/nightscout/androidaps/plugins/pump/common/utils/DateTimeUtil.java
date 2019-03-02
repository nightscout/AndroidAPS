package info.nightscout.androidaps.plugins.pump.common.utils;

/**
 * Created by andy on 10/25/18.
 */

import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;

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

        return new LocalDateTime(year, month, dayOfMonth, hourOfDay, minute, second);
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


    public static long toATechDate(GregorianCalendar gc) {
        long atechDateTime = 0L;

        atechDateTime += gc.get(Calendar.YEAR) * 10000000000L;
        atechDateTime += (gc.get(Calendar.MONTH) + 1) * 100000000L;
        atechDateTime += gc.get(Calendar.DAY_OF_MONTH) * 1000000L;
        atechDateTime += gc.get(Calendar.HOUR_OF_DAY) * 10000L;
        atechDateTime += gc.get(Calendar.MINUTE) * 100L;
        atechDateTime += gc.get(Calendar.SECOND);

        return atechDateTime;
    }


    public static boolean isSameDay(LocalDateTime ldt1, LocalDateTime ldt2) {

        return (ldt1.getYear() == ldt2.getYear() && //
            ldt1.getMonthOfYear() == ldt2.getMonthOfYear() && //
        ldt1.getDayOfMonth() == ldt2.getDayOfMonth());

    }


    public static boolean isSameDay(long ldt1, long ldt2) {

        long day1 = ldt1 / 10000L;
        long day2 = ldt2 / 10000L;

        return day1 == day2;
    }


    public static long toATechDate(int year, int month, int dayOfMonth, int hour, int minutes, int seconds) {

        long atechDateTime = 0L;

        atechDateTime += year * 10000000000L;
        atechDateTime += month * 100000000L;
        atechDateTime += dayOfMonth * 1000000L;
        atechDateTime += hour * 10000L;
        atechDateTime += minutes * 100L;
        atechDateTime += seconds;

        return atechDateTime;
    }


    public static long toATechDate(Date date) {

        long atechDateTime = 0L;

        atechDateTime += date.getYear() * 10000000000L;
        atechDateTime += date.getMonth() * 100000000L;
        atechDateTime += date.getDay() * 1000000L;
        atechDateTime += date.getHours() * 10000L;
        atechDateTime += date.getMinutes() * 100L;
        atechDateTime += date.getSeconds();

        return atechDateTime;
    }


    public static String toString(long atechDateTime) {
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

        return getZeroPrefixed(dayOfMonth) + "." + getZeroPrefixed(month) + "." + year + " " + //
            getZeroPrefixed(hourOfDay) + ":" + getZeroPrefixed(minute) + ":" + getZeroPrefixed(second);
    }


    private static String getZeroPrefixed(int number) {
        return (number < 10) ? "0" + number : "" + number;
    }


    public static int getYear(long atechDateTime) {

        int year = (int)(atechDateTime / 10000000000L);
        return year;
    }


    public static boolean isSameDayATDAndMillis(long atechDateTime, long date) {

        Date dt = new Date(date);
        long entryDate = toATechDate(dt);

        return (isSameDay(atechDateTime, entryDate));
    }


    public static long toMillisFromATD(long atechDateTime) {

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

        Date d = new Date();
        d.setDate(dayOfMonth);
        d.setMonth(month - 1);
        d.setYear(year);
        d.setHours(hourOfDay);
        d.setMinutes(minute);
        d.setSeconds(second);

        return d.getTime();
    }
}
