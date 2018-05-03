package info.nightscout.utils;

import com.crashlytics.android.Crashlytics;
import com.crashlytics.android.answers.Answers;
import com.crashlytics.android.answers.CustomEvent;

/**
 * Created by jamorham on 21/02/2018.
 *
 * Some users do not wish to be tracked, Fabric Answers and Crashlytics do not provide an easy way
 * to disable them and make calls from a potentially invalid singleton reference. This wrapper
 * emulates the methods but ignores the request if the instance is null or invalid.
 *
 */

public class FabricPrivacy {

    private static final String TAG = "FabricPrivacy";
    private static volatile FabricPrivacy instance;


    public static FabricPrivacy getInstance() {
        if (instance == null) {
            initSelf();
        }
        return instance;
    }

    private static synchronized void initSelf() {
        if (instance == null) {
            instance = new FabricPrivacy();
        }
    }

    // Crashlytics logException
    public static void logException(Throwable throwable) {
        try {
            final Crashlytics crashlytics = Crashlytics.getInstance();
            crashlytics.core.logException(throwable);
        } catch (NullPointerException | IllegalStateException e) {
            android.util.Log.d(TAG, "Ignoring opted out non-initialized log: " + throwable);
        }
    }

    // Crashlytics log
    public static void log(String msg) {
        try {
            final Crashlytics crashlytics = Crashlytics.getInstance();
            crashlytics.core.log(msg);
        } catch (NullPointerException | IllegalStateException e) {
            android.util.Log.d(TAG, "Ignoring opted out non-initialized log: " + msg);
        }
    }

    // Crashlytics log
    public static void log(int priority, String tag, String msg) {
        try {
            final Crashlytics crashlytics = Crashlytics.getInstance();
            crashlytics.core.log(priority, tag, msg);
        } catch (NullPointerException | IllegalStateException e) {
            android.util.Log.d(TAG, "Ignoring opted out non-initialized log: " + msg);
        }
    }

    public static boolean fabricEnabled() {
        return SP.getBoolean("enable_fabric", true);
    }

    // Answers logCustom
    public void logCustom(CustomEvent event) {
        try {
            final Answers answers = Answers.getInstance();
            if (fabricEnabled()) {
                answers.logCustom(event);
            } else {
                android.util.Log.d(TAG, "Ignoring recently opted-out event: " + event.toString());
            }
        } catch (NullPointerException | IllegalStateException e) {
            android.util.Log.d(TAG, "Ignoring opted-out non-initialized event: " + event.toString());
        }
    }

}
