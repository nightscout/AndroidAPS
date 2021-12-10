package info.nightscout.androidaps.plugins.constraints.objectives

import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.R
import info.nightscout.androidaps.TestBase
import info.nightscout.androidaps.interfaces.ActivePlugin
import info.nightscout.androidaps.interfaces.Config
import info.nightscout.androidaps.interfaces.Constraint
import info.nightscout.androidaps.logging.UserEntryLogger
import info.nightscout.androidaps.plugins.constraints.objectives.objectives.Objective
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.shared.sharedPreferences.SP
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`

class ObjectivesPluginTest : TestBase() {

    @Mock lateinit var rh: ResourceHelper
    @Mock lateinit var activePlugin: ActivePlugin
    @Mock lateinit var sp: SP
    @Mock lateinit var dateUtil: DateUtil
    @Mock lateinit var uel: UserEntryLogger
    @Mock lateinit var config: Config

    private lateinit var objectivesPlugin: ObjectivesPlugin

    val injector = HasAndroidInjector {
        AndroidInjector {
            if (it is Objective) {
                it.sp = sp
                it.rh = rh
                it.dateUtil = dateUtil
            }
        }
    }

    @Before fun prepareMock() {
        objectivesPlugin = ObjectivesPlugin(injector, aapsLogger, rh, activePlugin, sp, config, dateUtil, uel)
        objectivesPlugin.onStart()
        `when`(rh.gs(R.string.objectivenotstarted, 9)).thenReturn("Objective 9 not started")
        `when`(rh.gs(R.string.objectivenotstarted, 8)).thenReturn("Objective 8 not started")
        `when`(rh.gs(R.string.objectivenotstarted, 6)).thenReturn("Objective 6 not started")
        `when`(rh.gs(R.string.objectivenotstarted, 1)).thenReturn("Objective 1 not started")
    }

    @Test fun notStartedObjectivesShouldLimitLoopInvocation() {
        objectivesPlugin.objectives[ObjectivesPlugin.FIRST_OBJECTIVE].startedOn = 0
        var c = Constraint(true)
        c = objectivesPlugin.isLoopInvocationAllowed(c)
        Assert.assertEquals("Objectives: Objective 1 not started", c.getReasons(aapsLogger))
        Assert.assertEquals(false, c.value())
        objectivesPlugin.objectives[ObjectivesPlugin.FIRST_OBJECTIVE].startedOn = dateUtil.now()
    }

    @Test fun notStartedObjective6ShouldLimitClosedLoop() {
        objectivesPlugin.objectives[ObjectivesPlugin.MAXIOB_ZERO_CL_OBJECTIVE].startedOn = 0
        var c = Constraint(true)
        c = objectivesPlugin.isClosedLoopAllowed(c)
        Assert.assertEquals(true, c.getReasons(aapsLogger).contains("Objective 6 not started"))
        Assert.assertEquals(false, c.value())
    }

    @Test fun notStartedObjective8ShouldLimitAutosensMode() {
        objectivesPlugin.objectives[ObjectivesPlugin.AUTOSENS_OBJECTIVE].startedOn = 0
        var c = Constraint(true)
        c = objectivesPlugin.isAutosensModeEnabled(c)
        Assert.assertEquals(true, c.getReasons(aapsLogger).contains("Objective 8 not started"))
        Assert.assertEquals(false, c.value())
    }

    @Test fun notStartedObjective10ShouldLimitSMBMode() {
        objectivesPlugin.objectives[ObjectivesPlugin.SMB_OBJECTIVE].startedOn = 0
        var c = Constraint(true)
        c = objectivesPlugin.isSMBModeEnabled(c)
        Assert.assertEquals(true, c.getReasons(aapsLogger).contains("Objective 9 not started"))
        Assert.assertEquals(false, c.value())
    }
}