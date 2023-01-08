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
import dagger.android.AndroidInjector
import dagger.android.DaggerApplication
import info.nightscout.androidaps.di.DaggerAppComponent
import info.nightscout.androidaps.receivers.BTReceiver
import info.nightscout.androidaps.receivers.ChargingStateReceiver
import info.nightscout.androidaps.receivers.KeepAliveWorker
import info.nightscout.androidaps.receivers.TimeDateOrTZChangeReceiver
import info.nightscout.core.ui.locale.LocaleHelper
import info.nightscout.database.entities.TherapyEvent
import info.nightscout.database.entities.UserEntry
import info.nightscout.database.impl.AppRepository
import info.nightscout.database.impl.transactions.InsertIfNewByTimestampTherapyEventTransaction
import info.nightscout.database.impl.transactions.VersionChangeTransaction
import info.nightscout.implementation.db.CompatDBHelper
import info.nightscout.implementation.lifecycle.ProcessLifecycleListener
import info.nightscout.implementation.plugin.PluginStore
import info.nightscout.implementation.receivers.NetworkChangeReceiver
import info.nightscout.interfaces.Config
import info.nightscout.interfaces.ConfigBuilder
import info.nightscout.interfaces.LocalAlertUtils
import info.nightscout.interfaces.logging.UserEntryLogger
import info.nightscout.interfaces.notifications.Notification
import info.nightscout.interfaces.plugin.PluginBase
import info.nightscout.interfaces.ui.UiInteraction
import info.nightscout.interfaces.versionChecker.VersionCheckerUtils
import info.nightscout.plugins.general.overview.notifications.NotificationStore
import info.nightscout.plugins.general.themes.ThemeSwitcherPlugin
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.rx.logging.LTag
import info.nightscout.shared.interfaces.ResourceHelper
import info.nightscout.shared.sharedPreferences.SP
import info.nightscout.shared.utils.DateUtil
import info.nightscout.ui.activityMonitor.ActivityMonitor
import info.nightscout.ui.widget.Widget
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.exceptions.UndeliverableException
import io.reactivex.rxjava3.kotlin.plusAssign
import io.reactivex.rxjava3.plugins.RxJavaPlugins
import rxdogtag2.RxDogTag
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Provider

class MainApp : DaggerApplication() {

    private val disposable = CompositeDisposable()

    @Inject lateinit var pluginStore: PluginStore
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var activityMonitor: ActivityMonitor
    @Inject lateinit var versionCheckersUtils: VersionCheckerUtils
    @Inject lateinit var sp: SP
    @Inject lateinit var config: Config
    @Inject lateinit var configBuilder: ConfigBuilder
    @Inject lateinit var plugins: List<@JvmSuppressWildcards PluginBase>
    @Inject lateinit var compatDBHelper: CompatDBHelper
    @Inject lateinit var repository: AppRepository
    @Inject lateinit var dateUtil: DateUtil
    @Suppress("unused") @Inject lateinit var staticInjector: info.nightscout.plugins.aps.utils.StaticInjector// TODO avoid , here fake only to initialize
    @Inject lateinit var uel: UserEntryLogger
    @Inject lateinit var uiInteraction: UiInteraction
    @Inject lateinit var notificationStore: NotificationStore
    @Inject lateinit var processLifecycleListener: Provider<ProcessLifecycleListener>
    @Inject lateinit var profileSwitchPlugin: ThemeSwitcherPlugin
    @Inject lateinit var localAlertUtils: LocalAlertUtils
    @Inject lateinit var rh: Provider<ResourceHelper>

    private var handler = Handler(HandlerThread(this::class.simpleName + "Handler").also { it.start() }.looper)
    private lateinit var refreshWidget: Runnable

