package app.aaps

import android.app.Application
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import androidx.hilt.work.HiltWorkerFactory
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.Configuration
import app.aaps.core.data.configuration.Constants
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.ICfg
import app.aaps.core.data.model.RM
import app.aaps.core.data.model.TE
import app.aaps.core.data.model.TT
import app.aaps.core.data.model.TTPreset
import app.aaps.core.data.time.T
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
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileRepository
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.protection.ExportPasswordDataStore
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.resources.TextRefIdRegistry
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventAppInitialized
import app.aaps.core.interfaces.rx.events.EventShowSnackbar
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.core.interfaces.tempTargets.toJson
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.HardLimits
import app.aaps.core.interfaces.utils.SafeParse
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.interfaces.versionChecker.VersionCheckerUtils
import app.aaps.core.interfaces.widget.WidgetUpdater
import app.aaps.core.keys.BooleanComposedKey
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.BooleanNonKey
import app.aaps.core.keys.IntKey
import app.aaps.core.keys.LongComposedKey
import app.aaps.core.keys.LongNonKey
import app.aaps.core.keys.ProfileComposedBooleanKey
import app.aaps.core.keys.ProfileComposedStringKey
import app.aaps.core.keys.StringKey
import app.aaps.core.keys.StringNonKey
import app.aaps.core.keys.UnitDoubleKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.objects.crypto.CryptoUtil
import app.aaps.core.objects.profile.ProfileSealed
import app.aaps.core.interfaces.di.MetroMemberInjector
import app.aaps.core.ui.compose.MetroViewModelFactory
import app.aaps.core.ui.compose.MetroViewModelFactoryOwner
import app.aaps.core.ui.locale.LocaleHelper
import app.aaps.database.AppRepository
import app.aaps.di.metro.MetroGraphs
import app.aaps.di.metro.MetroWorkerFactory
import app.aaps.implementation.lifecycle.ProcessLifecycleListener
import app.aaps.implementation.plugin.PluginStore
import app.aaps.implementation.profile.ProfileSwitchExpiryScheduler
import app.aaps.implementation.receivers.BTReceiver
import app.aaps.implementation.receivers.ChargingStateReceiver
import app.aaps.implementation.receivers.KeepAliveWorker
import app.aaps.implementation.receivers.NetworkChangeReceiver
import app.aaps.implementation.receivers.TimeDateOrTZChangeReceiver
import app.aaps.plugins.aps.loop.runningMode.RunningModeExpiryScheduler
import app.aaps.plugins.aps.loop.runningMode.RunningModeReconciler
import app.aaps.plugins.automation.AutomationRuntime
import app.aaps.plugins.calibration.CalibrationStringIds
import app.aaps.plugins.constraints.objectives.keys.ObjectivesLongComposedKey
import app.aaps.plugins.constraints.signatureVerifier.SignatureVerifierPlugin
import app.aaps.plugins.main.MainStringIds
import app.aaps.plugins.sensitivity.SensitivityStringIds
import app.aaps.plugins.smoothing.SmoothingStringIds
import app.aaps.pump.virtual.VirtualStringIds
import app.aaps.ui.activityMonitor.ActivityMonitor
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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import rxdogtag2.RxDogTag
import java.io.IOException
import java.util.Locale
import javax.inject.Inject
import javax.inject.Provider
import kotlin.reflect.KMutableProperty
import kotlin.reflect.full.declaredMemberProperties
import kotlin.time.Duration.Companion.milliseconds

@HiltAndroidApp
class MainApp : Application(), HasAndroidInjector, MetroMemberInjector, MetroViewModelFactoryOwner, Configuration.Provider {

    @Inject lateinit var androidInjector: DispatchingAndroidInjector<Any>
    override fun androidInjector(): AndroidInjector<Any> = androidInjector

    // The Metro side of the same job. Classes already converted are filled by Metro; anything else is
    // still dagger.android's, so a converted and an unconverted receiver can sit side by side.
    override fun injectMembers(target: Any): Boolean = metroGraphs.injectMembers(target)

