package app.aaps.implementation.scenes

import app.aaps.core.data.model.ActiveSceneState
import app.aaps.core.data.model.ICfg
import app.aaps.core.data.model.PS
import app.aaps.core.data.model.RM
import app.aaps.core.data.model.Scene
import app.aaps.core.data.model.SceneAction
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.profile.SingleProfile
import app.aaps.core.interfaces.pump.PumpWithConcentration
import app.aaps.core.interfaces.utils.Translator
import app.aaps.core.ui.CoreUiStrings
import app.aaps.implementation.profile.ProfileSwitchSilentGate
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verifyBlocking
import org.mockito.kotlin.whenever

class SceneExecutorTest : TestBaseWithProfile() {

    @Mock lateinit var persistenceLayer: PersistenceLayer
    @Mock lateinit var activeSceneManager: ActiveSceneManager
    @Mock lateinit var uel: UserEntryLogger
    @Mock lateinit var loop: Loop
    @Mock lateinit var pump: PumpWithConcentration
    @Mock lateinit var translator: Translator
    @Mock lateinit var profileSwitchSilentGate: ProfileSwitchSilentGate

    private lateinit var sut: SceneExecutor
    private val expiryScheduler = TestSceneExpiryScheduler()

    private val scene = Scene(
        id = "s1",
        name = "Test scene",
        defaultDurationMinutes = 0,
        actions = listOf(SceneAction.ProfileSwitch(profileName = TESTPROFILENAME, percentage = 90))
    )

    /** A scene whose only action needs nothing stubbed, so a test can look at the scheduling alone. */
    private val smbScene = Scene(
        id = "s2",
        name = "Exercise",
        defaultDurationMinutes = 0,
        actions = listOf(SceneAction.SmbToggle(enabled = false))
    )

    @BeforeEach fun prepare() {
        sut = SceneExecutor(
            persistenceLayer, profileFunction, profileRepository, preferences, activeSceneManager,
            uel, dateUtil, aapsLogger, rh, rxBus, loop, activePlugin, profileUtil, translator,
            profileSwitchSilentGate, notificationManager, expiryScheduler
        )
        runBlocking {
            // Everything validateActivation() gates on, so the run reaches the action itself.
            whenever(loop.runningMode()).thenReturn(RM.Mode.CLOSED_LOOP)
            whenever(activePlugin.activePump).thenReturn(pump)
            whenever(pump.isInitialized()).thenReturn(true)
            whenever(profileFunction.getProfile()).thenReturn(effectiveProfile)
            // profileNames() is an extension over profiles.value — the scene's target must appear there or
            // validateActivation() rejects the whole activation before any action runs.
            val singleProfile = mock<SingleProfile>()
            whenever(singleProfile.name).thenReturn(TESTPROFILENAME)
            whenever(profileRepository.profiles).thenReturn(MutableStateFlow(listOf(singleProfile)))
            whenever(activeSceneManager.isActive()).thenReturn(false)
        }
        whenever(rh.gs(CoreUiStrings.profile_switch_no_insulin)).thenReturn("No insulin in use")
        whenever(rh.gs(CoreUiStrings.scene_some_actions_failed)).thenReturn("Some actions failed")
    }

    // A scene runs unattended, so with nothing in force there is nobody to ask which insulin to record: the action
    // fails with the reason instead of stamping an arbitrary catalogue entry onto the switch.
    @Test fun `profile switch action fails when no insulin is in force`() = runBlocking {
        whenever(profileFunction.getRunningOrRequestedICfg()).thenReturn(null)

        val result = sut.activate(scene, durationMinutes = 0)

        assertThat(result.success).isFalse()
        assertThat(result.actionResults.single().errorMessage).isEqualTo("No insulin in use")
        verifyBlocking(profileFunction, never()) {
            createProfileSwitch(anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull())
        }
    }

    // Counterpart to the refusal: a scene never changes the insulin, it records whatever is already in force.
    @Test fun `profile switch action records the insulin in force`() = runBlocking {
        val ps = mock<PS>()
        whenever(ps.id).thenReturn(7L)
        whenever(profileFunction.getRunningOrRequestedICfg()).thenReturn(someICfg)
        whenever(
            profileFunction.createProfileSwitch(
                anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull()
            )
        ).thenReturn(ps)

        val result = sut.activate(scene, durationMinutes = 0)

        assertThat(result.success).isTrue()
        assertThat(result.actionResults.single().recordId).isEqualTo(7L)
        val captor = argumentCaptor<ICfg>()
        verifyBlocking(profileFunction) {
            createProfileSwitch(
                anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), captor.capture()
            )
        }
        assertThat(captor.firstValue).isEqualTo(someICfg)
    }

    /**
     * The expiry is the ONLY thing that takes a scene's temp target, profile switch and SMB setting off
     * again. If it is not armed, the scene runs forever. This could not be asserted while the executor
     * called `WorkManager.getInstance(context)` itself - that threw on a mocked context and the executor
     * logged and carried on, so every test ran the "scheduling failed" path without saying so.
     */
    // Block body, not `= runBlocking { ... }`: `containsExactly` returns `Ordered`, so the expression form
    // would give this method a return value, and JUnit 5 skips a @Test that returns one WITHOUT reporting it.
    @Test fun `a timed scene arms its expiry for the requested duration`() {
        runBlocking { sut.activate(smbScene, durationMinutes = 30) }

        assertThat(expiryScheduler.scheduled).containsExactly("Exercise" to T.mins(30).msecs())
    }

    // An indefinite scene ends only when the user ends it, so arming a timer would end it behind their back.
    @Test fun `an indefinite scene arms no expiry`() = runBlocking {
        sut.activate(smbScene, durationMinutes = 0)

        assertThat(expiryScheduler.scheduled).isEmpty()
    }

    // Ending a scene early has to drop the pending expiry, or it fires later against whatever is active then.
    @Test fun `deactivate cancels the pending expiry`() = runBlocking {
        whenever(activeSceneManager.getActiveState()).thenReturn(
            ActiveSceneState(scene = smbScene, activatedAt = 1000L, durationMs = T.mins(30).msecs())
        )

        sut.deactivate()

        assertThat(expiryScheduler.cancelCount).isEqualTo(1)
    }
}
