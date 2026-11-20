package app.aaps.plugins.eversense.ui

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import app.aaps.core.keys.StringKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.compose.AapsTheme
import app.aaps.core.ui.compose.LocalPreferences
import app.aaps.plugins.eversense.R
import app.aaps.plugins.eversense.placement.EversensePage
import app.aaps.plugins.eversense.placement.EversensePlacementContent
import app.aaps.plugins.eversense.ui.EversenseStatusContent
import app.aaps.plugins.eversense.ui.EversenseStatusViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import app.aaps.core.interfaces.resources.TextRefIdRegistry
import app.aaps.core.interfaces.source.CgmAttention
import app.aaps.core.ui.CoreUiStringIds
import app.aaps.plugins.eversense.EversenseStringIds
import app.aaps.plugins.eversense.pairing.EversenseAccount
import app.aaps.plugins.eversense.pairing.EversenseModel
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
        onPair: () -> Unit = {},
        onSignInAgain: () -> Unit = {},
        onUnpair: () -> Unit = {},
        onDays: (Int) -> Unit = {},
        onCalibrate: () -> Unit = {}
    ) {
        whenever(preferences.observe(StringKey.GeneralDarkMode)).thenReturn(mode)
        compose.setContent {
            CompositionLocalProvider(LocalPreferences provides preferences) {
                AapsTheme {
                    EversenseStatusContent(
                        state = EversenseStatusViewModel.UiState(lines = listOf("Status example"), connected = connected, syncDays = 14),
                        attention = attention,
                        account = EversenseAccount("user@example.com", EversenseModel.E365_OTHER),
                        transmitter = "T123456",
                        onPair = onPair,
                        onSignInAgain = onSignInAgain,
                        onUnpair = onUnpair,
                        onDays = onDays,
                        onSync = {},
                        onCalibrate = onCalibrate,
                        onPlacement = {}
                    )
                }
            }
        }
    }

    private fun text(id: Int, vararg args: Any) = context.getString(id, *args)

    @Test fun statusShowsWhatTheTransmitterReports() {
        show()
        compose.onNodeWithText("Status example").assertIsDisplayed()
        compose.onNodeWithText(text(R.string.eversense_paired_transmitter, "T123456")).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.eversense_signed_in_as, "user@example.com")).assertIsDisplayed()
    }

    /** Where the bottom bar's "!" leads: the reason, and the button that starts the wizard. */
    @Test fun notPairedOffersPairingAndNothingElse() {
        var pair = false
        show(attention = CgmAttention.NOT_PAIRED, onPair = { pair = true })
        compose.onNodeWithText(text(R.string.eversense_not_paired)).assertIsDisplayed()
        compose.onNodeWithText("Status example").assertDoesNotExist()
        compose.onNodeWithText(text(R.string.eversense_pair_title)).performClick()
        compose.runOnIdle { assertEquals(true, pair) }
    }

    @Test fun refusedLoginOffersSigningInAgain() {
        var signIn = false
        show(attention = CgmAttention.SIGN_IN_FAILED, onSignInAgain = { signIn = true })
        compose.onNodeWithText(text(R.string.eversense_sign_in_failed)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.eversense_sign_in_again)).performClick()
        compose.runOnIdle { assertEquals(true, signIn) }
    }

    /** Unpair forgets the transmitter: asked first. */
    @Test fun unpairAsksFirst() {
        var unpaired = false
        show(onUnpair = { unpaired = true })
        compose.onNodeWithText(text(R.string.eversense_unpair)).performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.eversense_unpair_confirm, "T123456")).assertIsDisplayed()
        compose.runOnIdle { assertEquals(false, unpaired) }
        compose.onNodeWithText(context.getString(app.aaps.core.ui.R.string.ok)).performClick()
        compose.runOnIdle { assertEquals(true, unpaired) }
    }

    /** The link follows the plugin: there is no Connect or Disconnect to press, and Sync waits for the link. */
    @Test fun disconnectedSyncIsDisabledAndNoConnectIsOffered() {
        show(connected = false)
        compose.onNodeWithText(text(R.string.eversense_information_last_sync)).assertIsNotEnabled()
        compose.onNodeWithText(text(R.string.eversense_status_connect)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.eversense_status_disconnect)).assertDoesNotExist()
    }

    @Test fun syncDaysSelectionKeepsExistingChoicesAndCallback() {
        var selected = 0
        show(onDays = { selected = it })
        compose.onNodeWithText("14").performClick()
        compose.onNodeWithText("30").performClick()
        compose.runOnIdle { assertEquals(30, selected) }
    }

    /** Calibration goes through the AAPS calibration dialog, opened from here. */
    @Test fun calibrateOpensTheCalibration() {
        var calibrate = false
        show(onCalibrate = { calibrate = true })
        compose.onNodeWithText(text(R.string.eversense_calibration_action)).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(true, calibrate) }
    }

    @Test fun placementShowsLiveValuesAndInstructionsInsideTheSamePage() {
        whenever(preferences.observe(StringKey.GeneralDarkMode)).thenReturn(mode)
        compose.setContent {
            CompositionLocalProvider(LocalPreferences provides preferences) {
                AapsTheme {
                    EversensePage(R.string.eversense_placement_title, {}) {
                        EversensePlacementContent("Good", "60%", "Keep position", "Updated now", 4)
                    }
                }
            }
        }
        compose.onNodeWithText("Good").assertIsDisplayed()
        compose.onNodeWithText("60%").assertIsDisplayed()
        compose.onNodeWithText("Keep position").performScrollTo().assertIsDisplayed()
    }
}