    // The @HiltViewModel replacement, reached by activities the same way.
    override val metroViewModelFactory: MetroViewModelFactory get() = metroGraphs.viewModelFactory

    // WorkManager on-demand initialization. MetroWorkerFactory builds workers already moved to Metro
    // and hands the rest to HiltWorkerFactory, which constructs @HiltWorker workers via assisted
    // injection; workers on neither return null from it and fall back to WorkManager's default
    // reflective factory (which self-injects through HasAndroidInjector). The default androidx.startup
    // WorkManagerInitializer is removed in AndroidManifest.xml so this config wins.
    @Inject lateinit var hiltWorkerFactory: HiltWorkerFactory
    @Inject lateinit var metroGraphs: MetroGraphs
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(MetroWorkerFactory(metroGraphs, hiltWorkerFactory))
            .build()

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
    @Inject lateinit var profileRepository: ProfileRepository
    @Inject lateinit var localInsulinManager: InsulinManager
    @Inject lateinit var constraintChecker: ConstraintsChecker
    @Inject lateinit var signatureVerifierPlugin: SignatureVerifierPlugin
    @Inject lateinit var fileListProvider: FileListProvider
    @Inject lateinit var cryptoUtil: CryptoUtil
    @Inject lateinit var exportPasswordDataStore: ExportPasswordDataStore
    @Inject lateinit var widgetUpdater: WidgetUpdater
    @Inject lateinit var runningModeReconciler: RunningModeReconciler
    @Inject lateinit var runningModeExpiryScheduler: RunningModeExpiryScheduler
    @Inject lateinit var profileSwitchExpiryScheduler: ProfileSwitchExpiryScheduler
    @Inject lateinit var automationRuntime: AutomationRuntime
    @Inject @ApplicationScope lateinit var appScope: CoroutineScope

    private lateinit var insulinLabel: String
    private var insulinPeakTime: Long = 0L
    private var profileNameToDia: Map<String, Double> = emptyMap()

