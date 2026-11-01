package app.aaps

import android.app.Application
import android.app.LocaleManager
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.LocaleList
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.Configuration
import app.aaps.core.data.configuration.Constants
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.TE
import app.aaps.core.data.model.TT
import app.aaps.core.data.model.TTPreset
import app.aaps.core.data.time.T
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.configuration.ExternalOptions
import app.aaps.core.interfaces.db.VacuumResult
import app.aaps.core.interfaces.di.MetroMemberInjector
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.notifications.NotificationAction
import app.aaps.core.interfaces.notifications.NotificationId
import app.aaps.core.interfaces.notifications.NotificationLevel
import app.aaps.core.interfaces.rx.events.EventAppInitialized
import app.aaps.core.interfaces.tempTargets.toJson
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.BooleanNonKey
import app.aaps.core.keys.LongNonKey
import app.aaps.core.keys.StringKey
import app.aaps.core.keys.StringNonKey
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.ui.compose.MetroViewModelFactoryOwner
import app.aaps.database.di.DatabaseConfig
import app.aaps.di.ExternalOptionsOverride
import app.aaps.di.GeneratedStringOwners
import app.aaps.di.metro.MetroGraphs
import app.aaps.di.metro.MetroWorkerFactory
import app.aaps.implementation.maintenance.migration.InsulinMigration
import app.aaps.implementation.maintenance.migration.InsulinMigrationPlan
import app.aaps.implementation.maintenance.migration.LegacyInsulin
import app.aaps.implementation.maintenance.migration.PreferenceMigrations
import app.aaps.implementation.maintenance.migration.ProfileDiaHarvest
import app.aaps.implementation.receivers.BTReceiver
import app.aaps.implementation.receivers.ChargingStateReceiver
import app.aaps.implementation.receivers.KeepAliveWorker
import app.aaps.implementation.receivers.NetworkChangeReceiver
import app.aaps.implementation.receivers.TimeDateOrTZChangeReceiver
import app.aaps.settingsstore.AndroidStorage
import app.aaps.settingsstore.IdleWorkerFactory
import app.aaps.settingsstore.SettingsUnreadableNotification
import app.aaps.utils.configureLeakCanary
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import com.google.firebase.remoteconfig.remoteConfig
import dev.zacsweers.metrox.viewmodel.MetroViewModelFactory
import io.reactivex.rxjava3.exceptions.UndeliverableException
import io.reactivex.rxjava3.plugins.RxJavaPlugins
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import rxdogtag2.RxDogTag
import java.io.IOException
import java.util.Locale
import kotlin.reflect.KMutableProperty
import kotlin.reflect.full.declaredMemberProperties
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import android.content.res.Configuration as ResourcesConfiguration
import app.aaps.core.interfaces.R as InterfacesR
import app.aaps.core.ui.R as CoreUiR

class MainApp : Application(), MetroMemberInjector, MetroViewModelFactoryOwner, Configuration.Provider {

    companion object {

        /**
         * How long start up waits for the plugins to finish starting before carrying on regardless.
         * Matches `ConfigBuilderImpl.PLUGIN_SETTLE_WAIT`, which bounds the same wait on the import path -
         * the two start paths should behave the same, which is the whole point of waiting here at all.
         */
        private val PLUGIN_START_WAIT = 30.seconds
    }

    // When the settings cannot be opened, ComposeMainActivity only hands over to SettingsUnreadableActivity and
    // finishes. Filling its fields would build some 40 objects - plugins, the command queue - for nothing,
    // and on settings that are not the user's.
    override fun injectMembers(target: Any): Boolean =
        if (AndroidStorage.failure != null && target is ComposeMainActivity) true
        else metroGraphs.injectMembers(target)

    override val metroViewModelFactory: MetroViewModelFactory get() = metroGraphs.viewModelFactory

