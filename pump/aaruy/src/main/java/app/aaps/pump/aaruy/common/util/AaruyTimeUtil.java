

package app.aaps.pump.aaruy.common.util;

import android.annotation.SuppressLint;
import android.content.Context;

import java.text.DecimalFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

import app.aaps.pump.aaruy.R;

/**
 * 通用时间类
 *
 * @use TimeUtil.xxxMethod(...);
 */
public class AaruyTimeUtil {
    private static final String TAG = "TimeUtil";

    private AaruyTimeUtil() {/* 不能实例化**/}

    public static String getWeekOfDate(long timestamp, String[] weeks) {
        Date date = new Date(timestamp);
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        int week = calendar.get(Calendar.DAY_OF_WEEK) - 1;
        if (week < 0) {
            week = 0;
        }
        return weeks[week];
    }

    public static String dateToWeek(String datetime, String[] week) {
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd",Locale.getDefault());
        Calendar cal = Calendar.getInstance();
        Date date = null;
        try {
            date = f.parse(datetime);
            cal.setTime(date);
        } catch (ParseException e) {
            e.printStackTrace();
        }
        int w = cal.get(Calendar.DAY_OF_WEEK) - 1;
        if (w < 0) {
            w = 0;
        }
        return week[w];
    }

    /**
     * 日期转为时间戳
     *
     * @param s
     * @return
     * @throws ParseException
     */
    @SuppressLint("SimpleDateFormat")
    public static long dateToStamp(String s) throws ParseException {
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        Date date = simpleDateFormat.parse(s);
        long ts = date.getTime();
        return ts;
    }


    /**
     * 时间戳转为日期
     *
     * @param s
     * @return
     */
    @SuppressLint("SimpleDateFormat")
    public static String stampToDate(long s, String pattern) {
        String res;
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat(pattern);
        Date date = new Date(s);
        res = simpleDateFormat.format(date);
        return res;
    }


    public static boolean isLeapYear(int year) {
        return (year % 4 == 0 && year % 100 != 0) || year % 400 == 0;
    }


    /**
     * 获取时间,HH:mm:ss
     *
     * @param date
     * @return
     */
    public static String getTime(long date) {
        return new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date(date));
    }


    /**
     * 获取时间,hh:mm:ss
     *
     * @param time 秒
     * @return
     */
    public static String getTime_h_m_s(int time) {
        DecimalFormat decimalFormat = new DecimalFormat("00");
        String hh = decimalFormat.format(time / 3600);
        String mm = decimalFormat.format(time % 3600 / 60);
        String ss = decimalFormat.format(time % 60);
        return hh + ":" + mm + ":" + ss;
    }

    /**
     * 获取时间,hh:mm
     *
     * @param minute 分钟
     * @return
     */
    public static String get_H_m_String(int minute) {
        int time = minute * 60;
        DecimalFormat decimalFormat = new DecimalFormat("00");
        String hh = decimalFormat.format(time / 3600);
        String mm = decimalFormat.format(time % 3600 / 60);
        return hh + ":" + mm;
    }


    /**
     * 获取时间,HH:mm
     *
     * @param date
     * @return
     */
    public static String getTime_h_m(long date) {
        return new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date(date));
    }

    public static String getDateTime(Context context, long date) {
        //return getSafeDateFormat("yyyy-MM-dd").format(new Date(date));
        return new SimpleDateFormat(context.getString(R.string.yeaqr_month_day_hour_minute), Locale.getDefault()).format(new Date(date));
    }

    public static String getDate3(Context context, long date) {
        //return getSafeDateFormat("yyyy-MM-dd").format(new Date(date));
        return new SimpleDateFormat(context.getString(R.string.year_month_day), Locale.getDefault()).format(new Date(date));
    }

    public static String getDateWithTime(long date) {
        //return getSafeDateFormat("yyyy-MM-dd").format(new Date(date));
        return new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(new Date(date));
    }

    public static String getDateWithTime_ss(long date) {
        //return getSafeDateFormat("yyyy-MM-dd").format(new Date(date));
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date(date));
    }


    /**
     * 获取日期  时， 分， 秒 对应值
     *
     * @param time
     * @return
     */
    public static int[] getTimeDetail(long time) {
        final Calendar mCalendar = Calendar.getInstance();
        mCalendar.setTimeInMillis(time);
        return new int[]{
                mCalendar.get(Calendar.HOUR_OF_DAY),//3
                mCalendar.get(Calendar.MINUTE),//4
                mCalendar.get(Calendar.SECOND)//5
        };
    }

    /**
     * 获取日期 年，月， 日， 时， 分， 秒 对应值
     *
     * @param time
     * @return
     */
    public static int[] getWholeDetail(long time) {
        final Calendar mCalendar = Calendar.getInstance();
        mCalendar.setTimeInMillis(time);
        return new int[]{
                mCalendar.get(Calendar.YEAR),//0
                mCalendar.get(Calendar.MONTH) + 1,//1
                mCalendar.get(Calendar.DAY_OF_MONTH),//2
                mCalendar.get(Calendar.HOUR_OF_DAY),//3
                mCalendar.get(Calendar.MINUTE),//4
                mCalendar.get(Calendar.SECOND)//5
        };
    }

}
