package app.aaps

import android.app.Application
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
import app.aaps.core.data.model.ICfg
import app.aaps.core.data.model.RM
import app.aaps.core.data.model.TE
import app.aaps.core.data.model.TT
import app.aaps.core.data.model.TTPreset
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.data.ue.ValueWithUnit
import app.aaps.core.interfaces.alerts.LocalAlertUtils
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.configuration.ConfigBuilder
import app.aaps.core.interfaces.configuration.ExternalOptions
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.di.ApplicationScope
import app.aaps.core.interfaces.insulin.InsulinManager
import app.aaps.core.interfaces.insulin.InsulinType
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.maintenance.FileListProvider
import app.aaps.core.interfaces.notifications.NotificationAction
import app.aaps.core.interfaces.notifications.NotificationId
import app.aaps.core.interfaces.notifications.NotificationLevel
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.profile.LocalProfileManager
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.protection.ExportPasswordDataStore
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventAppInitialized
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.HardLimits
import app.aaps.core.interfaces.utils.SafeParse
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.interfaces.versionChecker.VersionCheckerUtils
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.BooleanNonKey
import app.aaps.core.keys.IntKey
import app.aaps.core.keys.LongComposedKey
import app.aaps.core.keys.ProfileComposedBooleanKey
import app.aaps.core.keys.ProfileComposedStringKey
import app.aaps.core.keys.StringKey
import app.aaps.core.keys.StringNonKey
import app.aaps.core.keys.UnitDoubleKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.objects.crypto.CryptoUtil
import app.aaps.core.objects.profile.ProfileSealed
import app.aaps.core.ui.locale.LocaleHelper
import app.aaps.core.utils.JsonHelper
import app.aaps.database.AppRepository
import app.aaps.implementation.lifecycle.ProcessLifecycleListener
import app.aaps.implementation.plugin.PluginStore
import app.aaps.implementation.receivers.NetworkChangeReceiver
import app.aaps.plugins.configuration.keys.ConfigurationBooleanComposedKey
import app.aaps.plugins.constraints.objectives.keys.ObjectivesLongComposedKey
import app.aaps.plugins.constraints.signatureVerifier.SignatureVerifierPlugin
import app.aaps.receivers.BTReceiver
import app.aaps.receivers.ChargingStateReceiver
import app.aaps.receivers.KeepAliveWorker
import app.aaps.receivers.TimeDateOrTZChangeReceiver
import app.aaps.ui.activityMonitor.ActivityMonitor
import app.aaps.ui.compose.tempTarget.toJson
import app.aaps.ui.widget.Widget
import app.aaps.utils.configureLeakCanary
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import com.google.firebase.remoteconfig.remoteConfig
import dagger.android.AndroidInjector
import dagger.android.DispatchingAndroidInjector
import dagger.android.HasAndroidInjector
import dagger.hilt.android.HiltAndroidApp
import io.reactivex.rxjava3.exceptions.UndeliverableException
import io.reactivex.rxjava3.plugins.RxJavaPlugins
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import rxdogtag2.RxDogTag
import java.io.IOException
import java.util.Locale
import javax.inject.Inject
import javax.inject.Provider
import kotlin.reflect.KMutableProperty
import kotlin.reflect.full.declaredMemberProperties

@HiltAndroidApp
class MainApp : Application(), HasAndroidInjector {

    @Inject lateinit var androidInjector: DispatchingAndroidInjector<Any>
    override fun androidInjector(): AndroidInjector<Any> = androidInjector

