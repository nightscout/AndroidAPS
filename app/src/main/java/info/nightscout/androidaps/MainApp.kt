package info.nightscout.androidaps

import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import com.uber.rxdogtag.RxDogTag
import dagger.android.AndroidInjector
import dagger.android.DaggerApplication
import info.nightscout.androidaps.database.AppRepository
import info.nightscout.androidaps.database.entities.TherapyEvent
import info.nightscout.androidaps.database.entities.UserEntry
import info.nightscout.androidaps.database.transactions.InsertIfNewByTimestampTherapyEventTransaction
import info.nightscout.androidaps.database.transactions.VersionChangeTransaction
import info.nightscout.androidaps.db.CompatDBHelper
import info.nightscout.androidaps.di.DaggerAppComponent
import info.nightscout.androidaps.di.StaticInjector
import info.nightscout.androidaps.interfaces.Config
import info.nightscout.androidaps.interfaces.ConfigBuilder
import info.nightscout.androidaps.interfaces.PluginBase
import info.nightscout.androidaps.logging.UserEntryLogger
import info.nightscout.androidaps.plugins.configBuilder.PluginStore
import info.nightscout.androidaps.plugins.constraints.versionChecker.VersionCheckerUtils
import info.nightscout.androidaps.plugins.general.overview.notifications.Notification
import info.nightscout.androidaps.plugins.general.overview.notifications.NotificationStore
import info.nightscout.androidaps.plugins.general.themes.ThemeSwitcherPlugin
import info.nightscout.androidaps.receivers.*
import info.nightscout.androidaps.services.AlarmSoundServiceHelper
import info.nightscout.androidaps.utils.ActivityMonitor
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.LocalAlertUtils
import info.nightscout.androidaps.utils.ProcessLifecycleListener
import info.nightscout.androidaps.utils.buildHelper.BuildHelper
import info.nightscout.androidaps.utils.locale.LocaleHelper
import info.nightscout.shared.logging.AAPSLogger
import info.nightscout.shared.logging.LTag
import info.nightscout.shared.sharedPreferences.SP
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.exceptions.UndeliverableException
import io.reactivex.rxjava3.kotlin.plusAssign
import io.reactivex.rxjava3.plugins.RxJavaPlugins
import net.danlew.android.joda.JodaTimeAndroid
import java.io.IOException
import java.net.SocketException
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class MainApp : DaggerApplication() {

    private val disposable = CompositeDisposable()

    @Inject lateinit var pluginStore: PluginStore
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var activityMonitor: ActivityMonitor
    @Inject lateinit var versionCheckersUtils: VersionCheckerUtils
    @Inject lateinit var sp: SP
    @Inject lateinit var config: Config
    @Inject lateinit var buildHelper: BuildHelper
    @Inject lateinit var configBuilder: ConfigBuilder
    @Inject lateinit var plugins: List<@JvmSuppressWildcards PluginBase>
    @Inject lateinit var compatDBHelper: CompatDBHelper
    @Inject lateinit var repository: AppRepository
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var staticInjector: StaticInjector// TODO avoid , here fake only to initialize
    @Inject lateinit var uel: UserEntryLogger
    @Inject lateinit var alarmSoundServiceHelper: AlarmSoundServiceHelper
    @Inject lateinit var notificationStore: NotificationStore
    @Inject lateinit var processLifecycleListener: ProcessLifecycleListener
    @Inject lateinit var profileSwitchPlugin: ThemeSwitcherPlugin
    @Inject lateinit var localAlertUtils: LocalAlertUtils

    private var handler = Handler(HandlerThread(this::class.simpleName + "Handler").also { it.start() }.looper)
    private lateinit var refreshWidget: Runnable

    override fun onCreate() {
        super.onCreate()
        aapsLogger.debug("onCreate")
        RxDogTag.install()
        setRxErrorHandler()
        LocaleHelper.update(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(processLifecycleListener)

        var gitRemote: String? = BuildConfig.REMOTE
        var commitHash: String? = BuildConfig.HEAD
        if (gitRemote?.contains("NoGitSystemAvailable") == true) {
            gitRemote = null
            commitHash = null
        }
        disposable += repository.runTransaction(VersionChangeTransaction(BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE, gitRemote, commitHash)).subscribe()
        if (sp.getBoolean(R.string.key_ns_logappstartedevent, config.APS))
            disposable += repository
                .runTransaction(
                    InsertIfNewByTimestampTherapyEventTransaction(
                        timestamp = dateUtil.now(),
                        type = TherapyEvent.Type.NOTE,
                        note = getString(info.nightscout.androidaps.core.R.string.androidaps_start) + " - " + Build.MANUFACTURER + " " + Build.MODEL,
                        glucoseUnit = TherapyEvent.GlucoseUnit.MGDL
                    )
                )
                .subscribe()
        disposable += compatDBHelper.dbChangeDisposable()
        registerActivityLifecycleCallbacks(activityMonitor)
        JodaTimeAndroid.init(this)
        profileSwitchPlugin.setThemeMode()
        aapsLogger.debug("Version: " + BuildConfig.VERSION_NAME)
        aapsLogger.debug("BuildVersion: " + BuildConfig.BUILDVERSION)
        aapsLogger.debug("Remote: " + BuildConfig.REMOTE)
        registerLocalBroadcastReceiver()

        // trigger here to see the new version on app start after an update
        versionCheckersUtils.triggerCheckVersion()
        // check if identification is set
        if (buildHelper.isDev() && sp.getStringOrNull(R.string.key_email_for_crash_report, null).isNullOrBlank())
            notificationStore.add(Notification(Notification.IDENTIFICATION_NOT_SET, getString(R.string.identification_not_set), Notification.INFO))

        // Register all tabs in app here
        pluginStore.plugins = plugins
        configBuilder.initialize()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "KeepAlive",
            ExistingPeriodicWorkPolicy.REPLACE,
            PeriodicWorkRequest.Builder(KeepAliveWorker::class.java, 15, TimeUnit.MINUTES)
                .setInputData(Data.Builder().putString("schedule", "KeepAlive").build())
                .setInitialDelay(5, TimeUnit.SECONDS)
                .build()
        )
        localAlertUtils.shortenSnoozeInterval()
        localAlertUtils.preSnoozeAlarms()
        doMigrations()
        uel.log(UserEntry.Action.START_AAPS, UserEntry.Sources.Aaps)

        //  schedule widget update
        refreshWidget = Runnable {
            handler.postDelayed(refreshWidget, 60000)
            updateWidget(this)
        }
        handler.postDelayed(refreshWidget, 60000)
    }

    private fun setRxErrorHandler() {
        RxJavaPlugins.setErrorHandler { t: Throwable ->
            var e = t
            if (e is UndeliverableException) {
                e = e.cause!!
            }
            if (e is IOException || e is SocketException) {
                // fine, irrelevant network problem or API that throws on cancellation
                return@setErrorHandler
            }
            if (e is InterruptedException) {
                // fine, some blocking code was interrupted by a dispose call
                return@setErrorHandler
            }
            if (e is NullPointerException || e is IllegalArgumentException) {
                // that's likely a bug in the application
                Thread.currentThread().uncaughtExceptionHandler?.uncaughtException(Thread.currentThread(), e)
                return@setErrorHandler
            }
            if (e is IllegalStateException) {
                // that's a bug in RxJava or in a custom operator
                Thread.currentThread().uncaughtExceptionHandler?.uncaughtException(Thread.currentThread(), e)
                return@setErrorHandler
            }
            aapsLogger.warn(LTag.CORE, "Undeliverable exception received, not sure what to do", e)
        }
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
        alarmSoundServiceHelper.stopService(this)
        super.onTerminate()
    }
}
