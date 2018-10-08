package info.nightscout.androidaps;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.net.ConnectivityManager;
import android.net.wifi.WifiManager;

import androidx.annotation.ColorRes;
import androidx.annotation.StringRes;
import androidx.core.app.NotificationCompat;
import androidx.core.app.TaskStackBuilder;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.crashlytics.android.Crashlytics;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.j256.ormlite.android.apptools.OpenHelperManager;

import net.danlew.android.joda.JodaTimeAndroid;

import org.json.JSONException;

import java.util.List;

import javax.inject.Inject;

import dagger.android.AndroidInjector;
import dagger.android.DaggerApplication;
import dagger.android.HasAndroidInjector;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.db.DatabaseHelper;
import info.nightscout.androidaps.dependencyInjection.DaggerAppComponent;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.logging.AAPSLogger;
import info.nightscout.androidaps.logging.LTag;
import info.nightscout.androidaps.plugins.configBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.configBuilder.PluginStore;
import info.nightscout.androidaps.plugins.configBuilder.ProfileFunction;
import info.nightscout.androidaps.plugins.constraints.versionChecker.VersionCheckerUtils;
import info.nightscout.androidaps.plugins.general.nsclient.NSUpload;
import info.nightscout.androidaps.receivers.BTReceiver;
import info.nightscout.androidaps.receivers.ChargingStateReceiver;
import info.nightscout.androidaps.receivers.DataReceiver;
import info.nightscout.androidaps.receivers.KeepAliveReceiver;
import info.nightscout.androidaps.receivers.NSAlarmReceiver;
import info.nightscout.androidaps.receivers.NetworkChangeReceiver;
import info.nightscout.androidaps.receivers.TimeDateOrTZChangeReceiver;
import info.nightscout.androidaps.services.Intents;
import info.nightscout.androidaps.utils.ActivityMonitor;
import info.nightscout.androidaps.utils.FabricPrivacy;
import info.nightscout.androidaps.utils.LocaleHelper;
import info.nightscout.androidaps.utils.resources.ResourceHelper;
import info.nightscout.androidaps.utils.sharedPreferences.SP;
import io.fabric.sdk.android.Fabric;

public class MainApp extends DaggerApplication {

    static MainApp sInstance;
    private static Resources sResources;

    static FirebaseAnalytics firebaseAnalytics;

    static DatabaseHelper sDatabaseHelper = null;

    private String CHANNEL_ID = "AndroidAPS-Ongoing"; // TODO: move to OngoingNotificationProvider (and dagger)
    private int ONGOING_NOTIFICATION_ID = 4711; // TODO: move to OngoingNotificationProvider (and dagger)
    private Notification notification; // TODO: move to OngoingNotificationProvider (and dagger)

    @Inject PluginStore pluginStore;
    @Inject public HasAndroidInjector injector;
    @Inject AAPSLogger aapsLogger;
    @Inject ReceiverStatusStore receiverStatusStore;
    @Inject ActivityMonitor activityMonitor;
    @Inject FabricPrivacy fabricPrivacy;
    @Inject ResourceHelper resourceHelper;
    @Inject VersionCheckerUtils versionCheckersUtils;
    @Inject SP sp;
    @Inject ProfileFunction profileFunction;

    @Inject ConfigBuilderPlugin configBuilderPlugin;
    @Inject KeepAliveReceiver.KeepAliveManager keepAliveManager;
    @Inject List<PluginBase> plugins;

    @Override
    public void onCreate() {
        super.onCreate();

        aapsLogger.debug("onCreate");
        sInstance = this;
        sResources = getResources();
        LocaleHelper.INSTANCE.update(this);
        generateEmptyNotification();
        sDatabaseHelper = OpenHelperManager.getHelper(sInstance, DatabaseHelper.class);

        Thread.setDefaultUncaughtExceptionHandler((thread, ex) -> {
            if (ex instanceof InternalError) {
                // usually the app trying to spawn a thread while being killed
                return;
            }
            aapsLogger.error("Uncaught exception crashing app", ex);
        });

        try {
            if (fabricPrivacy.fabricEnabled()) {
                Fabric.with(this, new Crashlytics());
            }
        } catch (Exception e) {
            aapsLogger.error("Error with Fabric init! " + e);
        }

        registerActivityLifecycleCallbacks(activityMonitor);

        firebaseAnalytics = FirebaseAnalytics.getInstance(this);
        firebaseAnalytics.setAnalyticsCollectionEnabled(!Boolean.getBoolean("disableFirebase") && fabricPrivacy.fabricEnabled());

        JodaTimeAndroid.init(this);

        aapsLogger.debug("Version: " + BuildConfig.VERSION_NAME);
        aapsLogger.debug("BuildVersion: " + BuildConfig.BUILDVERSION);
        aapsLogger.debug("Remote: " + BuildConfig.REMOTE);

        registerLocalBroadcastReceiver();

        //trigger here to see the new version on app start after an update
        versionCheckersUtils.triggerCheckVersion();

        // Register all tabs in app here
        pluginStore.setPlugins(plugins);
        configBuilderPlugin.initialize();

        NSUpload.uploadAppStart();

        new Thread(() -> keepAliveManager.setAlarm(this)).start();
        doMigrations();
    }


