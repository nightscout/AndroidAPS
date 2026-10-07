package app.aaps.appshell

import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createComposeRule
import app.aaps.core.interfaces.pump.BolusProgressState
import app.aaps.core.interfaces.pump.PumpInsulin
import app.aaps.core.keys.interfaces.TextRef
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Which boluses take over the screen, which do not.
 *
 * This overlay used to live in `ComposeMainActivity`, so only Android had it and nothing covered it.
 * A client mirrors the master's bolus into the same state, so on iOS and on the desktop a relayed
 * bolus showed no progress at all. These are the two decisions the overlay makes.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class BolusProgressOverlayTest {

    @get:Rule
    val compose = createComposeRule()

    private fun state(isSMB: Boolean) = BolusProgressState(
        insulin = 1.5,
        isSMB = isSMB,
        isPriming = false,
        percent = 20,
        status = TextRef.Literal("Delivering"),
        wearStatus = TextRef.Literal("Delivering"),
        delivered = PumpInsulin(0.3),
        stopPressed = false,
        stopDeliveryEnabled = true
    )

    private fun show(bolusState: BolusProgressState?) {
        compose.setContent {
            BolusProgressOverlay(
                bolusState = bolusState,
                pumpStatus = "",
                queueStatus = null,
                onStop = {},
                onDismiss = {}
            )
        }
    }

    private fun dialogCount() = compose.onAllNodes(isDialog()).fetchSemanticsNodes().size

    @Test
    fun `a standard bolus shows the modal dialog`() {
        show(state(isSMB = false))

        assertThat(dialogCount()).isEqualTo(1)
    }

    /** An SMB is the loop's action, not the user's - it belongs in the overview FAB, not over the screen. */
    @Test
    fun `an SMB shows nothing`() {
        show(state(isSMB = true))

        assertThat(dialogCount()).isEqualTo(0)
    }

    @Test
    fun `no bolus shows nothing`() {
        show(null)

        assertThat(dialogCount()).isEqualTo(0)
    }
}
