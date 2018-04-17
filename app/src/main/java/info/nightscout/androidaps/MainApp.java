package info.nightscout.androidaps;

import android.app.Application;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.os.SystemClock;
import android.support.annotation.Nullable;
import android.support.v4.content.LocalBroadcastManager;

import com.crashlytics.android.Crashlytics;
import com.crashlytics.android.answers.Answers;
import com.crashlytics.android.answers.CustomEvent;
import com.j256.ormlite.android.apptools.OpenHelperManager;
import com.squareup.otto.Bus;
import com.squareup.otto.LoggingBus;
import com.squareup.otto.ThreadEnforcer;

import net.danlew.android.joda.JodaTimeAndroid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;

import info.nightscout.androidaps.Services.Intents;
import info.nightscout.androidaps.db.DatabaseHelper;
import info.nightscout.androidaps.interfaces.InsulinInterface;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.plugins.Actions.ActionsFragment;
import info.nightscout.androidaps.plugins.Careportal.CareportalPlugin;
import info.nightscout.androidaps.plugins.ConfigBuilder.ConfigBuilderFragment;
import info.nightscout.androidaps.plugins.ConfigBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.ConstraintsObjectives.ObjectivesPlugin;
import info.nightscout.androidaps.plugins.ConstraintsSafety.SafetyPlugin;
import info.nightscout.androidaps.plugins.Food.FoodPlugin;
import info.nightscout.androidaps.plugins.Insulin.InsulinFastactingPlugin;
import info.nightscout.androidaps.plugins.Insulin.InsulinFastactingProlongedPlugin;
import info.nightscout.androidaps.plugins.Insulin.InsulinOrefFreePeakPlugin;
import info.nightscout.androidaps.plugins.Insulin.InsulinOrefRapidActingPlugin;
import info.nightscout.androidaps.plugins.Insulin.InsulinOrefUltraRapidActingPlugin;
import info.nightscout.androidaps.plugins.IobCobCalculator.IobCobCalculatorPlugin;
import info.nightscout.androidaps.plugins.Loop.LoopPlugin;
import info.nightscout.androidaps.plugins.NSClientInternal.NSClientInternalPlugin;
import info.nightscout.androidaps.plugins.NSClientInternal.receivers.AckAlarmReceiver;
import info.nightscout.androidaps.plugins.OpenAPSAMA.OpenAPSAMAPlugin;
import info.nightscout.androidaps.plugins.OpenAPSMA.OpenAPSMAPlugin;
import info.nightscout.androidaps.plugins.Overview.OverviewPlugin;
import info.nightscout.androidaps.plugins.Persistentnotification.PersistentNotificationPlugin;
import info.nightscout.androidaps.plugins.ProfileCircadianPercentage.CircadianPercentageProfileFragment;
import info.nightscout.androidaps.plugins.ProfileLocal.LocalProfilePlugin;
import info.nightscout.androidaps.plugins.ProfileNS.NSProfilePlugin;
import info.nightscout.androidaps.plugins.ProfileSimple.SimpleProfilePlugin;
import info.nightscout.androidaps.plugins.PumpDanaR.DanaRPlugin;
import info.nightscout.androidaps.plugins.PumpDanaRKorean.DanaRKoreanPlugin;
import info.nightscout.androidaps.plugins.PumpDanaRS.DanaRSPlugin;
import info.nightscout.androidaps.plugins.PumpDanaRv2.DanaRv2Plugin;
import info.nightscout.androidaps.plugins.PumpMDI.MDIPlugin;
import info.nightscout.androidaps.plugins.PumpVirtual.VirtualPumpPlugin;
import info.nightscout.androidaps.plugins.SensitivityAAPS.SensitivityAAPSPlugin;
import info.nightscout.androidaps.plugins.SensitivityOref0.SensitivityOref0Plugin;
import info.nightscout.androidaps.plugins.SensitivityWeightedAverage.SensitivityWeightedAveragePlugin;
import info.nightscout.androidaps.plugins.SmsCommunicator.SmsCommunicatorPlugin;
import info.nightscout.androidaps.plugins.SourceDexcomG5.SourceDexcomG5Plugin;
import info.nightscout.androidaps.plugins.SourceGlimp.SourceGlimpPlugin;
import info.nightscout.androidaps.plugins.SourceMM640g.SourceMM640gPlugin;
import info.nightscout.androidaps.plugins.SourceNSClient.SourceNSClientPlugin;
import info.nightscout.androidaps.plugins.SourceXdrip.SourceXdripPlugin;
import info.nightscout.androidaps.plugins.Treatments.TreatmentsPlugin;
import info.nightscout.androidaps.plugins.Wear.WearPlugin;
import info.nightscout.androidaps.plugins.XDripStatusline.StatuslinePlugin;
import info.nightscout.androidaps.receivers.DataReceiver;
import info.nightscout.androidaps.receivers.KeepAliveReceiver;
import info.nightscout.androidaps.receivers.NSAlarmReceiver;
import info.nightscout.utils.NSUpload;
import io.fabric.sdk.android.Fabric;


