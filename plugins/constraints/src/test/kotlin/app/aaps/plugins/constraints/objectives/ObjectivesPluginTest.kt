package app.aaps.plugins.constraints.objectives

import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.constraints.Objectives
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.protection.PasswordCheck
import app.aaps.core.interfaces.utils.HardLimits
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.objects.constraints.ConstraintObject
import app.aaps.implementation.sharedPreferences.PreferencesImpl
import app.aaps.plugins.constraints.objectives.objectives.Objective0
import app.aaps.plugins.constraints.objectives.objectives.Objective1
import app.aaps.plugins.constraints.objectives.objectives.Objective2
import app.aaps.plugins.constraints.objectives.objectives.Objective3
import app.aaps.plugins.constraints.objectives.objectives.Objective4
import app.aaps.plugins.constraints.objectives.objectives.Objective5
import app.aaps.plugins.constraints.objectives.objectives.Objective6
import app.aaps.plugins.constraints.objectives.objectives.Objective7
import app.aaps.plugins.constraints.objectives.objectives.Objective8
import app.aaps.plugins.constraints.objectives.objectives.Objective9
import app.aaps.pump.virtual.VirtualPumpPlugin
import app.aaps.shared.impl.sharedPreferences.SPImpl
import app.aaps.shared.tests.SharedPreferencesMock
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import dagger.Lazy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock

// SHTF eternal build: objectives gating disabled intentionally. See docs/SHTF_LOOP_RESILIENCE_PLAN.md
// These tests assert that no objective state can ever veto looping.
class ObjectivesPluginTest : TestBaseWithProfile() {

    @Mock lateinit var virtualPumpPlugin: VirtualPumpPlugin
    @Mock lateinit var persistenceLayer: PersistenceLayer
    @Mock lateinit var loop: Loop
    @Mock lateinit var passwordCheck: PasswordCheck
    @Mock lateinit var profileUtilLazy: Lazy<ProfileUtil>
    @Mock lateinit var profileFunctionLazy: Lazy<ProfileFunction>
    @Mock lateinit var hardLimitsLazy: Lazy<HardLimits>

    private lateinit var objectivesPlugin: ObjectivesPlugin
    private lateinit var emulatedPreferences: Preferences

    @BeforeEach
    fun setupMock() {
        val sp = SPImpl(SharedPreferencesMock(), context)
        emulatedPreferences = PreferencesImpl(sp, profileUtilLazy, profileFunctionLazy, hardLimitsLazy, persistenceLayer, config, dateUtil)

        val objectives = listOf(
            Objective0(emulatedPreferences, rh, dateUtil, activePlugin, virtualPumpPlugin, persistenceLayer, loop, iobCobCalculator, passwordCheck),
            Objective1(emulatedPreferences, rh, dateUtil, activePlugin),
            Objective2(emulatedPreferences, rh, dateUtil),
            Objective3(emulatedPreferences, rh, dateUtil),
            Objective4(emulatedPreferences, rh, dateUtil, profileFunction),
            Objective5(emulatedPreferences, rh, dateUtil),
            Objective6(emulatedPreferences, rh, dateUtil, constraintsChecker, loop),
            Objective7(emulatedPreferences, rh, dateUtil),
            Objective8(emulatedPreferences, rh, dateUtil),
            Objective9(emulatedPreferences, rh, dateUtil)
        )
        objectivesPlugin = ObjectivesPlugin(aapsLogger, rh, emulatedPreferences, config, objectives)
        objectivesPlugin.onStart()
    }

    @Test fun notStartedObjectivesShouldNotLimitLoopInvocation() {
        objectivesPlugin.objectives[Objectives.FIRST_OBJECTIVE].startedOn = 0
        val c = objectivesPlugin.isLoopInvocationAllowed(ConstraintObject(true, aapsLogger))
        assertThat(c.getReasons()).isEmpty()
        assertThat(c.value()).isTrue()
    }

    @Test fun notFinishedObjective5ShouldNotForceLgs() {
        objectivesPlugin.objectives[Objectives.LGS_OBJECTIVE].startedOn = 1
        objectivesPlugin.objectives[Objectives.LGS_OBJECTIVE].accomplishedOn = 0
        val c = objectivesPlugin.isLgsForced(ConstraintObject(false, aapsLogger))
        assertThat(c.getReasons()).isEmpty()
        assertThat(c.value()).isFalse()
    }

    @Test fun notStartedObjective6ShouldNotLimitClosedLoop() {
        objectivesPlugin.objectives[Objectives.CLOSED_LOOP_OBJECTIVE].startedOn = 0
        val c = objectivesPlugin.isClosedLoopAllowed(ConstraintObject(true, aapsLogger))
        assertThat(c.getReasons()).isEmpty()
        assertThat(c.value()).isTrue()
    }

    @Test fun notStartedObjective8ShouldNotLimitAutosensMode() {
        objectivesPlugin.objectives[Objectives.AUTOSENS_OBJECTIVE].startedOn = 0
        val c = objectivesPlugin.isAutosensModeEnabled(ConstraintObject(true, aapsLogger))
        assertThat(c.getReasons()).isEmpty()
        assertThat(c.value()).isTrue()
    }

    @Test fun notStartedObjective10ShouldNotLimitSMBMode() {
        objectivesPlugin.objectives[Objectives.SMB_OBJECTIVE].startedOn = 0
        val c = objectivesPlugin.isSMBModeEnabled(ConstraintObject(true, aapsLogger))
        assertThat(c.getReasons()).isEmpty()
        assertThat(c.value()).isTrue()
    }

    @Test fun interfaceAlwaysReportsStartedAndAccomplished() {
        assertThat(objectivesPlugin.isStarted(Objectives.FIRST_OBJECTIVE)).isTrue()
        assertThat(objectivesPlugin.isAccomplished(Objectives.AUTO_OBJECTIVE)).isTrue()
    }

    // SHTF eternal build: stored progress must survive a device clock far in the past
    // (stock code wiped objectives whose timestamps were >3h in the future).
    @Test fun progressSurvivesClockReset() {
        val objective = objectivesPlugin.objectives[Objectives.FIRST_OBJECTIVE]
        val farFuture = dateUtil.now() + 2L * 365 * 24 * 3600 * 1000
        objective.startedOn = farFuture
        objective.accomplishedOn = farFuture
        assertThat(objective.accomplishedOn).isEqualTo(farFuture)
        assertThat(objective.startedOn).isEqualTo(farFuture)
        assertThat(objective.isAccomplished).isTrue()
    }
}
