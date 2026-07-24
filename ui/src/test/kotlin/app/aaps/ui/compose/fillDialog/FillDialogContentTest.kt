package app.aaps.ui.compose.fillDialog

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.aaps.ui.R
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.text.DecimalFormat

/**
 * First screen-composable test in `:ui`. Renders the state-hoisted [FillDialogContent] headlessly on
 * the JVM via Robolectric (no emulator, no shard budget) — the coverage path the instrumented
 * SetupWizard walkthrough couldn't sustain on the CI emulator. Drives it with a fake [FillDialogUiState]
 * and asserts it renders the switch rows + fires the correct callback.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class FillDialogContentTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var siteChangeLabel: String
    private lateinit var cartridgeLabel: String
    private lateinit var primeAmountLabel: String

    @Before
    fun setUp() {
        val context: Context = RuntimeEnvironment.getApplication()
        siteChangeLabel = context.getString(R.string.record_pump_site_change)
        cartridgeLabel = context.getString(R.string.record_insulin_cartridge_change)
        primeAmountLabel = context.getString(R.string.fill_prime_amount)
    }

    @Test
    fun rendersSwitchRowsAndFiresSiteChangeCallback() {
        var siteChangeClicked = false
        var confirmClicked = false
        compose.setContent {
            MaterialTheme {
                FillDialogContent(
                    uiState = FillDialogUiState(siteChange = true, insulin = 0.3),
                    dateString = "2024-01-01",
                    timeString = "12:00",
                    bolusFormat = DecimalFormat("0.0"),
                    onSiteChangeClick = { siteChangeClicked = true },
                    onCartridgeChangeClick = {},
                    onInsulinChange = {},
                    onInsulinSelect = {},
                    onNotesChange = {},
                    onDateClick = {},
                    onTimeClick = {},
                    onSettingsClick = null,
                    onNavigateBack = {},
                    onConfirmClick = { confirmClicked = true }
                )
            }
        }

        compose.onNodeWithText(siteChangeLabel).assertIsDisplayed()
        compose.onNodeWithText(cartridgeLabel).assertIsDisplayed()
        compose.onNodeWithText(primeAmountLabel).assertIsDisplayed()

        compose.onNodeWithText(siteChangeLabel).performClick()
        assertThat(siteChangeClicked).isTrue()
        assertThat(confirmClicked).isFalse()
    }
}
