package info.nightscout.automation

import android.content.Context
import app.aaps.core.main.utils.fabric.FabricPrivacy
import app.aaps.interfaces.aps.Loop
import app.aaps.interfaces.configuration.Config
import app.aaps.interfaces.constraints.ConstraintsChecker
import app.aaps.interfaces.db.GlucoseUnit
import app.aaps.interfaces.plugin.ActivePlugin
import app.aaps.interfaces.profile.ProfileFunction
import app.aaps.interfaces.resources.ResourceHelper
import app.aaps.interfaces.sharedPreferences.SP
import app.aaps.interfaces.utils.DateUtil
import app.aaps.shared.impl.utils.DateUtilImpl
import app.aaps.shared.tests.TestBase
import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.automation.services.LocationServiceHelper
import info.nightscout.automation.triggers.Trigger
import info.nightscout.automation.ui.TimerUtil
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mock
import org.mockito.Mockito

class CarbTimerImplTest : TestBase() {

    @Mock lateinit var rh: ResourceHelper
    @Mock lateinit var context: Context
    @Mock lateinit var sp: SP
    @Mock lateinit var fabricPrivacy: FabricPrivacy
    @Mock lateinit var loop: Loop
    @Mock lateinit var constraintChecker: ConstraintsChecker
    @Mock lateinit var config: Config
    @Mock lateinit var locationServiceHelper: LocationServiceHelper
    @Mock lateinit var activePlugin: ActivePlugin
    @Mock lateinit var profileFunction: ProfileFunction

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

    private lateinit var automationPlugin: AutomationPlugin

    @BeforeEach
    fun init() {
        Mockito.`when`(rh.gs(anyInt())).thenReturn("")
        Mockito.`when`(profileFunction.getUnits()).thenReturn(GlucoseUnit.MGDL)
        dateUtil = DateUtilImpl(context)
        timerUtil = TimerUtil(context)
        automationPlugin = AutomationPlugin(
            injector, rh, context, sp, fabricPrivacy, loop, rxBus, constraintChecker, aapsLogger, aapsSchedulers, config, locationServiceHelper, dateUtil,
            activePlugin, timerUtil
        )
    }

    @Test
    fun doTest() {
        Assertions.assertEquals(0, automationPlugin.size())
        automationPlugin.scheduleAutomationEventEatReminder()
        Assertions.assertEquals(1, automationPlugin.size())
        automationPlugin.removeAutomationEventEatReminder()
        Assertions.assertEquals(0, automationPlugin.size())

        automationPlugin.scheduleTimeToEatReminder(1)
        Mockito.verify(context, Mockito.times(1)).startActivity(any())
    }
}