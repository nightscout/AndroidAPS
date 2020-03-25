package info.nightscout.androidaps;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
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

import javax.inject.Inject;

import dagger.android.AndroidInjector;
import dagger.android.DaggerApplication;
import dagger.android.HasAndroidInjector;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.db.DatabaseHelper;
import info.nightscout.androidaps.dependencyInjection.DaggerAppComponent;
import info.nightscout.androidaps.logging.AAPSLogger;
import info.nightscout.androidaps.logging.LTag;
import info.nightscout.androidaps.plugins.aps.loop.LoopPlugin;
import info.nightscout.androidaps.plugins.aps.openAPSAMA.OpenAPSAMAPlugin;
import info.nightscout.androidaps.plugins.aps.openAPSMA.OpenAPSMAPlugin;
import info.nightscout.androidaps.plugins.aps.openAPSSMB.OpenAPSSMBPlugin;
import info.nightscout.androidaps.plugins.configBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.configBuilder.PluginStore;
import info.nightscout.androidaps.plugins.configBuilder.ProfileFunction;
import info.nightscout.androidaps.plugins.constraints.dstHelper.DstHelperPlugin;
import info.nightscout.androidaps.plugins.constraints.objectives.ObjectivesPlugin;
import info.nightscout.androidaps.plugins.constraints.safety.SafetyPlugin;
import info.nightscout.androidaps.plugins.constraints.signatureVerifier.SignatureVerifierPlugin;
import info.nightscout.androidaps.plugins.constraints.storage.StorageConstraintPlugin;
import info.nightscout.androidaps.plugins.constraints.versionChecker.VersionCheckerPlugin;
import info.nightscout.androidaps.plugins.constraints.versionChecker.VersionCheckerUtils;
import info.nightscout.androidaps.plugins.general.actions.ActionsPlugin;
import info.nightscout.androidaps.plugins.general.automation.AutomationPlugin;
import info.nightscout.androidaps.plugins.general.careportal.CareportalPlugin;
import info.nightscout.androidaps.plugins.general.dataBroadcaster.DataBroadcastPlugin;
import info.nightscout.androidaps.plugins.general.food.FoodPlugin;
import info.nightscout.androidaps.plugins.general.maintenance.MaintenancePlugin;
import info.nightscout.androidaps.plugins.general.nsclient.NSClientPlugin;
import info.nightscout.androidaps.plugins.general.nsclient.NSUpload;
import info.nightscout.androidaps.plugins.general.overview.OverviewPlugin;
import info.nightscout.androidaps.plugins.general.persistentNotification.PersistentNotificationPlugin;
import info.nightscout.androidaps.plugins.general.smsCommunicator.SmsCommunicatorPlugin;
import info.nightscout.androidaps.plugins.general.tidepool.TidepoolPlugin;
import info.nightscout.androidaps.plugins.general.wear.WearPlugin;
import info.nightscout.androidaps.plugins.general.xdripStatusline.StatusLinePlugin;
import info.nightscout.androidaps.plugins.insulin.InsulinOrefFreePeakPlugin;
import info.nightscout.androidaps.plugins.insulin.InsulinOrefRapidActingPlugin;
import info.nightscout.androidaps.plugins.insulin.InsulinOrefUltraRapidActingPlugin;
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.IobCobCalculatorPlugin;
import info.nightscout.androidaps.plugins.profile.local.LocalProfilePlugin;
import info.nightscout.androidaps.plugins.profile.ns.NSProfilePlugin;
import info.nightscout.androidaps.plugins.pump.combo.ComboPlugin;
import info.nightscout.androidaps.plugins.pump.danaR.DanaRPlugin;
import info.nightscout.androidaps.plugins.pump.danaRKorean.DanaRKoreanPlugin;
import info.nightscout.androidaps.plugins.pump.danaRS.DanaRSPlugin;
import info.nightscout.androidaps.plugins.pump.danaRv2.DanaRv2Plugin;
import info.nightscout.androidaps.plugins.pump.insight.LocalInsightPlugin;
import info.nightscout.androidaps.plugins.pump.mdi.MDIPlugin;
import info.nightscout.androidaps.plugins.pump.medtronic.MedtronicPumpPlugin;
import info.nightscout.androidaps.plugins.pump.virtual.VirtualPumpPlugin;
import info.nightscout.androidaps.plugins.sensitivity.SensitivityAAPSPlugin;
import info.nightscout.androidaps.plugins.sensitivity.SensitivityOref0Plugin;
import info.nightscout.androidaps.plugins.sensitivity.SensitivityOref1Plugin;
import info.nightscout.androidaps.plugins.sensitivity.SensitivityWeightedAveragePlugin;
import info.nightscout.androidaps.plugins.source.DexcomPlugin;
import info.nightscout.androidaps.plugins.source.EversensePlugin;
import info.nightscout.androidaps.plugins.source.GlimpPlugin;
import info.nightscout.androidaps.plugins.source.MM640gPlugin;
import info.nightscout.androidaps.plugins.source.NSClientSourcePlugin;
import info.nightscout.androidaps.plugins.source.PoctechPlugin;
import info.nightscout.androidaps.plugins.source.RandomBgPlugin;
import info.nightscout.androidaps.plugins.source.TomatoPlugin;
import info.nightscout.androidaps.plugins.source.XdripPlugin;
import info.nightscout.androidaps.plugins.treatments.TreatmentsPlugin;
import info.nightscout.androidaps.receivers.ChargingStateReceiver;
import info.nightscout.androidaps.receivers.DataReceiver;
import info.nightscout.androidaps.receivers.KeepAliveReceiver;
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

    DataReceiver dataReceiver = new DataReceiver();
    TimeDateOrTZChangeReceiver timeDateOrTZChangeReceiver;

    private String CHANNEL_ID = "AndroidAPS-Ongoing"; // TODO: move to OngoingNotificationProvider (and dagger)
    private int ONGOING_NOTIFICATION_ID = 4711; // TODO: move to OngoingNotificationProvider (and dagger)
    private Notification notification; // TODO: move to OngoingNotificationProvider (and dagger)

    @Inject PluginStore pluginStore;
    @Inject public HasAndroidInjector injector;
    @Inject AAPSLogger aapsLogger;
    @Inject ActivityMonitor activityMonitor;
    @Inject FabricPrivacy fabricPrivacy;
    @Inject ResourceHelper resourceHelper;
    @Inject VersionCheckerUtils versionCheckersUtils;
    @Inject SP sp;
    @Inject ProfileFunction profileFunction;

    @Inject ActionsPlugin actionsPlugin;
    @Inject AutomationPlugin automationPlugin;
    @Inject ComboPlugin comboPlugin;
    @Inject CareportalPlugin careportalPlugin;
    @Inject ConfigBuilderPlugin configBuilderPlugin;
    @Inject DanaRPlugin danaRPlugin;
    @Inject DanaRSPlugin danaRSPlugin;
    @Inject DanaRv2Plugin danaRv2Plugin;
    @Inject DanaRKoreanPlugin danaRKoreanPlugin;
    @Inject DataBroadcastPlugin dataBroadcastPlugin;
    @Inject DstHelperPlugin dstHelperPlugin;
    @Inject FoodPlugin foodPlugin;
    @Inject InsulinOrefFreePeakPlugin insulinOrefFreePeakPlugin;
    @Inject InsulinOrefRapidActingPlugin insulinOrefRapidActingPlugin;
    @Inject InsulinOrefUltraRapidActingPlugin insulinOrefUltraRapidActingPlugin;
    @Inject IobCobCalculatorPlugin iobCobCalculatorPlugin;
    @Inject LocalInsightPlugin localInsightPlugin;
    @Inject LocalProfilePlugin localProfilePlugin;
    @Inject LoopPlugin loopPlugin;
    @Inject MedtronicPumpPlugin medtronicPumpPlugin;
    @Inject MDIPlugin mdiPlugin;
    @Inject NSProfilePlugin nsProfilePlugin;
    @Inject ObjectivesPlugin objectivesPlugin;
    @Inject SafetyPlugin safetyPlugin;
    @Inject SmsCommunicatorPlugin smsCommunicatorPlugin;
    @Inject OpenAPSMAPlugin openAPSMAPlugin;
    @Inject OpenAPSAMAPlugin openAPSAMAPlugin;
    @Inject OpenAPSSMBPlugin openAPSSMBPlugin;
    @Inject OverviewPlugin overviewPlugin;
    @Inject PersistentNotificationPlugin persistentNotificationPlugin;
    @Inject RandomBgPlugin randomBgPlugin;
    @Inject SensitivityOref1Plugin sensitivityOref1Plugin;
    @Inject SensitivityAAPSPlugin sensitivityAAPSPlugin;
    @Inject SensitivityOref0Plugin sensitivityOref0Plugin;
    @Inject SensitivityWeightedAveragePlugin sensitivityWeightedAveragePlugin;
    @Inject SignatureVerifierPlugin signatureVerifierPlugin;
    @Inject StorageConstraintPlugin storageConstraintPlugin;
    @Inject DexcomPlugin dexcomPlugin;
    @Inject EversensePlugin eversensePlugin;
    @Inject GlimpPlugin glimpPlugin;
    @Inject MaintenancePlugin maintenancePlugin;
    @Inject MM640gPlugin mM640GPlugin;
    @Inject NSClientPlugin nsClientPlugin;
    @Inject NSClientSourcePlugin nSClientSourcePlugin;
    @Inject PoctechPlugin poctechPlugin;
    @Inject TomatoPlugin tomatoPlugin;
    @Inject XdripPlugin xdripPlugin;
    @Inject StatusLinePlugin statusLinePlugin;
    @Inject TidepoolPlugin tidepoolPlugin;
    @Inject TreatmentsPlugin treatmentsPlugin;
    @Inject VirtualPumpPlugin virtualPumpPlugin;
    @Inject VersionCheckerPlugin versionCheckerPlugin;
    @Inject WearPlugin wearPlugin;
    @Inject KeepAliveReceiver.KeepAliveManager keepAliveManager;

    @Override
    public void onCreate() {
        super.onCreate();

        aapsLogger.debug("onCreate");
        sInstance = this;
        sResources = getResources();
        LocaleHelper.INSTANCE.update(this);
        generateEmptyNotification();
        sDatabaseHelper = OpenHelperManager.getHelper(sInstance, DatabaseHelper.class);

/* TODO: put back
        Thread.setDefaultUncaughtExceptionHandler((thread, ex) -> {
            if (ex instanceof InternalError) {
                // usually the app trying to spawn a thread while being killed
                return;
            }
            log.error("Uncaught exception crashing app", ex);
        });
*/

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
        pluginStore.add(overviewPlugin);
        pluginStore.add(iobCobCalculatorPlugin);
        if (!Config.NSCLIENT) pluginStore.add(actionsPlugin);
        pluginStore.add(insulinOrefRapidActingPlugin);
        pluginStore.add(insulinOrefUltraRapidActingPlugin);
        pluginStore.add(insulinOrefFreePeakPlugin);
        pluginStore.add(sensitivityOref0Plugin);
        pluginStore.add(sensitivityAAPSPlugin);
        pluginStore.add(sensitivityWeightedAveragePlugin);
        pluginStore.add(sensitivityOref1Plugin);
        if (Config.PUMPDRIVERS) pluginStore.add(danaRPlugin);
        if (Config.PUMPDRIVERS) pluginStore.add(danaRKoreanPlugin);
        if (Config.PUMPDRIVERS) pluginStore.add(danaRv2Plugin);
        if (Config.PUMPDRIVERS) pluginStore.add(danaRSPlugin);
        if (Config.PUMPDRIVERS) pluginStore.add(localInsightPlugin);
        if (Config.PUMPDRIVERS) pluginStore.add(comboPlugin);
        if (Config.PUMPDRIVERS) pluginStore.add(medtronicPumpPlugin);
        if (!Config.NSCLIENT) pluginStore.add(mdiPlugin);
        if (!Config.NSCLIENT) pluginStore.add(virtualPumpPlugin);
        if (Config.NSCLIENT) pluginStore.add(careportalPlugin);
        if (Config.APS) pluginStore.add(loopPlugin);
        if (Config.APS) pluginStore.add(openAPSMAPlugin);
        if (Config.APS) pluginStore.add(openAPSAMAPlugin);
        if (Config.APS) pluginStore.add(openAPSSMBPlugin);
        pluginStore.add(nsProfilePlugin);
        if (!Config.NSCLIENT) pluginStore.add(localProfilePlugin);
        pluginStore.add(treatmentsPlugin);
        if (!Config.NSCLIENT) pluginStore.add(safetyPlugin);
        if (!Config.NSCLIENT) pluginStore.add(versionCheckerPlugin);
        if (Config.APS) pluginStore.add(storageConstraintPlugin);
        if (Config.APS) pluginStore.add(signatureVerifierPlugin);
        if (Config.APS) pluginStore.add(objectivesPlugin);
        pluginStore.add(xdripPlugin);
        pluginStore.add(nSClientSourcePlugin);
        pluginStore.add(mM640GPlugin);
        pluginStore.add(glimpPlugin);
        pluginStore.add(dexcomPlugin);
        pluginStore.add(poctechPlugin);
        pluginStore.add(tomatoPlugin);
        pluginStore.add(eversensePlugin);
        pluginStore.add(randomBgPlugin);
        if (!Config.NSCLIENT) pluginStore.add(smsCommunicatorPlugin);
        pluginStore.add(foodPlugin);

        pluginStore.add(wearPlugin);
        pluginStore.add(statusLinePlugin);
        pluginStore.add(persistentNotificationPlugin);
        pluginStore.add(nsClientPlugin);
//            if (engineeringMode) pluginsList.add(tidepoolPlugin);
        pluginStore.add(maintenancePlugin);
        pluginStore.add(automationPlugin);
        pluginStore.add(dstHelperPlugin);
        pluginStore.add(dataBroadcastPlugin);

        pluginStore.add(configBuilderPlugin);

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
        LocalBroadcastManager lbm = LocalBroadcastManager.getInstance(this);
        lbm.registerReceiver(dataReceiver, new IntentFilter(Intents.ACTION_NEW_TREATMENT));
        lbm.registerReceiver(dataReceiver, new IntentFilter(Intents.ACTION_CHANGED_TREATMENT));
        lbm.registerReceiver(dataReceiver, new IntentFilter(Intents.ACTION_REMOVED_TREATMENT));
        lbm.registerReceiver(dataReceiver, new IntentFilter(Intents.ACTION_NEW_SGV));
        lbm.registerReceiver(dataReceiver, new IntentFilter(Intents.ACTION_NEW_PROFILE));
        lbm.registerReceiver(dataReceiver, new IntentFilter(Intents.ACTION_NEW_MBG));
        lbm.registerReceiver(dataReceiver, new IntentFilter(Intents.ACTION_NEW_CAL));

        this.timeDateOrTZChangeReceiver = new TimeDateOrTZChangeReceiver();
        this.timeDateOrTZChangeReceiver.registerBroadcasts(this);

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        intentFilter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        intentFilter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        registerReceiver(new NetworkChangeReceiver(), intentFilter);
        registerReceiver(new ChargingStateReceiver(), new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
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
    public static int gc(@ColorRes int id) {
        return ContextCompat.getColor(instance(), id);
    }

    @Deprecated
    public static Resources resources() {
        return sResources;
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

        if (timeDateOrTZChangeReceiver != null)
            unregisterReceiver(timeDateOrTZChangeReceiver);
        unregisterActivityLifecycleCallbacks(activityMonitor);
        keepAliveManager.cancelAlarm(this);
        super.onTerminate();
    }
}
