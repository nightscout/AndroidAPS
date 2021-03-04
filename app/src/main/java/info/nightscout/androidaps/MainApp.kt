package info.nightscout.androidaps

import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.j256.ormlite.android.apptools.OpenHelperManager
import dagger.android.AndroidInjector
import dagger.android.DaggerApplication
import info.nightscout.androidaps.database.AppRepository
import info.nightscout.androidaps.database.transactions.VersionChangeTransaction
import info.nightscout.androidaps.db.CompatDBHelper
import info.nightscout.androidaps.db.DatabaseHelper
import info.nightscout.androidaps.db.StaticInjector
import info.nightscout.androidaps.dependencyInjection.DaggerAppComponent
import info.nightscout.androidaps.interfaces.PluginBase
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.configBuilder.ConfigBuilderPlugin
import info.nightscout.androidaps.plugins.configBuilder.PluginStore
import info.nightscout.androidaps.plugins.constraints.versionChecker.VersionCheckerUtils
import info.nightscout.androidaps.plugins.general.nsclient.NSUpload
import info.nightscout.androidaps.receivers.BTReceiver
import info.nightscout.androidaps.receivers.ChargingStateReceiver
import info.nightscout.androidaps.receivers.DataReceiver
import info.nightscout.androidaps.receivers.KeepAliveReceiver.KeepAliveManager
import info.nightscout.androidaps.receivers.NetworkChangeReceiver
import info.nightscout.androidaps.receivers.TimeDateOrTZChangeReceiver
import info.nightscout.androidaps.services.Intents
import info.nightscout.androidaps.utils.ActivityMonitor
import info.nightscout.androidaps.utils.locale.LocaleHelper.update
import info.nightscout.androidaps.utils.sharedPreferences.SP
import io.reactivex.disposables.CompositeDisposable
import net.danlew.android.joda.JodaTimeAndroid
import javax.inject.Inject

class MainApp : DaggerApplication() {

    private val disposable = CompositeDisposable()

    @Inject lateinit var pluginStore: PluginStore
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var activityMonitor: ActivityMonitor
    @Inject lateinit var versionCheckersUtils: VersionCheckerUtils
    @Inject lateinit var sp: SP
    @Inject lateinit var nsUpload: NSUpload
    @Inject lateinit var config: Config
    @Inject lateinit var configBuilderPlugin: ConfigBuilderPlugin
    @Inject lateinit var keepAliveManager: KeepAliveManager
    @Inject lateinit var plugins: List<@JvmSuppressWildcards PluginBase>
    @Inject lateinit var compatDBHelper: CompatDBHelper
    @Inject lateinit var repository: AppRepository
    @Inject lateinit var staticInjector: StaticInjector// TODO avoid , here fake only to initialize

    override fun onCreate() {
        super.onCreate()
        aapsLogger.debug("onCreate")
        update(this)
        dbHelper = OpenHelperManager.getHelper(this, DatabaseHelper::class.java)

        var gitRemote: String? = BuildConfig.REMOTE
        var commitHash: String? = BuildConfig.HEAD
        if (gitRemote?.contains("NoGitSystemAvailable") == true) {
            gitRemote = null
            commitHash = null
        }
        disposable.add(repository.runTransaction(VersionChangeTransaction(BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE, gitRemote, commitHash)).subscribe())
        disposable.add(compatDBHelper.dbChangeDisposable())
        registerActivityLifecycleCallbacks(activityMonitor)
        JodaTimeAndroid.init(this)
        aapsLogger.debug("Version: " + BuildConfig.VERSION_NAME)
        aapsLogger.debug("BuildVersion: " + BuildConfig.BUILDVERSION)
        aapsLogger.debug("Remote: " + BuildConfig.REMOTE)
        registerLocalBroadcastReceiver()

        //trigger here to see the new version on app start after an update
        versionCheckersUtils.triggerCheckVersion()

        // Register all tabs in app here
        pluginStore.plugins = plugins
        configBuilderPlugin.initialize()
        nsUpload.uploadAppStart()
        Thread { keepAliveManager.setAlarm(this) }.start()
        doMigrations()
    }

    private fun doMigrations() {
        // set values for different builds
        if (!sp.contains(R.string.key_ns_alarms)) sp.putBoolean(R.string.key_ns_alarms, config.NSCLIENT)
        if (!sp.contains(R.string.key_ns_announcements)) sp.putBoolean(R.string.key_ns_announcements, config.NSCLIENT)
        if (!sp.contains(R.string.key_language)) sp.putString(R.string.key_language, "default")
    }

    override fun applicationInjector(): AndroidInjector<out DaggerApplication> {
        return DaggerAppComponent
            .builder()
            .application(this)
            .build()
    }

    private fun registerLocalBroadcastReceiver() {
        var filter = IntentFilter()
        filter.addAction(Intents.ACTION_NEW_TREATMENT)
        filter.addAction(Intents.ACTION_CHANGED_TREATMENT)
        filter.addAction(Intents.ACTION_REMOVED_TREATMENT)
        filter.addAction(Intents.ACTION_NEW_SGV)
        filter.addAction(Intents.ACTION_NEW_PROFILE)
        filter.addAction(Intents.ACTION_NEW_MBG)
        filter.addAction(Intents.ACTION_NEW_CAL)
        LocalBroadcastManager.getInstance(this).registerReceiver(DataReceiver(), filter)
        filter = IntentFilter()
        filter.addAction(Intent.ACTION_TIME_CHANGED)
        filter.addAction(Intent.ACTION_TIMEZONE_CHANGED)
        registerReceiver(TimeDateOrTZChangeReceiver(), filter)
        filter = IntentFilter()
        @Suppress("DEPRECATION")
        filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION)
        filter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION)
        filter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
        registerReceiver(NetworkChangeReceiver(), filter)
        filter = IntentFilter()
        filter.addAction(Intent.ACTION_POWER_CONNECTED)
        filter.addAction(Intent.ACTION_POWER_DISCONNECTED)
        filter.addAction(Intent.ACTION_BATTERY_CHANGED)
        registerReceiver(ChargingStateReceiver(), filter)
        filter = IntentFilter()
        filter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
        filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        registerReceiver(BTReceiver(), filter)
    }

    override fun onTerminate() {
        aapsLogger.debug(LTag.CORE, "onTerminate")
        unregisterActivityLifecycleCallbacks(activityMonitor)
        keepAliveManager.cancelAlarm(this)
        super.onTerminate()
    }

    companion object {

        lateinit var dbHelper: DatabaseHelper
    }
}