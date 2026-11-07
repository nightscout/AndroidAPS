package app.aaps.ui.compose.tempTarget

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.aaps.core.data.model.TT
import app.aaps.core.data.model.TTPreset
import app.aaps.core.ui.compose.ScreenMode
import app.aaps.ui.compose.testing.AapsScreenFixture
import app.aaps.ui.compose.testing.TempTargetManagementViewModelFixture
import app.aaps.ui.compose.testing.setAapsContent
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Robolectric composable test for [TempTargetManagementScreen].
 *
 * The screen decides which controls a user is allowed to reach, and every one of those decisions is
 * about safety or about losing work:
 *  - PLAY mode hides the whole edit toolbar and offers "switch to edit" instead.
 *  - A client whose master is unreachable must not be able to Activate, Cancel or edit a preset -
 *    those all need the master - and must say why.
 *  - A built-in preset cannot be deleted, so its Remove button is disabled rather than absent.
 *  - Save only appears once the editor really differs from what is stored.
 *  - Sort mode replaces the whole top bar, and hides the action buttons that would otherwise float
 *    over the sort controls.
 *
 * Every label is read from the real resources, so a translation edit cannot make the test lie.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class TempTargetManagementScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var screen: AapsScreenFixture
    private lateinit var viewModels: TempTargetManagementViewModelFixture
    private lateinit var context: Context

    private lateinit var switchToEdit: String
    private lateinit var moreOptions: String
    private lateinit var removeLabel: String
    private lateinit var activateLabel: String
    private lateinit var cancelLabel: String
    private lateinit var saveLabel: String
    private lateinit var reorderLabel: String
    private lateinit var okLabel: String
    private lateinit var presetSettings: String
    private lateinit var noRecords: String
    private lateinit var offlineBanner: String
    private lateinit var removeRecord: String

    private val now = TempTargetManagementViewModelFixture.NOW

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        screen = AapsScreenFixture()
        viewModels = TempTargetManagementViewModelFixture(screen)
        context = RuntimeEnvironment.getApplication()
        switchToEdit = context.getString(app.aaps.core.ui.R.string.switch_to_edit)
        moreOptions = context.getString(app.aaps.core.ui.R.string.more_options)
        removeLabel = context.getString(app.aaps.ui.R.string.remove_label)
        activateLabel = context.getString(app.aaps.ui.R.string.activate_label)
        cancelLabel = context.getString(app.aaps.core.ui.R.string.cancel)
        saveLabel = context.getString(app.aaps.core.ui.R.string.save)
        reorderLabel = context.getString(app.aaps.core.ui.R.string.reorder)
        okLabel = context.getString(app.aaps.core.ui.R.string.ok)
        presetSettings = context.getString(app.aaps.core.ui.R.string.preset_settings)
        noRecords = context.getString(app.aaps.ui.R.string.no_records_available)
        offlineBanner = context.getString(app.aaps.core.ui.R.string.master_offline_banner)
        removeRecord = context.getString(app.aaps.core.ui.R.string.removerecord)
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun builtIn(id: String, reason: TT.Reason) = TTPreset(
        id = id, reason = reason, isDeletable = false, targetValue = 100.0, duration = 3_600_000L
    )

    private fun custom(id: String) = TTPreset(
        id = id, name = id, reason = TT.Reason.CUSTOM, isDeletable = true,
        targetValue = 110.0, duration = 1_800_000L
    )

    private val builtIns = listOf(
        builtIn("eating_soon", TT.Reason.EATING_SOON),
        builtIn("activity", TT.Reason.ACTIVITY),
        builtIn("hypo", TT.Reason.HYPOGLYCEMIA)
    )

    private fun activeTt(durationMs: Long = 1_800_000L) = TT(
        timestamp = now, reason = TT.Reason.CUSTOM, highTarget = 140.0, lowTarget = 140.0,
        duration = durationMs
    )

    private fun setScreen(
        viewModel: TempTargetManagementViewModel,
        initialMode: ScreenMode = ScreenMode.EDIT,
        onNavigateBack: () -> Unit = {},
        onRequestEditMode: () -> Unit = {}
    ) {
        compose.setAapsContent(screen) {
            TempTargetManagementScreen(
                viewModel = viewModel,
                initialMode = initialMode,
                onNavigateBack = onNavigateBack,
                onRequestEditMode = onRequestEditMode
            )
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Edit mode versus play mode
    // ---------------------------------------------------------------------------------------------

    @Test
    fun editModeOffersTheEditToolbarAndTheActivateButton() {
        setScreen(viewModels.build(presets = builtIns))

        compose.onNodeWithContentDescription(moreOptions).assertIsDisplayed()
        compose.onNodeWithContentDescription(removeLabel).assertIsDisplayed()
        compose.onNodeWithContentDescription(activateLabel).assertIsDisplayed()
        compose.onNodeWithContentDescription(switchToEdit).assertDoesNotExist()
    }

    @Test
    fun playModeHidesTheEditToolbarAndOffersSwitchToEdit() {
        setScreen(viewModels.build(presets = builtIns), initialMode = ScreenMode.PLAY)

        compose.onNodeWithContentDescription(switchToEdit).assertIsDisplayed()
        // The whole editing toolbar is gone, not merely disabled.
        compose.onNodeWithContentDescription(removeLabel).assertDoesNotExist()
        compose.onNodeWithContentDescription(moreOptions).assertDoesNotExist()
        // Activating is still allowed from play mode.
        compose.onNodeWithContentDescription(activateLabel).assertIsDisplayed()
    }

    @Test
    fun switchToEditReportsBackToTheHost() {
        var requested = false
        setScreen(
            viewModels.build(presets = builtIns), initialMode = ScreenMode.PLAY,
            onRequestEditMode = { requested = true }
        )

        compose.onNodeWithContentDescription(switchToEdit).performClick()

        assertThat(requested).isTrue()
    }

    // ---------------------------------------------------------------------------------------------
    // Deleting a preset
    // ---------------------------------------------------------------------------------------------

    @Test
    fun aBuiltInPresetCannotBeRemoved() {
        // Loading selects the first preset, which is a built-in here.
        setScreen(viewModels.build(presets = builtIns))

        compose.onNodeWithContentDescription(removeLabel).assertIsNotEnabled()
    }

    @Test
    fun aCustomPresetCanBeRemovedAndAsksFirst() {
        setScreen(viewModels.build(presets = listOf(custom("mine"))))

        compose.onNodeWithContentDescription(removeLabel).assertIsEnabled()
        compose.onNodeWithContentDescription(removeLabel).performClick()

        // Removing a preset is destructive, so it must be confirmed rather than done on the tap.
        compose.onNodeWithText(removeRecord).assertIsDisplayed()
    }

    // ---------------------------------------------------------------------------------------------
    // Save
    // ---------------------------------------------------------------------------------------------

    @Test
    fun saveIsHiddenWhileTheEditorMatchesWhatIsStored() {
        setScreen(viewModels.build(presets = listOf(custom("mine"))))

        compose.onNodeWithContentDescription(saveLabel).assertDoesNotExist()
    }

    @Test
    fun saveAppearsOnceTheEditorWasChanged() {
        val viewModel = viewModels.build(presets = listOf(custom("mine")))
        viewModel.updateEditorName("renamed")

        setScreen(viewModel)

        compose.onNodeWithContentDescription(saveLabel).assertIsDisplayed()
    }

    // ---------------------------------------------------------------------------------------------
    // The active temp target
    // ---------------------------------------------------------------------------------------------

    @Test
    fun theCancelButtonIsOnlyThereWhileATargetIsRunning() {
        setScreen(viewModels.build(presets = builtIns))

        compose.onNodeWithContentDescription(cancelLabel).assertDoesNotExist()
    }

    @Test
    fun aRunningTargetCanBeCancelled() {
        setScreen(viewModels.build(presets = builtIns, activeTT = activeTt()))

        compose.onNodeWithContentDescription(cancelLabel).assertIsDisplayed()
    }

    // ---------------------------------------------------------------------------------------------
    // A client whose master is offline
    // ---------------------------------------------------------------------------------------------

    @Test
    fun anOfflineClientSaysWhyAndHidesEveryMasterBoundControl() {
        screen.asOfflineClient()

        setScreen(viewModels.build(presets = builtIns, activeTT = activeTt()))

        compose.onNodeWithText(offlineBanner).assertIsDisplayed()
        // Activate and Cancel both go to the master, so neither may be offered.
        compose.onNodeWithContentDescription(activateLabel).assertDoesNotExist()
        compose.onNodeWithContentDescription(cancelLabel).assertDoesNotExist()
        // The editor is hidden too: nothing typed there could be saved or activated.
        compose.onNodeWithText(presetSettings).assertDoesNotExist()
        // Offline forces play mode, so the edit toolbar is gone as well.
        compose.onNodeWithContentDescription(removeLabel).assertDoesNotExist()
    }

    @Test
    fun aReachableMasterOnAClientLeavesEverythingUsable() {
        // Same client build, master reachable: nothing is gated and no banner is shown.
        whenever(screen.config.AAPSCLIENT).thenReturn(true)

        setScreen(viewModels.build(presets = builtIns, activeTT = activeTt()))

        compose.onNodeWithText(offlineBanner).assertDoesNotExist()
        compose.onNodeWithContentDescription(activateLabel).assertIsDisplayed()
        compose.onNodeWithContentDescription(cancelLabel).assertIsDisplayed()
        compose.onNodeWithText(presetSettings).assertIsDisplayed()
    }

    // ---------------------------------------------------------------------------------------------
    // The empty list and sort mode
    // ---------------------------------------------------------------------------------------------

    @Test
    fun anEmptyPresetListSaysSoInsteadOfShowingNothing() {
        setScreen(viewModels.build(presets = emptyList()))

        compose.onNodeWithText(noRecords).assertIsDisplayed()
    }

    @Test
    fun sortIsOfferedButDisabledWhileThereIsNothingToRearrange() {
        // Only the three built-ins: they keep their positions, so there is no move to make.
        setScreen(viewModels.build(presets = builtIns))

        compose.onNodeWithContentDescription(moreOptions).performClick()

        compose.onNodeWithText(reorderLabel).assertIsNotEnabled()
    }

    @Test
    fun sortModeReplacesTheTopBarAndHidesTheFloatingActions() {
        setScreen(viewModels.build(presets = builtIns + listOf(custom("a"), custom("b"))))

        compose.onNodeWithContentDescription(moreOptions).performClick()
        compose.onNodeWithText(reorderLabel).assertIsEnabled()
        compose.onNodeWithText(reorderLabel).performClick()

        // Confirm and cancel replace back and the overflow menu.
        compose.onNodeWithContentDescription(okLabel).assertIsDisplayed()
        compose.onNodeWithContentDescription(cancelLabel).assertIsDisplayed()
        compose.onNodeWithContentDescription(moreOptions).assertDoesNotExist()
        // The buttons that float over the cards would sit on top of the sort controls.
        compose.onNodeWithContentDescription(activateLabel).assertDoesNotExist()
        compose.onNodeWithContentDescription(removeLabel).assertDoesNotExist()
        // The editor gives its Save slot up to Done, so an edit made there could not be kept.
        compose.onNodeWithText(presetSettings).assertDoesNotExist()
    }
}
