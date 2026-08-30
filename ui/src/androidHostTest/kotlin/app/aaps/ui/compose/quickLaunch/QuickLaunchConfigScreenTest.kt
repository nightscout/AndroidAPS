package app.aaps.ui.compose.quickLaunch

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import app.aaps.core.keys.StringNonKey
import app.aaps.core.objects.wizard.QuickWizardEntry
import app.aaps.ui.compose.testing.AapsScreenFixture
import app.aaps.ui.compose.testing.QuickLaunchConfigViewModelFixture
import app.aaps.ui.compose.testing.setAapsContent
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Robolectric composable test for [QuickLauchConfigScreen].
 *
 * This screen decides what the user's toolbar will contain, so the things worth pinning are what it
 * offers and what it writes back:
 *  - an empty selection says so rather than showing an empty gap;
 *  - a category section is hidden completely while it has nothing in it, instead of leaving a
 *    heading with no rows under it;
 *  - Add and Remove really change the stored list, and the configuration button stays last so the
 *    user can always get back here;
 *  - only a profile entry offers the preset editor, and that editor calls a duration of zero
 *    "permanent" rather than showing a bare 0.
 *
 * Every label is read from the real resources, so a translation edit cannot make the test lie.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class QuickLaunchConfigScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var screen: AapsScreenFixture
    private lateinit var viewModels: QuickLaunchConfigViewModelFixture
    private lateinit var context: Context

    private lateinit var selectedHeader: String
    private lateinit var noItems: String
    private lateinit var quickWizardHeader: String
    private lateinit var treatmentHeader: String
    private lateinit var careHeader: String
    private lateinit var editPreset: String
    private lateinit var permanent: String
    private lateinit var addLabel: String
    private lateinit var removeLabel: String

    @Before
    fun setUp() {
        screen = AapsScreenFixture()
        viewModels = QuickLaunchConfigViewModelFixture(screen)
        context = RuntimeEnvironment.getApplication()
        selectedHeader = context.getString(app.aaps.ui.R.string.quick_launch_selected_actions)
        noItems = context.getString(app.aaps.ui.R.string.quick_launch_no_dynamic_items)
        quickWizardHeader = context.getString(app.aaps.ui.R.string.quick_launch_category_quick_wizard)
        treatmentHeader = context.getString(app.aaps.ui.R.string.quick_launch_category_treatment)
        careHeader = context.getString(app.aaps.ui.R.string.quick_launch_category_care)
        editPreset = context.getString(app.aaps.ui.R.string.quick_launch_edit_profile_preset)
        permanent = context.getString(app.aaps.ui.R.string.quick_launch_profile_permanent)
        addLabel = context.getString(app.aaps.core.ui.R.string.add)
        removeLabel = context.getString(app.aaps.core.ui.R.string.remove)
    }

    private fun setScreen(onNavigateBack: () -> Unit = {}) {
        val viewModel = viewModels.build()
        compose.setAapsContent(screen) {
            QuickLauchConfigScreen(viewModel = viewModel, onNavigateBack = onNavigateBack)
        }
    }

    private fun storedActions(): List<QuickLaunchAction> {
        val captor = argumentCaptor<String>()
        verify(screen.preferences).put(eq(StringNonKey.QuickLaunchActions), captor.capture())
        return QuickLaunchSerializer.fromJson(captor.lastValue)
    }

    // ---------------------------------------------------------------------------------------------
    // The selected list
    // ---------------------------------------------------------------------------------------------

    @Test
    fun anEmptySelectionSaysSoUnderItsOwnHeader() {
        setScreen()

        compose.onNodeWithText(selectedHeader).assertIsDisplayed()
        compose.onNodeWithText(noItems).assertIsDisplayed()
    }

    @Test
    fun aSelectedActionIsListedWithItsLabelAndDescription() {
        // Only this one action gets the special text, so finding it on screen means the selected row
        // rendered it - not some other row that happens to share a label.
        viewModels.labelOf = { if (it == QuickLaunchAction.Wizard) "Bolus wizard" else it.typeId }
        viewModels.descriptionOf = { if (it == QuickLaunchAction.Wizard) "Opens the wizard" else null }
        viewModels.givenSelected(listOf(QuickLaunchAction.Wizard))

        setScreen()

        compose.onNodeWithText("Bolus wizard").assertIsDisplayed()
        compose.onNodeWithText("Opens the wizard").assertIsDisplayed()
        compose.onNodeWithText(noItems).assertDoesNotExist()
    }

    @Test
    fun theConfigurationButtonItselfIsNeverListedAsSelected() {
        // Only the config action is stored, so the list is really empty for the user.
        viewModels.givenSelected(emptyList())

        setScreen()

        compose.onNodeWithText(noItems).assertIsDisplayed()
    }

    // ---------------------------------------------------------------------------------------------
    // Sections that have nothing in them
    // ---------------------------------------------------------------------------------------------

    @Test
    fun theCarePortalSectionIsGoneWhenEverythingInItIsFilteredOut() {
        // An unpaired client cannot send any care portal entry, so the whole heading goes rather than
        // leaving a heading with nothing under it.
        viewModels.asUnpairedClient()

        setScreen()

        compose.onNodeWithText(careHeader).assertDoesNotExist()
        // Treatment keeps CGM and calibration, which are not gated, so that heading stays.
        compose.onNodeWithText(treatmentHeader).assertIsDisplayed()
    }

    @Test
    fun aQuickWizardEntryIsOfferedUnderItsOwnHeading() {
        val entry: QuickWizardEntry = mock()
        whenever(entry.guid()).thenReturn("qw-1")
        whenever(viewModels.quickWizard.list()).thenReturn(arrayListOf(entry))
        viewModels.labelOf = { action ->
            if (action is QuickLaunchAction.QuickWizardAction) "Breakfast" else action.typeId
        }

        setScreen()
        // The section sits below the static ones, so the list has to be scrolled to reach it.
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(quickWizardHeader))

        compose.onNodeWithText(quickWizardHeader).assertIsDisplayed()
        compose.onNodeWithText("Breakfast").assertIsDisplayed()
    }

    // ---------------------------------------------------------------------------------------------
    // Adding and removing
    // ---------------------------------------------------------------------------------------------

    @Test
    fun removingTheSelectedActionWritesTheShorterListBackAndSaysTheListIsEmpty() {
        viewModels.givenSelected(listOf(QuickLaunchAction.Wizard))

        setScreen()
        compose.onNodeWithContentDescription(removeLabel).performClick()

        // The configuration button is not a user entry, so what is left is an empty selection.
        assertThat(storedActions()).containsExactly(QuickLaunchAction.QuickLaunchConfig)
        compose.onNodeWithText(noItems).assertIsDisplayed()
    }

    @Test
    fun addingAnAvailableActionAppendsItAndKeepsTheConfigButtonLast() {
        viewModels.givenSelected(emptyList())

        setScreen()
        compose.onAllNodesWithContentDescription(addLabel)[0].performClick()

        val stored = storedActions()
        assertThat(stored).hasSize(2)
        // Without this the user could pin an action ahead of the button that reopens this screen.
        assertThat(stored.last()).isEqualTo(QuickLaunchAction.QuickLaunchConfig)
    }

    // ---------------------------------------------------------------------------------------------
    // The profile preset editor
    // ---------------------------------------------------------------------------------------------

    @Test
    fun onlyAProfileEntryOffersThePresetEditor() {
        viewModels.givenSelected(listOf(QuickLaunchAction.Carbs))

        setScreen()

        compose.onNodeWithContentDescription(editPreset).assertDoesNotExist()
    }

    @Test
    fun aProfileEntryOpensTheEditorWhichCallsZeroDurationPermanent() {
        viewModels.givenSelected(listOf(QuickLaunchAction.ProfileAction("Morning", 100, 0)))

        setScreen()
        compose.onNodeWithContentDescription(editPreset).performClick()

        // The dialog names the profile it is about, and says what a duration of 0 means.
        compose.onNodeWithText("Morning").assertIsDisplayed()
        compose.onNodeWithText(permanent).assertIsDisplayed()
    }
}