    /**
     * `by lazy` so nothing is resolved before `onCreate` runs - the graph reaches Android services, and
     * an Application field initialiser runs before the framework is ready for that.
     * The last two arguments are the only things an instrumented test does differently; see
     * `AppRootGraph.Factory`. Production wants the real database and no forced options.
     */
    private val metroGraphs by lazy {
        MetroGraphs(
            context = this,
            memberInjector = this,
            databaseConfig = DatabaseConfig.PRODUCTION,
            externalOptionsOverride = ExternalOptionsOverride.NONE
        )
    }
    // WorkManager on-demand initialization. `MetroWorkerFactory` builds every worker in the app; what
    // it does not know returns null and falls back to WorkManager's default reflective factory, which
    // is what builds WorkManager's own internal workers. The default androidx.startup
    // WorkManagerInitializer is removed in AndroidManifest.xml so this config wins.
    // While the settings cannot be opened, the app's jobs do nothing (see IdleWorkerFactory).
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(
                if (AndroidStorage.failure == null) MetroWorkerFactory(metroGraphs)
                else IdleWorkerFactory { metroGraphs.workerCreators().keys }
            )
            .build()

    private val pluginStore get() = metroGraphs.pluginStore
    private val aapsLogger get() = metroGraphs.aapsLogger
    private val versionCheckersUtils get() = metroGraphs.versionCheckerUtils
    private val sp get() = metroGraphs.keyValueStore
    private val preferences get() = metroGraphs.preferences
    private val config get() = metroGraphs.config
    private val configBuilder get() = metroGraphs.configBuilder
    private val plugins get() = metroGraphs.allPlugins(aapsLogger)
    private val persistenceLayer get() = metroGraphs.persistenceLayer
    private val dateUtil get() = metroGraphs.dateUtil
    private val uiInteraction get() = metroGraphs.uiInteraction
    private val processLifecycleListener get() = metroGraphs.processLifecycleListener
    private val localAlertUtils get() = metroGraphs.localAlertUtils
    private val periodicMaintenance get() = metroGraphs.periodicMaintenance
    private val notificationManager get() = metroGraphs.notificationManager
    private val rh get() = metroGraphs.resourceHelper
    private val loop get() = metroGraphs.loop
    private val profileFunction get() = metroGraphs.profileFunction
    private val profileUtil get() = metroGraphs.profileUtil
    private val fabricPrivacy get() = metroGraphs.fabricPrivacy
    // The concrete types, only so their start() can be called below. Both are @Singleton, so these are
    // the same objects the interface bindings hand out. `rh` is a Provider already - kept that way here
    // rather than risking the cycle that shape exists to avoid.
    private val resourceHelperImpl get() = metroGraphs.resourceHelperImpl
    private val fabricPrivacyImpl get() = metroGraphs.fabricPrivacyImpl
    private val rxBus get() = metroGraphs.rxBus
    private val repository get() = metroGraphs.appRepository
    private val hardLimits get() = metroGraphs.hardLimits
    private val activePlugin get() = metroGraphs.activePlugin
    private val profileRepository get() = metroGraphs.profileRepository
    private val localInsulinManager get() = metroGraphs.insulinManager
    private val constraintChecker get() = metroGraphs.constraintsChecker
    private val signatureVerifierPlugin get() = metroGraphs.signatureVerifier
    private val fileListProvider get() = metroGraphs.fileListProvider
    private val cryptoUtil get() = metroGraphs.cryptoUtil
    private val exportPasswordDataStore get() = metroGraphs.exportPasswordDataStore
    private val widgetUpdater get() = metroGraphs.widgetUpdater
    private val runningModeReconciler get() = metroGraphs.runningModeReconciler
    private val runningModeExpiryScheduler get() = metroGraphs.runningModeExpiryScheduler
    private val profileSwitchScheduler get() = metroGraphs.profileSwitchScheduler
    private val automationRuntime get() = metroGraphs.automationRuntime
    private val snackbarNotificationFallback get() = metroGraphs.snackbarNotificationFallback
    private val eatReminder get() = metroGraphs.eatReminder
    private val appScope get() = metroGraphs.applicationScope
    private val uiRestart get() = metroGraphs.uiRestart

    /** The rules; the two fields below are what this app's start-up sequence carries between its steps. */
    private val insulinMigration get() = InsulinMigration(aapsLogger, hardLimits, rh)

    /**
     * The v3 insulin plugin selection, read before the migrations delete its keys. Null when there was none.
     */
    private var legacyInsulin: LegacyInsulin? = null

