package info.nightscout.utils;

import android.support.v4.util.LongSparseArray;
import android.text.format.DateUtils;
import android.util.SparseIntArray;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;

/**
 * The Class DateUtil. A simple wrapper around SimpleDateFormat to ease the handling of iso date string &lt;-&gt; date obj
 * with TZ
 */
public class DateUtil {

    /**
     * The date format in iso.
     */
    private static String FORMAT_DATE_ISO = "yyyy-MM-dd'T'HH:mm:ss'Z'";
    private static String FORMAT_DATE_ISO_MSEC = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'";

    /**
     * Takes in an ISO date string of the following format:
     * yyyy-mm-ddThh:mm:ss.ms+HoMo
     *
     * @param isoDateString the iso date string
     * @return the date
     * @throws Exception the exception
     */
    public static Date fromISODateString(String isoDateString)
            throws Exception {
        SimpleDateFormat f = new SimpleDateFormat(FORMAT_DATE_ISO, Locale.getDefault());
        Date date;
        f.setTimeZone(TimeZone.getTimeZone("UTC"));
        try {
            date = f.parse(isoDateString);
        } catch (ParseException e) {
            f = new SimpleDateFormat(FORMAT_DATE_ISO_MSEC, Locale.getDefault());
            f.setTimeZone(TimeZone.getTimeZone("UTC"));
            date = f.parse(isoDateString);
        }
        return date;
    }

    /**
     * Render date
     *
     * @param date   the date obj
     * @param format - if not specified, will use FORMAT_DATE_ISO
     * @param tz     - tz to set to, if not specified uses local timezone
     * @return the iso-formatted date string
     */
    public static String toISOString(Date date, String format, TimeZone tz) {
        if (format == null) format = FORMAT_DATE_ISO;
        if (tz == null) tz = TimeZone.getDefault();
        DateFormat f = new SimpleDateFormat(format, Locale.getDefault());
        f.setTimeZone(tz);
        return f.format(date);
    }

    public static String toISOString(Date date) {
        return toISOString(date, FORMAT_DATE_ISO, TimeZone.getTimeZone("UTC"));
    }
    public static String toISOString(long date) {
        return toISOString(new Date(date), FORMAT_DATE_ISO, TimeZone.getTimeZone("UTC"));
    }

    public static Date toDate(Integer seconds) {
        Calendar calendar = new GregorianCalendar();
        calendar.set(Calendar.HOUR_OF_DAY, seconds / 60 / 60);
        String a = calendar.getTime().toString();
        calendar.set(Calendar.MINUTE, (seconds / 60) % 60);
        String b = calendar.getTime().toString();
        calendar.set(Calendar.SECOND, 0);
        String c = calendar.getTime().toString();
        return calendar.getTime();
    }

    public static int toSeconds(String hh_colon_mm) {
        Pattern p = Pattern.compile("(\\d+):(\\d+)( a.m.| p.m.| AM | PM|)");
        Matcher m = p.matcher(hh_colon_mm);
        int retval = 0;

        if (m.find()) {
            retval = SafeParse.stringToInt(m.group(1)) * 60 * 60 + SafeParse.stringToInt(m.group(2)) * 60;
            if ((m.group(3).equals(" a.m.") || m.group(3).equals(" AM")) && m.group(1).equals("12"))
                retval -= 12 * 60 * 60;
            if ((m.group(3).equals(" p.m.") || m.group(3).equals(" PM")) && !(m.group(1).equals("12")))
                retval += 12 * 60 * 60;
        }
        return retval;
    }

    public static String dateString(Date date) {
        //return DateUtils.formatDateTime(MainApp.instance(), date.getTime(), DateUtils.FORMAT_SHOW_DATE); this provide month name not number
        DateFormat df = DateFormat.getDateInstance(DateFormat.SHORT);
        return df.format(date);
    }

    public static String dateString(long mills) {
        //return DateUtils.formatDateTime(MainApp.instance(), mills, DateUtils.FORMAT_SHOW_DATE); this provide month name not number
        DateFormat df = DateFormat.getDateInstance(DateFormat.SHORT);
        return df.format(mills);
    }

    public static String timeString(Date date) {
        return DateUtils.formatDateTime(MainApp.instance(), date.getTime(), DateUtils.FORMAT_SHOW_TIME);
    }

    public static String timeString(long mills) {
        return DateUtils.formatDateTime(MainApp.instance(), mills, DateUtils.FORMAT_SHOW_TIME);
    }

    public static String dateAndTimeString(Date date) {
        return dateString(date) + " " + timeString(date);
    }
    public static String dateAndTimeString(long mills) {
        return dateString(mills) + " " + timeString(mills);
    }

    public static String minAgo(long time) {
        int mins = (int) ((System.currentTimeMillis() - time) / 1000 / 60);
        return String.format(MainApp.sResources.getString(R.string.minago), mins);
    }

    private static LongSparseArray<String> timeStrings = new LongSparseArray<>();

    public static String timeStringFromSeconds(int seconds) {
        String cached = timeStrings.get(seconds);
        if (cached != null)
            return cached;
        String t = DateUtils.formatDateTime(MainApp.instance(), toDate(seconds).getTime(), DateUtils.FORMAT_SHOW_TIME);
        timeStrings.put(seconds, t);
        return t;
    }


}