package app.aaps.implementation.scenes

import app.aaps.core.data.model.ActiveSceneState
import app.aaps.core.data.model.ActiveSceneState.ScopedRecords
import app.aaps.core.data.model.ICfg
import app.aaps.core.data.model.PS
import app.aaps.core.data.model.RM
import app.aaps.core.data.model.Scene
import app.aaps.core.data.model.SceneAction
import app.aaps.core.data.time.T
import app.aaps.core.data.ui.ConfirmationLine
import app.aaps.core.data.ui.ConfirmationRole
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.bolus.WizardBolusExecutor
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.notifications.AlarmSound
import app.aaps.core.interfaces.notifications.NotificationAction
import app.aaps.core.interfaces.notifications.NotificationId
import app.aaps.core.interfaces.notifications.NotificationLevel
import app.aaps.core.interfaces.profile.SingleProfile
import app.aaps.core.interfaces.pump.PumpWithConcentration
import app.aaps.core.interfaces.scenes.SceneChainResolver
import app.aaps.core.interfaces.utils.Translator
import app.aaps.implementation.profile.ProfileSwitchSilentGate
import app.aaps.shared.tests.TestBaseWithProfile
import app.aaps.shared.tests.generatedTextResolver
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
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
    @Mock lateinit var sceneChainResolver: SceneChainResolver

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
            uel, dateUtil, aapsLogger, generatedTextResolver(), rxBus, loop, activePlugin, profileUtil, translator,
            profileSwitchSilentGate, notificationManager, expiryScheduler, sceneChainResolver
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
    }

    // A scene runs unattended, so with nothing in force there is nobody to ask which insulin to record: the action
    // fails with the reason instead of stamping an arbitrary catalogue entry onto the switch.
    @Test fun `profile switch action fails when no insulin is in force`() = runBlocking {
        whenever(profileFunction.getRunningOrRequestedICfg()).thenReturn(null)

        val result = sut.activate(scene, durationMinutes = 0)

        assertThat(result.success).isFalse()
        assertThat(result.actionResults.single().errorMessage)
            .isEqualTo("Cannot switch profile: no insulin is in use, and none was selected.")
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

    // #4959: the scene's switch is marked, so its write stays silent whichever change event leads to it.
    @Test fun `a scene marks its profile switch silent`() = runBlocking {
        val ps = mock<PS>()
        whenever(ps.id).thenReturn(7L)
        whenever(profileFunction.getRunningOrRequestedICfg()).thenReturn(someICfg)
        whenever(
            profileFunction.createProfileSwitch(
                anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull()
            )
        ).thenReturn(ps)

        sut.activate(scene, durationMinutes = 0)

        verify(profileSwitchSilentGate).markSilent(7L, now)
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

    // The confirmation says what the scene sets in motion, so a follow-up is named before the user confirms.
    @Test fun `prepare names the follow-up scene in the confirmation`() = runBlocking {
        whenever(sceneChainResolver.resolveCatalogChainTarget(smbScene)).thenReturn(Scene(id = "s3", name = "Cooldown"))

        val result = sut.prepareScene(smbScene, durationMinutes = 0) as WizardBolusExecutor.PrepareResult.Preview

        // Last, and in the scene colour like the name above it
        assertThat(result.lines.last()).isEqualTo(ConfirmationLine(ConfirmationRole.SCENE, "→ Cooldown"))
    }

    @Test fun `prepare says nothing about a follow-up when there is none`() = runBlocking {
        val result = sut.prepareScene(smbScene, durationMinutes = 0) as WizardBolusExecutor.PrepareResult.Preview

        // The scene's name is the only scene-coloured line
        assertThat(result.lines.count { it.role == ConfirmationRole.SCENE }).isEqualTo(1)
    }

    // Ending a scene early has to drop the pending expiry, or it fires later against whatever is active then.
    @Test fun `deactivate cancels the pending expiry`() = runBlocking {
        whenever(activeSceneManager.getActiveState()).thenReturn(
            ActiveSceneState(scene = smbScene, activatedAt = 1000L, durationMs = T.mins(30).msecs())
        )

        sut.deactivate()

        assertThat(expiryScheduler.cancelCount).isEqualTo(1)
    }

    // ---------------------------------------------------------------------------------------------
    // Ending a scene: its profile switch is cut, unless the user chose another profile during it
    // ---------------------------------------------------------------------------------------------

    private val fiasp = ICfg(insulinLabel = "Fiasp", insulinEndTime = 8 * 3600 * 1000L, insulinPeakTime = 55 * 60 * 1000L, concentration = 1.0)

    /** [scene] runs with the profile switch [psId]. [requested] is the switch in force, and the pump has set [setByPump]. */
    private suspend fun sceneRunning(activatedAt: Long, durationMs: Long, psId: Long, requested: PS?, setByPump: Long? = requested?.id) {
        whenever(activeSceneManager.getActiveState())
            .thenReturn(ActiveSceneState(scene = scene, activatedAt = activatedAt, durationMs = durationMs, scopedRecords = ScopedRecords(psId = psId)))
        whenever(persistenceLayer.getProfileSwitchActiveAt(anyLong())).thenReturn(requested)
        whenever(persistenceLayer.getEffectiveProfileSwitchActiveAt(anyLong())).thenReturn(setByPump?.let { effectiveProfileSwitch.copy(originalPsId = it) })
        whenever(persistenceLayer.cancelProfileSwitch(any(), any(), any(), any(), anyOrNull(), any())).thenReturn(PersistenceLayer.TransactionResult())
    }

    @Test fun `ending a scene cuts its profile switch`() = runBlocking {
        val sceneSwitch = profileSwitch.copy(id = 22, profileName = TESTPROFILENAME, percentage = 90, timestamp = now - T.mins(10).msecs(), duration = T.mins(60).msecs())
        sceneRunning(activatedAt = now - T.mins(10).msecs(), durationMs = T.mins(60).msecs(), psId = 22, requested = sceneSwitch)

        assertThat(sut.deactivate().success).isTrue()

        verifyBlocking(persistenceLayer) { cancelProfileSwitch(eq(22L), eq(now), any(), any(), anyOrNull(), any()) }
    }

    // #4959: the switch the scene's end brings back is marked, so its write stays silent whichever event leads to it.
    @Test fun `the end of a scene marks the switch it brings back silent`() = runBlocking {
        val sceneSwitch = profileSwitch.copy(id = 22, profileName = TESTPROFILENAME, percentage = 90, timestamp = now - T.mins(10).msecs(), duration = T.mins(60).msecs())
        val before = profileSwitch.copy(id = 41, profileName = TESTPROFILENAME, percentage = 95, timestamp = now - T.hours(5).msecs(), duration = 0)
        sceneRunning(activatedAt = now - T.mins(10).msecs(), durationMs = T.mins(60).msecs(), psId = 22, requested = sceneSwitch)
        // In force before the cut, and after it
        whenever(persistenceLayer.getProfileSwitchActiveAt(anyLong())).thenReturn(sceneSwitch, before)

        sut.deactivate()

        verify(profileSwitchSilentGate).markSilent(41L, now)
    }

    // Until the pump has set the scene's switch, the effective one is still from before. The scene's switch is cut all the same.
    @Test fun `ending a scene cuts its profile switch before the pump has set it`() = runBlocking {
        val sceneSwitch = profileSwitch.copy(id = 22, profileName = TESTPROFILENAME, percentage = 90, timestamp = now - T.mins(1).msecs(), duration = T.mins(60).msecs())
        sceneRunning(activatedAt = now - T.mins(1).msecs(), durationMs = T.mins(60).msecs(), psId = 22, requested = sceneSwitch, setByPump = 10)

        sut.deactivate()

        verifyBlocking(persistenceLayer) { cancelProfileSwitch(eq(22L), eq(now), any(), any(), anyOrNull(), any()) }
    }

    @Test fun `a profile the user chose during the scene stays`() = runBlocking {
        val usersSwitch = profileSwitch.copy(id = 50, profileName = TESTPROFILENAME, percentage = 120, timestamp = now - T.mins(5).msecs())
        sceneRunning(activatedAt = now - T.mins(10).msecs(), durationMs = T.mins(60).msecs(), psId = 22, requested = usersSwitch)

        sut.deactivate()

        verifyBlocking(persistenceLayer, never()) { cancelProfileSwitch(any(), any(), any(), any(), anyOrNull(), any()) }
    }

    // #5081: an insulin change during the scene made a copy of its switch, and ActiveSceneManager follows the copy. The
    // copy is cut, so the scene's percentage ends with the scene. The permanent switch under it has the new insulin.
    @Test fun `ending a scene cuts the copy an insulin change made of its switch`() = runBlocking {
        val copy = profileSwitch.copy(id = 30, profileName = TESTPROFILENAME, percentage = 90, timestamp = now - T.mins(5).msecs(), duration = T.mins(55).msecs(), iCfg = fiasp)
        sceneRunning(activatedAt = now - T.mins(10).msecs(), durationMs = T.mins(60).msecs(), psId = 30, requested = copy)

        assertThat(sut.deactivate().success).isTrue()

        verifyBlocking(persistenceLayer) { cancelProfileSwitch(eq(30L), eq(now), any(), any(), anyOrNull(), any()) }
        verifyBlocking(profileFunction, never()) {
            createProfileSwitch(anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull())
        }
    }

    /**
     * An indefinite scene's switch is permanent, so the insulin change only copied it. Cutting the copy alone would bring
     * back the scene's own switch, or the profile from before it with the old insulin. So that profile is written again
     * with the new insulin first, then the copy is cut.
     */
    // Declared Unit: the last verify returns the mock's result, and JUnit 5 skips a @Test that returns a value without saying so.
    @Test fun `ending an indefinite scene after an insulin change puts the profile from before it back with the new insulin`(): Unit = runBlocking {
        val activatedAt = now - T.hours(2).msecs()
        val copy = profileSwitch.copy(id = 31, profileName = TESTPROFILENAME, percentage = 90, timestamp = now - T.hours(1).msecs(), duration = 0, iCfg = fiasp)
        val before = profileSwitch.copy(id = 41, profileName = TESTPROFILENAME, percentage = 95, timestamp = now - T.hours(5).msecs(), duration = 0, iCfg = someICfg)
        sceneRunning(activatedAt = activatedAt, durationMs = 0, psId = 31, requested = copy)
        whenever(persistenceLayer.getPermanentProfileSwitchActiveAt(activatedAt - 1)).thenReturn(before)
        whenever(profileFunction.createProfileSwitchLike(any(), any(), any(), any(), any(), any(), anyOrNull(), any()))
            .thenReturn(before.copy(id = 60, timestamp = now, iCfg = fiasp))

        assertThat(sut.deactivate().success).isTrue()

        // A copy of the switch from before the scene, so a profile renamed or edited during the scene does not matter
        val order = inOrder(profileFunction, persistenceLayer)
        order.verify(profileFunction).createProfileSwitchLike(eq(before), eq(now), eq(0L), eq(fiasp), any(), any(), anyOrNull(), any())
        order.verify(persistenceLayer).cancelProfileSwitch(eq(31L), eq(now), any(), any(), anyOrNull(), any())
        verify(profileSwitchSilentGate).markSilent(60L, now)
    }

    // Without an insulin change the scene's own switch is cut, and the resolver brings back what was under it.
    @Test fun `ending an indefinite scene without an insulin change only cuts its switch`() = runBlocking {
        val activatedAt = now - T.hours(2).msecs()
        val sceneSwitch = profileSwitch.copy(id = 22, profileName = TESTPROFILENAME, percentage = 90, timestamp = activatedAt, duration = 0, iCfg = someICfg)
        sceneRunning(activatedAt = activatedAt, durationMs = 0, psId = 22, requested = sceneSwitch)

        sut.deactivate()

        verifyBlocking(persistenceLayer) { cancelProfileSwitch(eq(22L), eq(now), any(), any(), anyOrNull(), any()) }
        verifyBlocking(profileFunction, never()) {
            createProfileSwitch(anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull())
        }
    }

    /** The text of the one notification that says the scene's profile is still in use. */
    private fun notRestoredNotice(): String {
        val text = argumentCaptor<String>()
        verify(notificationManager).post(
            eq(NotificationId.SCENE_PROFILE_NOT_RESTORED), text.capture(), any<NotificationLevel>(), eq(0), anyOrNull<AlarmSound>(),
            any<List<NotificationAction>>(), anyOrNull<() -> Boolean>()
        )
        return text.firstValue
    }

    // A percentage that stays is better than an old insulin: when the profile from before cannot be written, nothing is
    // cut. And the user is told, because the End Scene dialog does not show the result.
    @Test fun `when the profile from before an indefinite scene cannot be written its switch stays and the user is told`() = runBlocking {
        val activatedAt = now - T.hours(2).msecs()
        val copy = profileSwitch.copy(id = 31, profileName = TESTPROFILENAME, percentage = 90, timestamp = now - T.hours(1).msecs(), duration = 0, iCfg = fiasp)
        sceneRunning(activatedAt = activatedAt, durationMs = 0, psId = 31, requested = copy)
        whenever(persistenceLayer.getPermanentProfileSwitchActiveAt(activatedAt - 1))
            .thenReturn(profileSwitch.copy(id = 41, profileName = TESTPROFILENAME, percentage = 95, timestamp = now - T.hours(5).msecs(), duration = 0, iCfg = someICfg))
        whenever(profileFunction.createProfileSwitchLike(any(), any(), any(), any(), any(), any(), anyOrNull(), any())).thenReturn(null)
        whenever(profileFunction.profileSwitchLikeRefusal(any(), any(), any(), any())).thenReturn("The pump cannot take it.")

        val result = sut.deactivate()

        assertThat(result.success).isFalse()
        verifyBlocking(persistenceLayer, never()) { cancelProfileSwitch(any(), any(), any(), any(), anyOrNull(), any()) }
        assertThat(notRestoredNotice())
            .isEqualTo("Scene Test scene has ended, but its profile is still in use. The pump cannot take it. Activate a profile by hand.")
    }

    @Test fun `with no profile from before an indefinite scene its switch stays and the user is told`() = runBlocking {
        val activatedAt = now - T.hours(2).msecs()
        val copy = profileSwitch.copy(id = 31, profileName = TESTPROFILENAME, percentage = 90, timestamp = now - T.hours(1).msecs(), duration = 0, iCfg = fiasp)
        sceneRunning(activatedAt = activatedAt, durationMs = 0, psId = 31, requested = copy)
        whenever(persistenceLayer.getPermanentProfileSwitchActiveAt(activatedAt - 1)).thenReturn(null)

        assertThat(sut.deactivate().success).isFalse()

        verifyBlocking(persistenceLayer, never()) { cancelProfileSwitch(any(), any(), any(), any(), anyOrNull(), any()) }
        assertThat(notRestoredNotice()).isEqualTo(
            "Scene Test scene has ended, but its profile is still in use. No profile was in use before the scene started. Activate a profile by hand."
        )
    }

    // The manager follows a copy on its change event, which may not have run yet when the scene ends, or not at all
    // when the app was killed in between. The scene end asks it, and cuts the switch it names.
    @Test fun `ending a scene cuts the copy the manager follows now`() = runBlocking {
        val copy = profileSwitch.copy(id = 30, profileName = TESTPROFILENAME, percentage = 90, timestamp = now - T.mins(5).msecs(), duration = T.mins(55).msecs(), iCfg = fiasp)
        sceneRunning(activatedAt = now - T.mins(10).msecs(), durationMs = T.mins(60).msecs(), psId = 22, requested = copy, setByPump = 22)
        whenever(activeSceneManager.followInsulinCopyNow()).thenReturn(30L)

        sut.deactivate()

        verifyBlocking(persistenceLayer) { cancelProfileSwitch(eq(30L), eq(now), any(), any(), anyOrNull(), any()) }
        verifyBlocking(persistenceLayer, never()) { cancelProfileSwitch(eq(22L), any(), any(), any(), anyOrNull(), any()) }
    }
}
