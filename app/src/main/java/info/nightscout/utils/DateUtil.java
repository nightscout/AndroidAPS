package info.nightscout.utils;

import android.text.format.DateUtils;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import info.nightscout.androidaps.MainApp;

/**
 * The Class DateUtil. A simple wrapper around SimpleDateFormat to ease the handling of iso date string &lt;-&gt; date obj
 * with TZ
 */
public class DateUtil {

    /**
     * The date format in iso.
     */
    public static String FORMAT_DATE_ISO = "yyyy-MM-dd'T'HH:mm:ss'Z'";

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
        SimpleDateFormat f = new SimpleDateFormat(FORMAT_DATE_ISO);
        f.setTimeZone(TimeZone.getTimeZone("UTC"));
        Date date = f.parse(isoDateString);
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
        DateFormat f = new SimpleDateFormat(format);
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
        Pattern p = Pattern.compile("(\\d+):(\\d+)");
        Matcher m = p.matcher(hh_colon_mm);
        int retval = 0;

        if (m.find()) {
            retval = SafeParse.stringToInt(m.group(1)) * 60 * 60 + SafeParse.stringToInt(m.group(2)) * 60;
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
}