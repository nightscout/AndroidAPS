package app.aaps.ui.compose.wizardDialog

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import app.aaps.core.interfaces.utils.DecimalFormatter
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import app.aaps.core.interfaces.R as InterfacesR
import app.aaps.core.ui.R as CoreUiR

/** Robolectric composable test for [WizardDialogContent]. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class WizardDialogContentTest {

    @get:Rule
    val compose = createComposeRule()

    private val decimalFormatter: DecimalFormatter = mock()

    private lateinit var closeLabel: String
    private lateinit var calculationTitle: String
    private lateinit var recordOnlyBanner: SemanticsMatcher

    /** Screen reader name of the plus button of the BG input. It exists only while the BG input is open. */
    private lateinit var bgPlusButton: String

    @Before
    fun setUp() {
        val ctx: Context = RuntimeEnvironment.getApplication()
        closeLabel = ctx.getString(CoreUiR.string.close)
        calculationTitle = ctx.getString(CoreUiR.string.wizard_calculation)
        // The banner is the only node with this pane title.
        recordOnlyBanner = SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, ctx.getString(InterfacesR.string.bolus_recorded_only))
        bgPlusButton = ctx.getString(
            CoreUiR.string.a11y_plus_button_description,
            ctx.getString(CoreUiR.string.wizard_bg_label),
            ctx.getString(CoreUiR.string.value_with_unit, "1", UNITS)
        )
    }

    @Test
    fun rendersAndFiresNavigateBack() {
        var back = false
        setWizard(uiState = { WizardDialogUiState() }, onNavigateBack = { back = true })
        compose.onNodeWithContentDescription(closeLabel).assertIsDisplayed()
        compose.onNodeWithContentDescription(closeLabel).performClick()
        assertThat(back).isTrue()
    }

    /** The first frames are drawn from the default state, which has no BG. That must not open the BG input. */
    @Test
    fun bgInputStaysClosedWhenTheLoadedBgIsFresh() {
        var state by mutableStateOf(WizardDialogUiState())
        setWizard(uiState = { state })
        compose.runOnIdle { state = loaded(bgAgeMinutes = 2) }
        compose.onNodeWithContentDescription(bgPlusButton).assertDoesNotExist()
    }

    @Test
    fun bgInputOpensByItselfWhenThereIsNoBg() {
        setWizard(uiState = { loaded(hasBgData = false) })
        compose.onNodeWithContentDescription(bgPlusButton).assertExists()
    }

    /** Only a tap on "Change" may scroll to the BG input. When it opens by itself, the top of the screen stays. */
    @Test
    fun bgInputThatOpensByItselfDoesNotScroll() {
        setWizard(uiState = { loaded(hasBgData = false) })
        compose.mainClock.advanceTimeBy(2_000)
        compose.onNodeWithText(calculationTitle).assertIsDisplayed()
    }

    @Test
    fun recordOnlyBannerStaysOnScreenWhenTheUserScrollsDown() {
        setWizard(uiState = { loaded(bgAgeMinutes = 2, forcedRecordOnly = true) })
        compose.onNode(hasScrollAction()).performTouchInput { swipeUp() }
        compose.onNode(recordOnlyBanner).assertIsDisplayed()
    }

    private fun loaded(hasBgData: Boolean = true, bgAgeMinutes: Int = 0, forcedRecordOnly: Boolean = false) =
        WizardDialogUiState(
            initialized = true,
            bg = if (hasBgData) 120.0 else 0.0,
            maxCarbs = 100,
            maxBolus = 10.0,
            hasBgData = hasBgData,
            bgAgeMinutes = bgAgeMinutes,
            forcedRecordOnly = forcedRecordOnly
        )

    /** [uiState] is read inside the composition, so a test can change the state after the first frame. */
    private fun setWizard(uiState: () -> WizardDialogUiState, onNavigateBack: () -> Unit = {}) {
        compose.setContent {
            MaterialTheme {
                WizardDialogContent(
                    uiState = uiState(),
                    decimalFormatter = decimalFormatter,
                    unitsLabel = UNITS,
                    onBgChange = {},
                    onCarbsChange = {},
                    onAddCarbs = {},
                    onCarbsTypeChange = {},
                    onPercentageChange = {},
                    onDirectCorrectionChange = {},
                    onCarbTimeChange = {},
                    onNotesChange = {},
                    onProfileSelect = {},
                    onBgToggle = {},
                    onTTToggle = {},
                    onTrendToggle = {},
                    onIOBToggle = {},
                    onCOBToggle = {},
                    onAlarmToggle = {},
                    onCalculationExpandToggle = {},
                    onNavigateBack = onNavigateBack,
                    onConfirmClick = {},
                    onSettingsClick = {}
                )
            }
        }
    }

    private companion object {

        const val UNITS = "mg/dl"
    }
}
