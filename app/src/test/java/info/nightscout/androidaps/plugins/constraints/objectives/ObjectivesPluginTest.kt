package info.nightscout.androidaps.plugins.constraints.objectives

import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.Config
import info.nightscout.androidaps.R
import info.nightscout.androidaps.TestBase
import info.nightscout.androidaps.interfaces.ActivePluginProvider
import info.nightscout.androidaps.interfaces.Constraint
import info.nightscout.androidaps.logging.UserEntryLogger
import info.nightscout.androidaps.plugins.constraints.objectives.objectives.Objective
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.sharedPreferences.SP
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.powermock.core.classloader.annotations.PrepareForTest
import org.powermock.modules.junit4.PowerMockRunner

@PrepareForTest(UserEntryLogger::class)
@RunWith(PowerMockRunner::class)
class ObjectivesPluginTest : TestBase() {

    @Mock lateinit var resourceHelper: ResourceHelper
    @Mock lateinit var activePlugin: ActivePluginProvider
    @Mock lateinit var sp: SP
    @Mock lateinit var uel: UserEntryLogger

    private lateinit var objectivesPlugin: ObjectivesPlugin

    val injector = HasAndroidInjector {
        AndroidInjector {
            if (it is Objective) {
                it.sp = sp
                it.resourceHelper = resourceHelper
            }
        }
    }

    @Before fun prepareMock() {
        objectivesPlugin = ObjectivesPlugin(injector, aapsLogger, resourceHelper, activePlugin, sp, Config(), uel)
        objectivesPlugin.onStart()
        `when`(resourceHelper.gs(R.string.objectivenotstarted)).thenReturn("Objective %1\$d not started")
    }

    @Test fun notStartedObjectivesShouldLimitLoopInvocation() {
        objectivesPlugin.objectives[ObjectivesPlugin.FIRST_OBJECTIVE].startedOn = 0
        var c = Constraint(true)
        c = objectivesPlugin.isLoopInvocationAllowed(c)
        Assert.assertEquals("Objectives: Objective 1 not started", c.getReasons(aapsLogger))
        Assert.assertEquals(false, c.value())
        objectivesPlugin.objectives[ObjectivesPlugin.FIRST_OBJECTIVE].startedOn = DateUtil.now()
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

    @Test fun notStartedObjective9ShouldLimitAMAMode() {
        objectivesPlugin.objectives[ObjectivesPlugin.AMA_OBJECTIVE].startedOn = 0
        var c = Constraint(true)
        c = objectivesPlugin.isAMAModeEnabled(c)
        Assert.assertEquals(true, c.getReasons(aapsLogger).contains("Objective 9 not started"))
        Assert.assertEquals(false, c.value())
    }

    @Test fun notStartedObjective10ShouldLimitSMBMode() {
        objectivesPlugin.objectives[ObjectivesPlugin.SMB_OBJECTIVE].startedOn = 0
        var c = Constraint(true)
        c = objectivesPlugin.isSMBModeEnabled(c)
        Assert.assertEquals(true, c.getReasons(aapsLogger).contains("Objective 10 not started"))
        Assert.assertEquals(false, c.value())
    }
}