package info.nightscout.implementation

import android.content.Context
import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.TestBase
import info.nightscout.automation.AutomationPlugin
import info.nightscout.automation.services.LocationServiceHelper
import info.nightscout.automation.triggers.Trigger
import info.nightscout.core.utils.fabric.FabricPrivacy
import info.nightscout.interfaces.Config
import info.nightscout.interfaces.GlucoseUnit
import info.nightscout.interfaces.aps.Loop
import info.nightscout.interfaces.constraints.Constraints
import info.nightscout.interfaces.plugin.ActivePlugin
import info.nightscout.interfaces.profile.ProfileFunction
import info.nightscout.rx.bus.RxBus
import info.nightscout.shared.interfaces.ResourceHelper
import info.nightscout.shared.sharedPreferences.SP
import info.nightscout.shared.utils.DateUtil
import org.junit.Assert
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.Mockito.anyInt
import org.mockito.Mockito.`when`

class BolusTimerImplTest : TestBase() {

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
    private lateinit var sut: BolusTimerImpl

    @BeforeEach
    fun init() {
        `when`(rh.gs(anyInt())).thenReturn("")
        `when`(profileFunction.getUnits()).thenReturn(GlucoseUnit.MGDL)
        dateUtil = DateUtil(context)
        automationPlugin = AutomationPlugin(injector, rh, context, sp, fabricPrivacy, loop, rxBus, constraintChecker, aapsLogger, aapsSchedulers, config, locationServiceHelper, dateUtil, activePlugin)
        sut = BolusTimerImpl(injector, rh, automationPlugin)
    }

    @Test
    fun doTest() {
        Assert.assertEquals(0, automationPlugin.size())
        sut.scheduleAutomationEventBolusReminder()
        Assert.assertEquals(1, automationPlugin.size())
        sut.removeAutomationEventBolusReminder()
        Assert.assertEquals(0, automationPlugin.size())
    }
}