    private var handler = Handler(HandlerThread(this::class.simpleName + "Handler").also { it.start() }.looper)
    private lateinit var refreshWidget: Runnable
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onCreate() {
        super.onCreate()

        registerStringOwners()

        // Here should be everything injected
        aapsLogger.debug("onCreate")
        ProcessLifecycleOwner.get().lifecycle.addObserver(processLifecycleListener.get())

        // Background fallback for EventShowSnackbar: when no activity is STARTED
        // (app in background / process alive but UI offscreen), promote the
        // snackbar to a system Notification so the message is not lost.
        // Visible activities host their own GlobalSnackbarHost that also
        // subscribes; those win while UI is present.
        appScope.launch {
            rxBus.toFlow(EventShowSnackbar::class).collect { event ->
                val uiVisible = ProcessLifecycleOwner.get().lifecycle.currentState
                    .isAtLeast(Lifecycle.State.STARTED)
                if (!uiVisible) {
                    notificationManager.post(
                        id = NotificationId.SNACKBAR_FALLBACK,
                        text = event.message,
                        // URGENT is reserved for pump/loop alarms that play alarm-stream
                        // sounds and wake users. Generic snackbar errors — "failed to save
                        // preference", etc. — route through NORMAL instead.
                        level = when (event.type) {
                            EventShowSnackbar.Type.Error   -> NotificationLevel.NORMAL
                            EventShowSnackbar.Type.Warning -> NotificationLevel.NORMAL
                            EventShowSnackbar.Type.Success -> NotificationLevel.INFO
                            EventShowSnackbar.Type.Info    -> NotificationLevel.INFO
                        },
                        validMinutes = 30
                    )
                }
            }
        }
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

                // ProfileRepository is a @Singleton, so it already loaded during field injection —
                // before doMigrations() converted the ancient raw SharedPreferences profile keys into
                // the numbered ones. Re-read now, otherwise that upgrade would be picked up only on
                // the next start (and the profile-to-JSON conversion with it).
                profileRepository.reset()

                // Defragment the DB while it is quiescent: plugins, loop, sync and UI all start
                // later, so the (memory heavy) VACUUM has the DB to itself. Runs at most monthly.
                vacuumDatabaseIfDue()

                // Register and initialize plugins
                config.updateInitProgress(getString(R.string.initializing_plugins))
                pluginStore.plugins = plugins
                configBuilder.initialize()

                // Running-mode reconciler + expiry scheduler. Start after plugins are registered:
                // the reconciler's startup-drift check reads the active pump, which requires
                // pluginStore.plugins to be populated. Both internally gated by config.APS.
                runningModeReconciler.start()
                runningModeExpiryScheduler.start()
                // Fires a profile change at the exact end of a temporary ProfileSwitch (master-only),
                // removing the up-to-5-min KeepAlive latency. KeepAlive remains the backstop.
                profileSwitchExpiryScheduler.start()

                // Standalone automation runtime (no longer a plugin). Loads definitions on all
                // flavors; the processing loop + location service are master-only (gated internally).
                automationRuntime.start()

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

    // Perform a full VACUUM at most once a month. VACUUM defragments the DB file and reclaims
    // space, restoring query performance that degrades after long use. It is heavy and memory
    // intensive, so it runs only here at startup while nothing else touches the DB (this avoids
    // the SQLITE_NOMEM crash seen when VACUUM overlapped live DB activity).
    private suspend fun vacuumDatabaseIfDue() {
        val lastRun = preferences.get(LongNonKey.LastVacuumRun)
        if (lastRun >= dateUtil.now() - T.days(30).msecs()) return

        // Crash-loop guard. VACUUM can die below the JVM (native SQLite abort / OOM) in a way no
        // try/catch can intercept — the process just vanishes, leaving no log. If that happened last
        // time, the committed flag below is still set (the finally never ran). Detect it, skip
        // VACUUM so the app can boot, and back off a month instead of re-crashing on every launch.
        if (preferences.get(BooleanNonKey.VacuumInProgress)) {
            aapsLogger.error(LTag.CORE, "Previous startup VACUUM did not finish (likely native crash) — skipping for 30 days")
            // Report to Firebase so we get a fleet-wide count of users hit by a crashing startup VACUUM.
            fabricPrivacy.logCustom("db_vacuum_crash_recovered")
            preferences.put(BooleanNonKey.VacuumInProgress, false)
            preferences.put(LongNonKey.LastVacuumRun, dateUtil.now())
            return
        }

        config.updateInitProgress(getString(R.string.optimizing_database))
        try {
            // Log size + per-table counts (total / older-than-retention backlog / tracked changes)
            // before VACUUM, so a crash report tells us whether the DB is the problem. 6*31 mirrors
            // KeepAliveWorker.databaseCleanup's retention so "deletable" reflects the real backlog.
            val info = persistenceLayer.databaseMaintenanceInfo(retentionDays = 6L * 31)
            aapsLogger.info(LTag.CORE, "Startup DB maintenance, pre-VACUUM diagnostics:\n${info.report}")
            // Crashlytics breadcrumb: attaches the diagnostics to any exception logged below.
            fabricPrivacy.logMessage("Pre-VACUUM: ${info.report}")
            val dbMb = info.dbSizeBytes / 1_048_576
            // VACUUM rebuilds the whole DB into a temporary copy, so it needs roughly the DB size
            // again in free space. Skip (and retry next launch) if there isn't enough, to avoid a
            // SQLITE_FULL failure mid-rebuild.
            if (info.availableBytes in 0 until info.dbSizeBytes * 2) {
                val freeMb = info.availableBytes / 1_048_576
                aapsLogger.warn(LTag.CORE, "Skipping startup VACUUM: free $freeMb MB < 2x DB $dbMb MB")
                fabricPrivacy.logCustom("db_vacuum_skip_space", mapOf("db_mb" to dbMb, "free_mb" to freeMb))
                return
            }
            // Commit the marker synchronously BEFORE running: a plain put() uses apply() and may not
            // reach disk before an early native abort (e.g. during the wal_checkpoint VACUUM starts with).
            sp.edit(commit = true) { putBoolean(BooleanNonKey.VacuumInProgress.key, true) }
            persistenceLayer.vacuumDatabase()
            // Only advance the timestamp on success, so a transient failure (e.g. DB busy because a
            // persisted worker is running) is retried on a future launch instead of suppressed for a month.
            preferences.put(LongNonKey.LastVacuumRun, dateUtil.now())
            aapsLogger.debug(LTag.CORE, "Startup VACUUM done")
            // Fleet overview of DB size / cleanup backlog / change-row volume across users.
            fabricPrivacy.logCustom("db_vacuum_ok", mapOf("db_mb" to dbMb, "deletable" to info.deletableRows, "changes" to info.changeRows))
        } catch (e: Throwable) {
            // Throwable, not just Exception: a JVM OutOfMemoryError here must not abort app init.
            aapsLogger.error(LTag.CORE, "Startup VACUUM failed", e)
            // Catchable failures (SQLiteException, OOM) → Crashlytics non-fatal for the overview.
            fabricPrivacy.logException(e)
        } finally {
            preferences.put(BooleanNonKey.VacuumInProgress, false)
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
                appScope.launch {
                    persistenceLayer.insertVersionChangeIfChanged(config.VERSION_NAME, BuildConfig.VERSION_CODE, gitRemote, commitHash)
                }
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
            widgetUpdater.update("ScheduleEveryMin")
        }
        handler.postDelayed(refreshWidget, 5000)
        setUserStats()
        passwordResetCheck()
        exportPasswordResetCheck()
        config.initCompleted()
        rxBus.send(EventAppInitialized())
        aapsLogger.debug("doInit end")
    }

    private suspend fun setUserStats() {
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
        fabricPrivacy.setUserProperty("HEAD", BuildConfig.BUILDVERSION)
        fabricPrivacy.setUserProperty("Remote", remote)
        val hashes: List<String> = signatureVerifierPlugin.shortHashes()
        if (hashes.isNotEmpty()) fabricPrivacy.setUserProperty("Hash", hashes[0])
        activePlugin.activePumpInternal.let { fabricPrivacy.setUserProperty("Pump", it::class.java.simpleName) }
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
            val serialNumber = withTimeoutOrNull(30_000.milliseconds) {
                while (activePlugin.activePump.serialNumber().isEmpty()) {
                    delay(100.milliseconds)
                }
                activePlugin.activePump.serialNumber()
            }
            if (serialNumber != null) {
                preferences.put(StringKey.ProtectionMasterPassword, cryptoUtil.hashPassword(serialNumber))
                fh.delete()
                exportPasswordDataStore.clearPasswordDataStore()
                config.showInitSnackbar(getString(app.aaps.core.ui.R.string.password_set))
            } else {
                aapsLogger.warn(LTag.CORE, "Password reset timed out waiting for pump serial number")
            }
        }
    }

