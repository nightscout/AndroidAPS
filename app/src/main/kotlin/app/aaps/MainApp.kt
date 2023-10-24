package app.aaps

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
import app.aaps.core.interfaces.alerts.LocalAlertUtils
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.configuration.ConfigBuilder
import app.aaps.core.interfaces.extensions.runOnUiThread
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.notifications.Notification
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.versionChecker.VersionCheckerUtils
import app.aaps.core.ui.locale.LocaleHelper
import app.aaps.database.entities.TherapyEvent
import app.aaps.database.entities.UserEntry
import app.aaps.database.impl.AppRepository
import app.aaps.database.impl.transactions.InsertIfNewByTimestampTherapyEventTransaction
import app.aaps.database.impl.transactions.VersionChangeTransaction
import app.aaps.di.DaggerAppComponent
import app.aaps.implementation.db.CompatDBHelper
import app.aaps.implementation.lifecycle.ProcessLifecycleListener
import app.aaps.implementation.plugin.PluginStore
import app.aaps.implementation.receivers.NetworkChangeReceiver
import app.aaps.plugins.aps.utils.StaticInjector
import app.aaps.plugins.main.general.overview.notifications.NotificationStore
import app.aaps.plugins.main.general.themes.ThemeSwitcherPlugin
import app.aaps.receivers.BTReceiver
import app.aaps.receivers.ChargingStateReceiver
import app.aaps.receivers.KeepAliveWorker
import app.aaps.receivers.TimeDateOrTZChangeReceiver
import app.aaps.ui.activityMonitor.ActivityMonitor
import app.aaps.ui.widget.Widget
import dagger.android.AndroidInjector
import dagger.android.DaggerApplication
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.exceptions.UndeliverableException
import io.reactivex.rxjava3.kotlin.plusAssign
import io.reactivex.rxjava3.plugins.RxJavaPlugins
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
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
    @Suppress("unused") @Inject lateinit var staticInjector: StaticInjector// better avoid, here fake only to initialize
    @Inject lateinit var uel: UserEntryLogger
    @Inject lateinit var uiInteraction: UiInteraction
    @Inject lateinit var notificationStore: NotificationStore
    @Inject lateinit var processLifecycleListener: Provider<ProcessLifecycleListener>
    @Inject lateinit var themeSwitcherPlugin: ThemeSwitcherPlugin
    @Inject lateinit var localAlertUtils: LocalAlertUtils
    @Inject lateinit var rh: Provider<ResourceHelper>

    private var handler = Handler(HandlerThread(this::class.simpleName + "Handler").also { it.start() }.looper)
    private lateinit var refreshWidget: Runnable
    private val scope = CoroutineScope(Dispatchers.Default + Job())

    override fun onCreate() {
        super.onCreate()
        aapsLogger.debug("onCreate")
        ProcessLifecycleOwner.get().lifecycle.addObserver(processLifecycleListener.get())
        scope.launch {
            RxDogTag.install()
            setRxErrorHandler()
            LocaleHelper.update(this@MainApp)

            var gitRemote: String? = config.REMOTE
            var commitHash: String? = BuildConfig.HEAD
            if (gitRemote?.contains("NoGitSystemAvailable") == true) {
                gitRemote = null
                commitHash = null
            }
            disposable += compatDBHelper.dbChangeDisposable()
            registerActivityLifecycleCallbacks(activityMonitor)
            runOnUiThread { themeSwitcherPlugin.setThemeMode() }
            aapsLogger.debug("Version: " + config.VERSION_NAME)
            aapsLogger.debug("BuildVersion: " + config.BUILD_VERSION)
            aapsLogger.debug("Remote: " + config.REMOTE)
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
                    if (config.isDev() && sp.getStringOrNull(app.aaps.core.utils.R.string.key_email_for_crash_report, null).isNullOrBlank())
                        notificationStore.add(Notification(Notification.IDENTIFICATION_NOT_SET, rh.get().gs(R.string.identification_not_set), Notification.INFO))
                    // log version
                    disposable += repository.runTransaction(VersionChangeTransaction(config.VERSION_NAME, BuildConfig.VERSION_CODE, gitRemote, commitHash)).subscribe()
                    // log app start
                    if (sp.getBoolean(app.aaps.plugins.sync.R.string.key_ns_log_app_started_event, config.APS))
                        disposable += repository
                            .runTransaction(
                                InsertIfNewByTimestampTherapyEventTransaction(
                                    timestamp = dateUtil.now(),
                                    type = TherapyEvent.Type.NOTE,
                                    note = rh.get().gs(app.aaps.core.ui.R.string.androidaps_start) + " - " + Build.MANUFACTURER + " " + Build.MODEL,
                                    glucoseUnit = TherapyEvent.GlucoseUnit.MGDL
                                )
                            )
                            .subscribe()
                }, 10000
            )
            WorkManager.getInstance(this@MainApp).enqueueUniquePeriodicWork(
                KeepAliveWorker.KA_0,
                ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequest.Builder(KeepAliveWorker::class.java, 15, TimeUnit.MINUTES)
                    .setInputData(Data.Builder().putString("schedule", KeepAliveWorker.KA_0).build())
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
                Widget.updateWidget(this@MainApp, "ScheduleEveryMin")
            }
            handler.postDelayed(refreshWidget, 60000)
            config.appInitialized = true
        }
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
        if (!sp.contains(app.aaps.core.ui.R.string.key_language)) sp.putString(app.aaps.core.ui.R.string.key_language, "default")
        // 3.1.0
        if (sp.contains("ns_wifionly")) {
            if (sp.getBoolean("ns_wifionly", false)) {
                sp.putBoolean(app.aaps.plugins.sync.R.string.key_ns_cellular, false)
                sp.putBoolean(app.aaps.plugins.sync.R.string.key_ns_wifi, true)
            } else {
                sp.putBoolean(app.aaps.plugins.sync.R.string.key_ns_cellular, true)
                sp.putBoolean(app.aaps.plugins.sync.R.string.key_ns_wifi, false)
            }
            sp.remove("ns_wifionly")
        }
        if (sp.contains("ns_charginonly")) {
            if (sp.getBoolean("ns_charginonly", false)) {
                sp.putBoolean(app.aaps.plugins.sync.R.string.key_ns_battery, false)
                sp.putBoolean(app.aaps.plugins.sync.R.string.key_ns_charging, true)
            } else {
                sp.putBoolean(app.aaps.plugins.sync.R.string.key_ns_battery, true)
                sp.putBoolean(app.aaps.plugins.sync.R.string.key_ns_charging, true)
            }
            sp.remove("ns_charginonly")
        }
        if (!sp.contains(app.aaps.plugins.sync.R.string.key_ns_log_app_started_event))
            sp.putBoolean(app.aaps.plugins.sync.R.string.key_ns_log_app_started_event, config.APS)
        if (sp.getString(app.aaps.plugins.configuration.R.string.key_maintenance_logs_email, "") == "logs@androidaps.org")
            sp.putString(app.aaps.plugins.configuration.R.string.key_maintenance_logs_email, "logs@aaps.app")
        // fix values for theme switching
        sp.putString(app.aaps.plugins.main.R.string.value_dark_theme, "dark")
        sp.putString(app.aaps.plugins.main.R.string.value_light_theme, "light")
        sp.putString(app.aaps.plugins.main.R.string.value_system_theme, "system")

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
