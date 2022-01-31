package info.nightscout.androidaps.plugins.pump.common.utils;

/**
 * Created by geoff on 5/27/16.
 */
public class ThreadUtil {

    public static long getThreadId() {
        return Thread.currentThread().getId();
    }


    public static String getThreadName() {
        return Thread.currentThread().getName();
    }


    public static String sig() {
        Thread t = Thread.currentThread();
        return t.getName() + "[" + t.getId() + "]";
    }
}
