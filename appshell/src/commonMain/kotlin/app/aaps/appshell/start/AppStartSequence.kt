package app.aaps.appshell.start

import app.aaps.core.data.configuration.Constants
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.TE
import app.aaps.core.data.model.TT
import app.aaps.core.data.model.TTPreset
import app.aaps.core.data.time.T
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.InterfacesStrings
import app.aaps.core.interfaces.alerts.EatReminder
import app.aaps.core.interfaces.alerts.LocalAlertUtils
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.configuration.ConfigBuilder
import app.aaps.core.interfaces.configuration.PLUGIN_SETTLE_WAIT
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.db.VacuumResult
import app.aaps.core.interfaces.insulin.InsulinManager
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.notifications.NotificationAction
import app.aaps.core.interfaces.notifications.NotificationId
import app.aaps.core.interfaces.notifications.NotificationLevel
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileRepository
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventAppInitialized
import app.aaps.core.interfaces.sharedPreferences.KeyValueStore
import app.aaps.core.interfaces.tempTargets.toJson
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.HardLimits
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.BooleanNonKey
import app.aaps.core.keys.LongNonKey
import app.aaps.core.keys.StringKey
import app.aaps.core.keys.StringNonKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.CoreUiStrings
import app.aaps.implementation.ImplementationStrings
import app.aaps.implementation.maintenance.migration.InsulinMigration
import app.aaps.implementation.maintenance.migration.InsulinMigrationPlan
import app.aaps.implementation.maintenance.migration.LegacyInsulin
import app.aaps.implementation.maintenance.migration.PreferenceMigrations
import app.aaps.implementation.maintenance.migration.ProfileDiaHarvest
import app.aaps.implementation.notifications.SnackbarNotificationFallback
import app.aaps.implementation.plugin.PluginStore
import app.aaps.implementation.profile.ProfileSwitchScheduler
import app.aaps.plugins.aps.loop.runningMode.RunningModeExpiryScheduler
import app.aaps.plugins.aps.loop.runningMode.RunningModeReconciler
import app.aaps.plugins.automation.AutomationRuntime
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

/**
 * What every app does when it starts, in one order, on every platform.
 *
 * `MainApp` used to do all of this itself, and each new platform copied the parts it knew about. The iOS
 * and the desktop shells missed some of them: the migrations, the database vacuum, and every start that
 * only a device driving the pump needs. A missed start is silent. The class exists and the screens work,
 * but the work never happens. So the order is written once, here, and a platform gives only what it alone
 * can do, through [AppStartPlatform].
 *
 * The order matters:
 * 1. the migrations, before anything reads the settings they change
 * 2. the database vacuum, while nothing else uses the database
 * 3. the plugins, and the wait for them, because everything after reads plugin state: the running mode
 *    reconciler asks for the active pump
 * 4. the starts that need the plugins: running mode, the end of a temporary profile switch, automation,
 *    the "time to eat" alarm
 * 5. the migration of old records, which needs the profile
 * 6. the platform's own services, the keep-alive, and only then "ready"
 *
 * Runs once, in the background. The app shows its splash screen until [Config.initCompleted].
 */
