package app.aaps

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import androidx.lifecycle.ProcessLifecycleOwner
import app.aaps.core.data.configuration.Constants
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.RM
import app.aaps.core.data.model.TE
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.data.ue.ValueWithUnit
import app.aaps.core.interfaces.alerts.LocalAlertUtils
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.configuration.ConfigBuilder
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.SafeParse
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.interfaces.versionChecker.VersionCheckerUtils
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.IntKey
import app.aaps.core.keys.LongComposedKey
import app.aaps.core.keys.StringKey
import app.aaps.core.keys.UnitDoubleKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.extensions.runOnUiThread
import app.aaps.core.ui.locale.LocaleHelper
import app.aaps.core.utils.JsonHelper
import app.aaps.database.persistence.CompatDBHelper
import app.aaps.di.AppComponent
import app.aaps.di.DaggerAppComponent
import app.aaps.implementation.lifecycle.ProcessLifecycleListener
import app.aaps.implementation.plugin.PluginStore
import app.aaps.implementation.receivers.NetworkChangeReceiver
import app.aaps.plugins.configuration.keys.ConfigurationBooleanComposedKey
import app.aaps.plugins.constraints.objectives.keys.ObjectivesLongComposedKey
import app.aaps.plugins.main.general.themes.ThemeSwitcherPlugin
import app.aaps.plugins.main.profile.keys.ProfileComposedBooleanKey
import app.aaps.plugins.main.profile.keys.ProfileComposedDoubleKey
import app.aaps.plugins.main.profile.keys.ProfileComposedStringKey
import app.aaps.receivers.BTReceiver
import app.aaps.receivers.ChargingStateReceiver
import app.aaps.receivers.KeepAliveWorker
import app.aaps.receivers.TimeDateOrTZChangeReceiver
import app.aaps.ui.activityMonitor.ActivityMonitor
import app.aaps.ui.widget.Widget
import app.aaps.utils.configureLeakCanary
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import com.google.firebase.remoteconfig.remoteConfig
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
import org.json.JSONObject
import rxdogtag2.RxDogTag
import java.io.IOException
import javax.inject.Inject
import javax.inject.Provider
import kotlin.reflect.KMutableProperty
import kotlin.reflect.full.declaredMemberProperties

class MainApp : DaggerApplication() {

    private val disposable = CompositeDisposable()

    @Inject lateinit var pluginStore: PluginStore
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var activityMonitor: ActivityMonitor
    @Inject lateinit var versionCheckersUtils: VersionCheckerUtils
    @Inject lateinit var sp: SP
    @Inject lateinit var preferences: Preferences
    @Inject lateinit var config: Config
    @Inject lateinit var configBuilder: ConfigBuilder
    @Inject lateinit var plugins: List<@JvmSuppressWildcards PluginBase>
    @Inject lateinit var compatDBHelper: CompatDBHelper
    @Inject lateinit var persistenceLayer: PersistenceLayer
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var uiInteraction: UiInteraction
    @Inject lateinit var processLifecycleListener: Provider<ProcessLifecycleListener>
    @Inject lateinit var themeSwitcherPlugin: ThemeSwitcherPlugin
    @Inject lateinit var localAlertUtils: LocalAlertUtils
    @Inject lateinit var rh: Provider<ResourceHelper>
    @Inject lateinit var loop: Loop
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    lateinit var appComponent: AppComponent

    private var handler = Handler(HandlerThread(this::class.simpleName + "Handler").also { it.start() }.looper)
    private lateinit var refreshWidget: Runnable
    private val scope = CoroutineScope(Dispatchers.Default + Job())

    override fun onCreate() {
        super.onCreate()

        // Here should be everything injected
        aapsLogger.debug("onCreate")
        ProcessLifecycleOwner.get().lifecycle.addObserver(processLifecycleListener.get())
        // Configure LeakCanary with Firebase reporting
        // Memory leaks will be uploaded to Firebase Crashlytics via FabricPrivacy.logException
        configureLeakCanary(
            isEnabled = !config.disableLeakCanary(),
            fabricPrivacy = fabricPrivacy
        )

        // Do necessary migrations
        doMigrations()

        // Register and initialize plugins
        pluginStore.plugins = plugins
        configBuilder.initialize()

        // Do initializations in another thread
        scope.launch { doInit() }
    }