    private void doMigrations() {

        // guarantee that the unreachable threshold is at least 30 and of type String
        // Added in 1.57 at 21.01.2018
        int unreachable_threshold = sp.getInt(R.string.key_pump_unreachable_threshold, 30);
        sp.remove(R.string.key_pump_unreachable_threshold);
        if (unreachable_threshold < 30) unreachable_threshold = 30;
        sp.putString(R.string.key_pump_unreachable_threshold, Integer.toString(unreachable_threshold));

        // 2.5 -> 2.6
        if (!sp.contains(R.string.key_units)) {
            String newUnits = Constants.MGDL;
            Profile p = profileFunction.getProfile();
            if (p != null && p.getData() != null && p.getData().has("units")) {
                try {
                    newUnits = p.getData().getString("units");
                } catch (JSONException e) {
                    aapsLogger.error("Unhandled exception", e);
                }
            }
            sp.putString(R.string.key_units, newUnits);
        }
    }

    @Override
    protected AndroidInjector<? extends DaggerApplication> applicationInjector() {
        return DaggerAppComponent
                .builder()
                .application(this)
                .build();
    }

    private void registerLocalBroadcastReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intents.ACTION_NEW_TREATMENT);
        filter.addAction(Intents.ACTION_CHANGED_TREATMENT);
        filter.addAction(Intents.ACTION_REMOVED_TREATMENT);
        filter.addAction(Intents.ACTION_NEW_SGV);
        filter.addAction(Intents.ACTION_NEW_PROFILE);
        filter.addAction(Intents.ACTION_NEW_MBG);
        filter.addAction(Intents.ACTION_NEW_CAL);
        LocalBroadcastManager.getInstance(this).registerReceiver(new DataReceiver(), filter);

        filter = new IntentFilter();
        filter.addAction(Intent.ACTION_TIME_CHANGED);
        filter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        registerReceiver(new TimeDateOrTZChangeReceiver(), filter);

        filter = new IntentFilter();
        filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        filter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        filter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        registerReceiver(new NetworkChangeReceiver(), filter);

        filter = new IntentFilter();
        filter.addAction(Intent.ACTION_POWER_CONNECTED);
        filter.addAction(Intent.ACTION_POWER_DISCONNECTED);
        filter.addAction(Intent.ACTION_BATTERY_CHANGED);
        registerReceiver(new ChargingStateReceiver(), filter);

        filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
        filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        registerReceiver(new BTReceiver(), filter);
    }

    @Deprecated
    public static String gs(@StringRes int id) {
        return sResources.getString(id);
    }

    @Deprecated
    public static String gs(@StringRes int id, Object... args) {
        return sResources.getString(id, args);
    }

    @Deprecated
    public static MainApp instance() {
        return sInstance;
    }

    public static DatabaseHelper getDbHelper() {
        return sDatabaseHelper;
    }

    public FirebaseAnalytics getFirebaseAnalytics() {
        return firebaseAnalytics;
    }

    // global Notification has been moved to MainApp because PersistentNotificationPlugin is initialized too late
    private void generateEmptyNotification() {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID);
        builder.setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setSmallIcon(resourceHelper.getNotificationIcon())
                .setLargeIcon(resourceHelper.decodeResource(resourceHelper.getIcon()));
        builder.setContentTitle(resourceHelper.gs(R.string.loading));
        Intent resultIntent = new Intent(this, MainApp.class);
        TaskStackBuilder stackBuilder = TaskStackBuilder.create(this);
        stackBuilder.addParentStack(MainActivity.class);
        stackBuilder.addNextIntent(resultIntent);
        PendingIntent resultPendingIntent = stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);
        builder.setContentIntent(resultPendingIntent);
        NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        notification = builder.build();
        mNotificationManager.notify(ONGOING_NOTIFICATION_ID, notification);
    }

    public int notificationId() {
        return ONGOING_NOTIFICATION_ID;
    }

    public String channelId() {
        return CHANNEL_ID;
    }

    public void setNotification(Notification notification) {
        this.notification = notification;
    }

    public Notification getNotification() {
        return notification;
    }

    @Override
    public void onTerminate() {
        aapsLogger.debug(LTag.CORE, "onTerminate");
        unregisterActivityLifecycleCallbacks(activityMonitor);
        keepAliveManager.cancelAlarm(this);
        super.onTerminate();
    }
}