    /**
     * The DIA each v3 profile was using, with the raw keys those values came from still in the old store.
     *
     * `doMigrations` used to remove those keys as it read them, which put the only copy of the DIA values
     * in a field, in memory. `dataMigrations` is the only consumer and runs much later, after
     * `vacuumDatabaseIfDue` (which documents that it can take the process down below the JVM, where no
     * `catch` reaches) and after the plugins start. A death anywhere in that gap lost the DIA values for
     * good: the next start finds no keys and stamps the old records with a guess instead. That is wrong IOB
     * on historical records and it is one-way - the sentinel is consumed and those rows are never revisited.
     *
     * So they are removed only once the value they carry is safely in the insulin list.
     */
    private var profileDia: ProfileDiaHarvest = ProfileDiaHarvest(emptyMap(), emptyList())

    private var handler = Handler(HandlerThread(this::class.simpleName + "Handler").also { it.start() }.looper)
    private lateinit var refreshWidget: Runnable
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onCreate() {
        super.onCreate()

        // The settings first: everything below reads them. Opened here on purpose, not on first use, so a
        // store that cannot be read is found at once and handled in one place.
        val storage = AndroidStorage.open(this)
        storage.failure?.let { failure ->
            startWithoutSettings(failure, storage.notes)
            return
        }
        GeneratedStringOwners.registerAll()
        resourceHelperImpl.start()
        // As early as possible, so the first screen is already in it
        applyAppLanguage()
        appLocales = resources.configuration.locales
        // Applies the analytics opt-out. Must come before configureLeakCanary below, which reports
        // through fabricPrivacy.
        fabricPrivacyImpl.start()
        // Build identity goes on the crash report here, not in setUserStats() near the end of doInit.
        // A crash during plugin initialization happens seconds before that runs, so those reports carried
        // no HEAD and no Committed - exactly the ones where the build has to be known to tell a stale
        // local build from a live bug. Collection is already gated by the call above, so an opted-out
        // user still uploads nothing.
        setBuildIdentityKeys()

        // Here should be everything injected
        aapsLogger.debug("onCreate")
        storage.notes.forEach { aapsLogger.info(LTag.CORE, it) }
        AndroidStorage.onSaveFailure = { aapsLogger.error(LTag.CORE, "Settings could not be saved, the next change tries again: ${it.message}") }
        // Left by an earlier start whose settings could not be opened.
        SettingsUnreadableNotification.cancel(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(processLifecycleListener)

        // Background fallback for EventShowSnackbar: when no GlobalSnackbarHost is collecting,
        // promote the snackbar to a system Notification so the message is not lost. Shared code -
        // the iOS and desktop shells start the same class. Started here, before the migrations
        // below, so messages sent during startup are covered too.
        snackbarNotificationFallback.start()

        // All initialization in background thread to avoid blocking main thread
        // (ComposeMainActivity shows splash screen until initProgress.done)
        scope.launch {
            try {
                // Configure LeakCanary with Firebase reporting. Memory leaks are uploaded to Firebase
                // Crashlytics via FabricPrivacy.logException. Not on the main thread: reading the
                // DISABLE_LEAK_CANARY marker lists the AAPS directory through SAF, and on slow phones
                // that held up app start long enough for an ANR (4.0.0-rc1).
                configureLeakCanary(
                    isEnabled = { !config.isEnabled(ExternalOptions.DISABLE_LEAK_CANARY) },
                    fabricPrivacy = fabricPrivacy
                )

                config.updateInitProgress(getString(R.string.migrating_preferences))
                doMigrations()

                // Re-read the profiles now that doMigrations() has converted the ancient raw
                // SharedPreferences profile keys into the numbered ones. Without this the upgrade
                // would only be picked up on the next start, and the profile-to-JSON conversion
                // with it.
                //
                // This used to say the repository "already loaded during field injection". There is
                // no field injection any more - it is a lazy `metroGraphs` accessor, so whether it
                // was built before this line depends on what else happened to pull it in. Which is
                // exactly why the call stays: it costs a re-read when nothing was loaded, and it is
                // the difference between a right and a stale profile when something was.
                profileRepository.reset()

                // Defragment the DB while it is quiescent: plugins, loop, sync and UI all start
                // later, so the (memory heavy) VACUUM has the DB to itself. Runs at most monthly.
                vacuumDatabaseIfDue()

                // Register and initialize plugins
                config.updateInitProgress(getString(R.string.initializing_plugins))
                pluginStore.plugins = plugins
                // Wait for the plugins to actually start, not just to be marked enabled. initialize()
                // only schedules onStart, and everything below reads plugin state - the reconciler asks
                // for the active pump on the next line. Bounded for the same reason applyConfiguration
                // bounds it: a driver whose onStart will not settle must not hold up start up for ever.
                val started = configBuilder.initialize()
                if (withTimeoutOrNull(PLUGIN_START_WAIT) { started.joinAll() } == null)
                    aapsLogger.warn(LTag.CORE, "Plugins did not finish starting within $PLUGIN_START_WAIT")

                // Running-mode reconciler + expiry scheduler. Start after plugins are registered:
                // the reconciler's startup-drift check reads the active pump, which requires
                // pluginStore.plugins to be populated. Both internally gated by config.APS.
                runningModeReconciler.start()
                runningModeExpiryScheduler.start()
                // Fires a profile change at the exact end of a temporary ProfileSwitch (master-only),
                // removing the up-to-5-min KeepAlive latency. KeepAlive remains the backstop.
                profileSwitchScheduler.start()

                // Standalone automation runtime (no longer a plugin). Loads definitions on all
                // flavors; the processing loop + location service are master-only (gated internally).
                automationRuntime.start()
                // A reboot or a force stop removes the alarm of a running "time to eat" timer; set it again.
                // Master only (gated internally).
                eatReminder.restore()

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

    /**
     * The settings could not be opened ([AndroidStorage] says why), so AAPS does not start: no plugins, so no
     * loop, and nothing written to the database. The app runs on an empty store that lives in memory only,
     * which keeps its parts from crashing without writing over the unreadable file. A notification that
     * cannot be swiped away says so, and SettingsUnreadableActivity shows the way out.
     */
    private fun startWithoutSettings(failure: Exception, notes: List<String>) {
        GeneratedStringOwners.registerAll()
        // The user's answer about analytics and crash reports is in the settings that cannot be read, so it
        // is "no" now - in memory only, like everything else.
        sp.putBoolean(BooleanKey.MaintenanceEnableFabric.key, false)
        fabricPrivacyImpl.start()
        notes.forEach { aapsLogger.info(LTag.CORE, it) }
        aapsLogger.error(LTag.CORE, "The settings cannot be opened, so AAPS does not start", failure)
        SettingsUnreadableNotification.show(this, metroGraphs.iconsProvider.getNotificationIcon())
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
            val result = persistenceLayer.vacuumDatabase()
            // Advance the timestamp on every result here, and only here: a thrown failure (e.g. DB busy
            // because a persisted worker is running) is retried on a future launch instead of suppressed
            // for a month. OUT_OF_MEMORY is not one of those: the next launch would fail the same way, so
            // it waits a month too.
            preferences.put(LongNonKey.LastVacuumRun, dateUtil.now())
            when (result) {
                VacuumResult.DONE, VacuumResult.DONE_AFTER_TRIM -> {
                    aapsLogger.debug(LTag.CORE, "Startup VACUUM done: $result")
                    // Fleet overview of DB size / cleanup backlog / change-row volume across users.
                    fabricPrivacy.logCustom("db_vacuum_ok", mapOf("db_mb" to dbMb, "deletable" to info.deletableRows, "changes" to info.changeRows))
                    // How many databases only fit after the old APS results and device statuses were deleted.
                    if (result == VacuumResult.DONE_AFTER_TRIM) fabricPrivacy.logCustom("db_vacuum_trimmed", mapOf("db_mb" to dbMb))
                }

                VacuumResult.OUT_OF_MEMORY                      -> {
                    aapsLogger.error(LTag.CORE, "Startup VACUUM ran out of memory, even after trimming. Next try in 30 days.")
                    fabricPrivacy.logCustom("db_vacuum_out_of_memory", mapOf("db_mb" to dbMb))
                }
            }
        } catch (e: Throwable) {
            // Throwable, not just Exception: a JVM OutOfMemoryError here must not abort app init.
            aapsLogger.error(LTag.CORE, "Startup VACUUM failed", e)
            // Catchable failures (SQLiteException, OOM) → Crashlytics non-fatal for the overview.
            fabricPrivacy.logException(e)
        } finally {
            preferences.put(BooleanNonKey.VacuumInProgress, false)
        }
    }

    /**
     * Gives Android the language picked in AAPS as this app's language (Android 13 and newer). Android then shows
     * the whole app in it - every screen, the notifications, the widget - and keeps the default locale on it, so
     * numbers and dates are formatted in it too.
     *
     * AAPS used to move the default locale itself. Android sets the default locale back from the app's resources
     * on many configuration updates, often without calling [onConfigurationChanged], so a Czech AAPS on an English
     * phone showed "4.5" instead of "4,5" after its alarm screen had been shown.
     *
     * Android 12 has no app languages: there AAPS follows the phone's language, and the setting is hidden. Simple
     * mode follows the phone too. Nothing is sent when Android has this language already.
     */
    private fun applyAppLanguage() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val tag = appLanguageTag(preferences.get(StringKey.GeneralLanguage), preferences.get(BooleanKey.GeneralSimpleMode))
        val wanted = tag?.let { LocaleList.forLanguageTags(it) } ?: LocaleList.getEmptyLocaleList()
        val localeManager = getSystemService(LocaleManager::class.java)
        if (localeManager.applicationLocales == wanted) return
        aapsLogger.debug(LTag.CORE, "App language: ${tag ?: "the phone's"}")
        localeManager.applicationLocales = wanted
    }

    /** A change of the AAPS language or of Simple mode goes to Android. What follows is in [onConfigurationChanged]. */
    private fun followLanguage() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        appScope.launch {
            merge(preferences.observe(StringKey.GeneralLanguage), preferences.observe(BooleanKey.GeneralSimpleMode))
                .map { appLanguageTag(preferences.get(StringKey.GeneralLanguage), preferences.get(BooleanKey.GeneralSimpleMode)) }
                .distinctUntilChanged()
                .drop(1)
                .collect { applyAppLanguage() }
        }
    }

    /** The app's language as Android applies it, to notice a change. Null until the app has started. */
    private var appLocales: LocaleList? = null

    /**
     * The app's language changed: the one picked in AAPS ([applyAppLanguage]), or the phone's while AAPS follows it.
     * Android recreates the screens itself, but text built before - in the overview cache and in the view models -
     * is in the old language. So the screens are rebuilt through `UiRestart`, as iOS and the desktop do, and the home
     * screen widget draws again; it would otherwise wait up to a minute.
     *
     * Here and not where the language is picked: Android delivers the new language a moment later, and text read
     * before that would still come out in the old one.
     */
    override fun onConfigurationChanged(newConfig: ResourcesConfiguration) {
        super.onConfigurationChanged(newConfig)
        val previous = appLocales ?: return
        if (newConfig.locales == previous) return
        appLocales = newConfig.locales
        aapsLogger.debug(LTag.CORE, "App language changed to ${newConfig.locales.toLanguageTags()}")
        uiRestart.request()
        widgetUpdater.update("LanguageChanged")
    }

    private suspend fun doInit() {
        aapsLogger.debug("doInit")
        RxDogTag.install()
        setRxErrorHandler()
        followLanguage()

        var gitRemote: String? = config.REMOTE
        var commitHash: String? = BuildConfig.HEAD
        if (gitRemote?.contains("NoGitSystemAvailable") == true) {
            gitRemote = null
            commitHash = null
        }
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
                                note = rh.gs(InterfacesR.string.values_with_dash, rh.gs(CoreUiR.string.androidaps_start), "${Build.MANUFACTURER} ${Build.MODEL}"),
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
        // The worker runs the periodic pass. This adds the check on each new reading, which iOS and
        // desktop get from PeriodicMaintenance.start, so a low is not held back until the next pass.
        periodicMaintenance.startGlucoseAlarmTrigger(appScope)
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

    /** The build this is, in short form - "github:owner/repo". */
    private val gitRemoteShort: String
        get() = config.REMOTE.lowercase(Locale.getDefault())
            .replace("https://", "")
            .replace("http://", "")
            .replace(".git", "")
            .replace(".com/", ":")
            .replace(".org/", ":")
            .replace(".net/", ":")

    /**
     * Which build is running. All of it is known from BuildConfig before anything starts, so it is set as
     * early as collection is allowed - see the call in [onCreate].
     */
    private fun setBuildIdentityKeys() {
        FirebaseCrashlytics.getInstance().apply {
            setCustomKey("HEAD", BuildConfig.HEAD)
            setCustomKey("Version", config.VERSION_NAME)
            setCustomKey("BuildType", config.BUILD_TYPE)
            setCustomKey("BuildFlavor", config.FLAVOR)
            setCustomKey("Remote", gitRemoteShort)
            setCustomKey("Committed", config.COMMITTED)
        }
    }

    private suspend fun setUserStats() {
        if (!fabricPrivacy.fabricEnabled()) return
        val closedLoopEnabled = if (constraintChecker.isClosedLoopAllowed().value()) "CLOSED_LOOP_ENABLED" else "CLOSED_LOOP_DISABLED"
        fabricPrivacy.setUserProperty("Mode", config.APPLICATION_ID + "-" + closedLoopEnabled)
        fabricPrivacy.setUserProperty("Language", preferences.getIfExists(StringKey.GeneralLanguage) ?: Locale.getDefault().language)
        fabricPrivacy.setUserProperty("Version", config.VERSION_NAME)
        fabricPrivacy.setUserProperty("HEAD", BuildConfig.BUILDVERSION)
        fabricPrivacy.setUserProperty("Remote", gitRemoteShort)
        val hashes: List<String> = signatureVerifierPlugin.shortHashes()
        if (hashes.isNotEmpty()) fabricPrivacy.setUserProperty("Hash", hashes[0])
        activePlugin.activePumpInternal.let { fabricPrivacy.setUserProperty("Pump", it::class.java.simpleName) }
        if (!config.AAPSCLIENT && !config.PUMPCONTROL)
            activePlugin.activeAPS?.let { fabricPrivacy.setUserProperty("Aps", it::class.java.simpleName) }
        activePlugin.activeBgSource.let { fabricPrivacy.setUserProperty("BgSource", it::class.java.simpleName) }
        activePlugin.activeSensitivity.let { fabricPrivacy.setUserProperty("Sensitivity", it::class.java.simpleName) }
        // HEAD/Version/BuildType/BuildFlavor/Remote/Committed are set in setBuildIdentityKeys() during
        // onCreate. These two are not known that early.
        if (hashes.isNotEmpty()) FirebaseCrashlytics.getInstance().setCustomKey("Hash", hashes[0])
        FirebaseCrashlytics.getInstance().setCustomKey("Email", preferences.get(StringKey.MaintenanceIdentification))
    }

    private suspend fun passwordResetCheck() {
        val fh = fileListProvider.ensureExtraDirExists()?.findFile("PasswordReset")
        if (fh?.exists() == true) {
            config.updateInitProgress(getString(CoreUiR.string.waiting_for_pump))
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
        // AAPS directory selected, but the permission behind it is gone.
        //
        // The check above only ever looked at whether the URI string is there, so this case was
        // completely silent: the directory is still "selected", the folder and the old exports are
        // still on the phone, and the Local export button simply goes grey. Android drops a persisted
        // SAF grant on reinstall and on "clear storage", so it happens to people who did nothing
        // wrong. Found on a real phone where the last local backup was four months old and nobody
        // knew - and a user with no cloud set up has, at that point, no backup at all.
        else if (!fileListProvider.isDirectoryAccessGranted())
            notificationManager.post(
                id = NotificationId.AAPS_DIR_ACCESS_LOST,
                TextRef.AndroidRes(app.aaps.core.ui.R.string.aaps_directory_access_lost),
                actions = listOf(NotificationAction(TextRef.AndroidRes(R.string.select)) {}),
                validityCheck = { !fileListProvider.isDirectoryAccessGranted() }
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
        // Which insulin curve the old records were delivered with, for the database migration in
        // dataMigrations(). Read BEFORE the migrations, because the migration that deletes these dead
        // ConfigBuilder rows is one of them now. InsulinMigrationTest pins that order.
        legacyInsulin = insulinMigration.readLegacyInsulin(sp)

        // The preference migrations, in order, against this device's store.
        //
        // They live in :implementation and take the store to work on, because the settings IMPORT runs
        // exactly the same functions over the imported file. An old backup's names never reach this
        // store any more - PreferenceImportApplier drops what it cannot resolve - so a start-up only
        // pass can no longer see them, and the user loses their profiles, plugin selection, objectives
        // and loop mode. See PreferenceMigrations for what is in the list and what is deliberately not.
        PreferenceMigrations(aapsLogger, config, persistenceLayer, dateUtil, profileUtil).migrate(sp)
        // Those writes go below Preferences, so the cached flows have not seen them.
        preferences.reloadFromStore()

        // Seeds simple mode for an install that predates the key.
        //
        // NOT a migration and deliberately not in the list above: it invents a value rather than moving
        // one, and `simple_mode` feeds PreferencesImpl.calculatedDefaultValue, so a wrong guess changes
        // the effective value of a whole family of settings. Skipped on a client, which adopts the
        // value from the master via sync - a local put here would trigger a client→master round-trip
        // (modal) at startup. The master seeds and publishes; the client follows.
        if (!config.AAPSCLIENT && preferences.getIfExists(BooleanKey.GeneralSimpleMode) == null)
            preferences.put(BooleanKey.GeneralSimpleMode, !preferences.get(BooleanNonKey.GeneralSetupWizardProcessed))

        // Harvest the DIA each old profile was using, for the database migration in dataMigrations().
        //
        // NOT a key migration and deliberately not in PreferenceMigrations: it fills a field this class
        // reads much later. The migration writes the new `_name` key but leaves the raw one, exactly so
        // this can still pair a profile with its DIA - see profileDia, whose keys are dropped in
        // dataMigrations(), once the values they carry are safely in the insulin list.
        profileDia = insulinMigration.harvestProfileDia(sp)

        // Rebuilding them from the old keys is a migration and has already run above. This only fills
        // in the three factory presets for an install that has none.
        seedTempTargetPresets()
    }

    /**
     * Creates the three factory temp target presets for an install that has none.
     *
     * New-install bootstrap only. Rebuilding the presets from an older AAPS's keys is a migration and
     * lives in `PreferenceMigrations`; this half cannot, because it INVENTS values, and over an
     * imported file it would write factory presets on top of the ones the user actually has.
     *
     * Skipped on a client, which adopts the presets from the master over the sync channel - a local
     * put here would trigger a client-to-master round trip at startup.
     */
    private fun seedTempTargetPresets() {
        if (config.AAPSCLIENT) return
        val existing = preferences.get(StringNonKey.TempTargetPresets)
        if (existing != "[]" && existing.isNotEmpty()) return
        // Only when there is nothing to migrate from. PreferenceMigrations has already run and would
        // have written the document if the old keys were there.
        if (sp.contains("eatingsoon_target")) return

        val presets = listOf(
            TTPreset(
                id = "eatingsoon", reason = TT.Reason.EATING_SOON,
                targetValue = Constants.DEFAULT_TT_EATING_SOON_TARGET,
                duration = Constants.DEFAULT_TT_EATING_SOON_DURATION * 60L * 1000L, isDeletable = false
            ),
            TTPreset(
                id = "activity", reason = TT.Reason.ACTIVITY,
                targetValue = Constants.DEFAULT_TT_ACTIVITY_TARGET,
                duration = Constants.DEFAULT_TT_ACTIVITY_DURATION * 60L * 1000L, isDeletable = false
            ),
            TTPreset(
                id = "hypo", reason = TT.Reason.HYPOGLYCEMIA,
                targetValue = Constants.DEFAULT_TT_HYPO_TARGET,
                duration = Constants.DEFAULT_TT_HYPO_DURATION * 60L * 1000L, isDeletable = false
            )
        )
        preferences.put(StringNonKey.TempTargetPresets, presets.toJson())
        aapsLogger.debug(LTag.CORE, "Seeded default temp target presets")
    }

    private suspend fun dataMigrations() {
        // Migrate to database 33 (ICfg)
        //
        // Goal: legacy rows predate ICfg, so they carry the `insulinEndTime = -1` sentinel and their IOB
        // is computed from the clamped math floors in ICfg.iobCalcForTreatment — which under-counts IOB.
        // Stamping them makes the insulin curve a property of the record, as v33 intends.
        //
        // The value must be the insulin those records were actually delivered with, and InsulinMigration
        // owns that decision - see its KDoc for the order it asks in and why. The record's own insulin wins:
        // migration32to33 carries the v3 columns across, so for a profile switch the row already knows. The
        // legacy preferences answer the rows that have nothing, boluses above all - v3 never stamped one.
        //
        // The record, not profileFunction.getProfile(): this runs before app init completes, and no profile
        // is handed out until then. getRunningOrRequestedICfg() rejects an unusable iCfg, so a running row
        // that still carries the sentinel falls through instead of writing the sentinel back over itself.
        val plan = insulinMigration.plan(
            recordICfg = profileFunction.getRunningOrRequestedICfg(),
            legacy = legacyInsulin,
            harvest = profileDia,
            activeProfileName = persistenceLayer.getEffectiveProfileSwitchActiveAt(dateUtil.now())?.originalProfileName
        )
        // Puts it in the catalogue and gives back the stored copy, whose label the records are stamped with.
        // On a v3 install this REPLACES the default the first load invented, so the user does not come out
        // of the upgrade with an insulin they never had sitting in front of their own.
        //
        // NO_LEGACY_DATA is the exception, and a fresh install is why: nothing from v3, no profile yet and
        // no records to stamp all look the same from here. So that plan is used for the stamping below -
        // which then changes nothing - and the catalogue is left alone, because the default already in it
        // is just as good and a second invented insulin beside the first is not.
        val runningICfg =
            if (plan.source == InsulinMigrationPlan.Source.NO_LEGACY_DATA) plan.iCfg
            else localInsulinManager.seedFromMigration(plan.iCfg)

        // The DIA these keys carry is now in the insulin list, so the old copies can go. This is the
        // first point where losing them costs nothing - see profileDia for why they were not dropped
        // when they were read.
        profileDia.keysToRemove.forEach { sp.remove(it) }
        profileDia = ProfileDiaHarvest(emptyMap(), emptyList())

        val label = runningICfg.insulinLabel
        val end = runningICfg.insulinEndTime
        val peak = runningICfg.insulinPeakTime
        val conc = runningICfg.concentration

        config.updateInitProgress(rh.gs(R.string.migrating_profile_switches))
        val migratedPs = repository.bulkMigrateProfileSwitchInsulinConfig(label, end, peak, conc)

        config.updateInitProgress(rh.gs(R.string.migrating_effective_profile_switches))
        val migratedEps = repository.bulkMigrateEffectiveProfileSwitchInsulinConfig(label, end, peak, conc)

        config.updateInitProgress(rh.gs(R.string.migrating_boluses))
        val migratedBoluses = repository.bulkMigrateBolusInsulinConfig(label, end, peak, conc)

        val totalMigrated = migratedPs + migratedEps + migratedBoluses
        if (totalMigrated > 0) {
            // Part of the value was invented, and records really were stamped with it, so say so. The count
            // is what makes this quiet on an install with nothing to migrate - a fresh one above all, which
            // used to be told its "older records" had no insulin configuration. It is one-way: the sentinel
            // is consumed, so these records are not revisited, which is why the notification says that too.
            if (plan.isGuess) reportGuessedInsulinMigration(plan)
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
     * Tells the user that part of the insulin stamped onto their old records was invented.
     *
     * Both cases are permanent: the stamp consumes the v33 sentinel, so these records are never revisited
     * and the value is not corrected later. That is why this is a notification and not only a log line.
     *
     * [InsulinMigrationPlan.Source.GUESSED_DIA] is the one that used to be silent. The insulin itself is
     * right - the v3 plugin selection said so - but no DIA could be found for it, so the hard-limit maximum
     * stands in, which is 9 h for an adult. On an install with one local profile, or several that agree,
     * `ProfileDiaHarvest.diaFor` now answers and this does not fire at all.
     */
    private fun reportGuessedInsulinMigration(plan: InsulinMigrationPlan) {
        val label = plan.iCfg.insulinLabel.ifBlank { plan.iCfg.insulinNickname }
        aapsLogger.warn(LTag.CORE, "Migration to DB 33: ${plan.source}, stamping $label with ${plan.iCfg.dia} h DIA")
        val text =
            if (plan.source == InsulinMigrationPlan.Source.GUESSED_DIA) rh.gs(R.string.insulin_migration_default_dia_used, plan.iCfg.dia, label)
            else rh.gs(R.string.insulin_migration_default_used, label)
        notificationManager.post(
            id = NotificationId.INSULIN_MIGRATION_DEFAULT_USED,
            text,
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
                                // Plus on the maps is a shallow merge with the remote keys winning.
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
        uiInteraction.stopAlarm("onTerminate")
        super.onTerminate()
    }

    /**
     * Teaches the resolvers which module owns which string names.
     * A `TextRef.Named` carries an owner and a name, and both the Compose and the ResourceHelper
     * paths need a way to turn that into an `R.string` id. `:core:keys`, `:core:interfaces` and
     * `:core:ui` are resolved directly because `:core:ui` sits above them, but a plugin or pump
     * module sits ABOVE `:core:ui`, so it can only be reached from here - `:app` is the one place
     * that depends on all of them.
     * Without this the lookup answers null and the raw name is drawn: `virtualpump_settings`
     * instead of "Virtual pump settings".
     */
}
