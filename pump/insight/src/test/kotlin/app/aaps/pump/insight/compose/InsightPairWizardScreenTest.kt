package app.aaps.pump.insight.compose

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.aaps.pump.insight.R
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import app.aaps.core.ui.R as CoreUiR

/**
 * Covers [InsightPairWizardScreen], the four steps of pairing a pump.
 *
 * The step that matters most is the code comparison. Bluetooth pairing shows the same number on the
 * pump and on the phone, and answering Yes is the user saying "these match". If the screen showed
 * the wrong number, or Yes and No were the wrong way round, the user would be confirming a pairing
 * with a pump they cannot see - so the code shown and which callback each button reaches are pinned
 * here rather than left to the eye.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class InsightPairWizardScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val context: Context get() = RuntimeEnvironment.getApplication()
    private val yes: String get() = context.getString(CoreUiR.string.yes)
    private val no: String get() = context.getString(CoreUiR.string.no)
    private val exit: String get() = context.getString(CoreUiR.string.exit)
    private val cancel: String get() = context.getString(CoreUiR.string.cancel)
    private val searching: String get() = context.getString(R.string.searching_for_devices)
    private val completed: String get() = context.getString(R.string.pairing_completed)

    private var selected: InsightPairDevice? = null
    private var confirmed = 0
    private var rejected = 0
    private var exited = 0
    private var cancelled = 0

    private fun show(state: InsightPairUiState) {
        compose.setContent {
            MaterialTheme {
                InsightPairWizardScreen(
                    state = state,
                    onDeviceSelected = { selected = it },
                    onConfirmCode = { confirmed++ },
                    onRejectCode = { rejected++ },
                    onExit = { exited++ },
                    onCancel = { cancelled++ }
                )
            }
        }
    }

    // ---- search ----

    @Test
    fun theSearchStepSaysItIsLooking() {
        show(InsightPairUiState(step = InsightPairStep.SEARCH))

        compose.onNodeWithText(searching).assertIsDisplayed()
    }

    @Test
    fun theFoundPumpsAreListedByNameAndAddress() {
        show(
            InsightPairUiState(
                step = InsightPairStep.SEARCH,
                devices = listOf(
                    InsightPairDevice(address = "AA:BB:CC:DD:EE:01", name = "Insight One"),
                    InsightPairDevice(address = "AA:BB:CC:DD:EE:02", name = "Insight Two")
                )
            )
        )

        compose.onNodeWithText("Insight One").assertIsDisplayed()
        compose.onNodeWithText("AA:BB:CC:DD:EE:01").assertIsDisplayed()
        compose.onNodeWithText("Insight Two").assertIsDisplayed()
    }

    /** Picking a pump must hand back the one that was tapped, not merely "a pump was tapped". */
    @Test
    fun tappingAPumpSelectsThatExactPump() {
        val second = InsightPairDevice(address = "AA:BB:CC:DD:EE:02", name = "Insight Two")
        show(
            InsightPairUiState(
                step = InsightPairStep.SEARCH,
                devices = listOf(InsightPairDevice(address = "AA:BB:CC:DD:EE:01", name = "Insight One"), second)
            )
        )

        compose.onNodeWithText("Insight Two").performClick()

        assertThat(selected).isEqualTo(second)
    }

    @Test
    fun theSearchStepCanBeCancelled() {
        show(InsightPairUiState(step = InsightPairStep.SEARCH))

        compose.onNodeWithText(cancel).performClick()

        assertThat(cancelled).isEqualTo(1)
    }

    // ---- code comparison ----

    @Test
    fun theCodeToCompareIsShown() {
        show(InsightPairUiState(step = InsightPairStep.CODE_COMPARE, verificationCode = "123456"))

        compose.onNodeWithText("123456").assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.code_compare)).assertIsDisplayed()
    }

    @Test
    fun answeringYesConfirmsTheCodeAndNothingElse() {
        show(InsightPairUiState(step = InsightPairStep.CODE_COMPARE, verificationCode = "123456"))

        compose.onNodeWithText(yes).performClick()

        assertThat(confirmed).isEqualTo(1)
        assertThat(rejected).isEqualTo(0)
    }

    @Test
    fun answeringNoRejectsTheCodeAndNothingElse() {
        show(InsightPairUiState(step = InsightPairStep.CODE_COMPARE, verificationCode = "123456"))

        compose.onNodeWithText(no).performClick()

        assertThat(rejected).isEqualTo(1)
        assertThat(confirmed).isEqualTo(0)
    }

    // ---- connecting and completed ----

    @Test
    fun theConnectingStepCanBeCancelled() {
        show(InsightPairUiState(step = InsightPairStep.CONNECTING))

        compose.onNodeWithText(cancel).performClick()

        assertThat(cancelled).isEqualTo(1)
    }

    @Test
    fun theCompletedStepSaysSoAndOffersExit() {
        show(InsightPairUiState(step = InsightPairStep.COMPLETED))

        compose.onNodeWithText(completed).assertIsDisplayed()
        compose.onNodeWithText(exit).performClick()

        assertThat(exited).isEqualTo(1)
    }

    /** Each step shows only its own content - a stale code must not survive into the last step. */
    @Test
    fun theCompletedStepDoesNotStillShowTheCode() {
        show(InsightPairUiState(step = InsightPairStep.COMPLETED, verificationCode = "123456"))

        compose.onNodeWithText("123456").assertDoesNotExist()
    }
}
