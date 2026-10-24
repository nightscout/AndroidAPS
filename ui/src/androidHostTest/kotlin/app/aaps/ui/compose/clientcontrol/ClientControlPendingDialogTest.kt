package app.aaps.ui.compose.clientcontrol

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import app.aaps.core.interfaces.clientcontrol.ActionProgress
import app.aaps.core.interfaces.clientcontrol.FailureReason
import app.aaps.core.interfaces.clientcontrol.PendingAction
import app.aaps.core.interfaces.resources.TextRefIdRegistry
import app.aaps.ui.R
import app.aaps.ui.UiStringIds
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import app.aaps.core.ui.R as CoreUiR

/**
 * The client's round-trip dialog says why the master refused. Every bolus prepare the master refuses comes back
 * as [FailureReason.BolusComputeFailed]; its text blamed a missing glucose or profile also when an SMB was being
 * delivered, and the screen then showed the master's real reason in a second dialog (#5381).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class ClientControlPendingDialogTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var ctx: Context

    @Before
    fun setUp() {
        TextRefIdRegistry.register("ui") { name -> UiStringIds.idOf(name) }
        ctx = RuntimeEnvironment.getApplication()
    }

    private fun show(progress: ActionProgress) {
        compose.setContent { MaterialTheme { ClientControlPendingDialog(PendingAction(progress, LABEL), onDismiss = {}) } }
    }

    @Test
    fun aRefusalTheMasterExplainedShowsTheMastersText() {
        show(ActionProgress.Rejected(FailureReason.BolusComputeFailed, SMB_BEING_DELIVERED))

        compose.onNodeWithText(ctx.getString(R.string.clientcontrol_pending_failed_format, LABEL, SMB_BEING_DELIVERED)).assertIsDisplayed()
        compose.onNodeWithText(ctx.getString(CoreUiR.string.clientcontrol_fail_bolus_compute), substring = true).assertDoesNotExist()
    }

    @Test
    fun aRefusalWithoutTheMastersTextShowsTheReason() {
        show(ActionProgress.Rejected(FailureReason.BolusComputeFailed))

        compose.onNodeWithText(
            ctx.getString(R.string.clientcontrol_pending_failed_format, LABEL, ctx.getString(CoreUiR.string.clientcontrol_fail_bolus_compute))
        ).assertIsDisplayed()
    }

    private companion object {

        const val LABEL = "Bolus"
        const val SMB_BEING_DELIVERED = "An SMB is being delivered right now. Try again when it has finished."
    }
}
