package info.nightscout.androidaps;

import android.app.Application;
import android.content.res.Resources;
import android.support.annotation.Nullable;

import com.crashlytics.android.Crashlytics;
import com.j256.ormlite.android.apptools.OpenHelperManager;
import com.squareup.otto.Bus;
import com.squareup.otto.ThreadEnforcer;

import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.plugins.Careportal.CareportalFragment;
import info.nightscout.androidaps.plugins.DanaR.DanaRFragment;
import info.nightscout.androidaps.plugins.Loop.LoopFragment;
import info.nightscout.androidaps.plugins.LowSuspend.LowSuspendFragment;
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
import io.fabric.sdk.android.Fabric;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Iterator;

import info.nightscout.androidaps.db.DatabaseHelper;
import info.nightscout.androidaps.plugins.ConfigBuilder.ConfigBuilderFragment;


public class MainApp  extends Application {
    private static Logger log = LoggerFactory.getLogger(MainApp.class);

    private static Bus sBus;
    private static MainApp sInstance;
    public static Resources sResources;

    private static DatabaseHelper sDatabaseHelper = null;
    private static ConfigBuilderFragment sConfigBuilder = null;

    private static ArrayList<PluginBase> pluginsList = null;

    @Override
    public void onCreate() {
        super.onCreate();
        Fabric.with(this, new Crashlytics());

        sBus = new Bus(ThreadEnforcer.ANY);
        sInstance = this;
        sResources = getResources();

        if (pluginsList == null) {
            pluginsList = new ArrayList<PluginBase>();
            // Register all tabs in app here
            pluginsList.add(OverviewFragment.newInstance());
            if (Config.DANAR) pluginsList.add(DanaRFragment.newInstance());
            if (Config.MM640G) pluginsList.add(MM640gFragment.newInstance());
            pluginsList.add(VirtualPumpFragment.newInstance());
            if (Config.CAREPORTALENABLED) pluginsList.add(CareportalFragment.newInstance());
            if (Config.LOOPENABLED) pluginsList.add(LoopFragment.newInstance());
            if (Config.LOWSUSPEDENABLED) pluginsList.add(LowSuspendFragment.newInstance());
            if (Config.OPENAPSMAENABLED) pluginsList.add(OpenAPSMAFragment.newInstance());
            pluginsList.add(NSProfileViewerFragment.newInstance());
            pluginsList.add(SimpleProfileFragment.newInstance());
            pluginsList.add(TreatmentsFragment.newInstance());
            pluginsList.add(TempBasalsFragment.newInstance());
            pluginsList.add(SafetyFragment.newInstance());
            if (Config.APS) pluginsList.add(ObjectivesFragment.newInstance());
            pluginsList.add(SourceXdripFragment.newInstance());
            pluginsList.add(SourceNSClientFragment.newInstance());
            if (Config.SMSCOMMUNICATORENABLED) pluginsList.add(SmsCommunicatorFragment.newInstance());
            pluginsList.add(sConfigBuilder = ConfigBuilderFragment.newInstance());

            MainApp.getConfigBuilder().initialize();
        }
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

    public static void setConfigBuilder(ConfigBuilderFragment cb) {
        sConfigBuilder = cb;
    }

    public static ConfigBuilderFragment getConfigBuilder() {
        return sConfigBuilder;
    }

    public static ArrayList<PluginBase> getPluginsList() {
        return pluginsList;
    }

    public static ArrayList<PluginBase> getSpecificPluginsList(int type) {
        ArrayList<PluginBase> newList = new ArrayList<PluginBase>();

        if (pluginsList != null) {
            Iterator<PluginBase> it = pluginsList.iterator();
            while (it.hasNext()) {
                PluginBase p = it.next();
                if (p.getType() == type)
                    newList.add(p);
            }
        } else {
            log.error("pluginsList=null");
        }
        return newList;
    }

    public static ArrayList<PluginBase> getSpecificPluginsListByInterface(Class interfaceClass) {
        ArrayList<PluginBase> newList = new ArrayList<PluginBase>();

        if (pluginsList != null) {
            Iterator<PluginBase> it = pluginsList.iterator();
            while (it.hasNext()) {
                PluginBase p = it.next();
                if (p.getClass() != ConfigBuilderFragment.class && interfaceClass.isAssignableFrom(p.getClass()))
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
            Iterator<PluginBase> it = pluginsList.iterator();
            while (it.hasNext()) {
                PluginBase p = it.next();
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