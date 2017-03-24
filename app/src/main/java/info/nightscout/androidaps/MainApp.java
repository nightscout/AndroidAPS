package info.nightscout.androidaps;

import android.app.Application;
import android.content.Intent;
import android.content.res.Resources;
import android.support.annotation.Nullable;

import com.crashlytics.android.Crashlytics;
import com.crashlytics.android.answers.Answers;
import com.crashlytics.android.answers.CustomEvent;
import com.j256.ormlite.android.apptools.OpenHelperManager;
import com.squareup.otto.Bus;
import com.squareup.otto.ThreadEnforcer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;

import info.nightscout.androidaps.db.DatabaseHelper;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.interfaces.PumpInterface;
import info.nightscout.androidaps.plugins.Actions.ActionsFragment;
import info.nightscout.androidaps.plugins.Careportal.CareportalFragment;
import info.nightscout.androidaps.plugins.CircadianPercentageProfile.CircadianPercentageProfileFragment;
import info.nightscout.androidaps.plugins.ConfigBuilder.ConfigBuilderFragment;
import info.nightscout.androidaps.plugins.ConfigBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.DanaR.DanaRFragment;
import info.nightscout.androidaps.plugins.DanaRKorean.DanaRKoreanFragment;
import info.nightscout.androidaps.plugins.LocalProfile.LocalProfileFragment;
import info.nightscout.androidaps.plugins.Loop.LoopFragment;
import info.nightscout.androidaps.plugins.MDI.MDIFragment;
import info.nightscout.androidaps.plugins.NSClientInternal.NSClientInternalFragment;
import info.nightscout.androidaps.plugins.NSProfile.NSProfileFragment;
import info.nightscout.androidaps.plugins.Objectives.ObjectivesFragment;
import info.nightscout.androidaps.plugins.OpenAPSAMA.OpenAPSAMAFragment;
import info.nightscout.androidaps.plugins.OpenAPSMA.OpenAPSMAFragment;
import info.nightscout.androidaps.plugins.Overview.OverviewFragment;
import info.nightscout.androidaps.plugins.SafetyFragment.SafetyFragment;
import info.nightscout.androidaps.plugins.SimpleProfile.SimpleProfileFragment;
import info.nightscout.androidaps.plugins.SmsCommunicator.SmsCommunicatorFragment;
import info.nightscout.androidaps.plugins.SourceGlimp.SourceGlimpFragment;
import info.nightscout.androidaps.plugins.SourceMM640g.SourceMM640gFragment;
import info.nightscout.androidaps.plugins.SourceNSClient.SourceNSClientFragment;
import info.nightscout.androidaps.plugins.SourceXdrip.SourceXdripFragment;
import info.nightscout.androidaps.plugins.TempBasals.TempBasalsFragment;
import info.nightscout.androidaps.plugins.TempTargetRange.TempTargetRangeFragment;
import info.nightscout.androidaps.plugins.Treatments.TreatmentsFragment;
import info.nightscout.androidaps.plugins.VirtualPump.VirtualPumpFragment;
import info.nightscout.androidaps.plugins.Wear.WearFragment;
import info.nightscout.androidaps.plugins.persistentnotification.PersistentNotificationPlugin;
import info.nightscout.androidaps.receivers.KeepAliveReceiver;
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

    @Override
    public void onCreate() {
        super.onCreate();
        Fabric.with(this, new Crashlytics());
        Fabric.with(this, new Answers());
        Crashlytics.setString("BUILDVERSION", BuildConfig.BUILDVERSION);
        log.info("Version: " + BuildConfig.VERSION_NAME);
        log.info("BuildVersion: " + BuildConfig.BUILDVERSION);

        Answers.getInstance().logCustom(new CustomEvent("AppStart"));

        sBus = new Bus(ThreadEnforcer.ANY);
        sInstance = this;
        sResources = getResources();

        if (pluginsList == null) {
            pluginsList = new ArrayList<>();
            // Register all tabs in app here
            pluginsList.add(OverviewFragment.getPlugin());
            if (Config.ACTION) pluginsList.add(ActionsFragment.getPlugin());
            if (Config.DANAR) pluginsList.add(DanaRFragment.getPlugin());
            if (Config.DANARKOREAN) pluginsList.add(DanaRKoreanFragment.getPlugin());
            pluginsList.add(CareportalFragment.getPlugin());
            if (Config.MDI) pluginsList.add(MDIFragment.getPlugin());
            if (Config.VIRTUALPUMP) pluginsList.add(VirtualPumpFragment.getPlugin());
            if (Config.LOOPENABLED) pluginsList.add(LoopFragment.getPlugin());
            if (Config.OPENAPSENABLED) pluginsList.add(OpenAPSMAFragment.getPlugin());
            if (Config.OPENAPSENABLED) pluginsList.add(OpenAPSAMAFragment.getPlugin());
            pluginsList.add(NSProfileFragment.getPlugin());
            if (Config.OTHERPROFILES) pluginsList.add(SimpleProfileFragment.getPlugin());
            if (Config.OTHERPROFILES) pluginsList.add(LocalProfileFragment.getPlugin());
            if (Config.OTHERPROFILES) pluginsList.add(CircadianPercentageProfileFragment.getPlugin());
            if (Config.APS) pluginsList.add(TempTargetRangeFragment.getPlugin());
            pluginsList.add(TreatmentsFragment.getPlugin());
            if (Config.TEMPBASALS) pluginsList.add(TempBasalsFragment.getPlugin());
            if (Config.SAFETY) pluginsList.add(SafetyFragment.getPlugin());
            if (Config.APS) pluginsList.add(ObjectivesFragment.getPlugin());
            pluginsList.add(SourceXdripFragment.getPlugin());
            pluginsList.add(SourceNSClientFragment.getPlugin());
            pluginsList.add(SourceMM640gFragment.getPlugin());
            pluginsList.add(SourceGlimpFragment.getPlugin());
            if (Config.SMSCOMMUNICATORENABLED) pluginsList.add(SmsCommunicatorFragment.getPlugin());

            if (Config.WEAR) pluginsList.add(WearFragment.getPlugin(this));
            pluginsList.add(new PersistentNotificationPlugin(this));
            pluginsList.add(NSClientInternalFragment.getPlugin());

            pluginsList.add(sConfigBuilder = ConfigBuilderFragment.getPlugin());

            MainApp.getConfigBuilder().initialize();
        }
        MainApp.getConfigBuilder().uploadAppStart();

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

    private void startKeepAliveService() {
        if (keepAliveReceiver == null) {
            keepAliveReceiver = new KeepAliveReceiver();
            if (Config.DANAR) {
                startService(new Intent(this, info.nightscout.androidaps.plugins.DanaR.Services.ExecutionService.class));
                startService(new Intent(this, info.nightscout.androidaps.plugins.DanaRKorean.Services.ExecutionService.class));
            }
            keepAliveReceiver.setAlarm(this);
        }
    }



    public void stopKeepAliveService(){
        if(keepAliveReceiver!=null)
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

    @Nullable
    public static PluginBase getSpecificPlugin(Class pluginClass) {
        if (pluginsList != null) {
            for (PluginBase p : pluginsList) {
                if (p.getClass() == pluginClass)
                    return p;
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
