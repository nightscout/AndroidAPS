package info.nightscout.androidaps.plugins.pump.common.utils;

/**
 * Created by andy on 10/25/18.
 */

import org.joda.time.LocalDateTime;
import org.joda.time.Minutes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Calendar;
import java.util.GregorianCalendar;

import info.nightscout.androidaps.logging.L;

/**
 * This is simple version of ATechDate, limited only to one format (yyyymmddHHMIss)
 */
public class DateTimeUtil {

    private static final Logger LOG = LoggerFactory.getLogger(L.PUMPCOMM);

    /**
     * DateTime is packed as long: yyyymmddHHMMss
     *
     * @param atechDateTime
     * @return
     */
    public static LocalDateTime toLocalDateTime(long atechDateTime) {
        int year = (int) (atechDateTime / 10000000000L);
        atechDateTime -= year * 10000000000L;

        int month = (int) (atechDateTime / 100000000L);
        atechDateTime -= month * 100000000L;

        int dayOfMonth = (int) (atechDateTime / 1000000L);
        atechDateTime -= dayOfMonth * 1000000L;

        int hourOfDay = (int) (atechDateTime / 10000L);
        atechDateTime -= hourOfDay * 10000L;

        int minute = (int) (atechDateTime / 100L);
        atechDateTime -= minute * 100L;

        int second = (int) atechDateTime;

        try {
            return new LocalDateTime(year, month, dayOfMonth, hourOfDay, minute, second);
        } catch (Exception ex) {
            if (L.isEnabled(L.PUMPCOMM))
                LOG.error("Error creating LocalDateTime from values [atechDateTime={}, year={}, month={}, day={}, hour={}, minute={}, second={}]. Exception: {}", atechDateTime, year, month, dayOfMonth, hourOfDay, minute, second, ex.getMessage());
            //return null;
            throw ex;
        }
    }


    /**
     * DateTime is packed as long: yyyymmddHHMMss
     *
     * @param atechDateTime
     * @return
     */
    public static GregorianCalendar toGregorianCalendar(long atechDateTime) {
        int year = (int) (atechDateTime / 10000000000L);
        atechDateTime -= year * 10000000000L;

        int month = (int) (atechDateTime / 100000000L);
        atechDateTime -= month * 100000000L;

        int dayOfMonth = (int) (atechDateTime / 1000000L);
        atechDateTime -= dayOfMonth * 1000000L;

        int hourOfDay = (int) (atechDateTime / 10000L);
        atechDateTime -= hourOfDay * 10000L;

        int minute = (int) (atechDateTime / 100L);
        atechDateTime -= minute * 100L;

        int second = (int) atechDateTime;

        try {
            return new GregorianCalendar(year, month - 1, dayOfMonth, hourOfDay, minute, second);
        } catch (Exception ex) {
            if (L.isEnabled(L.PUMPCOMM))
                LOG.error("DateTimeUtil", String.format("Error creating GregorianCalendar from values [atechDateTime=%d, year=%d, month=%d, day=%d, hour=%d, minute=%d, second=%d]", atechDateTime, year, month, dayOfMonth, hourOfDay, minute, second));
            //return null;
            throw ex;
        }
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


//    public static long toATechDate(Date date) {
//
//        long atechDateTime = 0L;
//
//        atechDateTime += (date.getYear() + 1900) * 10000000000L;
//        atechDateTime += (date.getMonth() + 1) * 100000000L;
//        atechDateTime += date.getDate() * 1000000L;
//        atechDateTime += date.getHours() * 10000L;
//        atechDateTime += date.getMinutes() * 100L;
//        atechDateTime += date.getSeconds();
//
//        return atechDateTime;
//    }


    public static String toString(long atechDateTime) {
        int year = (int) (atechDateTime / 10000000000L);
        atechDateTime -= year * 10000000000L;

        int month = (int) (atechDateTime / 100000000L);
        atechDateTime -= month * 100000000L;

        int dayOfMonth = (int) (atechDateTime / 1000000L);
        atechDateTime -= dayOfMonth * 1000000L;

        int hourOfDay = (int) (atechDateTime / 10000L);
        atechDateTime -= hourOfDay * 10000L;

        int minute = (int) (atechDateTime / 100L);
        atechDateTime -= minute * 100L;

        int second = (int) atechDateTime;

        return getZeroPrefixed(dayOfMonth) + "." + getZeroPrefixed(month) + "." + year + " " + //
                getZeroPrefixed(hourOfDay) + ":" + getZeroPrefixed(minute) + ":" + getZeroPrefixed(second);
    }


    public static String toString(GregorianCalendar gc) {

        return getZeroPrefixed(gc.get(Calendar.DAY_OF_MONTH)) + "." + getZeroPrefixed(gc.get(Calendar.MONTH) + 1) + "."
                + gc.get(Calendar.YEAR) + " "
                + //
                getZeroPrefixed(gc.get(Calendar.HOUR_OF_DAY)) + ":" + getZeroPrefixed(gc.get(Calendar.MINUTE)) + ":"
                + getZeroPrefixed(gc.get(Calendar.SECOND));
    }


    public static String toStringFromTimeInMillis(long timeInMillis) {

        GregorianCalendar gc = new GregorianCalendar();
        gc.setTimeInMillis(timeInMillis);

        return toString(gc);
    }


    private static String getZeroPrefixed(int number) {
        return (number < 10) ? "0" + number : "" + number;
    }


    public static int getYear(Long atechDateTime) {

        if (atechDateTime == null || atechDateTime == 0) {
            return 2000;
        }

        int year = (int) (atechDateTime / 10000000000L);
        return year;
    }


    public static boolean isSameDayATDAndMillis(long atechDateTime, long timeInMillis) {

        GregorianCalendar dt = new GregorianCalendar();
        dt.setTimeInMillis(timeInMillis);

        long entryDate = toATechDate(dt);

        return (isSameDay(atechDateTime, entryDate));
    }


    public static long toMillisFromATD(long atechDateTime) {

        GregorianCalendar gc = toGregorianCalendar(atechDateTime);

        return gc.getTimeInMillis();
    }


    public static int getATechDateDiferenceAsMinutes(Long date1, Long date2) {

        Minutes minutes = Minutes.minutesBetween(toLocalDateTime(date1), toLocalDateTime(date2));

        return minutes.getMinutes();
    }


    public static long getMillisFromATDWithAddedMinutes(long atd, int minutesDiff) {
        GregorianCalendar oldestEntryTime = DateTimeUtil.toGregorianCalendar(atd);
        oldestEntryTime.add(Calendar.MINUTE, minutesDiff);

        return oldestEntryTime.getTimeInMillis();
    }


    public static long getATDWithAddedMinutes(long atd, int minutesDiff) {
        GregorianCalendar oldestEntryTime = DateTimeUtil.toGregorianCalendar(atd);
        oldestEntryTime.add(Calendar.MINUTE, minutesDiff);

        return oldestEntryTime.getTimeInMillis();
    }


    public static long getATDWithAddedMinutes(GregorianCalendar oldestEntryTime, int minutesDiff) {
        oldestEntryTime.add(Calendar.MINUTE, minutesDiff);

        return toATechDate(oldestEntryTime);
    }


}
