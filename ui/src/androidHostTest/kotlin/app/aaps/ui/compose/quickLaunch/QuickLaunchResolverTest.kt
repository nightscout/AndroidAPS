package app.aaps.ui.compose.quickLaunch

import app.aaps.core.data.model.RM
import app.aaps.core.data.model.Scene
import app.aaps.core.data.model.SceneAction
import app.aaps.core.data.model.SceneEndAction
import app.aaps.core.data.model.TT
import app.aaps.core.data.model.TTPreset
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.automation.Automation
import app.aaps.core.interfaces.navigation.ElementType
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.ProfileRepository
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.scenes.SceneStore
import app.aaps.core.interfaces.tempTargets.toJson
import app.aaps.core.keys.StringNonKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.objects.wizard.QuickWizard
import app.aaps.core.objects.wizard.QuickWizardEntry
import app.aaps.shared.tests.generatedTextResolver
import app.aaps.ui.compose.navigation.ElementAvailability
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/** The scene text the QuickLaunch tooltip, overflow menu and configure rows show. */
internal class QuickLaunchResolverTest {

    @Mock private lateinit var preferences: Preferences
    @Mock private lateinit var quickWizard: QuickWizard
    @Mock private lateinit var automation: Automation
    @Mock private lateinit var activePlugin: ActivePlugin
    @Mock private lateinit var profileRepository: ProfileRepository
    @Mock private lateinit var profileUtil: ProfileUtil
    @Mock private lateinit var sceneRepository: SceneStore
    @Mock private lateinit var elementAvailability: ElementAvailability

    private lateinit var sut: QuickLaunchResolver

    private val twoActions = listOf(SceneAction.SmbToggle(enabled = false), SceneAction.LoopModeChange(mode = RM.Mode.CLOSED_LOOP_LGS))

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        sut = QuickLaunchResolver(
            preferences, quickWizard, automation, activePlugin, profileRepository, profileUtil,
            sceneRepository, generatedTextResolver(), elementAvailability
        )
    }

    // The name alone ("Eating soon") does not say what the button sets; the target was only visible
    // in the confirmation after pressing it.
    @Test
    fun `temp target preset description has the target and the duration`() {
        val preset = TTPreset(id = "p1", name = "Eating soon", reason = TT.Reason.EATING_SOON, targetValue = 90.0, duration = T.mins(45).msecs())
        whenever(preferences.get(StringNonKey.TempTargetPresets)).thenReturn(listOf(preset).toJson())
        whenever(profileUtil.fromMgdlToStringWithUnits(90.0)).thenReturn("5.0 mmol/L")

        assertThat(sut.resolveDescription(QuickLaunchAction.TempTargetPreset("p1"))).isEqualTo("5.0 mmol/L, 45 min")
    }

    @Test
    fun `unknown temp target preset has no description`() {
        whenever(preferences.get(StringNonKey.TempTargetPresets)).thenReturn("[]")

        assertThat(sut.resolveDescription(QuickLaunchAction.TempTargetPreset("missing"))).isNull()
    }

    // Same line as Manage -> Scenes, so a scene reads the same on every surface.
    @Test
    fun `scene description is the scene summary`() {
        whenever(sceneRepository.getScene("s1")).thenReturn(
            Scene(id = "s1", name = "Night", defaultDurationMinutes = 120, actions = twoActions)
        )

        assertThat(sut.resolveDescription(QuickLaunchAction.SceneAction("s1"))).isEqualTo("2 actions, 2 h")
    }

    @Test
    fun `scene description names the follow-up scene`() {
        whenever(sceneRepository.getScene("s1")).thenReturn(
            Scene(id = "s1", name = "Night", defaultDurationMinutes = 120, actions = twoActions, endAction = SceneEndAction.ChainScene("s2"))
        )
        whenever(sceneRepository.getScene("s2")).thenReturn(Scene(id = "s2", name = "Cooldown"))

        assertThat(sut.resolveDescription(QuickLaunchAction.SceneAction("s1"))).isEqualTo("2 actions, 2 h → Cooldown")
    }

    // A glance surface says nothing about a deleted follow-up; the scene list is where that is reported.
    @Test
    fun `scene description leaves out a deleted follow-up`() {
        whenever(sceneRepository.getScene("s1")).thenReturn(
            Scene(id = "s1", name = "Night", defaultDurationMinutes = 120, actions = twoActions, endAction = SceneEndAction.ChainScene("gone"))
        )
        whenever(sceneRepository.getScene("gone")).thenReturn(null)

        assertThat(sut.resolveDescription(QuickLaunchAction.SceneAction("s1"))).isEqualTo("2 actions, 2 h")
    }

    @Test
    fun `indefinite scene says so instead of a duration`() {
        whenever(sceneRepository.getScene("s1")).thenReturn(
            Scene(id = "s1", name = "Night", defaultDurationMinutes = 0, actions = twoActions)
        )

        assertThat(sut.resolveDescription(QuickLaunchAction.SceneAction("s1"))).isEqualTo("2 actions, Indefinite (manual end)")
    }

    @Test
    fun `unknown scene has no description`() {
        whenever(sceneRepository.getScene("missing")).thenReturn(null)

        assertThat(sut.resolveDescription(QuickLaunchAction.SceneAction("missing"))).isNull()
    }

    // The toolbar shows a QuickWizard entry only when its overview button would show: inside its
    // time window, for the phone, and not in its cool-down. isActive() holds all three rules.
    @Test
    fun `QuickWizard entry is shown when it is active now`() {
        val entry = mock<QuickWizardEntry>()
        whenever(entry.isActive()).thenReturn(true)
        whenever(quickWizard.get("qw-1")).thenReturn(entry)

        assertThat(sut.isShownNow(QuickLaunchAction.QuickWizardAction("qw-1"))).isTrue()
    }

    @Test
    fun `QuickWizard entry is hidden when it is not active now`() {
        val entry = mock<QuickWizardEntry>()
        whenever(entry.isActive()).thenReturn(false)
        whenever(quickWizard.get("qw-1")).thenReturn(entry)

        assertThat(sut.isShownNow(QuickLaunchAction.QuickWizardAction("qw-1"))).isFalse()
    }

    // Deleted on the main phone and synced here: hidden at once, before the next refresh prunes it.
    @Test
    fun `deleted QuickWizard entry is hidden`() {
        whenever(quickWizard.get("gone")).thenReturn(null)

        assertThat(sut.isShownNow(QuickLaunchAction.QuickWizardAction("gone"))).isFalse()
    }

    @Test
    fun `other actions are not limited by time`() {
        assertThat(sut.isShownNow(QuickLaunchAction.Fill)).isTrue()
        assertThat(sut.isShownNow(QuickLaunchAction.SceneAction("s1"))).isTrue()
    }

    // Fill and battery change follow the pump: a patch pump does both in its own wizard.
    @Test
    fun `static action is valid only when its element is available`() {
        whenever(elementAvailability.isAvailable(ElementType.FILL)).thenReturn(false)
        whenever(elementAvailability.isAvailable(ElementType.BATTERY_CHANGE)).thenReturn(true)

        assertThat(sut.isValid(QuickLaunchAction.Fill)).isFalse()
        assertThat(sut.isValid(QuickLaunchAction.BatteryChange)).isTrue()
    }
}
