package app.aaps.ui.compose.testing

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import app.aaps.core.interfaces.automation.Automation
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.ProfileRepository
import app.aaps.core.interfaces.scenes.SceneStore
import app.aaps.core.keys.StringNonKey
import app.aaps.core.keys.interfaces.VisibilityContext
import app.aaps.core.objects.wizard.QuickWizard
import app.aaps.ui.compose.quickLaunch.QuickLaunchAction
import app.aaps.ui.compose.quickLaunch.QuickLaunchConfigViewModel
import app.aaps.ui.compose.quickLaunch.QuickLaunchResolver
import app.aaps.ui.compose.quickLaunch.QuickLaunchSerializer
import app.aaps.ui.compose.quickLaunch.ResolvedQuickLaunchItem
import kotlinx.coroutines.flow.MutableStateFlow
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * Builds a real [QuickLaunchConfigViewModel] over an empty world.
 *
 * The view model is the real one: the screen calls back into it for every add, remove and move, and
 * the list it renders is what `loadState()` produced. Only the sources it reads are mocked, and each
 * of them starts empty so a test adds exactly the one thing it is about.
 *
 * The resolver is stubbed to hand back the action it was given with a predictable label, so a test
 * can find an item on screen without depending on how the real resolver names things - that naming
 * is the resolver's own concern and has its own tests.
 */
class QuickLaunchConfigViewModelFixture(private val screen: AapsScreenFixture) {

    val quickWizard: QuickWizard = mock()
    val automation: Automation = mock()
    val activePlugin: ActivePlugin = mock()
    val profileRepository: ProfileRepository = mock()
    val sceneStore: SceneStore = mock()
    val resolver: QuickLaunchResolver = mock()
    val visibilityContext: VisibilityContext = mock()

    /** How the stubbed resolver labels an action. Override before [build] to look for your own text. */
    var labelOf: (QuickLaunchAction) -> String = { it.dynamicId ?: it.typeId }

    /** The second line under the label, or null for none. */
    var descriptionOf: (QuickLaunchAction) -> String? = { null }

    /**
     * Stands in for the stored preference. The screen writes and then immediately reads back - every
     * add, remove and move goes through `saveAndReload` - so a mock that always answers the original
     * value would show the list never changing, which is not what a user sees.
     */
    private var storedJson: String = "[]"

    init {
        whenever(screen.preferences.get(StringNonKey.QuickLaunchActions)).thenAnswer { storedJson }
        doAnswer { invocation ->
            storedJson = invocation.getArgument(1)
            Unit
        }.whenever(screen.preferences).put(eq(StringNonKey.QuickLaunchActions), any<String>())
        whenever(screen.preferences.get(StringNonKey.TempTargetPresets)).thenReturn("[]")
        whenever(quickWizard.list()).thenReturn(arrayListOf())
        whenever(automation.executionEnabled).thenReturn(false)
        whenever(activePlugin.getPluginsList()).thenReturn(arrayListOf())
        whenever(profileRepository.profiles).thenReturn(MutableStateFlow(emptyList()))
        whenever(sceneStore.getScenes()).thenReturn(emptyList())
        whenever(resolver.resolveItem(any())).thenAnswer { invocation ->
            val action = invocation.getArgument<QuickLaunchAction>(0)
            ResolvedQuickLaunchItem(
                action = action,
                label = labelOf(action),
                icon = Icons.Default.Add,
                description = descriptionOf(action)
            )
        }
    }

    /**
     * A client that has not been paired with a master yet. Every action gated
     * `MASTER_OR_PAIRED_CLIENT` is then filtered out, which is what the screen's category sections
     * are meant to react to.
     */
    fun asUnpairedClient() {
        whenever(visibilityContext.isClient).thenReturn(true)
        whenever(visibilityContext.masterOrPairedClient).thenReturn(false)
    }

    /** Puts [selected] into the stored preference, through the real serializer the screen also uses. */
    fun givenSelected(selected: List<QuickLaunchAction>) {
        storedJson = QuickLaunchSerializer.toJson(selected + QuickLaunchAction.QuickLaunchConfig)
    }

    fun build(): QuickLaunchConfigViewModel = QuickLaunchConfigViewModel(
        screen.preferences, quickWizard, automation, activePlugin, profileRepository,
        sceneStore, resolver, visibilityContext
    )
}
