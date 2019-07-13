package info.nightscout.androidaps.utils;

import android.os.Bundle;

import com.crashlytics.android.Crashlytics;
import com.google.firebase.analytics.FirebaseAnalytics;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.BuildConfig;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.configBuilder.ConfigBuilderPlugin;

/**
 * Created by jamorham on 21/02/2018.
 * <p>
 * Some users do not wish to be tracked, Fabric Answers and Crashlytics do not provide an easy way
 * to disable them and make calls from a potentially invalid singleton reference. This wrapper
 * emulates the methods but ignores the request if the instance is null or invalid.
 */

public class FabricPrivacy {
    private static Logger log = LoggerFactory.getLogger(L.CORE);

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
            if (L.isEnabled(L.CORE))
                log.debug("Ignoring opted out non-initialized log: " + throwable);
        }
    }

    // Crashlytics log
    public static void log(String msg) {
        try {
            final Crashlytics crashlytics = Crashlytics.getInstance();
            crashlytics.core.log(msg);
        } catch (NullPointerException | IllegalStateException e) {
            if (L.isEnabled(L.CORE))
                log.debug("Ignoring opted out non-initialized log: " + msg);
        }
    }

    // Crashlytics log
    public static void log(int priority, String tag, String msg) {
        try {
            final Crashlytics crashlytics = Crashlytics.getInstance();
            crashlytics.core.log(priority, tag, msg);
        } catch (NullPointerException | IllegalStateException e) {
            if (L.isEnabled(L.CORE))
                log.debug("Ignoring opted out non-initialized log: " + msg);
        }
    }

    public static boolean fabricEnabled() {
        return SP.getBoolean("enable_fabric", true);
    }

    // Analytics logCustom
    public void logCustom(Bundle event) {
        try {
            if (fabricEnabled()) {
                MainApp.getFirebaseAnalytics().logEvent(FirebaseAnalytics.Event.SELECT_CONTENT, event);
            } else {
                if (L.isEnabled(L.CORE))
                    log.debug("Ignoring recently opted-out event: " + event.toString());
            }
        } catch (NullPointerException | IllegalStateException e) {
            if (L.isEnabled(L.CORE))
                log.debug("Ignoring opted-out non-initialized event: " + event.toString());
        }
    }

    // Analytics logCustom
    public void logCustom(String event) {
        try {
            if (fabricEnabled()) {
                MainApp.getFirebaseAnalytics().logEvent(event, new Bundle());
            } else {
                if (L.isEnabled(L.CORE))
                    log.debug("Ignoring recently opted-out event: " + event);
            }
        } catch (NullPointerException | IllegalStateException e) {
            if (L.isEnabled(L.CORE))
                log.debug("Ignoring opted-out non-initialized event: " + event);
        }
    }

    public static void setUserStats() {
        if (!fabricEnabled()) return;

        String closedLoopEnabled = MainApp.getConstraintChecker().isClosedLoopAllowed().value() ? "CLOSED_LOOP_ENABLED" : "CLOSED_LOOP_DISABLED";
        // Size is limited to 36 chars
        String remote = BuildConfig.REMOTE
                .replace("https://","")
                .replace("http://","")
                .replace(".git", "")
                .replace(".com/", ":")
                .replace(".org/", ":")
                .replace(".net/", ":");

        MainApp.getFirebaseAnalytics().setUserProperty("Mode", BuildConfig.APPLICATION_ID + "-" + closedLoopEnabled);
        MainApp.getFirebaseAnalytics().setUserProperty("Language", LocaleHelper.getLanguage(MainApp.instance()));
        MainApp.getFirebaseAnalytics().setUserProperty("Version", BuildConfig.VERSION);
        MainApp.getFirebaseAnalytics().setUserProperty("HEAD", BuildConfig.HEAD);
        MainApp.getFirebaseAnalytics().setUserProperty("Remote", remote);
        if (ConfigBuilderPlugin.getPlugin().getActivePump() != null)
            MainApp.getFirebaseAnalytics().setUserProperty("Pump", ConfigBuilderPlugin.getPlugin().getActivePump().getClass().getSimpleName());
        if (ConfigBuilderPlugin.getPlugin().getActiveAPS() != null)
            MainApp.getFirebaseAnalytics().setUserProperty("Aps", ConfigBuilderPlugin.getPlugin().getActiveAPS().getClass().getSimpleName());
        if (ConfigBuilderPlugin.getPlugin().getActiveBgSource() != null)
            MainApp.getFirebaseAnalytics().setUserProperty("BgSource", ConfigBuilderPlugin.getPlugin().getActiveBgSource().getClass().getSimpleName());
        if (ConfigBuilderPlugin.getPlugin().getActiveProfileInterface() != null)
            MainApp.getFirebaseAnalytics().setUserProperty("Profile", ConfigBuilderPlugin.getPlugin().getActiveProfileInterface().getClass().getSimpleName());
        if (ConfigBuilderPlugin.getPlugin().getActiveSensitivity() != null)
            MainApp.getFirebaseAnalytics().setUserProperty("Sensitivity", ConfigBuilderPlugin.getPlugin().getActiveSensitivity().getClass().getSimpleName());
        if (ConfigBuilderPlugin.getPlugin().getActiveInsulin() != null)
            MainApp.getFirebaseAnalytics().setUserProperty("Insulin", ConfigBuilderPlugin.getPlugin().getActiveInsulin().getClass().getSimpleName());

    }

}