@SingleIn(AppScope::class)
@Inject
class AppStartSequence(
    private val aapsLogger: AAPSLogger,
    private val config: Config,
    private val preferences: Preferences,
    private val keyValueStore: KeyValueStore,
    private val persistenceLayer: PersistenceLayer,
    private val dateUtil: DateUtil,
    private val profileUtil: ProfileUtil,
    private val hardLimits: HardLimits,
    private val rh: TextResolver,
    private val fabricPrivacy: FabricPrivacy,
    private val notificationManager: NotificationManager,
    private val snackbarNotificationFallback: SnackbarNotificationFallback,
    private val profileRepository: ProfileRepository,
    private val profileFunction: ProfileFunction,
    private val insulinManager: InsulinManager,
    private val pluginStore: PluginStore,
    private val configBuilder: ConfigBuilder,
    private val runningModeReconciler: RunningModeReconciler,
    private val runningModeExpiryScheduler: RunningModeExpiryScheduler,
    private val profileSwitchScheduler: ProfileSwitchScheduler,
    private val automationRuntime: AutomationRuntime,
    private val eatReminder: EatReminder,
    private val localAlertUtils: LocalAlertUtils,
    private val rxBus: RxBus,
    // Plain CoroutineScope, not @ApplicationScope: that qualifier is javax and cannot appear in
    // commonMain. AppCoroutineBindings.unqualifiedAppScope binds the very same scope without it.
    private val appScope: CoroutineScope
) {

    private val insulinMigration get() = InsulinMigration(aapsLogger, hardLimits, rh)

    /** The v3 insulin plugin selection, read before the migrations delete its keys. Null when there was none. */
    private var legacyInsulin: LegacyInsulin? = null

    /**
     * The DIA each v3 profile was using, with the raw keys those values came from still in the old store.
     *
     * The keys used to be removed as they were read, which put the only copy of the DIA values in a field, in
     * memory. [migrateRecords] is the only consumer and runs much later, after the vacuum (which can take the
     * process down below the JVM, where no `catch` reaches) and after the plugins start. A death anywhere in
     * that gap lost the DIA values for good: the next start found no keys and stamped the old records with a
     * guess instead. That is wrong IOB on historical records, and it is one-way.
     *
     * So the keys are removed only once the value they carry is safely in the insulin list.
     */
    private var profileDia: ProfileDiaHarvest = ProfileDiaHarvest(emptyMap(), emptyList())

    private var started = false

    /**
     * Runs the whole start. [plugins] are this build's plugins, in the order they are shown.
     *
     * An error stops the start and is shown on the splash screen through [Config.initFailed].
     */
    suspend fun run(plugins: List<PluginBase>, platform: AppStartPlatform) {
        if (started) {
            aapsLogger.warn(LTag.CORE, "The app start was asked for a second time, ignoring")
            return
        }
        started = true
        try {
            // A snackbar sent while no screen shows one becomes a notification instead of being lost.
            // First, so that messages sent during the start are covered too.
            snackbarNotificationFallback.start()

            config.updateInitProgress(rh.gs(ImplementationStrings.migrating_preferences))
            migratePreferences()

            // Read the profiles again now that the migrations have converted the old raw profile keys into
            // the numbered ones. Without this the upgrade would only be seen on the next start. Whether the
            // repository was loaded before this line depends on what else happened to pull it in, so the
            // call stays: it costs a read when nothing was loaded, and it is the difference between a right
            // and an old profile when something was.
            profileRepository.reset()

            vacuumDatabaseIfDue()

            config.updateInitProgress(rh.gs(ImplementationStrings.initializing_plugins))
            startPlugins(plugins)
            startAfterPlugins()

            migrateRecords()

            config.updateInitProgress(rh.gs(CoreUiStrings.initializing))
            finish(platform)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            aapsLogger.error(LTag.CORE, "Fatal initialization error", e)
            config.initFailed(e.message ?: "Unknown error")
        }
    }

    /** The preference migrations, and the first values a new install needs. */
    internal suspend fun migratePreferences() {
        // Which insulin curve the old records were delivered with, for [migrateRecords]. Read BEFORE the
        // migrations, because the migration that deletes these old ConfigBuilder rows is one of them.
        // InsulinMigrationTest pins that order.
        legacyInsulin = insulinMigration.readLegacyInsulin(keyValueStore)

        // The preference migrations, in order, against this device's store. They take the store to work on,
        // because the settings IMPORT runs exactly the same functions over the imported file. See
        // PreferenceMigrations for what is in the list and what is not.
        PreferenceMigrations(aapsLogger, config, persistenceLayer, dateUtil, profileUtil).migrate(keyValueStore)
        // Those writes go below Preferences, so the cached flows have not seen them.
        preferences.reloadFromStore()

        // Simple mode for an install that is older than the key.
        //
        // NOT a migration and on purpose not in the list above: it invents a value rather than moving one,
        // and `simple_mode` feeds the calculated defaults, so a wrong guess changes the value of a whole
        // family of settings. Skipped on a client, which takes the value from the master by sync - a local
        // put here would start a client to master round trip at start.
        if (!config.AAPSCLIENT && preferences.getIfExists(BooleanKey.GeneralSimpleMode) == null)
            preferences.put(BooleanKey.GeneralSimpleMode, !preferences.get(BooleanNonKey.GeneralSetupWizardProcessed))

        // The DIA each old profile was using, for [migrateRecords]. NOT a key migration and on purpose not in
        // PreferenceMigrations: it fills a field read much later. The migration writes the new `_name` key
        // but leaves the raw one, exactly so this can still pair a profile with its DIA.
        profileDia = insulinMigration.harvestProfileDia(keyValueStore)

        seedTempTargetPresets()
    }

    /**
     * Creates the three factory temp target presets for an install that has none.
     *
     * For a new install only. Rebuilding the presets from the keys of an older AAPS is a migration and lives in
     * `PreferenceMigrations`. This half cannot: it INVENTS values, and over an imported file it would write
     * factory presets over the ones the user really has.
     *
     * Skipped on a client, which takes the presets from the master by sync.
     */
    private fun seedTempTargetPresets() {
        if (config.AAPSCLIENT) return
        val existing = preferences.get(StringNonKey.TempTargetPresets)
        if (existing != "[]" && existing.isNotEmpty()) return
        // Only when there is nothing to migrate from. PreferenceMigrations has already run and would have
        // written the presets if the old keys were there.
        if (keyValueStore.contains("eatingsoon_target")) return

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

    /**
     * A full VACUUM, at most once a month. It defragments the database file and frees space, which brings
     * back the query speed that is lost after long use. It is heavy and needs much memory, so it runs only here,
     * while nothing else uses the database. That avoids the SQLITE_NOMEM crash seen when a VACUUM overlapped
     * other database work.
     */
    internal suspend fun vacuumDatabaseIfDue() {
        val lastRun = preferences.get(LongNonKey.LastVacuumRun)
        if (lastRun >= dateUtil.now() - T.days(30).msecs()) return

        // Crash loop guard. A VACUUM can die below the JVM (a native SQLite abort, out of memory) in a way no
        // catch can stop: the process just ends, with no log. If that happened last time, the flag below is
        // still set, because the finally never ran. Then skip the VACUUM so the app can start, and wait a
        // month instead of crashing on every start.
        if (preferences.get(BooleanNonKey.VacuumInProgress)) {
            aapsLogger.error(LTag.CORE, "Previous startup VACUUM did not finish (likely native crash) — skipping for 30 days")
            // How many users a crashing VACUUM hits.
            fabricPrivacy.logCustom("db_vacuum_crash_recovered")
            preferences.put(BooleanNonKey.VacuumInProgress, false)
            preferences.put(LongNonKey.LastVacuumRun, dateUtil.now())
            return
        }

        config.updateInitProgress(rh.gs(ImplementationStrings.optimizing_database))
        try {
            // Size and row counts before the VACUUM, so a crash report tells whether the database is the
            // problem. 6 * 31 days is the retention of the database cleanup, so "deletable" is the real backlog.
            val info = persistenceLayer.databaseMaintenanceInfo(retentionDays = 6L * 31)
            aapsLogger.info(LTag.CORE, "Startup DB maintenance, pre-VACUUM diagnostics:\n${info.report}")
            // Attaches the numbers to any exception logged below.
            fabricPrivacy.logMessage("Pre-VACUUM: ${info.report}")
            val dbMb = info.dbSizeBytes / BYTES_IN_MB
            // A VACUUM builds the whole database again in a copy, so it needs about the database size again in
            // free space. Skip it (and try at the next start) when there is not enough, so it does not fail
            // with SQLITE_FULL half way.
            if (info.availableBytes in 0 until info.dbSizeBytes * 2) {
                val freeMb = info.availableBytes / BYTES_IN_MB
                aapsLogger.warn(LTag.CORE, "Skipping startup VACUUM: free $freeMb MB < 2x DB $dbMb MB")
                fabricPrivacy.logCustom("db_vacuum_skip_space", mapOf("db_mb" to dbMb, "free_mb" to freeMb))
                return
            }
            // Write the flag to disk BEFORE the VACUUM: a plain put() may not reach the disk before an early
            // native abort.
            keyValueStore.edit(commit = true) { putBoolean(BooleanNonKey.VacuumInProgress.key, true) }
            val result = persistenceLayer.vacuumDatabase()
            // The time moves on for every result here, and only here. A thrown failure (the database is busy
            // because a stored job runs, for example) is tried again at a later start instead of waiting a month.
            // OUT_OF_MEMORY is different: the next start would fail the same way, so it waits a month too.
            preferences.put(LongNonKey.LastVacuumRun, dateUtil.now())
            when (result) {
                VacuumResult.DONE, VacuumResult.DONE_AFTER_TRIM -> {
                    aapsLogger.debug(LTag.CORE, "Startup VACUUM done: $result")
                    fabricPrivacy.logCustom("db_vacuum_ok", mapOf("db_mb" to dbMb, "deletable" to info.deletableRows, "changes" to info.changeRows))
                    // How many databases fit only after the old APS results and device statuses were deleted.
                    if (result == VacuumResult.DONE_AFTER_TRIM) fabricPrivacy.logCustom("db_vacuum_trimmed", mapOf("db_mb" to dbMb))
                }

                VacuumResult.OUT_OF_MEMORY                      -> {
                    aapsLogger.error(LTag.CORE, "Startup VACUUM ran out of memory, even after trimming. Next try in 30 days.")
                    fabricPrivacy.logCustom("db_vacuum_out_of_memory", mapOf("db_mb" to dbMb))
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            // Throwable, not only Exception: an OutOfMemoryError here must not stop the start.
            aapsLogger.error(LTag.CORE, "Startup VACUUM failed", e)
            fabricPrivacy.logException(e)
        } finally {
            preferences.put(BooleanNonKey.VacuumInProgress, false)
        }
    }

    /**
     * Registers the plugins and waits for them to really start, not only to be marked enabled.
     * [ConfigBuilder.initialize] only schedules `onStart`, and everything after this reads plugin state.
     * The wait is limited for the same reason `applyConfiguration` limits it: a driver whose `onStart` never
     * ends must not hold up the start for ever.
     */
    private suspend fun startPlugins(plugins: List<PluginBase>) {
        pluginStore.plugins = plugins
        val started = configBuilder.initialize()
        if (withTimeoutOrNull(PLUGIN_SETTLE_WAIT) { started.joinAll() } == null)
            aapsLogger.warn(LTag.CORE, "Plugins did not finish starting within $PLUGIN_SETTLE_WAIT")
        aapsLogger.debug(LTag.CORE, "Registered ${plugins.size} plugins")
    }

    /**
     * The starts that need the plugins. Each one decides itself whether it has work on this device: the
     * running mode and the profile switch ones only on the device that drives the pump, automation runs
     * rules only there but loads them everywhere.
     */
    private fun startAfterPlugins() {
        // The reconciler's start check reads the active pump, so it needs the plugins.
        runningModeReconciler.start()
        runningModeExpiryScheduler.start()
        // Changes the profile at the exact end of a temporary profile switch, instead of up to 5 minutes later
        // at the next keep-alive check. The keep-alive stays the backup.
        profileSwitchScheduler.start()
        // Loads the rules on every device and keeps them in step with the master. Runs them only where the
        // loop runs. After the plugins: an action's description can name the active pump.
        automationRuntime.start()
        // A restart removes the alarm of a running "time to eat" timer; set it again. Master only.
        eatReminder.restore()
    }

    /**
     * Gives the old records an insulin configuration (database 33).
     *
     * Old rows are older than ICfg, so they carry the `insulinEndTime = -1` mark, and their IOB comes from the
     * clamped lower limits of the math, which counts too little IOB. Stamping them makes the insulin curve a
     * property of the record.
     *
     * The value must be the insulin those records were really given with, and [InsulinMigration] owns that
     * decision. The record's own insulin wins. The old preferences answer for the rows that have nothing,
     * boluses above all.
     *
     * From the record, not from `profileFunction.getProfile()`: this runs before the start has finished, and
     * no profile is given out until then.
     */
    internal suspend fun migrateRecords() {
        val plan = insulinMigration.plan(
            recordICfg = profileFunction.getRunningOrRequestedICfg(),
            legacy = legacyInsulin,
            harvest = profileDia,
            activeProfileName = persistenceLayer.getEffectiveProfileSwitchActiveAt(dateUtil.now())?.originalProfileName
        )
        // Puts it in the list of insulins and gives back the stored copy, whose label the records get. On a v3
        // install this REPLACES the default the first load made up, so the user does not come out of the upgrade
        // with an insulin they never had.
        //
        // NO_LEGACY_DATA is the exception, because of a new install: nothing from v3, no profile yet and no
        // records to stamp all look the same from here. So that plan is used for the stamping below, which then
        // changes nothing, and the list is left alone.
        val runningICfg =
            if (plan.source == InsulinMigrationPlan.Source.NO_LEGACY_DATA) plan.iCfg
            else insulinManager.seedFromMigration(plan.iCfg)

        // The DIA these keys carry is in the insulin list now, so the old copies can go.
        profileDia.keysToRemove.forEach { keyValueStore.remove(it) }
        profileDia = ProfileDiaHarvest(emptyMap(), emptyList())

        val label = runningICfg.insulinLabel
        val end = runningICfg.insulinEndTime
        val peak = runningICfg.insulinPeakTime
        val concentration = runningICfg.concentration

        config.updateInitProgress(rh.gs(ImplementationStrings.migrating_profile_switches))
        val migratedPs = persistenceLayer.migrateProfileSwitchInsulinConfig(label, end, peak, concentration)

        config.updateInitProgress(rh.gs(ImplementationStrings.migrating_effective_profile_switches))
        val migratedEps = persistenceLayer.migrateEffectiveProfileSwitchInsulinConfig(label, end, peak, concentration)

        config.updateInitProgress(rh.gs(ImplementationStrings.migrating_boluses))
        val migratedBoluses = persistenceLayer.migrateBolusInsulinConfig(label, end, peak, concentration)

        val totalMigrated = migratedPs + migratedEps + migratedBoluses
        if (totalMigrated > 0) {
            // Part of the value was made up, and records really got it, so say so. The count keeps this quiet
            // on an install with nothing to migrate.
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
     * Tells the user that part of the insulin given to their old records was made up.
     *
     * Both cases are for good: the stamp uses up the database 33 mark, so these records are never looked at
     * again and the value is not corrected later. That is why this is a notification and not only a log line.
     */
    private fun reportGuessedInsulinMigration(plan: InsulinMigrationPlan) {
        val label = plan.iCfg.insulinLabel.ifBlank { plan.iCfg.insulinNickname }
        aapsLogger.warn(LTag.CORE, "Migration to DB 33: ${plan.source}, stamping $label with ${plan.iCfg.dia} h DIA")
        val text =
            if (plan.source == InsulinMigrationPlan.Source.GUESSED_DIA) rh.gs(ImplementationStrings.insulin_migration_default_dia_used, plan.iCfg.dia, label)
            else rh.gs(ImplementationStrings.insulin_migration_default_used, label)
        notificationManager.post(
            id = NotificationId.INSULIN_MIGRATION_DEFAULT_USED,
            text,
            level = NotificationLevel.IMPORTANT
        )
    }

    /** The end of the start: the platform's services, the records and notices of this start, the keep-alive. */
    private suspend fun finish(platform: AppStartPlatform) {
        platform.startServices()

        // Later, as Android has always done it: there the text needs the resources of the new language, which
        // come a moment after the start.
        appScope.launch {
            delay(RECORD_DELAY)
            recordVersion(platform)
            noteAppStart(platform)
        }

        postSetupNotifications()
        localAlertUtils.shortenSnoozeInterval()
        localAlertUtils.preSnoozeAlarms()
        platform.startKeepAlive()
        platform.beforeReady()

        config.initCompleted()
        // Starts the first IOB/COB calculation: IobCobCalculatorPlugin listens for it.
        rxBus.send(EventAppInitialized())
        aapsLogger.debug(LTag.CORE, "App start finished")
    }

    /** A version record when this build is not the one that ran last. */
    private suspend fun recordVersion(platform: AppStartPlatform) {
        val noGit = config.REMOTE.contains("NoGitSystemAvailable")
        persistenceLayer.insertVersionChangeIfChanged(
            versionName = config.VERSION_NAME,
            versionCode = platform.versionCode,
            gitRemote = if (noGit) null else config.REMOTE,
            commitHash = if (noGit) null else config.HEAD
        )
    }

    /** The "AAPS started" note in the treatments, when the user wants it. */
    private suspend fun noteAppStart(platform: AppStartPlatform) {
        if (!preferences.get(BooleanKey.NsClientLogAppStart)) return
        persistenceLayer.insertPumpTherapyEventIfNewByTimestamp(
            therapyEvent = TE(
                timestamp = dateUtil.now(),
                type = TE.Type.NOTE,
                note = rh.gs(InterfacesStrings.values_with_dash, rh.gs(CoreUiStrings.androidaps_start), platform.deviceName),
                glucoseUnit = GlucoseUnit.MGDL
            ),
            action = Action.START_AAPS,
            source = Sources.Aaps, note = "", listValues = listOf()
        )
    }

    /** The notices about settings the user still has to make. Each one goes away by itself once it is made. */
    private fun postSetupNotifications() {
        // Identification not set (dev builds only)
        if (config.isDev() && preferences.get(StringKey.MaintenanceIdentification).isBlank())
            notificationManager.post(
                id = NotificationId.IDENTIFICATION_NOT_SET,
                ImplementationStrings.identification_not_set,
                level = NotificationLevel.INFO,
                actions = listOf(NotificationAction(CoreUiStrings.set) {}),
                validityCheck = { config.isDev() && preferences.get(StringKey.MaintenanceIdentification).isBlank() }
            )
        if (preferences.get(StringKey.ProtectionMasterPassword) == "")
            notificationManager.post(
                id = NotificationId.MASTER_PASSWORD_NOT_SET,
                CoreUiStrings.master_password_not_set,
                level = NotificationLevel.NORMAL,
                actions = listOf(NotificationAction(CoreUiStrings.set) {}),
                validityCheck = { preferences.get(StringKey.ProtectionMasterPassword) == "" }
            )
    }

    private companion object {

        const val BYTES_IN_MB = 1_048_576L
        val RECORD_DELAY = 10.seconds
    }
}
