package info.nightscout.androidaps;

import android.app.Application;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.os.SystemClock;

import androidx.annotation.ColorRes;
import androidx.annotation.PluralsRes;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.crashlytics.android.Crashlytics;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.j256.ormlite.android.apptools.OpenHelperManager;

import net.danlew.android.joda.JodaTimeAndroid;

import org.json.JSONException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;

import info.nightscout.androidaps.data.ConstraintChecker;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.db.DatabaseHelper;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.interfaces.PluginType;
import info.nightscout.androidaps.interfaces.PumpInterface;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.aps.loop.LoopPlugin;
import info.nightscout.androidaps.plugins.aps.openAPSAMA.OpenAPSAMAPlugin;
import info.nightscout.androidaps.plugins.aps.openAPSMA.OpenAPSMAPlugin;
import info.nightscout.androidaps.plugins.aps.openAPSSMB.OpenAPSSMBPlugin;
import info.nightscout.androidaps.plugins.configBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.configBuilder.ProfileFunctions;
import info.nightscout.androidaps.plugins.constraints.dstHelper.DstHelperPlugin;
import info.nightscout.androidaps.plugins.constraints.objectives.ObjectivesPlugin;
import info.nightscout.androidaps.plugins.constraints.safety.SafetyPlugin;
import info.nightscout.androidaps.plugins.constraints.signatureVerifier.SignatureVerifierPlugin;
import info.nightscout.androidaps.plugins.constraints.storage.StorageConstraintPlugin;
import info.nightscout.androidaps.plugins.constraints.versionChecker.VersionCheckerPlugin;
import info.nightscout.androidaps.plugins.general.actions.ActionsPlugin;
import info.nightscout.androidaps.plugins.general.automation.AutomationPlugin;
import info.nightscout.androidaps.plugins.general.careportal.CareportalPlugin;
import info.nightscout.androidaps.plugins.general.food.FoodPlugin;
import info.nightscout.androidaps.plugins.general.maintenance.LoggerUtils;
import info.nightscout.androidaps.plugins.general.maintenance.MaintenancePlugin;
import info.nightscout.androidaps.plugins.general.nsclient.NSClientPlugin;
import info.nightscout.androidaps.plugins.general.nsclient.NSUpload;
import info.nightscout.androidaps.plugins.general.overview.OverviewPlugin;
import info.nightscout.androidaps.plugins.general.persistentNotification.PersistentNotificationPlugin;
import info.nightscout.androidaps.plugins.general.smsCommunicator.SmsCommunicatorPlugin;
import info.nightscout.androidaps.plugins.general.wear.WearPlugin;
import info.nightscout.androidaps.plugins.general.xdripStatusline.StatuslinePlugin;
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
import info.nightscout.androidaps.plugins.source.RandomBgPlugin;
import info.nightscout.androidaps.plugins.source.SourceDexcomPlugin;
import info.nightscout.androidaps.plugins.source.SourceEversensePlugin;
import info.nightscout.androidaps.plugins.source.SourceGlimpPlugin;
import info.nightscout.androidaps.plugins.source.SourceMM640gPlugin;
import info.nightscout.androidaps.plugins.source.SourceNSClientPlugin;
import info.nightscout.androidaps.plugins.source.SourcePoctechPlugin;
import info.nightscout.androidaps.plugins.source.SourceTomatoPlugin;
import info.nightscout.androidaps.plugins.source.SourceXdripPlugin;
import info.nightscout.androidaps.plugins.treatments.TreatmentsPlugin;
import info.nightscout.androidaps.receivers.DataReceiver;
import info.nightscout.androidaps.receivers.KeepAliveReceiver;
import info.nightscout.androidaps.receivers.NSAlarmReceiver;
import info.nightscout.androidaps.receivers.TimeDateOrTZChangeReceiver;
import info.nightscout.androidaps.services.Intents;
import info.nightscout.androidaps.utils.ActivityMonitor;
import info.nightscout.androidaps.utils.FabricPrivacy;
import info.nightscout.androidaps.utils.LocaleHelper;
import info.nightscout.androidaps.utils.SP;
import io.fabric.sdk.android.Fabric;

import static info.nightscout.androidaps.plugins.constraints.versionChecker.VersionCheckerUtilsKt.triggerCheckVersion;


public class MainApp extends Application {
    private static Logger log = LoggerFactory.getLogger(L.CORE);

    private static MainApp sInstance;
    public static Resources sResources;

    private static FirebaseAnalytics mFirebaseAnalytics;

    private static DatabaseHelper sDatabaseHelper = null;
    private static ConstraintChecker sConstraintsChecker = null;

    private static ArrayList<PluginBase> pluginsList = null;

