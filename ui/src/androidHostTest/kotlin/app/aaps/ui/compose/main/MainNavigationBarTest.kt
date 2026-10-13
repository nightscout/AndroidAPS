package app.aaps.ui.compose.main

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasStateDescription
import androidx.compose.ui.test.junit4.v2.createComposeRule
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.resources.TextRefIdRegistry
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.compose.icons.IcPluginObjectives
import app.aaps.ui.R
import app.aaps.ui.UiStringIds
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * What the badges in [MainNavigationBar] say to a screen reader.
 *
 * Material3's `NavigationBarItem` wraps its whole icon slot in `clearAndSetSemantics {}` whenever the
 * item has a label, and every item here has one, so no badge can be read: the count, the progress and
 * the warning dot are all dropped before they reach the user. A blind user heard "Objectives" with no
 * idea why the button was on her screen. Each badge's meaning is therefore set as a state on the item
 * itself, outside the cleared slot, and these tests pin that down - asserting on the badge's own text
 * would pass against the unmerged tree while a screen reader still heard nothing.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class MainNavigationBarTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var availableThree: String
    private lateinit var objectivesProgress: String

    @Before
    fun setUp() {
        // What MainApp does at startup: a TextRef owned by this module resolves through the registry,
        // and without it every one of these states would read as its raw resource name.
        TextRefIdRegistry.register("ui") { name -> UiStringIds.idOf(name) }
        val app = RuntimeEnvironment.getApplication()
        // Taken from the resources rather than hard coded, so editing the wording cannot make the
        // test lie about what is spoken.
        availableThree = app.getString(R.string.a11y_badge_available, 3)
        objectivesProgress = app.getString(R.string.a11y_objectives_progress, 9, 10)
    }

    private fun objectivesPlugin(): PluginBase = mock<PluginBase>().also {
        whenever(it.pluginDescription).thenReturn(
            PluginDescription().icon(IcPluginObjectives).pluginName(CoreUiStrings.objectives)
        )
        whenever(it.name).thenReturn("Objectives")
    }

    @Test
    fun objectivesProgressIsSpokenAsAStateOnTheItem() {
        compose.setContent {
            MaterialTheme {
                MainNavigationBar(
                    onManageClick = {},
                    onTreatmentClick = {},
                    objectivesSetupPlugin = objectivesPlugin(),
                    objectivesProgressText = "9/10",
                    objectivesProgressDescription = objectivesProgress
                )
            }
        }

        compose.onNode(hasStateDescription(objectivesProgress)).assertIsDisplayed()
    }

    @Test
    fun aCountingBadgeIsSpokenAsAStateOnTheItem() {
        compose.setContent {
            MaterialTheme {
                MainNavigationBar(onManageClick = {}, onTreatmentClick = {}, quickWizardCount = 3)
            }
        }

        compose.onNode(hasStateDescription(availableThree)).assertIsDisplayed()
    }

    @Test
    fun anItemWithoutABadgeGetsNoState() {
        compose.setContent {
            MaterialTheme {
                MainNavigationBar(onManageClick = {}, onTreatmentClick = {}, quickWizardCount = 0)
            }
        }

        // Treatments has no badge at a count of zero, and Manage never has one. A state on either
        // would be read out on every pass over the bar for no reason.
        val availableZero = RuntimeEnvironment.getApplication().getString(R.string.a11y_badge_available, 0)
        compose.onAllNodes(hasStateDescription(availableZero)).assertCountEquals(0)
    }
}