    @Inject lateinit var pluginStore: PluginStore
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var activityMonitor: ActivityMonitor
    @Inject lateinit var versionCheckersUtils: VersionCheckerUtils
    @Inject lateinit var sp: SP
    @Inject lateinit var preferences: Preferences
    @Inject lateinit var config: Config
    @Inject lateinit var configBuilder: ConfigBuilder
    @Inject lateinit var plugins: List<@JvmSuppressWildcards PluginBase>
    @Inject lateinit var persistenceLayer: PersistenceLayer
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var uiInteraction: UiInteraction
    @Inject lateinit var processLifecycleListener: Provider<ProcessLifecycleListener>
    @Inject lateinit var localAlertUtils: LocalAlertUtils
    @Inject lateinit var notificationManager: NotificationManager
    @Inject lateinit var rh: Provider<ResourceHelper>
    @Inject lateinit var loop: Loop
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var profileUtil: ProfileUtil
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var repository: AppRepository
    @Inject lateinit var hardLimits: HardLimits
    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var localProfileManager: LocalProfileManager
    @Inject lateinit var localInsulinManager: InsulinManager
    @Inject lateinit var constraintChecker: ConstraintsChecker
    @Inject lateinit var signatureVerifierPlugin: SignatureVerifierPlugin
    @Inject lateinit var fileListProvider: FileListProvider
    @Inject lateinit var cryptoUtil: CryptoUtil
    @Inject lateinit var exportPasswordDataStore: ExportPasswordDataStore
    @Inject @ApplicationScope lateinit var appScope: CoroutineScope

    private lateinit var insulinLabel: String
    private var insulinPeakTime: Long = 0L
    private var profileNameToDia: Map<String, Double> = emptyMap()

    private var handler = Handler(HandlerThread(this::class.simpleName + "Handler").also { it.start() }.looper)
    private lateinit var refreshWidget: Runnable
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    companion object {

        /** Throttle splash progress updates to avoid excessive StateFlow emissions */
        private const val PROGRESS_UPDATE_INTERVAL = 10
    }

    override fun onCreate() {
        super.onCreate()

        // Here should be everything injected
        aapsLogger.debug("onCreate")
        ProcessLifecycleOwner.get().lifecycle.addObserver(processLifecycleListener.get())
        // Configure LeakCanary with Firebase reporting
        // Memory leaks will be uploaded to Firebase Crashlytics via FabricPrivacy.logException
        configureLeakCanary(
            isEnabled = !config.isEnabled(ExternalOptions.DISABLE_LEAK_CANARY),
            fabricPrivacy = fabricPrivacy
        )

        // All initialization in background thread to avoid blocking main thread
        // (ComposeMainActivity shows splash screen until initProgress.done)
        scope.launch {
            try {
                config.updateInitProgress(getString(R.string.migrating_preferences))
                doMigrations()

                // Register and initialize plugins
                config.updateInitProgress(getString(R.string.initializing_plugins))
                pluginStore.plugins = plugins
                configBuilder.initialize()

                // Data migrations (DB I/O)
                dataMigrations()

                config.updateInitProgress(getString(R.string.initializing))
                doInit()
            } catch (e: Exception) {
                aapsLogger.error(LTag.CORE, "Fatal initialization error", e)
                config.initFailed(e.message ?: "Unknown error")
            }
        }
    }