    private static DataReceiver dataReceiver = new DataReceiver();
    private static NSAlarmReceiver alarmReceiver = new NSAlarmReceiver();
    TimeDateOrTZChangeReceiver timeDateOrTZChangeReceiver;

    public static boolean devBranch;
    public static boolean engineeringMode;

    @Override
    public void onCreate() {
        super.onCreate();
        log.debug("onCreate");
        sInstance = this;
        sResources = getResources();
        LocaleHelper.INSTANCE.update(this);
        sConstraintsChecker = new ConstraintChecker();
        sDatabaseHelper = OpenHelperManager.getHelper(sInstance, DatabaseHelper.class);

        Thread.setDefaultUncaughtExceptionHandler((thread, ex) -> {
            if (ex instanceof InternalError) {
                // usually the app trying to spawn a thread while being killed
                return;
            }

            log.error("Uncaught exception crashing app", ex);
        });

        try {
            if (FabricPrivacy.fabricEnabled()) {
                Fabric.with(this, new Crashlytics());
            }
        } catch (Exception e) {
            log.error("Error with Fabric init! " + e);
        }

        registerActivityLifecycleCallbacks(ActivityMonitor.INSTANCE);

        mFirebaseAnalytics = FirebaseAnalytics.getInstance(this);
        mFirebaseAnalytics.setAnalyticsCollectionEnabled(!Boolean.getBoolean("disableFirebase"));

        JodaTimeAndroid.init(this);

        log.info("Version: " + BuildConfig.VERSION_NAME);
        log.info("BuildVersion: " + BuildConfig.BUILDVERSION);
        log.info("Remote: " + BuildConfig.REMOTE);

        String extFilesDir = LoggerUtils.getLogDirectory();
        File engineeringModeSemaphore = new File(extFilesDir, "engineering_mode");

        engineeringMode = engineeringModeSemaphore.exists() && engineeringModeSemaphore.isFile();
        devBranch = BuildConfig.VERSION.contains("-") || BuildConfig.VERSION.matches(".*[a-zA-Z]+.*");

        registerLocalBroadcastReceiver();

        //trigger here to see the new version on app start after an update
        triggerCheckVersion();

        if (pluginsList == null) {
            pluginsList = new ArrayList<>();
            // Register all tabs in app here
            pluginsList.add(OverviewPlugin.INSTANCE);
            pluginsList.add(IobCobCalculatorPlugin.getPlugin());
            if (!Config.NSCLIENT) pluginsList.add(ActionsPlugin.INSTANCE);
            pluginsList.add(InsulinOrefRapidActingPlugin.getPlugin());
            pluginsList.add(InsulinOrefUltraRapidActingPlugin.getPlugin());
            pluginsList.add(InsulinOrefFreePeakPlugin.getPlugin());
            pluginsList.add(SensitivityOref0Plugin.getPlugin());
            pluginsList.add(SensitivityAAPSPlugin.getPlugin());
            pluginsList.add(SensitivityWeightedAveragePlugin.getPlugin());
            pluginsList.add(SensitivityOref1Plugin.getPlugin());
            if (Config.PUMPDRIVERS) pluginsList.add(DanaRPlugin.getPlugin());
            if (Config.PUMPDRIVERS) pluginsList.add(DanaRKoreanPlugin.getPlugin());
            if (Config.PUMPDRIVERS) pluginsList.add(DanaRv2Plugin.getPlugin());
            if (Config.PUMPDRIVERS) pluginsList.add(DanaRSPlugin.getPlugin());
            if (Config.PUMPDRIVERS) pluginsList.add(LocalInsightPlugin.getPlugin());
            if (Config.PUMPDRIVERS) pluginsList.add(ComboPlugin.getPlugin());
            if (Config.PUMPDRIVERS) pluginsList.add(MedtronicPumpPlugin.getPlugin());
            if (!Config.NSCLIENT) pluginsList.add(MDIPlugin.getPlugin());
            pluginsList.add(VirtualPumpPlugin.getPlugin());
            if (Config.NSCLIENT) pluginsList.add(CareportalPlugin.getPlugin());
            if (Config.APS) pluginsList.add(LoopPlugin.getPlugin());
            if (Config.APS) pluginsList.add(OpenAPSMAPlugin.getPlugin());
            if (Config.APS) pluginsList.add(OpenAPSAMAPlugin.getPlugin());
            if (Config.APS) pluginsList.add(OpenAPSSMBPlugin.getPlugin());
            pluginsList.add(NSProfilePlugin.getPlugin());
            if (!Config.NSCLIENT) pluginsList.add(LocalProfilePlugin.INSTANCE);
            pluginsList.add(TreatmentsPlugin.getPlugin());
            if (!Config.NSCLIENT) pluginsList.add(SafetyPlugin.getPlugin());
            if (!Config.NSCLIENT) pluginsList.add(VersionCheckerPlugin.INSTANCE);
            if (Config.APS) pluginsList.add(StorageConstraintPlugin.getPlugin());
            if (Config.APS) pluginsList.add(SignatureVerifierPlugin.getPlugin());
            if (Config.APS) pluginsList.add(ObjectivesPlugin.INSTANCE);
            pluginsList.add(SourceXdripPlugin.getPlugin());
            pluginsList.add(SourceNSClientPlugin.getPlugin());
            pluginsList.add(SourceMM640gPlugin.getPlugin());
            pluginsList.add(SourceGlimpPlugin.getPlugin());
            pluginsList.add(SourceDexcomPlugin.INSTANCE);
            pluginsList.add(SourcePoctechPlugin.getPlugin());
            pluginsList.add(SourceTomatoPlugin.getPlugin());
            pluginsList.add(SourceEversensePlugin.getPlugin());
            pluginsList.add(RandomBgPlugin.INSTANCE);
            if (!Config.NSCLIENT) pluginsList.add(SmsCommunicatorPlugin.INSTANCE);
            pluginsList.add(FoodPlugin.getPlugin());

            pluginsList.add(WearPlugin.initPlugin(this));
            pluginsList.add(StatuslinePlugin.initPlugin(this));
            pluginsList.add(PersistentNotificationPlugin.getPlugin());
            pluginsList.add(NSClientPlugin.getPlugin());
//            if (engineeringMode) pluginsList.add(TidepoolPlugin.INSTANCE);
            pluginsList.add(MaintenancePlugin.initPlugin(this));
            pluginsList.add(AutomationPlugin.INSTANCE);

            pluginsList.add(ConfigBuilderPlugin.getPlugin());

            pluginsList.add(DstHelperPlugin.getPlugin());


            ConfigBuilderPlugin.getPlugin().initialize();
        }

        NSUpload.uploadAppStart();

        final PumpInterface pump = ConfigBuilderPlugin.getPlugin().getActivePump();
        if (pump != null) {
            new Thread(() -> {
                SystemClock.sleep(5000);
                ConfigBuilderPlugin.getPlugin().getCommandQueue().readStatus("Initialization", null);
            }).start();
        }

        new Thread(() -> KeepAliveReceiver.setAlarm(this)).start();
        doMigrations();
    }

