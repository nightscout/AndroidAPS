package info.nightscout.implementation

import android.content.Context
import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.TestBase
import info.nightscout.androidaps.interfaces.ActivePlugin
import info.nightscout.androidaps.interfaces.Config
import info.nightscout.androidaps.interfaces.Constraints
import info.nightscout.androidaps.interfaces.GlucoseUnit
import info.nightscout.androidaps.interfaces.Loop
import info.nightscout.androidaps.interfaces.ProfileFunction
import info.nightscout.androidaps.interfaces.ResourceHelper
import info.nightscout.androidaps.plugins.bus.RxBus
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.TimerUtil
import info.nightscout.automation.AutomationPlugin
import info.nightscout.automation.services.LocationServiceHelper
import info.nightscout.automation.triggers.Trigger
import info.nightscout.shared.sharedPreferences.SP
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.Mockito.anyInt
import org.mockito.Mockito.`when`

class CarbTimerImplTest : TestBase() {

    @Mock lateinit var rh: ResourceHelper
    @Mock lateinit var context: Context
    @Mock lateinit var sp: SP
    @Mock lateinit var fabricPrivacy: FabricPrivacy
    @Mock lateinit var loop: Loop
    @Mock lateinit var rxBus: RxBus
    @Mock lateinit var constraintChecker: Constraints
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
    private lateinit var sut: CarbTimerImpl

    @Before
    fun init() {
        `when`(rh.gs(anyInt())).thenReturn("")
        `when`(profileFunction.getUnits()).thenReturn(GlucoseUnit.MGDL)
        dateUtil = DateUtil(context)
        automationPlugin = AutomationPlugin(injector, rh, context, sp, fabricPrivacy, loop, rxBus, constraintChecker, aapsLogger, aapsSchedulers, config, locationServiceHelper, dateUtil, activePlugin)
        sut = CarbTimerImpl(injector, rh, automationPlugin, timerUtil)
    }

    @Test
    fun doTest() {
        Assert.assertEquals(0, automationPlugin.size())
        sut.scheduleAutomationEventEatReminder()
        Assert.assertEquals(1, automationPlugin.size())
        sut.removeAutomationEventEatReminder()
        Assert.assertEquals(0, automationPlugin.size())

        sut.scheduleTimeToEatReminder(1)
        Mockito.verify(timerUtil, Mockito.times(1)).scheduleReminder(1, "")
    }
}