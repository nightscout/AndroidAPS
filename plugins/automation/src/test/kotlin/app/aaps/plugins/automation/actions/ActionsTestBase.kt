package app.aaps.plugins.automation.actions

import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.autotune.Autotune
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.iob.GlucoseStatusProvider
import app.aaps.core.interfaces.maintenance.ImportExportPrefs
import app.aaps.core.interfaces.protection.ExportPasswordDataStore
import app.aaps.core.interfaces.scenes.SceneAutomationApi
import app.aaps.core.interfaces.scenes.SceneIconResolver
import app.aaps.core.interfaces.smsCommunicator.SmsCommunicator
import app.aaps.core.interfaces.alerts.ReminderScheduler
import app.aaps.shared.tests.TestBaseWithProfile
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mock
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

open class
ActionsTestBase : TestBaseWithProfile() {

    @Mock lateinit var smsCommunicator: SmsCommunicator
    @Mock lateinit var loop: Loop
    @Mock lateinit var persistenceLayer: PersistenceLayer
    @Mock lateinit var sceneApi: SceneAutomationApi
    @Mock lateinit var sceneIconResolver: SceneIconResolver
    @Mock lateinit var glucoseStatusProvider: GlucoseStatusProvider
    @Mock lateinit var autotunePlugin: Autotune
    @Mock lateinit var importExportPrefs: ImportExportPrefs
    @Mock lateinit var exportPasswordDataStore: ExportPasswordDataStore

    /**
     * Actions take their dependencies through the constructor now, so most tests build the action
     * they exercise directly. This is here for the few that go through the factory, i.e. the ones
     * testing [ActionFactory.instantiate].
     */
    val triggerDeps: app.aaps.plugins.automation.triggers.TriggerDeps by lazy {
        app.aaps.plugins.automation.triggers.TriggerDeps(
            aapsLogger, rxBus, rh, profileFunction, profileUtil, preferences, mock(), persistenceLayer,
            activePlugin, mock(), glucoseStatusProvider, dateUtil
        ) { mock<app.aaps.plugins.automation.triggers.TriggerFactory>() }
    }

    val actionFactory: ActionFactory by lazy {
        ActionFactory(
            triggerDeps, aapsLogger, rh, pumpEnactResultProvider, rxBus, context, dateUtil, mock<ReminderScheduler>(),
            config, persistenceLayer, profileFunction, profileRepository, profileUtil, glucoseStatusProvider,
            notificationManager, activePlugin, preferences, sceneApi, sceneIconResolver, smsCommunicator,
            autotunePlugin, importExportPrefs, exportPasswordDataStore
        )
    }

    @BeforeEach
    fun prepareActionMocks() {
        whenever(profileFunction.getUnits()).thenReturn(GlucoseUnit.MGDL)
        runBlocking {
            whenever(profileFunction.getProfile()).thenReturn(effectiveProfile)
            whenever(loop.handleRunningModeChange(anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyInt(), anyOrNull())).thenReturn(true)
        }

        whenever(rh.gs(app.aaps.core.ui.R.string.ok)).thenReturn("OK")
        whenever(rh.gs(app.aaps.core.ui.R.string.error)).thenReturn("Error")
    }
}
