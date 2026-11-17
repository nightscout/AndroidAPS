package app.aaps.ui.compose.wizardDialog

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import app.aaps.core.interfaces.resources.TextRefIdRegistry
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.ui.UiStringIds
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
import app.aaps.ui.R as UiR

/** Robolectric composable test for [WizardDialogContent]. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class WizardDialogContentTest {

    @get:Rule
    val compose = createComposeRule()

    private val decimalFormatter: DecimalFormatter = mock()
    private var onLayout: (() -> Unit)? = null

    private lateinit var closeLabel: String
    private lateinit var calculationTitle: String
    private lateinit var recordOnlyBanner: SemanticsMatcher

    /** Screen reader name of the plus button of the BG input. It exists only while the BG input is open. */
    private lateinit var bgPlusButton: String
    private lateinit var bgExpandButton: String

    /** Result extras for 71% and a +1 U correction, built from the same templates as the screen. */
    private lateinit var percentageAndCorrection: String

    @Before
    fun setUp() {
        // The app registers this at start. Without it every UiStrings text shows its key name.
        TextRefIdRegistry.register("ui") { name -> UiStringIds.idOf(name) }
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
        // The chevron is what tells the two states apart, by which way it points. The +/- are in
        // both: the row can be nudged while closed, and the open field has its own pair with the
        // same spoken names.
        bgExpandButton = ctx.getString(CoreUiR.string.a11y_expand_button_description, ctx.getString(CoreUiR.string.wizard_bg_label))
        percentageAndCorrection = ctx.getString(
            CoreUiR.string.value_in_parentheses,
            ctx.getString(
                UiR.string.wizard_percentage_and_correction,
                ctx.getString(CoreUiR.string.units_format_percent, 71),
                ctx.getString(CoreUiR.string.format_insulin_units_signed, 1.0)
            )
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
        // Closed means the pencil is still offered, not that the row has no controls.
        compose.onNodeWithContentDescription(bgExpandButton).assertExists()
    }

    @Test
    fun bgInputOpensByItselfWhenThereIsNoBg() {
        setWizard(uiState = { loaded(hasBgData = false) })
        // Open: the field is there instead of the pencil, and the +/- belong to the field.
        compose.onNodeWithContentDescription(bgExpandButton).assertDoesNotExist()
        compose.onNodeWithContentDescription(bgPlusButton).assertExists()
    }

    /** Only a tap on "Change" may scroll to the BG input. When it opens by itself, the top of the screen stays. */
    @Test
    fun bgInputThatOpensByItselfDoesNotScroll() {
        setWizard(uiState = { loaded(hasBgData = false) })
        compose.mainClock.advanceTimeBy(2_000)
        compose.onNodeWithText(calculationTitle).assertIsDisplayed()
    }

    /** Percentage and correction are one text from the templates, not "(", values and ")" side by side. */
    @Test
    fun resultShowsPercentageAndCorrectionInOneText() {
        setWizard(uiState = { loaded().copy(hasResult = true, totalInsulin = 2.0, percentage = 71, directCorrection = 1.0) })
        compose.onNodeWithText(percentageAndCorrection).assertIsDisplayed()
    }

    /** A tap on the profile row opens the list straight away, and one tap on a name picks it. Before, the list needed a tap more. */
    @Test
    fun profileRowOpensTheListAndOneTapPicksAProfile() {
        var picked = -1
        setWizard(uiState = { loaded().copy(profileNames = listOf(DAY, NIGHT)) }, onProfileSelect = { picked = it })
        // The row is one node with the current name in it. Closed, it offers to expand, which is
        // how a screen reader knows that the list is closed.
        compose.onNodeWithText(DAY)
            .performScrollTo()
            .assert(SemanticsMatcher.keyIsDefined(SemanticsActions.Expand))
            .performClick()
        // The current profile is marked in the list, also for a screen reader.
        compose.onNode(hasText(DAY) and isSelected()).assertExists()
        compose.onNode(hasText(NIGHT) and isSelected()).assertDoesNotExist()
        compose.onNodeWithText(NIGHT).performClick()
        assertThat(picked).isEqualTo(1)
        // The pick closes the list. The row still shows Day, because this state does not change.
        compose.onNodeWithText(NIGHT).assertDoesNotExist()
    }

    @Test
    fun recordOnlyBannerStaysOnScreenWhenTheUserScrollsDown() {
        setWizard(uiState = { loaded(bgAgeMinutes = 2, forcedRecordOnly = true) })
        compose.onNode(hasScrollAction()).performTouchInput { swipeUp() }
        compose.onNode(recordOnlyBanner).assertIsDisplayed()
    }

    @Test
    fun correctionButtonsStayInPlaceWhenTheCalculationArrivesAfterTheInputChange() {
        checkCorrectionButtonPositions(expanded = false)
    }

    @Test
    fun expandedCorrectionButtonsStayInPlaceWhenTheCalculationArrivesAfterTheInputChange() {
        checkCorrectionButtonPositions(expanded = true)
    }

    private fun checkCorrectionButtonPositions(expanded: Boolean) {
        var state by mutableStateOf(loaded().copy(hasResult = true, calculationExpanded = true, totalInsulin = 1.0, showNotes = true))
        setWizard(
            uiState = { state },
            onDirectCorrectionChange = { state = state.copy(directCorrection = it) }
        )
        if (expanded) {
            val ctx: Context = RuntimeEnvironment.getApplication()
            compose.onNodeWithContentDescription(
                ctx.getString(CoreUiR.string.a11y_expand_button_description, ctx.getString(CoreUiR.string.wizard_correction))
            ).performScrollTo().performClick()
        }
        val plus = compose.onNodeWithContentDescription(stepDescription(CoreUiR.string.wizard_correction, true, "0.10", "U"))
        val minus = compose.onNodeWithContentDescription(stepDescription(CoreUiR.string.wizard_correction, false, "0.10", "U"))
        plus.performScrollTo().assertIsDisplayed()
        val top = plus.fetchSemanticsNode().boundsInRoot.top
        val plusPosition = plus.fetchSemanticsNode().boundsInRoot.center
        val minusPosition = minus.fetchSemanticsNode().boundsInRoot.center
        val placedPositions = recordPlacedPositions(plus.fetchSemanticsNode())

        compose.onRoot().performTouchInput { click(plusPosition) }
        assertThat(state.directCorrection).isWithin(0.001).of(0.1)
        assertThat(plus.fetchSemanticsNode().boundsInRoot.top).isWithin(1f).of(top)
        compose.runOnIdle { state = state.copy(insulinFromCorrection = state.directCorrection) }
        plus.assertIsDisplayed()
        assertThat(plus.fetchSemanticsNode().boundsInRoot.top).isWithin(1f).of(top)

        compose.onRoot().performTouchInput { click(minusPosition) }
        compose.runOnIdle { state = state.copy(insulinFromCorrection = state.directCorrection) }
        assertThat(state.directCorrection).isEqualTo(0.0)
        plus.assertIsDisplayed()
        assertThat(plus.fetchSemanticsNode().boundsInRoot.top).isWithin(1f).of(top)
        assertPlacedPositions(placedPositions, top)
    }

    @Test
    fun percentageButtonsStayInPlaceWhenSubtotalRowsAppearAndDisappear() {
        checkPercentageButtonPositions(expanded = false)
    }

    @Test
    fun expandedPercentageButtonsStayInPlaceWhenSubtotalRowsAppearAndDisappear() {
        checkPercentageButtonPositions(expanded = true)
    }

    @Test
    fun percentageButtonsStayInPlaceAtTheBottomWhenTheScrollLimitShrinks() {
        checkPercentageButtonPositions(expanded = false, atBottom = true)
    }

    private fun checkPercentageButtonPositions(expanded: Boolean, atBottom: Boolean = false) {
        var state by mutableStateOf(
            loaded().copy(hasResult = true, calculationExpanded = true, totalInsulin = 1.0, showNotes = !atBottom, simpleMode = atBottom)
        )
        setWizard(uiState = { state }, onPercentageChange = { state = state.copy(percentage = it.toInt()) })
        if (expanded) {
            val ctx: Context = RuntimeEnvironment.getApplication()
            compose.onNodeWithContentDescription(
                ctx.getString(CoreUiR.string.a11y_expand_button_description, ctx.getString(CoreUiR.string.wizard_use_percentage))
            ).performScrollTo().performClick()
        }
        val plus = compose.onNodeWithContentDescription(stepDescription(CoreUiR.string.wizard_use_percentage, true, "5", "%"))
        val minus = compose.onNodeWithContentDescription(stepDescription(CoreUiR.string.wizard_use_percentage, false, "5", "%"))
        plus.performScrollTo().assertIsDisplayed()
        if (atBottom) {
            val scroll = compose.onNode(hasScrollAction())
            val range = scroll.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
            scroll.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, range.maxValue()) }
            compose.runOnIdle { assertThat(range.value()).isEqualTo(range.maxValue()) }
        }
        val top = plus.fetchSemanticsNode().boundsInRoot.top
        val plusPosition = plus.fetchSemanticsNode().boundsInRoot.center
        val minusPosition = minus.fetchSemanticsNode().boundsInRoot.center
        val placedPositions = recordPlacedPositions(plus.fetchSemanticsNode())

        repeat(2) {
            compose.onRoot().performTouchInput { click(plusPosition) }
            assertThat(state.percentage).isEqualTo(105)
            plus.assertIsDisplayed()
            assertThat(plus.fetchSemanticsNode().boundsInRoot.top).isWithin(1f).of(top)
            compose.onRoot().performTouchInput { click(minusPosition) }
            assertThat(state.percentage).isEqualTo(100)
            plus.assertIsDisplayed()
            assertThat(plus.fetchSemanticsNode().boundsInRoot.top).isWithin(1f).of(top)
        }
        assertPlacedPositions(placedPositions, top)

        compose.onNode(hasScrollAction()).performScrollToNode(hasText(calculationTitle))
        val titleTop = compose.onNodeWithText(calculationTitle).fetchSemanticsNode().boundsInRoot.top
        compose.runOnIdle { state = state.copy(percentage = 105) }
        compose.onNodeWithText(calculationTitle).assertIsDisplayed()
        assertThat(compose.onNodeWithText(calculationTitle).fetchSemanticsNode().boundsInRoot.top).isWithin(1f).of(titleTop)
    }

    private fun stepDescription(labelId: Int, increase: Boolean, step: String, unit: String): String {
        val ctx: Context = RuntimeEnvironment.getApplication()
        return ctx.getString(
            if (increase) CoreUiR.string.a11y_plus_button_description else CoreUiR.string.a11y_min_button_description,
            ctx.getString(labelId),
            ctx.getString(CoreUiR.string.value_with_unit, step, unit)
        )
    }

    /** Records the first placement of each state change, before any late scroll correction. */
    private fun recordPlacedPositions(button: SemanticsNode): MutableList<Float> {
        val positions = mutableListOf<Float>()
        onLayout = { positions.add(button.boundsInRoot.top) }
        return positions
    }

    private fun assertPlacedPositions(positions: List<Float>, expectedTop: Float) {
        assertThat(positions).isNotEmpty()
        positions.forEach { position -> assertThat(position).isWithin(1f).of(expectedTop) }
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
    private fun setWizard(
        uiState: () -> WizardDialogUiState,
        onNavigateBack: () -> Unit = {},
        onProfileSelect: (Int) -> Unit = {},
        onPercentageChange: (Double) -> Unit = {},
        onDirectCorrectionChange: (Double) -> Unit = {}
    ) {
        compose.setContent {
            MaterialTheme {
                val state = uiState()
                Box(Modifier.onGloballyPositioned(remember(state) { { onLayout?.invoke() } })) {
                    WizardDialogContent(
                        uiState = state,
                        decimalFormatter = decimalFormatter,
                        unitsLabel = UNITS,
                        onBgChange = {},
                        onCarbsChange = {},
                        onAddCarbs = {},
                        onCarbsTypeChange = {},
                        onPercentageChange = onPercentageChange,
                        onDirectCorrectionChange = onDirectCorrectionChange,
                        onCarbTimeChange = {},
                        onNotesChange = {},
                        onProfileSelect = onProfileSelect,
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
    }

    private companion object {

        const val UNITS = "mg/dl"
        const val DAY = "Day"
        const val NIGHT = "Night"
    }
}
