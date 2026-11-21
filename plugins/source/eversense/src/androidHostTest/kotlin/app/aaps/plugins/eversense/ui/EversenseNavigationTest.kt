package app.aaps.plugins.eversense.ui

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import app.aaps.core.interfaces.resources.TextRefIdRegistry
import app.aaps.core.interfaces.source.CgmAttention
import app.aaps.core.keys.StringKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.CoreUiStringIds
import app.aaps.core.ui.compose.AapsTheme
import app.aaps.core.ui.compose.LocalPreferences
import app.aaps.core.ui.compose.pump.PumpInfoRow
import app.aaps.core.ui.compose.pump.StatusBanner
import app.aaps.plugins.eversense.EversenseStringIds
import app.aaps.plugins.eversense.R
import app.aaps.plugins.eversense.enums.SignalStrength
import app.aaps.plugins.eversense.placement.EversensePlacementContent
import app.aaps.plugins.eversense.placement.EversensePlacementViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import app.aaps.core.ui.R as CoreUiR
import org.junit.Before
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The status tab of the Eversense plugin screen, and the placement screen. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class)
class EversenseNavigationTest {

    @get:Rule val compose = createComposeRule()
    private val preferences: Preferences = mock()
    private val mode = MutableStateFlow("dark")
    private val context get() = RuntimeEnvironment.getApplication()

    @Before fun registerStrings() {
        TextRefIdRegistry.register("eversense") { name -> EversenseStringIds.idOf(name) }
        TextRefIdRegistry.register("coreUi") { name -> CoreUiStringIds.idOf(name) }
    }

    private fun show(
        connected: Boolean = true,
        attention: CgmAttention? = null,
        signedIn: Boolean = true,
        onPair: () -> Unit = {},
        onSignInAgain: () -> Unit = {},
        onUnpair: () -> Unit = {},
        onCalibrate: () -> Unit = {}
    ) {
        whenever(preferences.observe(StringKey.GeneralDarkMode)).thenReturn(mode)
        compose.setContent {
            CompositionLocalProvider(LocalPreferences provides preferences) {
                AapsTheme {
                    EversenseStatusContent(
                        state = EversenseStatusViewModel.UiState(
                            banner = StatusBanner("Banner example"),
                            rows = if (attention == CgmAttention.NOT_PAIRED) emptyList() else listOf(PumpInfoRow("Label example", "Value example")),
                            attention = attention,
                            signedIn = signedIn,
                            transmitter = "T123456",
                            connected = connected
                        ),
                        onPair = onPair,
                        onSignInAgain = onSignInAgain,
                        onUnpair = onUnpair,
                        onSync = {},
                        onCalibrate = onCalibrate,
                        onPlacement = {}
                    )
                }
            }
        }
    }

    private fun text(id: Int, vararg args: Any) = context.getString(id, *args)

    /** The pumps' layout: the banner, the label and value rows, and the buttons. */
    @Test fun statusShowsWhatTheTransmitterReports() {
        show()
        compose.onNodeWithText("Banner example").assertIsDisplayed()
        compose.onNodeWithText("Label example").assertIsDisplayed()
        compose.onNodeWithText("Value example").assertIsDisplayed()
        compose.onNodeWithText(text(R.string.eversense_information_last_sync)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.eversense_placement_title)).assertIsDisplayed()
        // Signed in: nothing to sign in to
        compose.onNodeWithText(text(R.string.eversense_sign_in)).assertDoesNotExist()
    }

    /** Where the bottom bar's "!" leads: the reason, and the button that starts the wizard. */
    @Test fun notPairedOffersPairingAndNothingElse() {
        var pair = false
        show(attention = CgmAttention.NOT_PAIRED, onPair = { pair = true })
        compose.onNodeWithText("Label example").assertDoesNotExist()
        compose.onNodeWithText(text(R.string.eversense_information_last_sync)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.eversense_unpair)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.eversense_pair_title)).performClick()
        compose.runOnIdle { assertEquals(true, pair) }
    }

    @Test fun refusedLoginOffersSigningInAgain() {
        var signIn = false
        show(attention = CgmAttention.SIGN_IN_FAILED, onSignInAgain = { signIn = true })
        compose.onNodeWithText(text(R.string.eversense_sign_in_again)).performClick()
        compose.runOnIdle { assertEquals(true, signIn) }
    }

    /** An E3 used without a login can still sign in from here. */
    @Test fun withoutALoginSignInIsOffered() {
        var signIn = false
        show(signedIn = false, onSignInAgain = { signIn = true })
        compose.onNodeWithText(text(R.string.eversense_sign_in)).performClick()
        compose.runOnIdle { assertEquals(true, signIn) }
    }

    /** Unpair forgets the transmitter: asked first. */
    @Test fun unpairAsksFirst() {
        var unpaired = false
        show(onUnpair = { unpaired = true })
        compose.onNodeWithText(text(R.string.eversense_unpair)).performClick()
        compose.onNodeWithText(text(R.string.eversense_unpair_confirm, "T123456")).assertIsDisplayed()
        compose.runOnIdle { assertEquals(false, unpaired) }
        compose.onNodeWithText(context.getString(CoreUiR.string.ok)).performClick()
        compose.runOnIdle { assertEquals(true, unpaired) }
    }

    /** The link follows the plugin (no Connect or Disconnect); Sync waits for it. */
    @Test fun disconnectedSyncIsDisabled() {
        show(connected = false)
        compose.onNodeWithText(text(R.string.eversense_information_last_sync)).assertIsNotEnabled()
    }

    /** Calibration goes through the AAPS calibration dialog, opened from here. */
    @Test fun calibrateOpensTheCalibration() {
        var calibrate = false
        show(onCalibrate = { calibrate = true })
        compose.onNodeWithText(text(R.string.eversense_calibration_action)).performClick()
        compose.runOnIdle { assertEquals(true, calibrate) }
    }

    private fun showPlacement(state: EversensePlacementViewModel.UiState) {
        whenever(preferences.observe(StringKey.GeneralDarkMode)).thenReturn(mode)
        compose.setContent {
            CompositionLocalProvider(LocalPreferences provides preferences) {
                AapsTheme {
                    EversensePlacementContent(state = state, timeString = { "12:00:05" })
                }
            }
        }
    }

    @Test fun placementShowsLiveValuesAndInstructions() {
        showPlacement(EversensePlacementViewModel.UiState.Signal(60, SignalStrength.from365(60), readAt = 1L))
        compose.onNodeWithText(text(R.string.eversense_signal_good)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(CoreUiR.string.units_format_percent, 60)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.eversense_placement_last_update, "12:00:05")).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.eversense_placement_instruction_good)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription(text(R.string.eversense_placement_bars, SignalStrength.GOOD.ordinal)).assertExists()
    }

    /** Without a link there is nothing to read: the guide says so, and lights no bar. */
    @Test fun placementWithoutALinkSaysNotConnected() {
        showPlacement(EversensePlacementViewModel.UiState.NotConnected)
        compose.onNodeWithText(text(R.string.eversense_not_connected)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.eversense_placement_instruction_not_connected)).assertIsDisplayed()
        compose.onNodeWithContentDescription(text(R.string.eversense_placement_bars, 0)).assertExists()
    }
}