    private suspend fun doInit() {
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
        registerActivityLifecycleCallbacks(activityMonitor)
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
                appScope.launch { persistenceLayer.insertVersionChangeIfChanged(config.VERSION_NAME, BuildConfig.VERSION_CODE, gitRemote, commitHash) }
                // log app start
                if (preferences.get(BooleanKey.NsClientLogAppStart))
                    appScope.launch {
                        persistenceLayer.insertPumpTherapyEventIfNewByTimestamp(
                            therapyEvent = TE(
                                timestamp = dateUtil.now(),
                                type = TE.Type.NOTE,
                                note = rh.get().gs(app.aaps.core.ui.R.string.androidaps_start) + " - " + Build.MANUFACTURER + " " + Build.MODEL,
                                glucoseUnit = GlucoseUnit.MGDL
                            ),
                            action = Action.START_AAPS,
                            source = Sources.Aaps, note = "", listValues = listOf()
                        )
                    }
            }, 10000
        )
        postSetupNotifications()
        KeepAliveWorker.schedule(this@MainApp)
        localAlertUtils.shortenSnoozeInterval()
        localAlertUtils.preSnoozeAlarms()

        //  schedule widget update
        refreshWidget = Runnable {
            handler.postDelayed(refreshWidget, 60000)
            Widget.updateWidget(this@MainApp, "ScheduleEveryMin")
        }
        handler.postDelayed(refreshWidget, 5000)
        setUserStats()
        passwordResetCheck()
        exportPasswordResetCheck()
        config.initCompleted()
        rxBus.send(EventAppInitialized())
        aapsLogger.debug("doInit end")
    }

    private fun setUserStats() {
        if (!fabricPrivacy.fabricEnabled()) return
        val closedLoopEnabled = if (constraintChecker.isClosedLoopAllowed().value()) "CLOSED_LOOP_ENABLED" else "CLOSED_LOOP_DISABLED"
        val remote = config.REMOTE.lowercase(Locale.getDefault())
            .replace("https://", "")
            .replace("http://", "")
            .replace(".git", "")
            .replace(".com/", ":")
            .replace(".org/", ":")
            .replace(".net/", ":")
        fabricPrivacy.setUserProperty("Mode", config.APPLICATION_ID + "-" + closedLoopEnabled)
        fabricPrivacy.setUserProperty("Language", preferences.getIfExists(StringKey.GeneralLanguage) ?: Locale.getDefault().language)
        fabricPrivacy.setUserProperty("Version", config.VERSION_NAME)
        fabricPrivacy.setUserProperty("HEAD", BuildConfig.HEAD)
        fabricPrivacy.setUserProperty("Remote", remote)
        val hashes: List<String> = signatureVerifierPlugin.shortHashes()
        if (hashes.isNotEmpty()) fabricPrivacy.setUserProperty("Hash", hashes[0])
        activePlugin.activePump.let { fabricPrivacy.setUserProperty("Pump", it::class.java.simpleName) }
        if (!config.AAPSCLIENT && !config.PUMPCONTROL)
            activePlugin.activeAPS?.let { fabricPrivacy.setUserProperty("Aps", it::class.java.simpleName) }
        activePlugin.activeBgSource.let { fabricPrivacy.setUserProperty("BgSource", it::class.java.simpleName) }
        activePlugin.activeSensitivity.let { fabricPrivacy.setUserProperty("Sensitivity", it::class.java.simpleName) }
        FirebaseCrashlytics.getInstance().setCustomKey("HEAD", BuildConfig.HEAD)
        FirebaseCrashlytics.getInstance().setCustomKey("Version", config.VERSION_NAME)
        FirebaseCrashlytics.getInstance().setCustomKey("BuildType", config.BUILD_TYPE)
        FirebaseCrashlytics.getInstance().setCustomKey("BuildFlavor", config.FLAVOR)
        FirebaseCrashlytics.getInstance().setCustomKey("Remote", remote)
        FirebaseCrashlytics.getInstance().setCustomKey("Committed", config.COMMITTED)
        if (hashes.isNotEmpty()) FirebaseCrashlytics.getInstance().setCustomKey("Hash", hashes[0])
        FirebaseCrashlytics.getInstance().setCustomKey("Email", preferences.get(StringKey.MaintenanceIdentification))
    }

    private suspend fun passwordResetCheck() {
        val fh = fileListProvider.ensureExtraDirExists()?.findFile("PasswordReset")
        if (fh?.exists() == true) {
            config.updateInitProgress(getString(app.aaps.core.ui.R.string.waiting_for_pump))
            val serialNumber = withTimeoutOrNull(30_000L) {
                while (activePlugin.activePump.serialNumber().isEmpty()) {
                    delay(100)
                }
                activePlugin.activePump.serialNumber()
            }
            if (serialNumber != null) {
                preferences.put(StringKey.ProtectionMasterPassword, cryptoUtil.hashPassword(serialNumber))
                fh.delete()
                exportPasswordDataStore.clearPasswordDataStore(this@MainApp)
                config.showInitSnackbar(getString(app.aaps.core.ui.R.string.password_set))
            } else {
                aapsLogger.warn(LTag.CORE, "Password reset timed out waiting for pump serial number")
            }
        }
    }

    private fun exportPasswordResetCheck() {
        val fh = fileListProvider.ensureExtraDirExists()?.findFile("ExportPasswordReset")
        if (fh?.exists() == true) {
            exportPasswordDataStore.clearPasswordDataStore(this@MainApp)
            fh.delete()
            config.showInitSnackbar(getString(app.aaps.core.ui.R.string.datastore_password_cleared))
        }
    }

    private fun postSetupNotifications() {
        // Identification not set (dev builds only)
        if (config.isDev() && preferences.get(StringKey.MaintenanceIdentification).isBlank())
            notificationManager.post(
                id = NotificationId.IDENTIFICATION_NOT_SET,
                R.string.identification_not_set,
                level = NotificationLevel.INFO,
                actions = listOf(NotificationAction(R.string.set) {}),
                validityCheck = { config.isDev() && preferences.get(StringKey.MaintenanceIdentification).isBlank() }
            )
        // Master password not set
        if (preferences.get(StringKey.ProtectionMasterPassword) == "")
            notificationManager.post(
                id = NotificationId.MASTER_PASSWORD_NOT_SET,
                app.aaps.core.ui.R.string.master_password_not_set,
                level = NotificationLevel.NORMAL,
                actions = listOf(NotificationAction(R.string.set) {}),
                validityCheck = { preferences.get(StringKey.ProtectionMasterPassword) == "" }
            )
        // AAPS directory not selected
        if (preferences.getIfExists(StringKey.AapsDirectoryUri).isNullOrEmpty())
            notificationManager.post(
                id = NotificationId.AAPS_DIR_NOT_SELECTED,
                app.aaps.core.ui.R.string.aaps_directory_not_selected,
                level = NotificationLevel.LOW,
                actions = listOf(NotificationAction(R.string.select) {}),
                validityCheck = { preferences.getIfExists(StringKey.AapsDirectoryUri).isNullOrEmpty() }
            )
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

    private suspend fun doMigrations() {
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
            preferences.put(BooleanKey.GeneralSimpleMode, !preferences.get(BooleanNonKey.GeneralSetupWizardProcessed))
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
        val indexToName = mutableMapOf<Int, String>()
        val indexToDia = mutableMapOf<Int, Double>()
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
                indexToName[SafeParse.stringToInt(number)] = value as String
                preferences.put(ProfileComposedStringKey.LocalProfileNumberedName, SafeParse.stringToInt(number), value = value)
                sp.remove(key)
            }
            if (key.startsWith(Constants.LOCAL_PROFILE + "_name_")) {
                val number = key.split("_")[2]
                indexToName[SafeParse.stringToInt(number)] = value as String
            }
            if (key.startsWith(Constants.LOCAL_PROFILE + "_") && key.endsWith("_dia")) {
                val number = SafeParse.stringToInt(key.split("_")[1])
                indexToDia[number] = SafeParse.stringToDouble(value.toString())
                sp.remove(key)
            }
            if (key.startsWith(Constants.LOCAL_PROFILE + "_dia_")) {
                val number = SafeParse.stringToInt(key.split("_")[2])
                indexToDia[number] = SafeParse.stringToDouble(value.toString())
                sp.remove(key)
            }
        }
        profileNameToDia = indexToDia.mapNotNull { (index, dia) ->
            indexToName[index]?.let { name -> name to dia }
        }.toMap()

        // Migrate Tidepool from username/password to OAuth2
        if (sp.contains("tidepool_username") || sp.contains("tidepool_password")) {
            sp.remove("tidepool_username")
            sp.remove("tidepool_password")
            sp.remove("tidepool_test_login")
            // Clear OAuth2 state to force re-authentication
            sp.remove("tidepool_auth_state")
            sp.remove("tidepool_service_configuration")
            sp.remove("tidepool_subscription_id")
        }

        // Migrate loop mode
        if (config.APS && sp.contains("aps_mode")) {
            val mode = when (sp.getString("aps_mode", "CLOSED")) {
                "OPEN"   -> RM.Mode.OPEN_LOOP
                "CLOSED" -> RM.Mode.CLOSED_LOOP
                "LGS"    -> RM.Mode.CLOSED_LOOP_LGS
                else     -> RM.Mode.CLOSED_LOOP
            }
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
            )
            sp.remove("aps_mode")
        }

        // Migrate temp target presets from old preference keys to JSON array
        migrateTempTargetPresets()

        // Get Insulin plugin information for database migration
        insulinLabel = rh.get().gs(
            when {
                sp.getBoolean("ConfigBuilder_Enabled_INSULIN_InsulinOrefRapidActingPlugin", false)      -> InsulinType.OREF_RAPID_ACTING.label
                sp.getBoolean("ConfigBuilder_Enabled_INSULIN_InsulinOrefUltraRapidActingPlugin", false) -> InsulinType.OREF_ULTRA_RAPID_ACTING.label
                sp.getBoolean("ConfigBuilder_Enabled_INSULIN_InsulinOrefFreePeakPlugin", false)         -> InsulinType.OREF_FREE_PEAK.label
                sp.getBoolean("ConfigBuilder_Enabled_INSULIN_InsulinLyumjevPlugin", false)              -> InsulinType.OREF_LYUMJEV.label
                else                                                                                    -> InsulinType.OREF_RAPID_ACTING.label
            }
        )
        insulinPeakTime = when {
            sp.getBoolean("ConfigBuilder_Enabled_INSULIN_InsulinOrefRapidActingPlugin", false)      -> InsulinType.OREF_RAPID_ACTING.insulinPeakTime
            sp.getBoolean("ConfigBuilder_Enabled_INSULIN_InsulinOrefUltraRapidActingPlugin", false) -> InsulinType.OREF_ULTRA_RAPID_ACTING.insulinPeakTime
            sp.getBoolean("ConfigBuilder_Enabled_INSULIN_InsulinOrefFreePeakPlugin", false)         -> (sp.getInt("insulin_oref_peak", 75) * 60 * 1000).toLong()
            sp.getBoolean("ConfigBuilder_Enabled_INSULIN_InsulinLyumjevPlugin", false)              -> InsulinType.OREF_LYUMJEV.insulinPeakTime
            else                                                                                    -> InsulinType.OREF_RAPID_ACTING.insulinPeakTime
        }
        // Migrate Insulin Plugins
        if (sp.getBoolean("ConfigBuilder_INSULIN_InsulinOrefRapidActingPlugin_Enabled", false) || sp.getBoolean("ConfigBuilder_Enabled_INSULIN_InsulinOrefRapidActingPlugin", false) ||
            sp.getBoolean("ConfigBuilder_INSULIN_InsulinOrefUltraRapidActingPlugin_Enabled", false) || sp.getBoolean("ConfigBuilder_Enabled_INSULIN_InsulinOrefUltraRapidActingPlugin", false) ||
            sp.getBoolean("ConfigBuilder_INSULIN_InsulinOrefFreePeakPlugin_Enabled", false) || sp.getBoolean("ConfigBuilder_Enabled_INSULIN_InsulinOrefFreePeakPlugin", false) ||
            sp.getBoolean("ConfigBuilder_INSULIN_InsulinLyumjevPlugin_Enabled", false) || sp.getBoolean("ConfigBuilder_Enabled_INSULIN_InsulinLyumjevPlugin", false)
        ) {
            sp.remove("ConfigBuilder_INSULIN_InsulinOrefRapidActingPlugin_Enabled")
            sp.remove("ConfigBuilder_INSULIN_InsulinOrefRapidActingPlugin_Visible")
            sp.remove("ConfigBuilder_Enabled_INSULIN_InsulinOrefRapidActingPlugin")
            sp.remove("ConfigBuilder_INSULIN_InsulinOrefUltraRapidActingPlugin_Enabled")
            sp.remove("ConfigBuilder_INSULIN_InsulinOrefUltraRapidActingPlugin_Visible")
            sp.remove("ConfigBuilder_Enabled_INSULIN_InsulinOrefUltraRapidActingPlugin")
            sp.remove("ConfigBuilder_INSULIN_InsulinOrefFreePeakPlugin_Enabled")
            sp.remove("ConfigBuilder_INSULIN_InsulinOrefFreePeakPlugin_Visible")
            sp.remove("ConfigBuilder_Enabled_INSULIN_InsulinOrefFreePeakPlugin")
            sp.remove("ConfigBuilder_INSULIN_InsulinLyumjevPlugin_Enabled")
            sp.remove("ConfigBuilder_INSULIN_InsulinLyumjevPlugin_Visible")
            sp.remove("ConfigBuilder_Enabled_INSULIN_InsulinLyumjevPlugin")
            sp.remove("insulin_oref_peak")
        }
    }

    /**
     * Migrates temp target presets from old individual preference keys to unified JSON storage.
     * Creates 3 default presets (Eating Soon, Activity, Hypo) for new installations.
     * For existing installations, migrates values from old keys.
     * Old keys remain functional for legacy TempTargetDialog.
     */
    private fun migrateTempTargetPresets() {
        // Check if migration already completed
        val existing = preferences.get(StringNonKey.TempTargetPresets)
        if (existing != "[]" && existing.isNotEmpty()) {
            return // Already migrated
        }

        // Check if old preferences exist (existing installation vs new installation)
        val hasOldPreferences = preferences.getIfExists(UnitDoubleKey.OverviewEatingSoonTarget) != null

        val units = profileFunction.getUnits()

        // Create 3 default presets - values always stored in mg/dL
        val presets = listOf(
            TTPreset(
                id = "eatingsoon",
                reason = TT.Reason.EATING_SOON,
                targetValue = if (hasOldPreferences) {
                    profileUtil.convertToMgdl(preferences.get(UnitDoubleKey.OverviewEatingSoonTarget), units)
                } else {
                    Constants.DEFAULT_TT_EATING_SOON_TARGET
                },
                duration = if (hasOldPreferences) {
                    preferences.get(IntKey.OverviewEatingSoonDuration) * 60L * 1000L
                } else {
                    Constants.DEFAULT_TT_EATING_SOON_DURATION * 60L * 1000L
                },
                isDeletable = false
            ),
            TTPreset(
                id = "activity",
                reason = TT.Reason.ACTIVITY,
                targetValue = if (hasOldPreferences) {
                    profileUtil.convertToMgdl(preferences.get(UnitDoubleKey.OverviewActivityTarget), units)
                } else {
                    Constants.DEFAULT_TT_ACTIVITY_TARGET
                },
                duration = if (hasOldPreferences) {
                    preferences.get(IntKey.OverviewActivityDuration) * 60L * 1000L
                } else {
                    Constants.DEFAULT_TT_ACTIVITY_DURATION * 60L * 1000L
                },
                isDeletable = false
            ),
            TTPreset(
                id = "hypo",
                reason = TT.Reason.HYPOGLYCEMIA,
                targetValue = if (hasOldPreferences) {
                    profileUtil.convertToMgdl(preferences.get(UnitDoubleKey.OverviewHypoTarget), units)
                } else {
                    Constants.DEFAULT_TT_HYPO_TARGET
                },
                duration = if (hasOldPreferences) {
                    preferences.get(IntKey.OverviewHypoDuration) * 60L * 1000L
                } else {
                    Constants.DEFAULT_TT_HYPO_DURATION * 60L * 1000L
                },
                isDeletable = false
            )
        )

        // Save to new JSON format
        preferences.put(StringNonKey.TempTargetPresets, presets.toJson())

        aapsLogger.debug(LTag.CORE, "Migrated temp target presets to JSON storage")

        // NOTE: Old preferences are NOT removed to keep legacy TempTargetDialog functional
        // They are marked as @Deprecated in preference key definitions
        // Removal will be done when legacy UI is completely removed in the future
    }

    private suspend fun dataMigrations() {
        // Migrate to database 33 (ICfg)
        // Grab default value first
        var runningICfg = if (profileNameToDia.size == 0) // no migration, get running iCfg from running Profile
                profileFunction.getProfile()?.iCfg ?: localInsulinManager.iCfg
            else {  // migration, create running iCfg from previous runningProfile dia and slected InsulinPlugin for peak
                val dia = (profileFunction.getProfile() as ProfileSealed.EPS?)?.profileName?.let { profileName ->
                    profileNameToDia[profileName]
                }
                val insulinEndTime = ((dia ?: hardLimits.maxDia()) * 3600 * 1000).toLong()
                ICfg("", insulinEndTime, insulinPeakTime, 1.0).also {
                    it.insulinNickname = insulinLabel
                    it.insulinLabel = "$insulinLabel ${localInsulinManager.buildSuffix(it.peak, it.dia, it.concentration)}"
                }
            }

        if (!localInsulinManager.insulinAlreadyExists(runningICfg)) { // Add running insulin in InsulinManager if missing
            localInsulinManager.addNewInsulin(runningICfg, keepName = true)
        }

        var totalMigrated = 0

        config.updateInitProgress(rh.get().gs(R.string.migrating_profile_switches))
        val profileSwitches = persistenceLayer.getProfileSwitches()
        val unmigrated = profileSwitches.filter { it.iCfg.insulinEndTime == -1L }
        if (unmigrated.isNotEmpty()) {
            val total = unmigrated.size
            val step = rh.get().gs(R.string.migrating_profile_switches)
            config.updateInitProgress(step, 0, total)
            unmigrated.forEachIndexed { index, ps ->
                ps.iCfg.insulinLabel = runningICfg.insulinLabel
                ps.iCfg.insulinEndTime = runningICfg.insulinEndTime
                ps.iCfg.insulinPeakTime = runningICfg.insulinPeakTime
                ps.iCfg.concentration = runningICfg.concentration
                persistenceLayer.updateProfileSwitchNoLogging(ps)
                if ((index + 1) % PROGRESS_UPDATE_INTERVAL == 0 || index + 1 == total)
                    config.updateInitProgress(step, index + 1, total)
            }
            totalMigrated += unmigrated.size
        }

        config.updateInitProgress(rh.get().gs(R.string.migrating_effective_profile_switches))
        val effectiveProfileSwitches = persistenceLayer.getEffectiveProfileSwitches()
        val unmigratedEps = effectiveProfileSwitches.filter { it.iCfg.insulinEndTime == -1L }
        if (unmigratedEps.isNotEmpty()) {
            val total = unmigratedEps.size
            val step = rh.get().gs(R.string.migrating_effective_profile_switches)
            config.updateInitProgress(step, 0, total)
            unmigratedEps.forEachIndexed { index, eps ->
                eps.iCfg.insulinLabel = runningICfg.insulinLabel
                eps.iCfg.insulinEndTime = runningICfg.insulinEndTime
                eps.iCfg.insulinPeakTime = runningICfg.insulinPeakTime
                eps.iCfg.concentration = runningICfg.concentration
                persistenceLayer.updateEffectiveProfileSwitchNoLogging(eps)
                if ((index + 1) % PROGRESS_UPDATE_INTERVAL == 0 || index + 1 == total)
                    config.updateInitProgress(step, index + 1, total)
            }
            totalMigrated += unmigratedEps.size
        }

        config.updateInitProgress(rh.get().gs(R.string.migrating_boluses))
        val boluses = persistenceLayer.getBoluses()
        val unmigratedBoluses = boluses.filter { it.iCfg.insulinEndTime == -1L }
        if (unmigratedBoluses.isNotEmpty()) {
            val total = unmigratedBoluses.size
            val step = rh.get().gs(R.string.migrating_boluses)
            config.updateInitProgress(step, 0, total)
            unmigratedBoluses.forEachIndexed { index, bolus ->
                bolus.iCfg.insulinLabel = runningICfg.insulinLabel
                bolus.iCfg.insulinEndTime = runningICfg.insulinEndTime
                bolus.iCfg.insulinPeakTime = runningICfg.insulinPeakTime
                bolus.iCfg.concentration = runningICfg.concentration
                persistenceLayer.updateBolusNoLogging(bolus)
                if ((index + 1) % PROGRESS_UPDATE_INTERVAL == 0 || index + 1 == total)
                    config.updateInitProgress(step, index + 1, total)
            }
            totalMigrated += unmigratedBoluses.size
        }

        // Log a single user entry for the entire migration
        if (totalMigrated > 0) {
            aapsLogger.debug(LTag.CORE, "Migration to DB 33 complete: $totalMigrated records updated")
            persistenceLayer.insertPumpTherapyEventIfNewByTimestamp(
                therapyEvent = TE(
                    timestamp = dateUtil.now(),
                    type = TE.Type.NOTE,
                    note = "Database migration to v33: $totalMigrated records updated (insulin configuration)",
                    glucoseUnit = GlucoseUnit.MGDL
                ),
                action = Action.START_AAPS,
                source = Sources.Aaps,
                note = "",
                listValues = listOf()
            )
        }
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
