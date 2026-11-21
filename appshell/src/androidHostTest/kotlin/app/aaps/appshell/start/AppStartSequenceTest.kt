package app.aaps.appshell.start

import app.aaps.core.interfaces.alerts.EatReminder
import app.aaps.core.interfaces.alerts.LocalAlertUtils
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.configuration.ConfigBuilder
import app.aaps.core.interfaces.db.DatabaseMaintenanceInfo
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.db.VacuumResult
import app.aaps.core.interfaces.insulin.InsulinManager
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.notifications.NotificationId
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileRepository
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventAppInitialized
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.HardLimits
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.BooleanNonKey
import app.aaps.core.keys.LongNonKey
import app.aaps.core.keys.StringKey
import app.aaps.core.keys.StringNonKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.implementation.maintenance.migration.FileKeyValueStore
import app.aaps.implementation.notifications.SnackbarNotificationFallback
import app.aaps.implementation.plugin.PluginStore
import app.aaps.implementation.profile.ProfileSwitchScheduler
import app.aaps.plugins.aps.loop.runningMode.RunningModeExpiryScheduler
import app.aaps.plugins.aps.loop.runningMode.RunningModeReconciler
import app.aaps.plugins.automation.AutomationRuntime
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mockingDetails
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import kotlin.time.Duration.Companion.seconds

class AppStartSequenceTest {

    private val now = 1_800_000_000_000L
    private val day = 24 * 3600 * 1000L

    /** Text is not what these tests check; every string is the same placeholder. */
    private val rh = object : TextResolver {
        override fun gs(ref: TextRef): String = "text"
        override fun gs(ref: TextRef, vararg args: Any?): String = "text"
        override fun gsNotLocalised(ref: TextRef): String = "text"
    }

    private val aapsLogger: AAPSLogger = mock()
    private val config: Config = mock()
    private val preferences: Preferences = mock()
    private var store = FileKeyValueStore()
    private val persistenceLayer: PersistenceLayer = mock()
    private val dateUtil: DateUtil = mock()
    private val profileUtil: ProfileUtil = mock()
    private val hardLimits: HardLimits = mock()
    private val fabricPrivacy: FabricPrivacy = mock()
    private val notificationManager: NotificationManager = mock()
    private val snackbarNotificationFallback: SnackbarNotificationFallback = mock()
    private val profileRepository: ProfileRepository = mock()
    private val profileFunction: ProfileFunction = mock()
    private val insulinManager: InsulinManager = mock()
    private val pluginStore: PluginStore = mock()
    private val configBuilder: ConfigBuilder = mock()
    private val runningModeReconciler: RunningModeReconciler = mock()
    private val runningModeExpiryScheduler: RunningModeExpiryScheduler = mock()
    private val profileSwitchScheduler: ProfileSwitchScheduler = mock()
    private val automationRuntime: AutomationRuntime = mock()
    private val eatReminder: EatReminder = mock()
    private val localAlertUtils: LocalAlertUtils = mock()
    private val rxBus: RxBus = mock()
    private val platform: AppStartPlatform = mock()
    private val plugins: List<PluginBase> = listOf(mock(), mock())

    @BeforeEach fun setUp() {
        whenever(dateUtil.now()).thenReturn(now)
        whenever(hardLimits.diaRange()).thenReturn(5.0..9.0)
        whenever(configBuilder.initialize()).thenReturn(emptyList())
        whenever(config.VERSION_NAME).thenReturn("4.0.0")
        whenever(config.REMOTE).thenReturn("https://github.com/nightscout/AndroidAPS.git")
        whenever(config.HEAD).thenReturn("c52a27f035")
        whenever(platform.versionCode).thenReturn(1234)
        whenever(platform.deviceName).thenReturn("Google Pixel 8")
        whenever(preferences.get(StringNonKey.TempTargetPresets)).thenReturn("[]")
        whenever(preferences.get(StringKey.MaintenanceIdentification)).thenReturn("me")
        whenever(preferences.get(StringKey.ProtectionMasterPassword)).thenReturn("hash")
        // The vacuum ran yesterday, so it is not due; the vacuum tests change this.
        whenever(preferences.get(LongNonKey.LastVacuumRun)).thenReturn(now - day)
        runBlocking {
            whenever(profileRepository.reset()).thenReturn(Result.success(Unit))
            whenever(persistenceLayer.migrateProfileSwitchInsulinConfig(any(), any(), any(), any())).thenReturn(0)
            whenever(persistenceLayer.migrateEffectiveProfileSwitchInsulinConfig(any(), any(), any(), any())).thenReturn(0)
            whenever(persistenceLayer.migrateBolusInsulinConfig(any(), any(), any(), any())).thenReturn(0)
        }
        whenever(insulinManager.seedFromMigration(any())).thenAnswer { it.arguments[0] }
    }