    private fun exportPasswordResetCheck() {
        val fh = fileListProvider.ensureExtraDirExists()?.findFile("ExportPasswordReset")
        if (fh?.exists() == true) {
            exportPasswordDataStore.clearPasswordDataStore()
            fh.delete()
            config.showInitSnackbar(getString(app.aaps.core.ui.R.string.datastore_password_cleared))
        }
    }

    private fun postSetupNotifications() {
        // Identification not set (dev builds only)
        if (config.isDev() && preferences.get(StringKey.MaintenanceIdentification).isBlank())
            notificationManager.post(
                id = NotificationId.IDENTIFICATION_NOT_SET,
                TextRef.AndroidRes(R.string.identification_not_set),
                level = NotificationLevel.INFO,
                actions = listOf(NotificationAction(TextRef.AndroidRes(R.string.set)) {}),
                validityCheck = { config.isDev() && preferences.get(StringKey.MaintenanceIdentification).isBlank() }
            )
        // Master password not set
        if (preferences.get(StringKey.ProtectionMasterPassword) == "")
            notificationManager.post(
                id = NotificationId.MASTER_PASSWORD_NOT_SET,
                TextRef.AndroidRes(app.aaps.core.ui.R.string.master_password_not_set),
                level = NotificationLevel.NORMAL,
                actions = listOf(NotificationAction(TextRef.AndroidRes(R.string.set)) {}),
                validityCheck = { preferences.get(StringKey.ProtectionMasterPassword) == "" }
            )
        // AAPS directory not selected
        if (preferences.getIfExists(StringKey.AapsDirectoryUri).isNullOrEmpty())
            notificationManager.post(
                id = NotificationId.AAPS_DIR_NOT_SELECTED,
                TextRef.AndroidRes(app.aaps.core.ui.R.string.aaps_directory_not_selected),
                level = NotificationLevel.LOW,
                actions = listOf(NotificationAction(TextRef.AndroidRes(R.string.select)) {}),
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
        if (preferences.get(UnitDoubleKey.OverviewLowMark) == 0.0) preferences.remove(UnitDoubleKey.OverviewLowMark)
        if (preferences.get(UnitDoubleKey.OverviewHighMark) == 0.0) preferences.remove(UnitDoubleKey.OverviewHighMark)
        // These three migrate bidirectionally-synced keys. Skip on a client: it adopts the value from
        // the master via sync, and a local put here would now trigger a client→master round-trip (modal)
        // at startup. The master migrates and publishes; the client follows.
        if (!config.AAPSCLIENT && preferences.getIfExists(BooleanKey.GeneralSimpleMode) == null)
            preferences.put(BooleanKey.GeneralSimpleMode, !preferences.get(BooleanNonKey.GeneralSetupWizardProcessed))
        // Migrate from OpenAPSSMBDynamicISFPlugin
        if (sp.getBoolean("ConfigBuilder_APS_OpenAPSSMBDynamicISFPlugin_Enabled", false)) {
            sp.remove("ConfigBuilder_APS_OpenAPSSMBDynamicISFPlugin_Enabled")
            sp.remove("ConfigBuilder_APS_OpenAPSSMBDynamicISFPlugin_Visible")
            sp.putBoolean("ConfigBuilder_APS_OpenAPSSMB_Enabled", true)
            if (!config.AAPSCLIENT) preferences.put(BooleanKey.ApsUseDynamicSensitivity, true)
        }
        // convert Double to Int
        try {
            val dynIsf = sp.getDouble("DynISFAdjust", 0.0)
            if (!config.AAPSCLIENT && dynIsf != 0.0 && dynIsf.toInt() != preferences.get(IntKey.ApsDynIsfAdjustmentFactor))
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
                preferences.put(BooleanComposedKey.ConfigBuilderEnabled, plugin, value = value as Boolean)
                sp.remove(key)
            }
            if (key.startsWith("ConfigBuilder_") && key.endsWith("_Visible")) {
                // Legacy fragment-visibility pref — no longer tracked; drop during migration.
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
     * Old keys are kept for backward compatibility during migration period.
     */
    private fun migrateTempTargetPresets() {
        // Clean up zero-value old preferences (3.3 migration)
        if (sp.getInt("eatingsoon_duration", 45) == 0) sp.remove("eatingsoon_duration")
        if (sp.getDouble("eatingsoon_target", 90.0) == 0.0) sp.remove("eatingsoon_target")
        if (sp.getInt("activity_duration", 90) == 0) sp.remove("activity_duration")
        if (sp.getDouble("activity_target", 140.0) == 0.0) sp.remove("activity_target")
        if (sp.getInt("hypo_duration", 60) == 0) sp.remove("hypo_duration")
        if (sp.getDouble("hypo_target", 160.0) == 0.0) sp.remove("hypo_target")

        // Seeds the bidirectionally-synced TempTargetPresets. Skip on a client: it adopts the presets
        // from the master via sync, and a local put here would trigger a client→master round-trip
        // (modal) at startup. The master seeds and publishes; the client follows.
        if (config.AAPSCLIENT) return

        // Check if migration already completed
        val existing = preferences.get(StringNonKey.TempTargetPresets)
        if (existing != "[]" && existing.isNotEmpty()) {
            return // Already migrated
        }

        // Check if old preferences exist (existing installation vs new installation)
        val hasOldPreferences = sp.contains("eatingsoon_target")

        // Create 3 default presets - values always stored in mg/dL
        val presets = listOf(
            TTPreset(
                id = "eatingsoon",
                reason = TT.Reason.EATING_SOON,
                targetValue = if (hasOldPreferences) {
                    profileUtil.convertToMgdlDetect(sp.getDouble("eatingsoon_target", 90.0))
                } else {
                    Constants.DEFAULT_TT_EATING_SOON_TARGET
                },
                duration = if (hasOldPreferences) {
                    sp.getInt("eatingsoon_duration", 45) * 60L * 1000L
                } else {
                    Constants.DEFAULT_TT_EATING_SOON_DURATION * 60L * 1000L
                },
                isDeletable = false
            ),
            TTPreset(
                id = "activity",
                reason = TT.Reason.ACTIVITY,
                targetValue = if (hasOldPreferences) {
                    profileUtil.convertToMgdlDetect(sp.getDouble("activity_target", 140.0))
                } else {
                    Constants.DEFAULT_TT_ACTIVITY_TARGET
                },
                duration = if (hasOldPreferences) {
                    sp.getInt("activity_duration", 90) * 60L * 1000L
                } else {
                    Constants.DEFAULT_TT_ACTIVITY_DURATION * 60L * 1000L
                },
                isDeletable = false
            ),
            TTPreset(
                id = "hypo",
                reason = TT.Reason.HYPOGLYCEMIA,
                targetValue = if (hasOldPreferences) {
                    profileUtil.convertToMgdlDetect(sp.getDouble("hypo_target", 160.0))
                } else {
                    Constants.DEFAULT_TT_HYPO_TARGET
                },
                duration = if (hasOldPreferences) {
                    sp.getInt("hypo_duration", 60) * 60L * 1000L
                } else {
                    Constants.DEFAULT_TT_HYPO_DURATION * 60L * 1000L
                },
                isDeletable = false
            )
        )

        // Save to new JSON format
        preferences.put(StringNonKey.TempTargetPresets, presets.toJson())

        aapsLogger.debug(LTag.CORE, "Migrated temp target presets to JSON storage")
    }

    private suspend fun dataMigrations() {
        // Migrate to database 33 (ICfg)
        //
        // Goal: legacy rows predate ICfg, so they carry the `insulinEndTime = -1` sentinel and their IOB
        // is computed from the clamped math floors in ICfg.iobCalcForTreatment — which under-counts IOB.
        // Stamping them makes the insulin curve a property of the record, as v33 intends.
        //
        // The value must be the insulin those records were actually delivered with. Legacy preferences
        // (below) reconstruct it; failing that the running profile owns the authoritative iCfg. Only when
        // there is neither is a substitute used — declared to the user rather than guessed silently, and
        // never taken from the insulin list, whose first entry is a positional accident, not "the current
        // insulin". Note this is one-way: once stamped the sentinel is consumed and these records are not
        // revisited, which is why the notification says so.
        val runningICfg = if (profileNameToDia.isEmpty())
            profileFunction.getRunningOrRequestedICfg() ?: substituteICfgForMigration()
        else {
            val dia = (profileFunction.getProfile() as ProfileSealed.EPS?)?.profileName?.let { profileName ->
                profileNameToDia[profileName]
            }
            val insulinEndTime = ((dia ?: hardLimits.diaRange().endInclusive) * 3600 * 1000).toLong()
            ICfg("", insulinEndTime, insulinPeakTime, 1.0).also {
                it.insulinNickname = insulinLabel
                it.insulinLabel = "$insulinLabel ${localInsulinManager.buildSuffix(it.peak, it.dia, it.concentration)}"
            }
        }

        if (!localInsulinManager.insulinAlreadyExists(runningICfg))
            localInsulinManager.addNewInsulin(runningICfg, keepName = true)

        val label = runningICfg.insulinLabel
        val end = runningICfg.insulinEndTime
        val peak = runningICfg.insulinPeakTime
        val conc = runningICfg.concentration

        config.updateInitProgress(rh.get().gs(R.string.migrating_profile_switches))
        val migratedPs = repository.bulkMigrateProfileSwitchInsulinConfig(label, end, peak, conc)

        config.updateInitProgress(rh.get().gs(R.string.migrating_effective_profile_switches))
        val migratedEps = repository.bulkMigrateEffectiveProfileSwitchInsulinConfig(label, end, peak, conc)

        config.updateInitProgress(rh.get().gs(R.string.migrating_boluses))
        val migratedBoluses = repository.bulkMigrateBolusInsulinConfig(label, end, peak, conc)

        val totalMigrated = migratedPs + migratedEps + migratedBoluses
        if (totalMigrated > 0) {
            profileFunction.invalidateCache()
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

    /**
     * Insulin config stamped onto legacy records when neither legacy preferences nor a running profile
     * can tell us what was actually used: no active profile switch at first start, or — the case that
     * matters — a running profile whose own row still carries the v33 sentinel.
     *
     * That second case is why this must not read the running profile unguarded. The SQL step of the
     * migration stamps `insulinEndTime = -1` onto every pre-ICfg row *including the active one*, so
     * reading it back yields a DIA of 0.0 and writes the sentinel straight back over itself. The rows stay
     * unmigrated, the profile keeps reporting a zero DIA, and the APS hard-limit check aborts every loop
     * cycle. [app.aaps.core.interfaces.profile.ProfileFunction.getRunningOrRequestedICfg] rejects an
     * unusable [ICfg] so that path lands here instead — and because the repair re-runs on every start and
     * still matches the sentinel rows, an install already broken this way heals on its next launch.
     *
     * Ultra-rapid: an 8h DIA is the same across every [InsulinType] template, so the only real choice is
     * the peak. Concentration is 1.0 by construction, which is not a guess — these records predate
     * concentration entirely, so 1.0 is the identity that leaves historical doses unscaled.
     */
    private fun substituteICfgForMigration(): ICfg =
        InsulinType.OREF_ULTRA_RAPID_ACTING.getICfg(rh.get()).also {
            aapsLogger.warn(LTag.CORE, "Migration to DB 33: no profile and no legacy DIA, substituting ${it.insulinLabel}")
            notificationManager.post(
                id = NotificationId.INSULIN_MIGRATION_DEFAULT_USED,
                rh.get().gs(R.string.insulin_migration_default_used, it.insulinLabel),
                level = NotificationLevel.IMPORTANT
            )
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
                                // `definition` is read through reflection, so the cast below is unchecked and the
                                // compiler cannot see it. It said JSONObject long after the property became a
                                // kotlinx JsonObject, and the app crashed on start as soon as a remote config
                                // fetch actually succeeded - which needs network and Play Services, so no test or
                                // CI build ever reached it. Keep the two types here in step by hand.
                                val current = it.getter.call(versionCheckersUtils) as JsonObject
                                val remote = Json.parseToJsonElement(firebaseRemoteConfig.getString("defs")).jsonObject
                                // Plus on the maps is a shallow merge with the remote keys winning, which is what
                                // the JsonHelper.merge that used to be here did.
                                it.setter.call(versionCheckersUtils, JsonObject(current + remote))
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

    /**
     * Teaches the resolvers which module owns which string names.
     *
     * A `TextRef.Named` carries an owner and a name, and both the Compose and the ResourceHelper
     * paths need a way to turn that into an `R.string` id. `:core:keys`, `:core:interfaces` and
     * `:core:ui` are resolved directly because `:core:ui` sits above them, but a plugin or pump
     * module sits ABOVE `:core:ui`, so it can only be reached from here - `:app` is the one place
     * that depends on all of them.
     *
     * Without this the lookup answers null and the raw name is drawn: `virtual_pump_shortname`
     * instead of "Virtual Pump".
     */
    private fun registerStringOwners() {
        TextRefIdRegistry.register("virtual") { name -> VirtualStringIds.idOf(name) }
        TextRefIdRegistry.register("smoothing") { name -> SmoothingStringIds.idOf(name) }
        TextRefIdRegistry.register("calibration") { name -> CalibrationStringIds.idOf(name) }
        TextRefIdRegistry.register("sensitivity") { name -> SensitivityStringIds.idOf(name) }
        TextRefIdRegistry.register("main") { name -> MainStringIds.idOf(name) }
    }
}
