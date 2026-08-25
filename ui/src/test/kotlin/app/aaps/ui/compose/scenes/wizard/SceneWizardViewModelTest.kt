package app.aaps.ui.compose.scenes.wizard

import androidx.lifecycle.SavedStateHandle
import app.aaps.core.data.model.SceneAction
import app.aaps.core.interfaces.profile.ProfileRepository
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.scenes.SceneStore
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.Translator
import app.aaps.core.keys.StringNonKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.R
import app.aaps.ui.compose.scenes.SceneTemplate
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
internal class SceneWizardViewModelTest {

    @Mock private lateinit var sceneRepository: SceneStore
    @Mock private lateinit var profileRepository: ProfileRepository
    @Mock private lateinit var profileUtil: ProfileUtil
    @Mock private lateinit var preferences: Preferences
    @Mock private lateinit var translator: Translator
    @Mock private lateinit var dateUtil: DateUtil
    @Mock private lateinit var rh: ResourceHelper

    private lateinit var sut: SceneWizardViewModel

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        // StandardTestDispatcher DEFERS the availableChainTargets sharing coroutine (no advanceUntilIdle),
        // so construction stays synchronous. Empty SavedStateHandle => not edit mode => init{} is a no-op.
        Dispatchers.setMain(StandardTestDispatcher())
        // Non-null cold sources referenced during field init must be stubbed or construction NPEs.
        whenever(sceneRepository.scenesFlow).thenReturn(MutableStateFlow(""))
        whenever(sceneRepository.getScenes()).thenReturn(emptyList())
        // ttPresets getter (used by selectTemplate) parses this blob.
        whenever(preferences.get(StringNonKey.TempTargetPresets)).thenReturn("[]")
        sut = SceneWizardViewModel(
            SavedStateHandle(), sceneRepository, profileRepository, profileUtil,
            preferences, translator, dateUtil, rh
        )
    }

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `default state is the template step with empty selections`() {
        val state = sut.state.value
        assertThat(state.currentStep).isEqualTo(SceneWizardViewModel.STEP_TEMPLATE)
        assertThat(state.template).isNull()
        assertThat(state.name).isEmpty()
        assertThat(state.icon).isEqualTo("star")
        assertThat(state.durationMinutes).isEqualTo(60)
        assertThat(state.profileEnabled).isFalse()
        assertThat(state.ttEnabled).isFalse()
        assertThat(state.smbEnabled).isFalse()
        assertThat(state.runningModeEnabled).isFalse()
        assertThat(state.carePortalEnabled).isFalse()
        assertThat(sut.isEditMode).isFalse()
    }

    @Test
    fun `setName and setDuration update the state`() {
        sut.setName("Morning walk")
        sut.setDuration(120)

        val state = sut.state.value
        assertThat(state.name).isEqualTo("Morning walk")
        assertThat(state.durationMinutes).isEqualTo(120)
    }

    @Test
    fun `setIcon and setChainTarget update the state`() {
        sut.setIcon("heart")
        sut.setChainTarget("scene-123")

        val state = sut.state.value
        assertThat(state.icon).isEqualTo("heart")
        assertThat(state.chainTargetId).isEqualTo("scene-123")
    }

    @Test
    fun `action toggles flip their flags independently`() {
        sut.setProfileEnabled(true)
        sut.setTtEnabled(true)
        sut.setSmbEnabled(true)
        sut.setLoopModeEnabled(true)
        sut.setCarePortalEnabled(true)

        val state = sut.state.value
        assertThat(state.profileEnabled).isTrue()
        assertThat(state.ttEnabled).isTrue()
        assertThat(state.smbEnabled).isTrue()
        assertThat(state.runningModeEnabled).isTrue()
        assertThat(state.carePortalEnabled).isTrue()
    }

    @Test
    fun `updateProfileAction applies matching type and ignores mismatched type`() {
        val profile = SceneAction.ProfileSwitch(profileName = "Work", percentage = 80)
        sut.updateProfileAction(profile)
        assertThat(sut.state.value.profileAction).isEqualTo(profile)

        // Wrong type is silently ignored — profileAction stays as previously set.
        sut.updateProfileAction(SceneAction.SmbToggle(enabled = true))
        assertThat(sut.state.value.profileAction).isEqualTo(profile)
    }

    @Test
    fun `updateTtAction ignores non-TempTarget actions`() {
        sut.updateTtAction(SceneAction.SmbToggle(enabled = true))
        assertThat(sut.state.value.ttAction).isNull()
    }

    @Test
    fun `selectTemplate for a bundled template enables its actions and jumps to info step`() {
        whenever(rh.gs(R.string.scene_template_exercise)).thenReturn("Exercise")

        sut.selectTemplate(SceneTemplate.EXERCISE)

        val state = sut.state.value
        assertThat(state.template).isEqualTo(SceneTemplate.EXERCISE)
        assertThat(state.currentStep).isEqualTo(SceneWizardViewModel.STEP_INFO)
        assertThat(state.profileEnabled).isTrue()
        assertThat(state.ttEnabled).isTrue()
        assertThat(state.smbEnabled).isTrue()
        assertThat(state.carePortalEnabled).isTrue()
        assertThat(state.runningModeEnabled).isFalse()
        assertThat(state.icon).isEqualTo("exercise")
        assertThat(state.durationMinutes).isEqualTo(180)
    }

    @Test
    fun `selectTemplate BLANK jumps to profile step with no actions and empty name`() {
        whenever(rh.gs(R.string.scene_template_blank)).thenReturn("Blank")

        sut.selectTemplate(SceneTemplate.BLANK)

        val state = sut.state.value
        assertThat(state.template).isEqualTo(SceneTemplate.BLANK)
        assertThat(state.currentStep).isEqualTo(SceneWizardViewModel.STEP_PROFILE)
        assertThat(state.name).isEmpty()
        assertThat(state.icon).isEqualTo("star")
        assertThat(state.profileEnabled).isFalse()
        assertThat(state.ttEnabled).isFalse()
    }
}