    private fun TestScope.sequence() = AppStartSequence(
        aapsLogger, config, preferences, store, persistenceLayer, dateUtil, profileUtil, hardLimits, rh, fabricPrivacy,
        notificationManager, snackbarNotificationFallback, profileRepository, profileFunction, insulinManager, pluginStore,
        configBuilder, runningModeReconciler, runningModeExpiryScheduler, profileSwitchScheduler, automationRuntime,
        eatReminder, localAlertUtils, rxBus, backgroundScope
    )

    private fun postedNotifications(): List<NotificationId> =
        mockingDetails(notificationManager).invocations.filter { it.method.name == "post" }.map { it.arguments[0] as NotificationId }

    @Test fun `the start runs its steps in the order that is safe`() = runTest {
        sequence().run(plugins, platform)

        val order = inOrder(
            snackbarNotificationFallback, preferences, profileRepository, pluginStore, configBuilder, runningModeReconciler,
            runningModeExpiryScheduler, profileSwitchScheduler, automationRuntime, eatReminder, persistenceLayer, platform,
            localAlertUtils, config, rxBus
        )
        order.verify(snackbarNotificationFallback).start()
        // The migrations before anything reads the settings...
        order.verify(preferences).reloadFromStore()
        order.verify(profileRepository).reset()
        // ...then the plugins...
        order.verify(pluginStore).plugins = plugins
        order.verify(configBuilder).initialize()
        // ...and only then what reads plugin state.
        order.verify(runningModeReconciler).start()
        order.verify(runningModeExpiryScheduler).start()
        order.verify(profileSwitchScheduler).start()
        order.verify(automationRuntime).start()
        order.verify(eatReminder).restore()
        order.verify(persistenceLayer).migrateProfileSwitchInsulinConfig(any(), any(), any(), any())
        order.verify(platform).startServices()
        // The pre-snooze before the keep-alive, whose first check can run at once.
        order.verify(localAlertUtils).preSnoozeAlarms()
        order.verify(platform).startKeepAlive()
        order.verify(platform).beforeReady()
        order.verify(config).initCompleted()
        order.verify(rxBus).send(any<EventAppInitialized>())
    }

    @Test fun `a start that fails says so and never says it is ready`() = runTest {
        whenever(configBuilder.initialize()).thenThrow(IllegalStateException("No pump selected"))

        sequence().run(plugins, platform)

        verify(config).initFailed("No pump selected")
        verify(config, never()).initCompleted()
        verify(runningModeReconciler, never()).start()
        verify(platform, never()).startKeepAlive()
    }

    @Test fun `a second start does nothing`() = runTest {
        val sequence = sequence()

        sequence.run(plugins, platform)
        sequence.run(plugins, platform)

        verify(configBuilder, times(1)).initialize()
        verify(config, times(1)).initCompleted()
    }

    @Test fun `a new master install gets simple mode and the factory temp targets`() = runTest {
        whenever(preferences.getIfExists(BooleanKey.GeneralSimpleMode)).thenReturn(null)
        whenever(preferences.get(BooleanNonKey.GeneralSetupWizardProcessed)).thenReturn(false)

        sequence().run(plugins, platform)

        verify(preferences).put(BooleanKey.GeneralSimpleMode, true)
        verify(preferences).put(eq(StringNonKey.TempTargetPresets), any<String>())
    }

    @Test fun `a client takes simple mode and the temp targets from the master`() = runTest {
        whenever(config.AAPSCLIENT).thenReturn(true)
        whenever(preferences.getIfExists(BooleanKey.GeneralSimpleMode)).thenReturn(null)

        sequence().run(plugins, platform)

        verify(preferences, never()).put(eq(BooleanKey.GeneralSimpleMode), any<Boolean>())
        verify(preferences, never()).put(eq(StringNonKey.TempTargetPresets), any<String>())
    }

    @Test fun `the temp targets of an older install are left for the migration`() = runTest {
        store = FileKeyValueStore(mapOf("eatingsoon_target" to "90"))

        sequence().run(plugins, platform)

        verify(preferences, never()).put(eq(StringNonKey.TempTargetPresets), any<String>())
    }

    @Test fun `the vacuum runs at most once a month`() = runTest {
        whenever(preferences.get(LongNonKey.LastVacuumRun)).thenReturn(now - 29 * day)

        sequence().vacuumDatabaseIfDue()

        verify(persistenceLayer, never()).vacuumDatabase()
    }

    @Test fun `a vacuum that ended the last start is not tried again for a month`() = runTest {
        whenever(preferences.get(LongNonKey.LastVacuumRun)).thenReturn(now - 31 * day)
        whenever(preferences.get(BooleanNonKey.VacuumInProgress)).thenReturn(true)

        sequence().vacuumDatabaseIfDue()

        verify(persistenceLayer, never()).vacuumDatabase()
        verify(preferences).put(BooleanNonKey.VacuumInProgress, false)
        verify(preferences).put(LongNonKey.LastVacuumRun, now)
        verify(fabricPrivacy).logCustom("db_vacuum_crash_recovered")
    }