    private void doMigrations() {

        // guarantee that the unreachable threshold is at least 30 and of type String
        // Added in 1.57 at 21.01.2018
        int unreachable_threshold = SP.getInt(R.string.key_pump_unreachable_threshold, 30);
        SP.remove(R.string.key_pump_unreachable_threshold);
        if (unreachable_threshold < 30) unreachable_threshold = 30;
        SP.putString(R.string.key_pump_unreachable_threshold, Integer.toString(unreachable_threshold));

        // 2.5 -> 2.6
        if (!SP.contains(R.string.key_units)) {
            String newUnits = Constants.MGDL;
            Profile p = ProfileFunctions.getInstance().getProfile();
            if (p != null && p.getData() != null && p.getData().has("units")) {
                try {
                    newUnits = p.getData().getString("units");
                } catch (JSONException e) {
                    log.error("Unhandled exception", e);
                }
            }
            SP.putString(R.string.key_units, newUnits);
        }
    }


    private void registerLocalBroadcastReceiver() {
        LocalBroadcastManager lbm = LocalBroadcastManager.getInstance(this);
        lbm.registerReceiver(dataReceiver, new IntentFilter(Intents.ACTION_NEW_TREATMENT));
        lbm.registerReceiver(dataReceiver, new IntentFilter(Intents.ACTION_CHANGED_TREATMENT));
        lbm.registerReceiver(dataReceiver, new IntentFilter(Intents.ACTION_REMOVED_TREATMENT));
        lbm.registerReceiver(dataReceiver, new IntentFilter(Intents.ACTION_NEW_FOOD));
        lbm.registerReceiver(dataReceiver, new IntentFilter(Intents.ACTION_CHANGED_FOOD));
        lbm.registerReceiver(dataReceiver, new IntentFilter(Intents.ACTION_REMOVED_FOOD));
        lbm.registerReceiver(dataReceiver, new IntentFilter(Intents.ACTION_NEW_SGV));
        lbm.registerReceiver(dataReceiver, new IntentFilter(Intents.ACTION_NEW_PROFILE));
        lbm.registerReceiver(dataReceiver, new IntentFilter(Intents.ACTION_NEW_STATUS));
        lbm.registerReceiver(dataReceiver, new IntentFilter(Intents.ACTION_NEW_MBG));
        lbm.registerReceiver(dataReceiver, new IntentFilter(Intents.ACTION_NEW_DEVICESTATUS));
        lbm.registerReceiver(dataReceiver, new IntentFilter(Intents.ACTION_NEW_CAL));

        //register alarms
        lbm.registerReceiver(alarmReceiver, new IntentFilter(Intents.ACTION_ALARM));
        lbm.registerReceiver(alarmReceiver, new IntentFilter(Intents.ACTION_ANNOUNCEMENT));
        lbm.registerReceiver(alarmReceiver, new IntentFilter(Intents.ACTION_CLEAR_ALARM));
        lbm.registerReceiver(alarmReceiver, new IntentFilter(Intents.ACTION_URGENT_ALARM));

        this.timeDateOrTZChangeReceiver = new TimeDateOrTZChangeReceiver();
        this.timeDateOrTZChangeReceiver.registerBroadcasts(this);

    }