public class MainApp extends Application {
    private static Logger log = LoggerFactory.getLogger(MainApp.class);
    private static KeepAliveReceiver keepAliveReceiver;

    private static Bus sBus;
    private static MainApp sInstance;
    public static Resources sResources;

    private static DatabaseHelper sDatabaseHelper = null;
    private static ConfigBuilderPlugin sConfigBuilder = null;

    private static ArrayList<PluginBase> pluginsList = null;

    private static DataReceiver dataReceiver = new DataReceiver();
    private static NSAlarmReceiver alarmReciever = new NSAlarmReceiver();
    private static AckAlarmReceiver ackAlarmReciever = new AckAlarmReceiver();
    private LocalBroadcastManager lbm;

    @Override
    public void onCreate() {
        super.onCreate();
        Fabric.with(this, new Crashlytics());
        Fabric.with(this, new Answers());
        JodaTimeAndroid.init(this);
        Crashlytics.setString("BUILDVERSION", BuildConfig.BUILDVERSION);
        log.info("Version: " + BuildConfig.VERSION_NAME);
        log.info("BuildVersion: " + BuildConfig.BUILDVERSION);

        sBus = Config.logEvents ? new LoggingBus(ThreadEnforcer.ANY) : new Bus(ThreadEnforcer.ANY);

        sInstance = this;
        sResources = getResources();

        registerLocalBroadcastReceiver();

        if (pluginsList == null) {
            pluginsList = new ArrayList<>();
            // Register all tabs in app here
            pluginsList.add(OverviewPlugin.getPlugin());
            pluginsList.add(IobCobCalculatorPlugin.getPlugin());
            if (Config.ACTION) pluginsList.add(ActionsFragment.getPlugin());
            pluginsList.add(InsulinFastactingPlugin.getPlugin());
            pluginsList.add(InsulinFastactingProlongedPlugin.getPlugin());
            pluginsList.add(InsulinOrefRapidActingPlugin.getPlugin());
            pluginsList.add(InsulinOrefUltraRapidActingPlugin.getPlugin());
            pluginsList.add(InsulinOrefFreePeakPlugin.getPlugin());
            pluginsList.add(SensitivityOref0Plugin.getPlugin());
            pluginsList.add(SensitivityAAPSPlugin.getPlugin());
            pluginsList.add(SensitivityWeightedAveragePlugin.getPlugin());
            if (Config.DANAR) pluginsList.add(DanaRPlugin.getPlugin());
            if (Config.DANAR) pluginsList.add(DanaRKoreanPlugin.getPlugin());
            if (Config.DANAR) pluginsList.add(DanaRv2Plugin.getPlugin());
            if (Config.DANAR) pluginsList.add(DanaRSPlugin.getPlugin());
            pluginsList.add(CareportalPlugin.getPlugin());
            if (Config.MDI) pluginsList.add(MDIPlugin.getPlugin());
            if (Config.VIRTUALPUMP) pluginsList.add(VirtualPumpPlugin.getPlugin());
            if (Config.APS) pluginsList.add(LoopPlugin.getPlugin());
            if (Config.APS) pluginsList.add(OpenAPSMAPlugin.getPlugin());
            if (Config.APS) pluginsList.add(OpenAPSAMAPlugin.getPlugin());
            pluginsList.add(NSProfilePlugin.getPlugin());
            if (Config.OTHERPROFILES) pluginsList.add(SimpleProfilePlugin.getPlugin());
            if (Config.OTHERPROFILES) pluginsList.add(LocalProfilePlugin.getPlugin());
            if (Config.OTHERPROFILES)
                pluginsList.add(CircadianPercentageProfileFragment.getPlugin());
            pluginsList.add(TreatmentsPlugin.getPlugin());
            if (Config.SAFETY) pluginsList.add(SafetyPlugin.getPlugin());
            if (Config.APS) pluginsList.add(ObjectivesPlugin.getPlugin());
            if (!Config.NSCLIENT && !Config.G5UPLOADER)
                pluginsList.add(SourceXdripPlugin.getPlugin());
            if (!Config.G5UPLOADER)
                pluginsList.add(SourceNSClientPlugin.getPlugin());
            if (!Config.NSCLIENT && !Config.G5UPLOADER)
                pluginsList.add(SourceMM640gPlugin.getPlugin());
            if (!Config.NSCLIENT && !Config.G5UPLOADER)
                pluginsList.add(SourceGlimpPlugin.getPlugin());
            if (!Config.NSCLIENT)
                pluginsList.add(SourceDexcomG5Plugin.getPlugin());
            if (Config.SMSCOMMUNICATORENABLED) pluginsList.add(SmsCommunicatorPlugin.getPlugin());
            pluginsList.add(FoodPlugin.getPlugin());

            pluginsList.add(WearPlugin.initPlugin(this));
            pluginsList.add(StatuslinePlugin.initPlugin(this));
            pluginsList.add(new PersistentNotificationPlugin(this));
            pluginsList.add(NSClientInternalPlugin.getPlugin());

            pluginsList.add(sConfigBuilder = ConfigBuilderFragment.getPlugin());

            MainApp.getConfigBuilder().initialize();
        }
        NSUpload.uploadAppStart();
        if (Config.NSCLIENT)
            Answers.getInstance().logCustom(new CustomEvent("AppStart-NSClient"));
        else if (Config.G5UPLOADER)
            Answers.getInstance().logCustom(new CustomEvent("AppStart-G5Uploader"));
        else if (Config.PUMPCONTROL)
            Answers.getInstance().logCustom(new CustomEvent("AppStart-PumpControl"));
        else if (MainApp.getConfigBuilder().isClosedModeEnabled())
            Answers.getInstance().logCustom(new CustomEvent("AppStart-ClosedLoop"));
        else
            Answers.getInstance().logCustom(new CustomEvent("AppStart-OpenLoop"));

        new Thread(new Runnable() {
            @Override
            public void run() {
                SystemClock.sleep(5000);
                ConfigBuilderPlugin.getCommandQueue().readStatus("Initialization", null);
                startKeepAliveService();
            }
        }).start();

    }

