package info.nightscout.utils;

import com.crashlytics.android.Crashlytics;
import com.crashlytics.android.answers.Answers;
import com.crashlytics.android.answers.CustomEvent;

import info.nightscout.androidaps.BuildConfig;
import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.interfaces.PluginBase;

import java.util.Date;

/**
 * Created by jamorham on 21/02/2018.
 * <p>
 * Some users do not wish to be tracked, Fabric Answers and Crashlytics do not provide an easy way
 * to disable them and make calls from a potentially invalid singleton reference. This wrapper
 * emulates the methods but ignores the request if the instance is null or invalid.
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

    public static void uploadDailyStats() {
        if (!fabricEnabled()) return;

        long lastUploadDay = SP.getLong(MainApp.gs(R.string.key_plugin_stats_report_timestamp), 0L);

        Date date = new Date();
        date.setHours(0);
        date.setMinutes(0);
        date.setSeconds(0);
        long today = date.getTime() - date.getTime() % 1000;

        if (today > lastUploadDay) {
            uploadAppUsageType();
            uploadPluginStats();

            SP.putLong(MainApp.gs(R.string.key_plugin_stats_report_timestamp), today);
        }
    }

    private static void uploadPluginStats() {
        CustomEvent pluginStats = new CustomEvent("PluginStats");
        pluginStats.putCustomAttribute("version", BuildConfig.VERSION);
        pluginStats.putCustomAttribute("HEAD", BuildConfig.HEAD);
        pluginStats.putCustomAttribute("language", SP.getString(R.string.key_language,"default"));
        for (PluginBase plugin : MainApp.getPluginsList()) {
            if (plugin.isEnabled(plugin.getType()) && !plugin.pluginDescription.alwaysEnabled) {
                // Fabric allows no more than 20 attributes attached to an event. By reporting disabled plugins as
                // well, we would exceed that threshold, so only report what is enabled
                // TODO >2.0: consider reworking this to upload an event per enabled plugin instead.
                pluginStats.putCustomAttribute(plugin.getClass().getSimpleName(), "enabled");
            }
        }

        getInstance().logCustom(pluginStats);
    }

    private static void uploadAppUsageType() {
        CustomEvent type = new CustomEvent("AppUsageType");
        if (Config.NSCLIENT)
            type.putCustomAttribute("type", "NSClient");
        else if (Config.PUMPCONTROL)
            type.putCustomAttribute("type", "PumpControl");
        else if (MainApp.getConstraintChecker().isClosedLoopAllowed().value())
            type.putCustomAttribute("type", "ClosedLoop");
        else
            type.putCustomAttribute("type", "OpenLoop");

        getInstance().logCustom(type);
    }

}
