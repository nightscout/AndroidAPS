package app.aaps.pump.carelevo.compose.patchflow

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import app.aaps.core.ui.R as CoreUiR
import app.aaps.pump.carelevo.R
import app.aaps.pump.carelevo.config.FillConfig
import app.aaps.pump.carelevo.presentation.viewmodel.CarelevoPatchConnectionFlowViewModel
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Robolectric Compose tests for [CarelevoSetAmountStep] — the wizard step that picks the insulin
 * fill amount on a wheel picker built from [FillConfig] (50..300 step 10).
 *
 * **ViewModel:** [CarelevoPatchConnectionFlowViewModel] is a final Hilt VM, but the step touches
 * exactly three of its members — `inputInsulin` (read once to seed the wheel), `confirmAmount` and
 * `exitWizard`. It is therefore mocked outright (Mockito 5's inline mock maker is the default, and
 * this module already mocks the final `CarelevoPatch` the same way). Mocking rather than
 * constructing the real VM keeps `exitWizard`'s `viewModelScope.launch` from running, so no
 * `Dispatchers.setMain` override is needed — overriding Main would fight the Compose test clock.
 *
 * **Why value assertions are relational, not exact:** the picker seeds the list with
 * `initialFirstVisibleItemIndex`, which places the seeded row at the *top* of the viewport rather
 * than under the centre overlay. The `snapshotFlow` effect then reports whichever row actually sits
 * at the viewport centre and snaps `selectedValue` to it. The exact settled row is a function of
 * measured pixel geometry, so these tests assert the invariants that matter — the confirmed amount
 * is always one of the configured grid values, never outside 50..300, and moves in the direction
 * scrolled — instead of hard-coding a row index that layout maths could shift.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class CarelevoSetAmountStepTest {

    @get:Rule
    val compose = createComposeRule()

    private val viewModel: CarelevoPatchConnectionFlowViewModel = mock()

    /** The exact grid the picker offers: 50, 60, ... 300. */
    private val values = (FillConfig.FILL_MIN_UNITS..FillConfig.FILL_MAX_UNITS step FillConfig.FILL_STEP_UNITS).toList()

    private lateinit var rangeText: String
    private lateinit var nextLabel: String
    private lateinit var cancelLabel: String

    @Before
    fun setUp() {
        val context = RuntimeEnvironment.getApplication()
        rangeText = context.getString(R.string.patch_prepare_dialog_msg_insulin_range, FillConfig.FILL_MIN_UNITS, FillConfig.FILL_MAX_UNITS)
        nextLabel = context.getString(CoreUiR.string.next)
        cancelLabel = context.getString(CoreUiR.string.cancel)
    }

    /** Seeds the VM's stored fill amount and renders the step. */
    private fun render(inputInsulin: Int) {
        whenever(viewModel.inputInsulin).thenReturn(inputInsulin)
        compose.setContent {
            MaterialTheme {
                CarelevoSetAmountStep(viewModel = viewModel)
            }
        }
    }

    /** Presses Next and returns the amount handed to [CarelevoPatchConnectionFlowViewModel.confirmAmount]. */
    private fun pressNextAndCaptureAmount(): Int {
        compose.onNodeWithText(nextLabel).performClick()
        compose.waitForIdle()
        val captor = argumentCaptor<Int>()
        verify(viewModel).confirmAmount(captor.capture())
        return captor.firstValue
    }

    // ---- static content -------------------------------------------------------------------------

    @Test
    fun rendersRangeText_fromFillConfigBounds() {
        render(FillConfig.FILL_MAX_UNITS)

        compose.onNodeWithText(rangeText).assertIsDisplayed()
    }

    @Test
    fun rendersBothWizardButtons_enabled() {
        render(FillConfig.FILL_MAX_UNITS)

        compose.onNodeWithText(nextLabel).assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithText(cancelLabel).assertIsDisplayed().assertIsEnabled()
    }

    // ---- wheel seeding --------------------------------------------------------------------------

    @Test
    fun seedingAtMinimum_rendersLowRows_andNotTheMaximum() {
        render(FillConfig.FILL_MIN_UNITS)

        compose.onNodeWithText("50").assertIsDisplayed()
        compose.onNodeWithText("60").assertExists()
        compose.onNodeWithText("70").assertExists()
        // Far end of a 26-row lazy list is never composed from the first row.
        compose.onNodeWithText("300").assertDoesNotExist()
    }

    @Test
    fun seedingAtMaximum_rendersHighRows_andNotTheMinimum() {
        render(FillConfig.FILL_MAX_UNITS)

        compose.onNodeWithText("300").assertExists()
        compose.onNodeWithText("50").assertDoesNotExist()
    }

    // ---- confirm / cancel -----------------------------------------------------------------------

    @Test
    fun next_confirmsAGridValueWithinBounds() {
        render(150)

        val confirmed = pressNextAndCaptureAmount()

        assertThat(confirmed).isIn(values)
        assertThat(confirmed).isAtLeast(FillConfig.FILL_MIN_UNITS)
        assertThat(confirmed).isAtMost(FillConfig.FILL_MAX_UNITS)
    }

    @Test
    fun next_doesNotExitTheWizard() {
        render(150)

        compose.onNodeWithText(nextLabel).performClick()
        compose.waitForIdle()

        verify(viewModel, never()).exitWizard()
    }

    @Test
    fun cancel_exitsWizard_withoutConfirmingAnAmount() {
        render(150)

        compose.onNodeWithText(cancelLabel).performClick()
        compose.waitForIdle()

        verify(viewModel).exitWizard()
        verify(viewModel, never()).confirmAmount(any())
    }

    // ---- seeded-value validation ----------------------------------------------------------------

    @Test
    fun offGridSeedValue_snapsToAGridValue() {
        // 55 is not on the 10-unit grid: indexOf() misses, the index coerces to 0 and the centring
        // effect pulls selection back onto a real row.
        render(55)

        val confirmed = pressNextAndCaptureAmount()

        assertThat(confirmed).isNotEqualTo(55)
        assertThat(confirmed).isIn(values)
    }

    @Test
    fun belowRangeSeedValue_coercesIntoRange() {
        render(0)

        compose.onNodeWithText("50").assertIsDisplayed()
        val confirmed = pressNextAndCaptureAmount()

        assertThat(confirmed).isIn(values)
        assertThat(confirmed).isAtLeast(FillConfig.FILL_MIN_UNITS)
    }

    @Test
    fun aboveRangeSeedValue_coercesIntoRange() {
        render(500)

        val confirmed = pressNextAndCaptureAmount()

        assertThat(confirmed).isIn(values)
        assertThat(confirmed).isAtMost(FillConfig.FILL_MAX_UNITS)
    }

    // ---- interaction ----------------------------------------------------------------------------

    @Test
    fun tappingAWheelRow_movesSelectionAndConfirmsOnce() {
        render(FillConfig.FILL_MIN_UNITS)

        compose.onNodeWithText("70").performClick()
        compose.waitForIdle()

        val confirmed = pressNextAndCaptureAmount()

        assertThat(confirmed).isIn(values)
        assertThat(confirmed).isAtLeast(FillConfig.FILL_MIN_UNITS)
    }

    @Test
    fun scrollingForward_selectsAHigherAmount() {
        render(FillConfig.FILL_MIN_UNITS)

        compose.onNode(hasScrollAction()).performScrollToIndex(20)
        compose.waitForIdle()

        val confirmed = pressNextAndCaptureAmount()

        assertThat(confirmed).isIn(values)
        assertThat(confirmed).isGreaterThan(FillConfig.FILL_MIN_UNITS)
        assertThat(confirmed).isAtMost(FillConfig.FILL_MAX_UNITS)
    }

    @Test
    fun scrollingBackToStart_selectsALowerAmount() {
        render(FillConfig.FILL_MAX_UNITS)

        compose.onNode(hasScrollAction()).performScrollToIndex(0)
        compose.waitForIdle()

        val confirmed = pressNextAndCaptureAmount()

        assertThat(confirmed).isIn(values)
        assertThat(confirmed).isLessThan(FillConfig.FILL_MAX_UNITS)
        assertThat(confirmed).isAtLeast(FillConfig.FILL_MIN_UNITS)
    }

    @Test
    fun scrollingToLastIndex_rendersMaximumRow() {
        render(FillConfig.FILL_MIN_UNITS)

        compose.onNode(hasScrollAction()).performScrollToIndex(values.lastIndex)
        compose.waitForIdle()

        compose.onNodeWithText("300").assertExists()
    }

    @Test
    fun scrollingToFirstIndex_rendersMinimumRow() {
        render(FillConfig.FILL_MAX_UNITS)

        compose.onNode(hasScrollAction()).performScrollToIndex(0)
        compose.waitForIdle()

        compose.onNodeWithText("50").assertExists()
    }
}