    public static String gs(@StringRes int id) {
        return sResources.getString(id);
    }

    public static String gs(@StringRes int id, Object... args) {
        return sResources.getString(id, args);
    }

    public static String gq(@PluralsRes int id, int quantity, Object... args) {
        return sResources.getQuantityString(id, quantity, args);
    }

    public static int gc(@ColorRes int id) {
        return ContextCompat.getColor(instance(), id);
    }

    public static MainApp instance() {
        return sInstance;
    }

    public static DatabaseHelper getDbHelper() {
        return sDatabaseHelper;
    }

    public static FirebaseAnalytics getFirebaseAnalytics() {
        return mFirebaseAnalytics;
    }

    public static ConstraintChecker getConstraintChecker() {
        return sConstraintsChecker;
    }

    public static ArrayList<PluginBase> getPluginsList() {
        return pluginsList;
    }

    public static ArrayList<PluginBase> getSpecificPluginsList(PluginType type) {
        ArrayList<PluginBase> newList = new ArrayList<>();

        if (pluginsList != null) {
            for (PluginBase p : pluginsList) {
                if (p.getType() == type)
                    newList.add(p);
            }
        } else {
            log.error("pluginsList=null");
        }
        return newList;
    }

    public static ArrayList<PluginBase> getSpecificPluginsVisibleInList(PluginType type) {
        ArrayList<PluginBase> newList = new ArrayList<>();

        if (pluginsList != null) {
            for (PluginBase p : pluginsList) {
                if (p.getType() == type)
                    if (p.showInList(type))
                        newList.add(p);
            }
        } else {
            log.error("pluginsList=null");
        }
        return newList;
    }

    public static ArrayList<PluginBase> getSpecificPluginsListByInterface(Class interfaceClass) {
        ArrayList<PluginBase> newList = new ArrayList<>();

        if (pluginsList != null) {
            for (PluginBase p : pluginsList) {
                if (p.getClass() != ConfigBuilderPlugin.class && interfaceClass.isAssignableFrom(p.getClass()))
                    newList.add(p);
            }
        } else {
            log.error("pluginsList=null");
        }
        return newList;
    }

    public static ArrayList<PluginBase> getSpecificPluginsVisibleInListByInterface(Class interfaceClass, PluginType type) {
        ArrayList<PluginBase> newList = new ArrayList<>();

        if (pluginsList != null) {
            for (PluginBase p : pluginsList) {
                if (p.getClass() != ConfigBuilderPlugin.class && interfaceClass.isAssignableFrom(p.getClass()))
                    if (p.showInList(type))
                        newList.add(p);
            }
        } else {
            log.error("pluginsList=null");
        }
        return newList;
    }

    public static boolean isEngineeringModeOrRelease() {
        if (!Config.APS)
            return true;
        return engineeringMode || !devBranch;
    }

    public static boolean isDev() {
        return devBranch;
    }

    public static int getIcon() {
        if (Config.NSCLIENT)
            return R.mipmap.ic_yellowowl;
        else if (Config.PUMPCONTROL)
            return R.mipmap.ic_pumpcontrol;
        else
            return R.mipmap.ic_launcher;
    }

    public static int getNotificationIcon() {
        if (Config.NSCLIENT)
            return R.drawable.ic_notif_nsclient;
        else if (Config.PUMPCONTROL)
            return R.drawable.ic_notif_pumpcontrol;
        else
            return R.drawable.ic_notif_aaps;
    }

    @Override
    public void onTerminate() {
        if (L.isEnabled(L.CORE))
            log.debug("onTerminate");

        if (timeDateOrTZChangeReceiver != null)
            unregisterReceiver(timeDateOrTZChangeReceiver);
        unregisterActivityLifecycleCallbacks(ActivityMonitor.INSTANCE);
        KeepAliveReceiver.cancelAlarm(this);
        super.onTerminate();
    }

    public static int dpToPx(int dp) {
        float scale = sResources.getDisplayMetrics().density;
        return (int) (dp * scale + 0.5f);
    }
}
