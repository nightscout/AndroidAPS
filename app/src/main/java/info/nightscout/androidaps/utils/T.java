package info.nightscout.androidaps.utils;

/**
 * Created by mike on 26.03.2018.
 */

public class T {
    private long time; // in msec

    public static T now() {
        T t = new T();
        t.time = System.currentTimeMillis();
        return t;
    }

    public static T msecs(long msec) {
        T t = new T();
        t.time = msec;
        return t;
    }

    public static T secs(long sec) {
        T t = new T();
        t.time = sec * 1000L;
        return t;
    }

    public static T mins(long min) {
        T t = new T();
        t.time = min * 60 * 1000L;
        return t;
    }

    public static T hours(long hour) {
        T t = new T();
        t.time = hour * 60 * 60 * 1000L;
        return t;
    }

    public static T days(long day) {
        T t = new T();
        t.time = day * 24 * 60 * 60 * 1000L;
        return t;
    }

    public long msecs() {
        return time;
    }

    public long secs() {
        return time / 1000L;
    }

    public long mins() {
        return time / 60 / 1000L;
    }

    public long hours() {
        return time / 60 / 60 / 1000L;
    }

    public long days() {
        return time / 24 / 60 / 60 / 1000L;
    }

    public T plus(T plus) {
        return T.msecs(time + plus.time);
    }

    public T minus(T minus) {
        return T.msecs(time - minus.time);
    }
}
