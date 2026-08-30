package app.aaps.ui.compose.testing

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.resources.TextRefIdRegistry
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.StringKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.compose.AapsTheme
import app.aaps.core.ui.compose.LocalConfig
import app.aaps.core.ui.compose.LocalDateUtil
import app.aaps.core.ui.compose.LocalMasterReachable
import app.aaps.core.ui.compose.LocalPreferences
import app.aaps.core.ui.compose.LocalProfileUtil
import app.aaps.shared.tests.stubTextRefResolution
import app.aaps.ui.UiStringIds
import kotlinx.coroutines.flow.MutableStateFlow
import org.mockito.kotlin.any
import org.mockito.kotlin.anyVararg
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RuntimeEnvironment

/**
 * The ambient environment a `:ui` screen needs before it will render in a Robolectric test.
 *
 * This is deliberately only the *environment* - the string owner registration and the
 * CompositionLocals that `AapsTheme` and the shared widgets read. It builds no ViewModel: each
 * screen has its own small builder next to it (see `TempTargetManagementViewModelFixture` and
 * `QuickLaunchConfigViewModelFixture`) so a screen only pays for the dependencies it really uses.
 *
 * Three things are needed together and each fails in a way that does not point at itself:
 *  - `TextRefIdRegistry.register("ui")`. `MainApp` does this in production. Without it every
 *    `TextRef.Named` from this module renders as its raw name, and the assertion reads
 *    "the component ... is not displayed".
 *  - a `Preferences` that answers `observe(GeneralDarkMode)`. `AapsTheme` collects it, and
 *    `LocalPreferences` has no default, so the composition throws "No Preferences provided".
 *  - a `Config`. `masterEditingEnabled()` reads `LocalConfig`, which also has no default.
 *
 * The mocks are exposed so a test can add its own stubs, and [masterReachable] lets a test render
 * the client-offline state without touching the screen.
 */
class AapsScreenFixture(
    val preferences: Preferences = mock(),
    val config: Config = mock(),
    val dateUtil: DateUtil = mock(),
    val profileUtil: ProfileUtil = mock()
) {

    /** Mirrors `NsClient.masterReachable`. Only gates anything when [config] says AAPSCLIENT. */
    var masterReachable: Boolean = true

    init {
        TextRefIdRegistry.register("ui") { name -> UiStringIds.idOf(name) }
        whenever(preferences.observe(StringKey.GeneralDarkMode)).thenReturn(MutableStateFlow("light"))
        whenever(config.AAPSCLIENT).thenReturn(false)
    }

    /** Renders as a client whose master cannot be reached - the state that disables master-bound actions. */
    fun asOfflineClient() {
        whenever(config.AAPSCLIENT).thenReturn(true)
        masterReachable = false
    }

    /** Wraps [content] in the locals and the real theme, exactly as the app shell does. */
    @Composable
    fun Content(content: @Composable () -> Unit) {
        CompositionLocalProvider(
            LocalPreferences provides preferences,
            LocalConfig provides config,
            LocalDateUtil provides dateUtil,
            LocalProfileUtil provides profileUtil,
            LocalMasterReachable provides masterReachable
        ) {
            AapsTheme { content() }
        }
    }
}

/** Sets [content] inside the fixture's environment. Keeps the two lines out of every test. */
fun ComposeContentTestRule.setAapsContent(fixture: AapsScreenFixture, content: @Composable () -> Unit) {
    setContent { fixture.Content(content) }
}

/**
 * Makes a mocked [ResourceHelper] answer from the real Android resources.
 *
 * A ViewModel that formats text through `rh` would otherwise get `null` from an unstubbed mock, and
 * the null lands far from its cause. Answering from the resources also means a test can compare
 * against `context.getString(...)` instead of a hard coded word, so a translation edit cannot make
 * the test lie.
 */
fun stubResourcesFromRobolectric(rh: ResourceHelper) {
    val context = RuntimeEnvironment.getApplication()
    whenever(rh.gs(any<Int>())).thenAnswer { invocation ->
        context.getString(invocation.getArgument<Int>(0))
    }
    whenever(rh.gs(any<Int>(), anyVararg())).thenAnswer { invocation ->
        context.getString(invocation.getArgument<Int>(0), *invocation.arguments.drop(1).toTypedArray())
    }
    // Routes the TextRef forms the screens use back to the id stubs above.
    stubTextRefResolution(rh)
}
