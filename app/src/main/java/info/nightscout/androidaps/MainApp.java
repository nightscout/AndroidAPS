package info.nightscout.androidaps;

import android.app.Application;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.support.annotation.Nullable;
import android.support.v4.content.LocalBroadcastManager;

import com.crashlytics.android.Crashlytics;
import com.crashlytics.android.answers.Answers;
import com.crashlytics.android.answers.CustomEvent;
import com.j256.ormlite.android.apptools.OpenHelperManager;
import com.squareup.otto.Bus;
import com.squareup.otto.ThreadEnforcer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;

import info.nightscout.androidaps.Services.Intents;
import info.nightscout.androidaps.db.DatabaseHelper;
import info.nightscout.androidaps.interfaces.InsulinInterface;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.interfaces.PumpInterface;
import info.nightscout.androidaps.plugins.Actions.ActionsFragment;
import info.nightscout.androidaps.plugins.Careportal.CareportalFragment;
import info.nightscout.androidaps.plugins.ConfigBuilder.ConfigBuilderFragment;
import info.nightscout.androidaps.plugins.ConfigBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.ConstraintsObjectives.ObjectivesFragment;
import info.nightscout.androidaps.plugins.ConstraintsSafety.SafetyPlugin;
import info.nightscout.androidaps.plugins.InsulinFastacting.InsulinFastactingFragment;
import info.nightscout.androidaps.plugins.InsulinFastactingProlonged.InsulinFastactingProlongedFragment;
import info.nightscout.androidaps.plugins.InsulinOrefCurves.InsulinOrefFreePeakFragment;
import info.nightscout.androidaps.plugins.InsulinOrefCurves.InsulinOrefRapidActingFragment;
import info.nightscout.androidaps.plugins.InsulinOrefCurves.InsulinOrefUltraRapidActingFragment;
import info.nightscout.androidaps.plugins.IobCobCalculator.IobCobCalculatorPlugin;
import info.nightscout.androidaps.plugins.Loop.LoopFragment;
import info.nightscout.androidaps.plugins.NSClientInternal.NSClientInternalFragment;
import info.nightscout.androidaps.plugins.NSClientInternal.receivers.AckAlarmReceiver;
import info.nightscout.androidaps.plugins.OpenAPSAMA.OpenAPSAMAFragment;
import info.nightscout.androidaps.plugins.OpenAPSMA.OpenAPSMAFragment;
import info.nightscout.androidaps.plugins.Overview.OverviewFragment;
import info.nightscout.androidaps.plugins.Persistentnotification.PersistentNotificationPlugin;
import info.nightscout.androidaps.plugins.ProfileCircadianPercentage.CircadianPercentageProfileFragment;
import info.nightscout.androidaps.plugins.ProfileLocal.LocalProfileFragment;
import info.nightscout.androidaps.plugins.ProfileNS.NSProfileFragment;
import info.nightscout.androidaps.plugins.ProfileSimple.SimpleProfileFragment;
import info.nightscout.androidaps.plugins.PumpDanaR.DanaRFragment;
import info.nightscout.androidaps.plugins.PumpDanaR.services.DanaRExecutionService;
import info.nightscout.androidaps.plugins.PumpDanaRKorean.DanaRKoreanFragment;
import info.nightscout.androidaps.plugins.PumpDanaRKorean.services.DanaRKoreanExecutionService;
import info.nightscout.androidaps.plugins.PumpDanaRv2.DanaRv2Fragment;
import info.nightscout.androidaps.plugins.PumpDanaRv2.services.DanaRv2ExecutionService;
import info.nightscout.androidaps.plugins.PumpMDI.MDIPlugin;
import info.nightscout.androidaps.plugins.PumpVirtual.VirtualPumpPlugin;
import info.nightscout.androidaps.plugins.SensitivityAAPS.SensitivityAAPSPlugin;
import info.nightscout.androidaps.plugins.SensitivityOref0.SensitivityOref0Plugin;
import info.nightscout.androidaps.plugins.SensitivityWeightedAverage.SensitivityWeightedAveragePlugin;
import info.nightscout.androidaps.plugins.SmsCommunicator.SmsCommunicatorFragment;
import info.nightscout.androidaps.plugins.SourceGlimp.SourceGlimpPlugin;
import info.nightscout.androidaps.plugins.SourceMM640g.SourceMM640gPlugin;
import info.nightscout.androidaps.plugins.SourceNSClient.SourceNSClientPlugin;
import info.nightscout.androidaps.plugins.SourceXdrip.SourceXdripPlugin;
import info.nightscout.androidaps.plugins.Treatments.TreatmentsFragment;
import info.nightscout.androidaps.plugins.Wear.WearFragment;
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
        Crashlytics.setString("BUILDVERSION", BuildConfig.BUILDVERSION);
        log.info("Version: " + BuildConfig.VERSION_NAME);
        log.info("BuildVersion: " + BuildConfig.BUILDVERSION);

        sBus = new Bus(ThreadEnforcer.ANY);
        sInstance = this;
        sResources = getResources();

        registerLocalBroadcastReceiver();

        if (pluginsList == null) {
            pluginsList = new ArrayList<>();
            // Register all tabs in app here
            pluginsList.add(OverviewFragment.getPlugin());
            pluginsList.add(IobCobCalculatorPlugin.getPlugin());
            if (Config.ACTION) pluginsList.add(ActionsFragment.getPlugin());
            pluginsList.add(InsulinFastactingFragment.getPlugin());
            pluginsList.add(InsulinFastactingProlongedFragment.getPlugin());
            pluginsList.add(InsulinOrefRapidActingFragment.getPlugin());
            pluginsList.add(InsulinOrefUltraRapidActingFragment.getPlugin());
            pluginsList.add(InsulinOrefFreePeakFragment.getPlugin());
            pluginsList.add(SensitivityOref0Plugin.getPlugin());
            pluginsList.add(SensitivityAAPSPlugin.getPlugin());
            pluginsList.add(SensitivityWeightedAveragePlugin.getPlugin());
            if (Config.DANAR) pluginsList.add(DanaRFragment.getPlugin());
            if (Config.DANAR) pluginsList.add(DanaRKoreanFragment.getPlugin());
            if (Config.DANARv2) pluginsList.add(DanaRv2Fragment.getPlugin());
            pluginsList.add(CareportalFragment.getPlugin());
            if (Config.MDI) pluginsList.add(MDIPlugin.getPlugin());
            if (Config.VIRTUALPUMP) pluginsList.add(VirtualPumpPlugin.getInstance());
            if (Config.LOOPENABLED) pluginsList.add(LoopFragment.getPlugin());
            if (Config.OPENAPSENABLED) pluginsList.add(OpenAPSMAFragment.getPlugin());
            if (Config.OPENAPSENABLED) pluginsList.add(OpenAPSAMAFragment.getPlugin());
            pluginsList.add(NSProfileFragment.getPlugin());
            if (Config.OTHERPROFILES) pluginsList.add(SimpleProfileFragment.getPlugin());
            if (Config.OTHERPROFILES) pluginsList.add(LocalProfileFragment.getPlugin());
            if (Config.OTHERPROFILES)
                pluginsList.add(CircadianPercentageProfileFragment.getPlugin());
            pluginsList.add(TreatmentsFragment.getPlugin());
            if (Config.SAFETY) pluginsList.add(SafetyPlugin.getPlugin());
            if (Config.APS) pluginsList.add(ObjectivesFragment.getPlugin());
            if (!Config.NSCLIENT)
                pluginsList.add(SourceXdripPlugin.getPlugin());
            pluginsList.add(SourceNSClientPlugin.getPlugin());
            if (!Config.NSCLIENT)
                pluginsList.add(SourceMM640gPlugin.getPlugin());
            if (!Config.NSCLIENT)
                pluginsList.add(SourceGlimpPlugin.getPlugin());
            if (Config.SMSCOMMUNICATORENABLED) pluginsList.add(SmsCommunicatorFragment.getPlugin());

            if (Config.WEAR) pluginsList.add(WearFragment.getPlugin(this));
            pluginsList.add(StatuslinePlugin.getPlugin(this));
            pluginsList.add(new PersistentNotificationPlugin(this));
            pluginsList.add(NSClientInternalFragment.getPlugin());

            pluginsList.add(sConfigBuilder = ConfigBuilderFragment.getPlugin());

            MainApp.getConfigBuilder().initialize();
        }
        NSUpload.uploadAppStart();
        if (MainApp.getConfigBuilder().isClosedModeEnabled())
            Answers.getInstance().logCustom(new CustomEvent("AppStart-ClosedLoop"));
        else
            Answers.getInstance().logCustom(new CustomEvent("AppStart"));


        startKeepAliveService();

        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                }
                PumpInterface pump = MainApp.getConfigBuilder();
                if (pump != null)
                    pump.refreshDataFromPump("Initialization");
            }
        });
        t.start();

    }

    private void registerLocalBroadcastReceiver() {
        lbm = LocalBroadcastManager.getInstance(this);
        lbm.registerReceiver(dataReceiver, new IntentFilter(Intents.ACTION_NEW_TREATMENT));
        lbm.registerReceiver(dataReceiver, new IntentFilter(Intents.ACTION_CHANGED_TREATMENT));
        lbm.registerReceiver(dataReceiver, new IntentFilter(Intents.ACTION_REMOVED_TREATMENT));
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
            if (Config.DANAR) {
                startService(new Intent(this, DanaRExecutionService.class));
                startService(new Intent(this, DanaRKoreanExecutionService.class));
                startService(new Intent(this, DanaRv2ExecutionService.class));
            }
            keepAliveReceiver.setAlarm(this);
        }
    }


    public void stopKeepAliveService() {
        if (keepAliveReceiver != null)
            keepAliveReceiver.cancelAlarm(this);
    }

    public static Bus bus() {
        return sBus;
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
