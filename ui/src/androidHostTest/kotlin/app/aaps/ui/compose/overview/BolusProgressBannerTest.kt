package app.aaps.ui.compose.overview

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.AnnotatedString
import app.aaps.core.interfaces.pump.BolusProgressState
import app.aaps.core.interfaces.pump.PumpInsulin
import app.aaps.core.keys.interfaces.TextRef
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import app.aaps.core.ui.R as CoreUiR

/** Robolectric composable test for [BolusProgressBanner]: a standard bolus shows in place, not as a dialog; an SMB shows nothing. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class BolusProgressBannerTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var ctx: Context

    @Before
    fun setUp() {
        ctx = RuntimeEnvironment.getApplication()
    }

    private fun state(
        isSMB: Boolean = false,
        percent: Int = 20,
        stopPressed: Boolean = false,
        stalled: Boolean = false
    ) = BolusProgressState(
        insulin = 1.5,
        isSMB = isSMB,
        isPriming = false,
        percent = percent,
        status = TextRef.Literal("Delivering"),
        wearStatus = TextRef.Literal("Delivering"),
        delivered = PumpInsulin(0.3),
        stopPressed = stopPressed,
        stopDeliveryEnabled = true,
        stalled = stalled
    )

    private fun show(
        bolusState: BolusProgressState?,
        onStop: () -> Unit = {},
        onDismiss: () -> Unit = {},
        pumpStatus: String = "",
        queueStatus: AnnotatedString? = null
    ) {
        compose.setContent {
            MaterialTheme {
                BolusProgressBanner(
                    state = bolusState,
                    onStop = onStop,
                    onDismiss = onDismiss,
                    pumpStatus = pumpStatus,
                    queueStatus = queueStatus
                )
            }
        }
    }

    @Test
    fun `a standard bolus shows the title and Stop in place, not in a dialog`() {
        show(state())

        compose.onNodeWithText(ctx.getString(CoreUiR.string.goingtodeliver, 1.5)).assertIsDisplayed()
        compose.onNodeWithText(ctx.getString(CoreUiR.string.stop)).assertIsDisplayed()
        assertThat(compose.onAllNodes(isDialog()).fetchSemanticsNodes()).isEmpty()
    }

    @Test
    fun `Stop calls onStop`() {
        var stopped = 0
        show(state(), onStop = { stopped++ })

        compose.onNodeWithText(ctx.getString(CoreUiR.string.stop)).performClick()

        assertThat(stopped).isEqualTo(1)
    }

    @Test
    fun `after Stop is pressed the button is disabled`() {
        show(state(stopPressed = true))

        compose.onNodeWithText(ctx.getString(CoreUiR.string.stop_pressed)).assertIsNotEnabled()
    }

    /** An SMB is the loop's action, not the user's - it belongs in the pump FAB. */
    @Test
    fun `an SMB shows nothing`() {
        show(state(isSMB = true))

        compose.onNodeWithText(ctx.getString(CoreUiR.string.goingtodeliver, 1.5)).assertDoesNotExist()
    }

    @Test
    fun `no bolus shows nothing`() {
        show(null)

        compose.onNodeWithText(ctx.getString(CoreUiR.string.stop)).assertDoesNotExist()
    }

    @Test
    fun `a stalled bolus offers Dismiss instead of Stop`() {
        var dismissed = 0
        show(state(stalled = true), onDismiss = { dismissed++ })

        compose.onNodeWithText(ctx.getString(CoreUiR.string.stop)).assertDoesNotExist()
        compose.onNodeWithText(ctx.getString(CoreUiR.string.dismiss)).performClick()

        assertThat(dismissed).isEqualTo(1)
    }

    @Test
    fun `before any progress the bar is indeterminate and the pump status is shown`() {
        show(state(percent = 0), pumpStatus = "Connecting for 12s", queueStatus = AnnotatedString("Bolus 1.5 U"))

        compose.onNodeWithText("Connecting for 12s").assertIsDisplayed()
        compose.onNodeWithText("Bolus 1.5 U").assertIsDisplayed()
        compose.onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate)).assertIsDisplayed()
    }

    @Test
    fun `once delivery has progress the bar is determinate and the pump status is hidden`() {
        show(state(percent = 40), pumpStatus = "Connecting for 12s", queueStatus = AnnotatedString("Bolus 1.5 U"))

        compose.onNodeWithText("Connecting for 12s").assertDoesNotExist()
        compose.onNodeWithText("Bolus 1.5 U").assertDoesNotExist()
        compose.onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate)).assertDoesNotExist()
        compose.onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo(0.4f, 0f..1f))).assertIsDisplayed()
    }

    @Test
    fun `a stalled bolus shows no bar`() {
        show(state(percent = 0, stalled = true), pumpStatus = "Connecting for 12s")

        compose.onNodeWithText("Connecting for 12s").assertDoesNotExist()
        compose.onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate)).assertDoesNotExist()
    }
}
