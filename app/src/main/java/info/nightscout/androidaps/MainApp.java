package info.nightscout.androidaps;

import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.wifi.WifiManager;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.j256.ormlite.android.apptools.OpenHelperManager;

import net.danlew.android.joda.JodaTimeAndroid;

import java.util.List;

import javax.inject.Inject;

import dagger.android.AndroidInjector;
import dagger.android.DaggerApplication;
import info.nightscout.androidaps.database.AppRepository;
import info.nightscout.androidaps.database.transactions.VersionChangeTransaction;
import info.nightscout.androidaps.db.CompatDBHelper;
import info.nightscout.androidaps.db.DatabaseHelper;
import info.nightscout.androidaps.db.StaticInjector;
import info.nightscout.androidaps.dependencyInjection.DaggerAppComponent;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.logging.AAPSLogger;
import info.nightscout.androidaps.logging.LTag;
import info.nightscout.androidaps.plugins.configBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.configBuilder.PluginStore;
import info.nightscout.androidaps.plugins.constraints.versionChecker.VersionCheckerUtils;
import info.nightscout.androidaps.plugins.general.nsclient.NSUpload;
import info.nightscout.androidaps.receivers.BTReceiver;
import info.nightscout.androidaps.receivers.ChargingStateReceiver;
import info.nightscout.androidaps.receivers.DataReceiver;
import info.nightscout.androidaps.receivers.KeepAliveReceiver;
import info.nightscout.androidaps.receivers.NetworkChangeReceiver;
import info.nightscout.androidaps.receivers.TimeDateOrTZChangeReceiver;
import info.nightscout.androidaps.services.Intents;
import info.nightscout.androidaps.utils.ActivityMonitor;
import info.nightscout.androidaps.utils.locale.LocaleHelper;
import info.nightscout.androidaps.utils.sharedPreferences.SP;
import io.reactivex.disposables.CompositeDisposable;

public class MainApp extends DaggerApplication {

    static DatabaseHelper sDatabaseHelper = null;

    private final CompositeDisposable disposable = new CompositeDisposable();

    @Inject PluginStore pluginStore;
    @Inject AAPSLogger aapsLogger;
    @Inject ActivityMonitor activityMonitor;
    @Inject VersionCheckerUtils versionCheckersUtils;
    @Inject SP sp;
    @Inject NSUpload nsUpload;
    @Inject Config config;

    @Inject ConfigBuilderPlugin configBuilderPlugin;
    @Inject KeepAliveReceiver.KeepAliveManager keepAliveManager;
    @Inject List<PluginBase> plugins;
    @Inject CompatDBHelper compatDBHelper;
    @Inject AppRepository repository;

    @Inject StaticInjector staticInjector; // TODO avoid , here fake only to initialize

    @Override
    public void onCreate() {
        super.onCreate();

        aapsLogger.debug("onCreate");
        LocaleHelper.INSTANCE.update(this);
        sDatabaseHelper = OpenHelperManager.getHelper(this, DatabaseHelper.class);
/*
        Thread.setDefaultUncaughtExceptionHandler((thread, ex) -> {
            if (ex instanceof InternalError) {
                // usually the app trying to spawn a thread while being killed
                return;
            }
            aapsLogger.error("Uncaught exception crashing app", ex);
        });
*/
        String gitRemote = BuildConfig.REMOTE;
        String commitHash = BuildConfig.HEAD;
        if (gitRemote.contains("NoGitSystemAvailable")) {
            gitRemote = null;
            commitHash = null;
        }
        disposable.add(repository.runTransaction(new VersionChangeTransaction(BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE, gitRemote, commitHash)).subscribe());
        disposable.add(compatDBHelper.dbChangeDisposable());

        registerActivityLifecycleCallbacks(activityMonitor);

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

        nsUpload.uploadAppStart();

        new Thread(() -> keepAliveManager.setAlarm(this)).start();
        doMigrations();
    }


    private void doMigrations() {
        // set values for different builds
        if (!sp.contains(R.string.key_ns_alarms))
            sp.putBoolean(R.string.key_ns_alarms, config.getNSCLIENT());
        if (!sp.contains(R.string.key_ns_announcements))
            sp.putBoolean(R.string.key_ns_announcements, config.getNSCLIENT());
        if (!sp.contains(R.string.key_language))
            sp.putString(R.string.key_language, "default");
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

    public static DatabaseHelper getDbHelper() {
        return sDatabaseHelper;
    }

    @Override
    public void onTerminate() {
        aapsLogger.debug(LTag.CORE, "onTerminate");
        unregisterActivityLifecycleCallbacks(activityMonitor);
        keepAliveManager.cancelAlarm(this);
        super.onTerminate();
    }
}
