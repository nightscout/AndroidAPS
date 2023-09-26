package app.aaps.plugins.automation

import android.content.Context
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.db.GlucoseUnit
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.main.utils.fabric.FabricPrivacy
import app.aaps.plugins.automation.services.LocationServiceHelper
import app.aaps.plugins.automation.triggers.Trigger
import app.aaps.plugins.automation.ui.TimerUtil
import app.aaps.shared.impl.utils.DateUtilImpl
import app.aaps.shared.tests.TestBase
import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mock
import org.mockito.Mockito

class BolusTimerImplTest : TestBase() {

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
    @Mock lateinit var timerUtil: TimerUtil

    private val injector = HasAndroidInjector {
        AndroidInjector {
            if (it is Trigger) {
                it.profileFunction = profileFunction
                it.rh = rh
            }
        }
    }
    private lateinit var dateUtil: DateUtil
    private lateinit var automationPlugin: AutomationPlugin

    @BeforeEach
    fun init() {
        Mockito.`when`(rh.gs(anyInt())).thenReturn("")
        Mockito.`when`(profileFunction.getUnits()).thenReturn(GlucoseUnit.MGDL)
        dateUtil = DateUtilImpl(context)
        automationPlugin = AutomationPlugin(
            injector, rh, context, sp, fabricPrivacy, loop, rxBus, constraintChecker, aapsLogger, aapsSchedulers, config, locationServiceHelper, dateUtil,
            activePlugin, timerUtil
        )
    }

    @Test
    fun doTest() {
        Assertions.assertEquals(0, automationPlugin.size())
        automationPlugin.scheduleAutomationEventBolusReminder()
        Assertions.assertEquals(1, automationPlugin.size())
        automationPlugin.removeAutomationEventBolusReminder()
        Assertions.assertEquals(0, automationPlugin.size())
    }
}