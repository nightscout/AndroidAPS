package app.aaps.plugins.constraints.objectives

import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.constraints.Objectives
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.main.constraints.ConstraintObject
import app.aaps.plugins.constraints.R
import app.aaps.plugins.constraints.objectives.objectives.Objective
import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`

class ObjectivesPluginTest : TestBase() {

    @Mock lateinit var rh: ResourceHelper
    @Mock lateinit var activePlugin: ActivePlugin
    @Mock lateinit var sp: SP
    @Mock lateinit var dateUtil: DateUtil
    @Mock lateinit var config: Config

    private lateinit var objectivesPlugin: ObjectivesPlugin

    private val injector = HasAndroidInjector {
        AndroidInjector {
            if (it is Objective) {
                it.sp = sp
                it.rh = rh
                it.dateUtil = dateUtil
            }
        }
    }

    @BeforeEach fun prepareMock() {
        objectivesPlugin = ObjectivesPlugin(injector, aapsLogger, rh, activePlugin, sp, config)
        objectivesPlugin.onStart()
        `when`(rh.gs(R.string.objectivenotstarted, 9)).thenReturn("Objective 9 not started")
        `when`(rh.gs(R.string.objectivenotstarted, 8)).thenReturn("Objective 8 not started")
        `when`(rh.gs(R.string.objectivenotstarted, 6)).thenReturn("Objective 6 not started")
        `when`(rh.gs(R.string.objectivenotstarted, 1)).thenReturn("Objective 1 not started")
    }

    @Test fun notStartedObjectivesShouldLimitLoopInvocation() {
        objectivesPlugin.objectives[Objectives.FIRST_OBJECTIVE].startedOn = 0
        val c = objectivesPlugin.isLoopInvocationAllowed(ConstraintObject(true, aapsLogger))
        assertThat(c.getReasons()).isEqualTo("Objectives: Objective 1 not started")
        assertThat(c.value()).isFalse()
        objectivesPlugin.objectives[Objectives.FIRST_OBJECTIVE].startedOn = dateUtil.now()
    }

    @Test fun notStartedObjective6ShouldLimitClosedLoop() {
        objectivesPlugin.objectives[Objectives.MAXIOB_ZERO_CL_OBJECTIVE].startedOn = 0
        val c = objectivesPlugin.isClosedLoopAllowed(ConstraintObject(true, aapsLogger))
        assertThat(c.getReasons()).contains("Objective 6 not started")
        assertThat(c.value()).isFalse()
    }

    @Test fun notStartedObjective8ShouldLimitAutosensMode() {
        objectivesPlugin.objectives[Objectives.AUTOSENS_OBJECTIVE].startedOn = 0
        val c = objectivesPlugin.isAutosensModeEnabled(ConstraintObject(true, aapsLogger))
        assertThat(c.getReasons()).contains("Objective 8 not started")
        assertThat(c.value()).isFalse()
    }

    @Test fun notStartedObjective10ShouldLimitSMBMode() {
        objectivesPlugin.objectives[Objectives.SMB_OBJECTIVE].startedOn = 0
        val c = objectivesPlugin.isSMBModeEnabled(ConstraintObject(true, aapsLogger))
        assertThat(c.getReasons()).contains("Objective 9 not started")
        assertThat(c.value()).isFalse()
    }
}