    @Test fun `the vacuum waits for free space of twice the database`() = runTest {
        whenever(preferences.get(LongNonKey.LastVacuumRun)).thenReturn(now - 31 * day)
        whenever(persistenceLayer.databaseMaintenanceInfo(any())).thenReturn(maintenanceInfo(dbBytes = 100L * MB, freeBytes = 150L * MB))

        sequence().vacuumDatabaseIfDue()

        verify(persistenceLayer, never()).vacuumDatabase()
        // Not marked as done, so the next start tries again.
        verify(preferences, never()).put(eq(LongNonKey.LastVacuumRun), any<Long>())
    }

    @Test fun `a done vacuum sets the time and clears the crash flag`() = runTest {
        whenever(preferences.get(LongNonKey.LastVacuumRun)).thenReturn(now - 31 * day)
        whenever(persistenceLayer.databaseMaintenanceInfo(any())).thenReturn(maintenanceInfo(dbBytes = 100L * MB, freeBytes = 500L * MB))
        whenever(persistenceLayer.vacuumDatabase()).thenReturn(VacuumResult.DONE)

        sequence().vacuumDatabaseIfDue()

        verify(persistenceLayer).vacuumDatabase()
        verify(preferences).put(LongNonKey.LastVacuumRun, now)
        verify(preferences).put(BooleanNonKey.VacuumInProgress, false)
        // The flag went to disk before the vacuum, so a crash in it is seen at the next start.
        assertThat(store.getBoolean(BooleanNonKey.VacuumInProgress.key, false)).isTrue()
    }

    @Test fun `a failed vacuum does not stop the start`() = runTest {
        whenever(preferences.get(LongNonKey.LastVacuumRun)).thenReturn(now - 31 * day)
        whenever(persistenceLayer.databaseMaintenanceInfo(any())).thenReturn(maintenanceInfo(dbBytes = 100L * MB, freeBytes = 500L * MB))
        whenever(persistenceLayer.vacuumDatabase()).thenThrow(IllegalStateException("database is locked"))

        sequence().run(plugins, platform)

        verify(preferences).put(BooleanNonKey.VacuumInProgress, false)
        verify(config).initCompleted()
    }

    @Test fun `an install with nothing to migrate gets no notice`() = runTest {
        sequence().run(plugins, platform)

        assertThat(postedNotifications()).doesNotContain(NotificationId.INSULIN_MIGRATION_DEFAULT_USED)
        verify(persistenceLayer, never()).insertPumpTherapyEventIfNewByTimestamp(any(), any(), any(), any(), anyOrNull(), any())
    }

    @Test fun `old records that got an insulin without a known DIA are told to the user`() = runTest {
        // A v3 install that had Lyumjev selected, and no profile DIA to be found.
        store = FileKeyValueStore(mapOf("ConfigBuilder_Enabled_INSULIN_InsulinLyumjevPlugin" to true))
        whenever(persistenceLayer.migrateBolusInsulinConfig(any(), any(), any(), any())).thenReturn(3)

        sequence().run(plugins, platform)

        assertThat(postedNotifications()).contains(NotificationId.INSULIN_MIGRATION_DEFAULT_USED)
        verify(insulinManager).seedFromMigration(any())
        verify(profileFunction).invalidateCache()
    }

    @Test fun `the version record waits 10 s and leaves out the git data of a build without git`() = runTest {
        whenever(config.REMOTE).thenReturn("NoGitSystemAvailable")

        sequence().run(plugins, platform)
        verify(persistenceLayer, never()).insertVersionChangeIfChanged(any(), any(), anyOrNull(), anyOrNull())

        advanceTimeBy(11.seconds)
        verify(persistenceLayer).insertVersionChangeIfChanged("4.0.0", 1234, null, null)
    }

    @Test fun `the start note is written only when the user wants it`() = runTest {
        whenever(preferences.get(BooleanKey.NsClientLogAppStart)).thenReturn(true)

        sequence().run(plugins, platform)
        advanceTimeBy(11.seconds)

        verify(persistenceLayer).insertVersionChangeIfChanged("4.0.0", 1234, "https://github.com/nightscout/AndroidAPS.git", "c52a27f035")
        verify(persistenceLayer).insertPumpTherapyEventIfNewByTimestamp(any(), any(), any(), any(), anyOrNull(), any())
    }

    @Test fun `a missing master password is shown and an identification only in a dev build`() = runTest {
        whenever(preferences.get(StringKey.ProtectionMasterPassword)).thenReturn("")
        whenever(preferences.get(StringKey.MaintenanceIdentification)).thenReturn("")

        sequence().run(plugins, platform)

        assertThat(postedNotifications()).contains(NotificationId.MASTER_PASSWORD_NOT_SET)
        assertThat(postedNotifications()).doesNotContain(NotificationId.IDENTIFICATION_NOT_SET)
    }

    private fun maintenanceInfo(dbBytes: Long, freeBytes: Long) =
        DatabaseMaintenanceInfo(dbSizeBytes = dbBytes, availableBytes = freeBytes, totalRows = 0, deletableRows = 0, changeRows = 0, report = "report")

    private companion object {

        const val MB = 1_048_576L
    }
}