    private fun doInit() {
        aapsLogger.debug("doInit")
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
        aapsLogger.debug("Phone: " + Build.MANUFACTURER + " " + Build.MODEL)
        registerLocalBroadcastReceiver()
        setupRemoteConfig()

        // trigger here to see the new version on app start after an update
        handler.postDelayed({ versionCheckersUtils.triggerCheckVersion() }, 30000)

        // delayed actions to make rh context updated for translations
        handler.postDelayed(
            {
                // log version
                disposable += persistenceLayer.insertVersionChangeIfChanged(config.VERSION_NAME, BuildConfig.VERSION_CODE, gitRemote, commitHash).subscribe()
                // log app start
                if (preferences.get(BooleanKey.NsClientLogAppStart))
                    disposable += persistenceLayer.insertPumpTherapyEventIfNewByTimestamp(
                        therapyEvent = TE(
                            timestamp = dateUtil.now(),
                            type = TE.Type.NOTE,
                            note = rh.get().gs(app.aaps.core.ui.R.string.androidaps_start) + " - " + Build.MANUFACTURER + " " + Build.MODEL,
                            glucoseUnit = GlucoseUnit.MGDL
                        ),
                        action = Action.START_AAPS,
                        source = Sources.Aaps, note = "", listValues = listOf()
                    ).subscribe()
            }, 10000
        )
        KeepAliveWorker.schedule(this@MainApp)
        localAlertUtils.shortenSnoozeInterval()
        localAlertUtils.preSnoozeAlarms()

        //  schedule widget update
        refreshWidget = Runnable {
            handler.postDelayed(refreshWidget, 60000)
            Widget.updateWidget(this@MainApp, "ScheduleEveryMin")
        }
        handler.postDelayed(refreshWidget, 60000)
        config.appInitialized = true
        aapsLogger.debug("doInit end")
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

    private fun doMigrations() {
        // set values for different builds
        // 3.3
        if (preferences.get(IntKey.OverviewEatingSoonDuration) == 0) preferences.remove(IntKey.OverviewEatingSoonDuration)
        if (preferences.get(UnitDoubleKey.OverviewEatingSoonTarget) == 0.0) preferences.remove(UnitDoubleKey.OverviewEatingSoonTarget)
        if (preferences.get(IntKey.OverviewActivityDuration) == 0) preferences.remove(IntKey.OverviewActivityDuration)
        if (preferences.get(UnitDoubleKey.OverviewActivityTarget) == 0.0) preferences.remove(UnitDoubleKey.OverviewActivityTarget)
        if (preferences.get(IntKey.OverviewHypoDuration) == 0) preferences.remove(IntKey.OverviewHypoDuration)
        if (preferences.get(UnitDoubleKey.OverviewHypoTarget) == 0.0) preferences.remove(UnitDoubleKey.OverviewHypoTarget)
        if (preferences.get(UnitDoubleKey.OverviewLowMark) == 0.0) preferences.remove(UnitDoubleKey.OverviewLowMark)
        if (preferences.get(UnitDoubleKey.OverviewHighMark) == 0.0) preferences.remove(UnitDoubleKey.OverviewHighMark)
        if (preferences.getIfExists(BooleanKey.GeneralSimpleMode) == null)
            preferences.put(BooleanKey.GeneralSimpleMode, !preferences.get(BooleanKey.GeneralSetupWizardProcessed))
        // Migrate from OpenAPSSMBDynamicISFPlugin
        if (sp.getBoolean("ConfigBuilder_APS_OpenAPSSMBDynamicISFPlugin_Enabled", false)) {
            sp.remove("ConfigBuilder_APS_OpenAPSSMBDynamicISFPlugin_Enabled")
            sp.remove("ConfigBuilder_APS_OpenAPSSMBDynamicISFPlugin_Visible")
            sp.putBoolean("ConfigBuilder_APS_OpenAPSSMB_Enabled", true)
            preferences.put(BooleanKey.ApsUseDynamicSensitivity, true)
        }
        // convert Double to Int
        try {
            val dynIsf = sp.getDouble("DynISFAdjust", 0.0)
            if (dynIsf != 0.0 && dynIsf.toInt() != preferences.get(IntKey.ApsDynIsfAdjustmentFactor))
                preferences.put(IntKey.ApsDynIsfAdjustmentFactor, dynIsf.toInt())
        } catch (_: Exception) { /* ignore */
        }
        // Clear SmsOtpPassword if wrongly replaced
        if (preferences.get(StringKey.SmsOtpPassword).length > 10) preferences.put(StringKey.SmsOtpPassword, "")

        val keys: Map<String, *> = sp.getAll()
        // Migrate ActivityMonitor
        for ((key, value) in keys) {
            if (key.startsWith("Monitor") && key.endsWith("total")) {
                val activity = key.split("_")[1]
                if (value is String)
                    preferences.put(LongComposedKey.ActivityMonitorTotal, activity, value = SafeParse.stringToLong(value))
                else
                    preferences.put(LongComposedKey.ActivityMonitorTotal, activity, value = value as Long)
                sp.remove(key)
            }
            if (key.startsWith("Monitor") && key.endsWith("resumed")) {
                val activity = key.split("_")[1]
                if (value is String)
                    preferences.put(LongComposedKey.ActivityMonitorResumed, activity, value = SafeParse.stringToLong(value))
                else
                    preferences.put(LongComposedKey.ActivityMonitorResumed, activity, value = value as Long)
                sp.remove(key)
            }
            if (key.startsWith("Monitor") && key.endsWith("start")) {
                val activity = key.split("_")[1]
                if (value is String)
                    preferences.put(LongComposedKey.ActivityMonitorStart, activity, value = SafeParse.stringToLong(value))
                else
                    preferences.put(LongComposedKey.ActivityMonitorStart, activity, value = value as Long)
                sp.remove(key)
            }
        }
        // Migrate Objectives
        for ((key, value) in keys) {
            if (key.startsWith("Objectives_") && key.endsWith("_started")) {
                val objective = key.split("_")[1]
                if (value is String)
                    preferences.put(ObjectivesLongComposedKey.Started, objective, value = SafeParse.stringToLong(value))
                else
                    preferences.put(ObjectivesLongComposedKey.Started, objective, value = value as Long)
                sp.remove(key)
            }
            if (key.startsWith("Objectives_") && key.endsWith("_accomplished")) {
                val objective = key.split("_")[1]
                if (value is String)
                    preferences.put(ObjectivesLongComposedKey.Accomplished, objective, value = SafeParse.stringToLong(value))
                else
                    preferences.put(ObjectivesLongComposedKey.Accomplished, objective, value = value as Long)
                sp.remove(key)
            }
        }
        // Migrate ConfigBuilder
        for ((key, value) in keys) {
            if (key.startsWith("ConfigBuilder_") && key.endsWith("_Enabled")) {
                val plugin = key.split("_")[1] + "_" + key.split("_")[2]
                preferences.put(ConfigurationBooleanComposedKey.ConfigBuilderEnabled, plugin, value = value as Boolean)
                sp.remove(key)
            }
            if (key.startsWith("ConfigBuilder_") && key.endsWith("_Visible")) {
                val plugin = key.split("_")[1] + "_" + key.split("_")[2]
                preferences.put(ConfigurationBooleanComposedKey.ConfigBuilderVisible, plugin, value = value as Boolean)
                sp.remove(key)
            }
        }
        // Migrate Profile
        for ((key, value) in keys) {
            if (key.startsWith(Constants.LOCAL_PROFILE + "_") && key.endsWith("_mgdl")) {
                val number = key.split("_")[1]
                preferences.put(ProfileComposedBooleanKey.LocalProfileNumberedMgdl, SafeParse.stringToInt(number), value = value as Boolean)
                sp.remove(key)
            }
            if (key.startsWith(Constants.LOCAL_PROFILE + "_") && key.endsWith("_isf")) {
                val number = key.split("_")[1]
                preferences.put(ProfileComposedStringKey.LocalProfileNumberedIsf, SafeParse.stringToInt(number), value = value as String)
                sp.remove(key)
            }
            if (key.startsWith(Constants.LOCAL_PROFILE + "_") && key.endsWith("_ic")) {
                val number = key.split("_")[1]
                preferences.put(ProfileComposedStringKey.LocalProfileNumberedIc, SafeParse.stringToInt(number), value = value as String)
                sp.remove(key)
            }
            if (key.startsWith(Constants.LOCAL_PROFILE + "_") && key.endsWith("_ic")) {
                val number = key.split("_")[1]
                preferences.put(ProfileComposedStringKey.LocalProfileNumberedIc, SafeParse.stringToInt(number), value = value as String)
                sp.remove(key)
            }
            if (key.startsWith(Constants.LOCAL_PROFILE + "_") && key.endsWith("_basal")) {
                val number = key.split("_")[1]
                preferences.put(ProfileComposedStringKey.LocalProfileNumberedBasal, SafeParse.stringToInt(number), value = value as String)
                sp.remove(key)
            }
            if (key.startsWith(Constants.LOCAL_PROFILE + "_") && key.endsWith("_targetlow")) {
                val number = key.split("_")[1]
                preferences.put(ProfileComposedStringKey.LocalProfileNumberedTargetLow, SafeParse.stringToInt(number), value = value as String)
                sp.remove(key)
            }
            if (key.startsWith(Constants.LOCAL_PROFILE + "_") && key.endsWith("_targethigh")) {
                val number = key.split("_")[1]
                preferences.put(ProfileComposedStringKey.LocalProfileNumberedTargetHigh, SafeParse.stringToInt(number), value = value as String)
                sp.remove(key)
            }
            if (key.startsWith(Constants.LOCAL_PROFILE + "_") && key.endsWith("_name")) {
                val number = key.split("_")[1]
                preferences.put(ProfileComposedStringKey.LocalProfileNumberedName, SafeParse.stringToInt(number), value = value as String)
                sp.remove(key)
            }
            if (key.startsWith(Constants.LOCAL_PROFILE + "_") && key.endsWith("_dia")) {
                val number = key.split("_")[1]
                if (value is String)
                    preferences.put(ProfileComposedDoubleKey.LocalProfileNumberedDia, SafeParse.stringToInt(number), value = SafeParse.stringToDouble(value))
                else if (value is Float)
                    preferences.put(ProfileComposedDoubleKey.LocalProfileNumberedDia, SafeParse.stringToInt(number), value = value.toDouble())
                else
                    preferences.put(ProfileComposedDoubleKey.LocalProfileNumberedDia, SafeParse.stringToInt(number), value = value as Double)
                sp.remove(key)
            }
        }

        // Migrate loop mode
        if (config.APS && sp.contains("aps_mode")) {
            val mode = when (sp.getString("aps_mode", "CLOSED")) {
                "OPEN"   -> RM.Mode.OPEN_LOOP
                "CLOSED" -> RM.Mode.CLOSED_LOOP
                "LGS"    -> RM.Mode.CLOSED_LOOP_LGS
                else     -> RM.Mode.CLOSED_LOOP
            }
            @SuppressLint("CheckResult")
            persistenceLayer.insertOrUpdateRunningMode(
                runningMode = RM(
                    timestamp = dateUtil.now(),
                    mode = mode,
                    autoForced = false,
                    duration = 0
                ),
                action = Action.CLOSED_LOOP_MODE,
                source = Sources.Aaps,
                listValues = listOf(ValueWithUnit.SimpleString("Migration"))
            ).blockingGet()
            sp.remove("aps_mode")
        }
    }

    override fun applicationInjector(): AndroidInjector<out DaggerApplication> {
        appComponent = DaggerAppComponent
            .builder()
            .application(this)
            .build()
        return appComponent
    }

    private val timeDateReceiver = TimeDateOrTZChangeReceiver()
    private val networkReceiver = NetworkChangeReceiver()
    private val chargingReceiver = ChargingStateReceiver()
    private val btReceiver = BTReceiver()

    private fun registerLocalBroadcastReceiver() {
        var filter = IntentFilter()
        filter.addAction(Intent.ACTION_TIME_CHANGED)
        filter.addAction(Intent.ACTION_TIMEZONE_CHANGED)
        registerReceiver(timeDateReceiver, filter)
        filter = IntentFilter()
        @Suppress("DEPRECATION")
        filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION)
        filter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION)
        filter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
        registerReceiver(networkReceiver, filter)
        filter = IntentFilter()
        filter.addAction(Intent.ACTION_POWER_CONNECTED)
        filter.addAction(Intent.ACTION_POWER_DISCONNECTED)
        filter.addAction(Intent.ACTION_BATTERY_CHANGED)
        registerReceiver(chargingReceiver, filter)
        filter = IntentFilter()
        filter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
        filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        registerReceiver(btReceiver, filter)
    }

    private fun unregisterReceivers() {
        super.onTerminate()
        unregisterReceiver(timeDateReceiver)
        unregisterReceiver(networkReceiver)
        unregisterReceiver(chargingReceiver)
        unregisterReceiver(btReceiver)
    }

    private fun setupRemoteConfig() {
        FirebaseApp.initializeApp(this)
        Firebase.remoteConfig.also { firebaseRemoteConfig ->

            firebaseRemoteConfig.setConfigSettingsAsync(
                FirebaseRemoteConfigSettings
                    .Builder()
                    .setMinimumFetchIntervalInSeconds(3600)
                    .build()
            )
            firebaseRemoteConfig
                .fetchAndActivate()
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        aapsLogger.debug("RemoteConfig received successfully")
                        @Suppress("UNCHECKED_CAST")
                        (versionCheckersUtils::class.declaredMemberProperties.find { it.name == "definition" } as KMutableProperty<Any>?)
                            ?.let {
                                val merged = JsonHelper.merge(it.getter.call(versionCheckersUtils) as JSONObject, JSONObject(firebaseRemoteConfig.getString("defs")))
                                it.setter.call(versionCheckersUtils, merged)
                            }
                    } else aapsLogger.error("RemoteConfig fetch failed")
                }
        }
    }

    override fun onTerminate() {
        aapsLogger.debug(LTag.CORE, "onTerminate")
        handler.removeCallbacksAndMessages(null)
        handler.looper.quitSafely()
        unregisterReceivers()
        unregisterActivityLifecycleCallbacks(activityMonitor)
        uiInteraction.stopAlarm("onTerminate")
        super.onTerminate()
    }
}