    override fun onCreate() {
        super.onCreate()
        aapsLogger.debug("onCreate")
        RxDogTag.install()
        setRxErrorHandler()
        LocaleHelper.update(this)

        var gitRemote: String? = BuildConfig.REMOTE
        var commitHash: String? = BuildConfig.HEAD
        if (gitRemote?.contains("NoGitSystemAvailable") == true) {
            gitRemote = null
            commitHash = null
        }
        disposable += compatDBHelper.dbChangeDisposable()
        registerActivityLifecycleCallbacks(activityMonitor)
        profileSwitchPlugin.setThemeMode()
        aapsLogger.debug("Version: " + BuildConfig.VERSION_NAME)
        aapsLogger.debug("BuildVersion: " + BuildConfig.BUILDVERSION)
        aapsLogger.debug("Remote: " + BuildConfig.REMOTE)
        registerLocalBroadcastReceiver()

        // trigger here to see the new version on app start after an update
        versionCheckersUtils.triggerCheckVersion()

        // Register all tabs in app here
        pluginStore.plugins = plugins
        configBuilder.initialize()

        // delayed actions to make rh context updated for translations
        handler.postDelayed(
            {
                // check if identification is set
                if (config.isDev() && sp.getStringOrNull(info.nightscout.core.utils.R.string.key_email_for_crash_report, null).isNullOrBlank())
                    notificationStore.add(Notification(Notification.IDENTIFICATION_NOT_SET, rh.get().gs(R.string.identification_not_set), Notification.INFO))
                // log version
                disposable += repository.runTransaction(VersionChangeTransaction(BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE, gitRemote, commitHash)).subscribe()
                // log app start
                if (sp.getBoolean(info.nightscout.plugins.sync.R.string.key_ns_log_app_started_event, config.APS))
                    disposable += repository
                        .runTransaction(
                            InsertIfNewByTimestampTherapyEventTransaction(
                                timestamp = dateUtil.now(),
                                type = TherapyEvent.Type.NOTE,
                                note = rh.get().gs(info.nightscout.core.ui.R.string.androidaps_start) + " - " + Build.MANUFACTURER + " " + Build.MODEL,
                                glucoseUnit = TherapyEvent.GlucoseUnit.MGDL
                            )
                        )
                        .subscribe()
            }, 10000
        )
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            KeepAliveWorker.KA_0,
            ExistingPeriodicWorkPolicy.REPLACE,
            PeriodicWorkRequest.Builder(KeepAliveWorker::class.java, 15, TimeUnit.MINUTES)
                .setInputData(Data.Builder().putString("schedule", KeepAliveWorker.KA_0).build())
                .setInitialDelay(5, TimeUnit.SECONDS)
                .build()
        )
        localAlertUtils.shortenSnoozeInterval()
        localAlertUtils.preSnoozeAlarms()
        doMigrations()
        uel.log(UserEntry.Action.START_AAPS, UserEntry.Sources.Aaps)
        ProcessLifecycleOwner.get().lifecycle.addObserver(processLifecycleListener.get())

        //  schedule widget update
        refreshWidget = Runnable {
            handler.postDelayed(refreshWidget, 60000)
            Widget.updateWidget(this)
        }
        handler.postDelayed(refreshWidget, 60000)
    }

    private fun setRxErrorHandler() {
        RxJavaPlugins.setErrorHandler { t: Throwable ->
            var e = t
            if (e is UndeliverableException) {
                e = e.cause!!
            }
            if (e is IOException) {
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
            aapsLogger.warn(LTag.CORE, "Undeliverable exception received, not sure what to do", e.localizedMessage)
        }
    }

    @Suppress("SpellCheckingInspection")
    private fun doMigrations() {
        // set values for different builds
        if (!sp.contains(R.string.key_ns_alarms)) sp.putBoolean(R.string.key_ns_alarms, config.NSCLIENT)
        if (!sp.contains(R.string.key_ns_announcements)) sp.putBoolean(R.string.key_ns_announcements, config.NSCLIENT)
        if (!sp.contains(info.nightscout.core.ui.R.string.key_language)) sp.putString(info.nightscout.core.ui.R.string.key_language, "default")
        // 3.1.0
        if (sp.contains("ns_wifionly")) {
            if (sp.getBoolean("ns_wifionly", false)) {
                sp.putBoolean(info.nightscout.plugins.sync.R.string.key_ns_cellular, false)
                sp.putBoolean(info.nightscout.plugins.sync.R.string.key_ns_wifi, true)
            } else {
                sp.putBoolean(info.nightscout.plugins.sync.R.string.key_ns_cellular, true)
                sp.putBoolean(info.nightscout.plugins.sync.R.string.key_ns_wifi, false)
            }
            sp.remove("ns_wifionly")
        }
        if (sp.contains("ns_charginonly")) {
            if (sp.getBoolean("ns_charginonly", false)) {
                sp.putBoolean(info.nightscout.plugins.sync.R.string.key_ns_battery, false)
                sp.putBoolean(info.nightscout.plugins.sync.R.string.key_ns_charging, true)
            } else {
                sp.putBoolean(info.nightscout.plugins.sync.R.string.key_ns_battery, true)
                sp.putBoolean(info.nightscout.plugins.sync.R.string.key_ns_charging, true)
            }
            sp.remove("ns_charginonly")
        }
        if (!sp.contains(info.nightscout.plugins.sync.R.string.key_ns_log_app_started_event))
            sp.putBoolean(info.nightscout.plugins.sync.R.string.key_ns_log_app_started_event, config.APS)
        if (sp.getString(info.nightscout.configuration.R.string.key_maintenance_logs_email, "") == "logs@androidaps.org")
            sp.putString(info.nightscout.configuration.R.string.key_maintenance_logs_email, "logs@aaps.app")
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
        uiInteraction.stopAlarm("onTerminate")
        super.onTerminate()
    }
}
