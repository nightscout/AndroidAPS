package app.aaps.plugins.automation

import android.content.Context
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.constraints.ConstraintsChecker
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
import app.aaps.plugins.automation.services.LocationServiceHelper
import app.aaps.plugins.automation.triggers.Trigger
import app.aaps.shared.impl.utils.DateUtilImpl
import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class CarbTimerImplTest : TestBase() {

    @Mock lateinit var rh: ResourceHelper
    @Mock lateinit var context: Context
    @Mock lateinit var fabricPrivacy: FabricPrivacy
    @Mock lateinit var loop: Loop
    @Mock lateinit var constraintChecker: ConstraintsChecker
    @Mock lateinit var config: Config
    @Mock lateinit var locationServiceHelper: LocationServiceHelper
    @Mock lateinit var activePlugin: ActivePlugin
    @Mock lateinit var profileFunction: ProfileFunction
    @Mock lateinit var profileRepository: ProfileRepository
    @Mock lateinit var preferences: Preferences
    @Mock lateinit var receiverStatusStore: ReceiverStatusStore
    @Mock lateinit var uel: UserEntryLogger
    @Mock lateinit var sceneApi: SceneAutomationApi

    private val injector = HasAndroidInjector {
        AndroidInjector {
            if (it is Trigger) {
                it.profileFunction = profileFunction
                it.rh = rh
            }
        }
    }
    private lateinit var dateUtil: DateUtil
    private lateinit var timerUtil: TimerUtil

    private lateinit var automationRuntime: AutomationRuntime

    @BeforeEach fun init() {
        whenever(rh.gs(anyInt())).thenReturn("")
        whenever(profileFunction.getUnits()).thenReturn(GlucoseUnit.MGDL)
        dateUtil = DateUtilImpl(context)
        timerUtil = TimerUtil(context, rh, rxBus, dateUtil)
        automationRuntime = AutomationRuntime(
            injector, aapsLogger, rh, preferences, context, fabricPrivacy, loop, rxBus, constraintChecker, aapsSchedulers, config, locationServiceHelper, dateUtil, activePlugin, timerUtil, receiverStatusStore, uel, profileRepository, sceneApi
        )
    }

    @Test fun doTest() {
        assertThat(automationRuntime.size()).isEqualTo(0)
        automationRuntime.scheduleAutomationEventEatReminder()
        assertThat(automationRuntime.size()).isEqualTo(1)
        automationRuntime.removeAutomationEventEatReminder()
        assertThat(automationRuntime.size()).isEqualTo(0)

        automationRuntime.scheduleTimeToEatReminder(1)
        // Reminder now goes via AlarmManager (background-safe), NOT the Clock app's startActivity (background-blocked).
        verify(context, never()).startActivity(any())
        verify(context, times(1)).getSystemService(Context.ALARM_SERVICE)
    }
}
