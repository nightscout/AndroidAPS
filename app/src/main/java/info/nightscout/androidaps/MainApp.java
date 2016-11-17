package info.nightscout.androidaps;

import android.app.Application;
import android.content.res.Resources;
import android.support.annotation.Nullable;

import com.crashlytics.android.Crashlytics;
import com.j256.ormlite.android.apptools.OpenHelperManager;
import com.squareup.otto.Bus;
import com.squareup.otto.ThreadEnforcer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;

import info.nightscout.androidaps.db.DatabaseHelper;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.plugins.Actions.ActionsFragment;
import info.nightscout.androidaps.plugins.Careportal.CareportalFragment;
import info.nightscout.androidaps.plugins.CircadianPercentageProfile.CircadianPercentageProfileFragment;
import info.nightscout.androidaps.plugins.ConfigBuilder.ConfigBuilderFragment;
import info.nightscout.androidaps.plugins.ConfigBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.DanaR.DanaRFragment;
import info.nightscout.androidaps.plugins.Loop.LoopFragment;
import info.nightscout.androidaps.plugins.MM640g.MM640gFragment;
import info.nightscout.androidaps.plugins.NSProfileViewer.NSProfileViewerFragment;
import info.nightscout.androidaps.plugins.Objectives.ObjectivesFragment;
import info.nightscout.androidaps.plugins.OpenAPSMA.OpenAPSMAFragment;
import info.nightscout.androidaps.plugins.Overview.OverviewFragment;
import info.nightscout.androidaps.plugins.SafetyFragment.SafetyFragment;
import info.nightscout.androidaps.plugins.SimpleProfile.SimpleProfileFragment;
import info.nightscout.androidaps.plugins.SmsCommunicator.SmsCommunicatorFragment;
import info.nightscout.androidaps.plugins.SourceNSClient.SourceNSClientFragment;
import info.nightscout.androidaps.plugins.SourceXdrip.SourceXdripFragment;
import info.nightscout.androidaps.plugins.TempBasals.TempBasalsFragment;
import info.nightscout.androidaps.plugins.Treatments.TreatmentsFragment;
import info.nightscout.androidaps.plugins.VirtualPump.VirtualPumpFragment;
import info.nightscout.androidaps.plugins.Wear.WearFragment;
import io.fabric.sdk.android.Fabric;


public class MainApp extends Application {
    private static Logger log = LoggerFactory.getLogger(MainApp.class);

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
        Crashlytics.setString("BUILDVERSION", BuildConfig.BUILDVERSION);
        log.info("Version: " + BuildConfig.VERSION_NAME);
        log.info("BuildVersion: " + BuildConfig.BUILDVERSION);

        sBus = new Bus(ThreadEnforcer.ANY);
        sInstance = this;
        sResources = getResources();

        if (pluginsList == null) {
            pluginsList = new ArrayList<>();
            // Register all tabs in app here
            pluginsList.add(OverviewFragment.getPlugin());
            pluginsList.add(ActionsFragment.getPlugin());
            if (Config.DANAR) pluginsList.add(DanaRFragment.getPlugin());
            if (Config.MM640G) pluginsList.add(MM640gFragment.getPlugin());
            if (Config.CAREPORTALENABLED) pluginsList.add(CareportalFragment.getPlugin());
            pluginsList.add(VirtualPumpFragment.getPlugin());
            if (Config.LOOPENABLED) pluginsList.add(LoopFragment.getPlugin());
            if (Config.OPENAPSMAENABLED) pluginsList.add(OpenAPSMAFragment.getPlugin());
            pluginsList.add(NSProfileViewerFragment.getPlugin());
            pluginsList.add(SimpleProfileFragment.getPlugin());
            pluginsList.add(CircadianPercentageProfileFragment.getPlugin());
            pluginsList.add(TreatmentsFragment.getPlugin());
            pluginsList.add(TempBasalsFragment.getPlugin());
            pluginsList.add(SafetyFragment.getPlugin());
            if (Config.APS) pluginsList.add(ObjectivesFragment.getPlugin());
            pluginsList.add(SourceXdripFragment.getPlugin());
            pluginsList.add(SourceNSClientFragment.getPlugin());
            if (Config.SMSCOMMUNICATORENABLED)
                pluginsList.add(SmsCommunicatorFragment.getPlugin());

            pluginsList.add(WearFragment.getPlugin(this));

            pluginsList.add(sConfigBuilder = ConfigBuilderFragment.getPlugin());

            MainApp.getConfigBuilder().initialize();
        }
        MainApp.getConfigBuilder().uploadAppStart();
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