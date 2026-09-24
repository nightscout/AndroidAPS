package app.aaps.plugins.automation.actions

import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.interfaces.alerts.ReminderScheduler
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.autotune.Autotune
import app.aaps.core.interfaces.configuration.ConfigBuilder
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.iob.GlucoseStatusProvider
import app.aaps.core.interfaces.maintenance.ImportExportPrefs
import app.aaps.core.interfaces.protection.ExportPasswordDataStore
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.interfaces.scenes.SceneAutomationApi
import app.aaps.core.interfaces.scenes.SceneIconResolver
import app.aaps.core.interfaces.smsCommunicator.SmsCommunicator
import app.aaps.implementation.pump.PumpEnactResultObject
import app.aaps.plugins.automation.AutomationStringsValues
import app.aaps.shared.tests.TestBaseWithProfile
import app.aaps.shared.tests.generatedTextResolver
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
    @Mock lateinit var configBuilder: ConfigBuilder

    /**
     * Real English for every name and description an action renders, so the expected text is what the
     * user reads. `:shared:tests` cannot see this module, so the generated map is handed over here.
     */
    val text: TextResolver = generatedTextResolver("automation" to AutomationStringsValues::textOf)

    /**
     * Actions take their dependencies through the constructor now, so most tests build the action
     * they exercise directly. This is here for the few that go through the factory, i.e. the ones
     * testing [ActionFactory.instantiate].
     */
    val triggerDeps: app.aaps.plugins.automation.triggers.TriggerDeps by lazy {
        app.aaps.plugins.automation.triggers.TriggerDeps(
            aapsLogger, rxBus, text, profileFunction, profileUtil, preferences, mock(), persistenceLayer,
            activePlugin, mock(), glucoseStatusProvider, dateUtil
        )
    }

    val actionFactory: ActionFactory by lazy {
        ActionFactory(
            triggerDeps, aapsLogger, text, { pumpEnactResultProvider() }, rxBus, dateUtil, mock<ReminderScheduler>(),
            config, persistenceLayer, profileFunction, profileRepository, profileUtil, glucoseStatusProvider,
            notificationManager, activePlugin, preferences, sceneApi, sceneIconResolver, smsCommunicator,
            autotunePlugin, importExportPrefs, exportPasswordDataStore, configBuilder
        )
    }

    @BeforeEach
    fun prepareActionMocks() {
        // An action reports its outcome through PumpEnactResult, which resolves the comment itself. The
        // base builds that with the mocked `rh`, whose real default method answers a Named ref with its
        // own NAME - so a comment assert read "alreadyset" instead of "Already set". Same resolver as the
        // actions, so the comment is real English too.
        pumpEnactResultProvider = { PumpEnactResultObject(text) }
        whenever(profileFunction.getUnits()).thenReturn(GlucoseUnit.MGDL)
        runBlocking {
            whenever(profileFunction.getProfile()).thenReturn(effectiveProfile)
            whenever(loop.handleRunningModeChange(anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyInt(), anyOrNull())).thenReturn(true)
        }

    }
}