    private void registerLocalBroadcastReceiver() {
        lbm = LocalBroadcastManager.getInstance(this);
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
        lbm.registerReceiver(alarmReciever, new IntentFilter(Intents.ACTION_ALARM));
        lbm.registerReceiver(alarmReciever, new IntentFilter(Intents.ACTION_ANNOUNCEMENT));
        lbm.registerReceiver(alarmReciever, new IntentFilter(Intents.ACTION_CLEAR_ALARM));
        lbm.registerReceiver(alarmReciever, new IntentFilter(Intents.ACTION_URGENT_ALARM));

        //register ack alarm
        lbm.registerReceiver(ackAlarmReciever, new IntentFilter(Intents.ACTION_ACK_ALARM));
    }

    private void startKeepAliveService() {
        if (keepAliveReceiver == null) {
            keepAliveReceiver = new KeepAliveReceiver();
            keepAliveReceiver.setAlarm(this);
        }
    }


    public void stopKeepAliveService() {
        if (keepAliveReceiver != null)
            KeepAliveReceiver.cancelAlarm(this);
    }

    public static Bus bus() {
        return sBus;
    }

    public static String gs(int id) {
        return sResources.getString(id);
    }

    public static String gs(int id, Object... args) {
        return sResources.getString(id, args);
    }

    public static MainApp instance() {
        return sInstance;
    }

    public static DatabaseHelper getDbHelper() {
        if (sDatabaseHelper == null) {
            sDatabaseHelper = OpenHelperManager.getHelper(sInstance, DatabaseHelper.class);
        }
        return sDatabaseHelper;
    }

    public static void closeDbHelper() {
        if (sDatabaseHelper != null) {
            sDatabaseHelper.close();
            sDatabaseHelper = null;
        }
    }

    public static ConfigBuilderPlugin getConfigBuilder() {
        return sConfigBuilder;
    }

    public static ArrayList<PluginBase> getPluginsList() {
        return pluginsList;
    }

    public static ArrayList<PluginBase> getSpecificPluginsList(int type) {
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

    @Nullable
    public static InsulinInterface getInsulinIterfaceById(int id) {
        if (pluginsList != null) {
            for (PluginBase p : pluginsList) {
                if (p.getType() == PluginBase.INSULIN && ((InsulinInterface) p).getId() == id)
                    return (InsulinInterface) p;
            }
        } else {
            log.error("InsulinInterface not found");
        }
        return null;
    }

    public static ArrayList<PluginBase> getSpecificPluginsVisibleInList(int type) {
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

    public static ArrayList<PluginBase> getSpecificPluginsVisibleInListByInterface(Class interfaceClass, int type) {
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

    @Nullable
    public static <T extends PluginBase> T getSpecificPlugin(Class<T> pluginClass) {
        if (pluginsList != null) {
            for (PluginBase p : pluginsList) {
                if (pluginClass.isAssignableFrom(p.getClass()))
                    return (T) p;
            }
        } else {
            log.error("pluginsList=null");
        }
        return null;
    }

    @Override
    public void onTerminate() {
        super.onTerminate();
        sDatabaseHelper.close();
    }
}
