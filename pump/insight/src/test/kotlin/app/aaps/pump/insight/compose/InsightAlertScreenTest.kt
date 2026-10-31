package app.aaps.pump.insight.compose

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.aaps.pump.insight.descriptors.AlertStatus
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
 * Covers [InsightAlertScreen], the screen the pump's alerts are shown on.
 *
 * The screen is what the user sees when the pump raises something - a cartridge running out, an
 * occlusion, a battery warning - and its two buttons are the only way to answer it. The behaviour
 * worth pinning is which buttons are offered: an alert that is already snoozed must not offer Mute
 * again, and a button the driver has disabled must not be pressable while the pump is busy.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class InsightAlertScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val context: Context get() = RuntimeEnvironment.getApplication()
    private val mute: String get() = context.getString(CoreUiR.string.mute)
    private val confirm: String get() = context.getString(CoreUiR.string.confirm)

    private fun state(
        alertStatus: AlertStatus? = AlertStatus.ACTIVE,
        description: String? = "Cartridge is nearly empty",
        muteEnabled: Boolean = true,
        confirmEnabled: Boolean = true
    ) = InsightAlertUiState(
        icon = Icons.Filled.Warning,
        errorCode = "W36",
        title = "Cartridge low",
        description = description,
        alertStatus = alertStatus,
        muteEnabled = muteEnabled,
        confirmEnabled = confirmEnabled
    )

    private fun show(
        state: InsightAlertUiState,
        onMute: () -> Unit = {},
        onConfirm: () -> Unit = {}
    ) {
        compose.setContent {
            MaterialTheme { InsightAlertScreen(state = state, onMute = onMute, onConfirm = onConfirm) }
        }
    }

    @Test
    fun theAlertShowsItsCodeTitleAndDescription() {
        show(state())

        compose.onNodeWithText("W36").assertIsDisplayed()
        compose.onNodeWithText("Cartridge low").assertIsDisplayed()
        compose.onNodeWithText("Cartridge is nearly empty").assertIsDisplayed()
    }

    @Test
    fun anAlertWithoutADescriptionStillShowsItsTitle() {
        show(state(description = null))

        compose.onNodeWithText("Cartridge low").assertIsDisplayed()
        compose.onNodeWithText("Cartridge is nearly empty").assertDoesNotExist()
    }

    @Test
    fun anActiveAlertOffersBothMuteAndConfirm() {
        show(state(alertStatus = AlertStatus.ACTIVE))

        compose.onNodeWithText(mute).assertIsDisplayed().assertHasClickAction()
        compose.onNodeWithText(confirm).assertIsDisplayed().assertHasClickAction()
    }

    /** An alert that is already quiet has nothing to mute, so the button is not offered at all. */
    @Test
    fun anAlreadySnoozedAlertOffersNoMuteButton() {
        show(state(alertStatus = AlertStatus.SNOOZED))

        compose.onNodeWithText(mute).assertDoesNotExist()
        compose.onNodeWithText(confirm).assertIsDisplayed()
    }

    /** An unknown status is not the same as snoozed - muting must still be possible. */
    @Test
    fun anAlertOfUnknownStatusStillOffersMute() {
        show(state(alertStatus = null))

        compose.onNodeWithText(mute).assertIsDisplayed()
    }

    @Test
    fun theButtonsAreDisabledWhileTheDriverIsBusy() {
        show(state(muteEnabled = false, confirmEnabled = false))

        compose.onNodeWithText(mute).assertIsNotEnabled()
        compose.onNodeWithText(confirm).assertIsNotEnabled()
    }

    @Test
    fun theButtonsAreEnabledWhenTheDriverIsReady() {
        show(state(muteEnabled = true, confirmEnabled = true))

        compose.onNodeWithText(mute).assertIsEnabled()
        compose.onNodeWithText(confirm).assertIsEnabled()
    }

    @Test
    fun pressingMuteTellsTheDriverToMute() {
        var muted = 0
        show(state(), onMute = { muted++ })

        compose.onNodeWithText(mute).performClick()

        assertThat(muted).isEqualTo(1)
    }

    @Test
    fun pressingConfirmTellsTheDriverToConfirm() {
        var confirmed = 0
        show(state(), onConfirm = { confirmed++ })

        compose.onNodeWithText(confirm).performClick()

        assertThat(confirmed).isEqualTo(1)
    }

    /** A disabled button must not reach the pump, however hard it is pressed. */
    @Test
    fun pressingADisabledConfirmDoesNothing() {
        var confirmed = 0
        show(state(confirmEnabled = false), onConfirm = { confirmed++ })

        compose.onNodeWithText(confirm).performClick()

        assertThat(confirmed).isEqualTo(0)
    }
}
