package app.aaps.plugins.automation

import app.aaps.core.keys.interfaces.TextRef
import android.content.Context
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.interfaces.alerts.ReminderScheduler
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.location.LocationServiceController
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileRepository
import app.aaps.core.interfaces.receivers.ReceiverStatusStore
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.scenes.SceneAutomationApi
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.plugins.automation.triggers.TriggerDeps
import app.aaps.plugins.automation.triggers.TriggerFactory
import app.aaps.shared.impl.utils.DateUtilImpl
import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mock
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class CarbTimerImplTest : TestBase() {

@Mock lateinit var actionFactory: app.aaps.plugins.automation.actions.ActionFactory
    private val triggerFactory: TriggerFactory by lazy {
        TriggerFactory(triggerDeps, mock(), sceneApi, receiverStatusStore)
    }
    // Real, not mocked: the runtime rebuilds triggers from JSON, and a mocked bundle would hand
    // nulls to element constructors that require them.
    private val triggerDeps: TriggerDeps by lazy {
        TriggerDeps(
            aapsLogger, rxBus, rh, profileFunction, mock(), preferences, mock(), mock(),
            activePlugin, mock(), mock(), dateUtil
        )
    }
    @Mock lateinit var rh: ResourceHelper
    @Mock lateinit var context: Context
    @Mock lateinit var fabricPrivacy: FabricPrivacy
    @Mock lateinit var loop: Loop
    @Mock lateinit var constraintChecker: ConstraintsChecker
    @Mock lateinit var config: Config
    @Mock lateinit var locationServiceController: LocationServiceController
    @Mock lateinit var activePlugin: ActivePlugin
    @Mock lateinit var profileFunction: ProfileFunction
    @Mock lateinit var profileRepository: ProfileRepository
    @Mock lateinit var preferences: Preferences
    @Mock lateinit var receiverStatusStore: ReceiverStatusStore
    @Mock lateinit var uel: UserEntryLogger
    @Mock lateinit var sceneApi: SceneAutomationApi

    private val eventFactory by lazy { AutomationEventFactory(aapsLogger, dateUtil, actionFactory, triggerFactory, triggerDeps) }
    private lateinit var dateUtil: DateUtil
    private lateinit var reminderScheduler: ReminderScheduler

    private lateinit var automationRuntime: AutomationRuntime

    @BeforeEach fun init() {
        doAnswer { "" }.whenever(rh).gs(any<TextRef>())
        whenever(profileFunction.getUnits()).thenReturn(GlucoseUnit.MGDL)
        dateUtil = DateUtilImpl(context)
        reminderScheduler = mock()
        automationRuntime = AutomationRuntime(
            eventFactory, aapsLogger, rh, preferences, loop, rxBus, constraintChecker, config, locationServiceController, dateUtil, activePlugin, reminderScheduler, actionFactory, triggerFactory, triggerDeps, receiverStatusStore, uel, profileRepository, sceneApi, mock()
        )
    }

    @Test fun doTest() {
        assertThat(automationRuntime.size()).isEqualTo(0)
        automationRuntime.scheduleAutomationEventEatReminder()
        assertThat(automationRuntime.size()).isEqualTo(1)
        automationRuntime.removeAutomationEventEatReminder()
        assertThat(automationRuntime.size()).isEqualTo(0)

        automationRuntime.scheduleTimeToEatReminder(1)
        // The reminder is handed to ReminderScheduler, NOT to the Clock app via startActivity, which
        // Android blocks from the background. How the reminder actually rings is the implementation's
        // business and it lives in :app now - this only pins that automation asks for it.
        verify(context, never()).startActivity(any())
        verify(reminderScheduler, times(1)).scheduleReminder(eq(1), anyOrNull())
    }
}
