package app.aaps.ui.compose.quickLaunch

import app.aaps.core.interfaces.automation.Automation
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.ProfileRepository
import app.aaps.core.interfaces.scenes.SceneStore
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.keys.interfaces.VisibilityContext
import app.aaps.core.objects.wizard.QuickWizard
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations

@OptIn(ExperimentalCoroutinesApi::class)
internal class QuickLaunchConfigViewModelTest {

    @Mock private lateinit var preferences: Preferences
    @Mock private lateinit var quickWizard: QuickWizard
    @Mock private lateinit var automation: Automation
    @Mock private lateinit var activePlugin: ActivePlugin
    @Mock private lateinit var profileRepository: ProfileRepository
    @Mock private lateinit var sceneRepository: SceneStore
    @Mock private lateinit var resolver: QuickLaunchResolver
    @Mock private lateinit var visibilityContext: VisibilityContext

    private lateinit var sut: QuickLaunchConfigViewModel

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        // No init block / viewModelScope in this VM; setMain kept for recipe consistency.
        Dispatchers.setMain(StandardTestDispatcher())
        sut = QuickLaunchConfigViewModel(
            preferences, quickWizard, automation, activePlugin, profileRepository,
            sceneRepository, resolver, visibilityContext
        )
    }

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `default uiState has empty selected and available items`() {
        val state = sut.uiState.value
        assertThat(state.selectedItems).isEmpty()
        assertThat(state.availableStaticItems).isEmpty()
        assertThat(state.availablePluginGroups).isEmpty()
    }
}
