package app.aaps.plugins.source.compose

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import app.aaps.core.interfaces.resources.TextRefIdRegistry
import app.aaps.core.interfaces.rx.weardata.EventData
import app.aaps.plugins.source.DexcomOnePlusWatchReport
import app.aaps.plugins.source.SourceStringIds
import app.aaps.plugins.source.SourceStringsValues
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.Config as RobolectricConfig

/**
 * The direct-to-watch row on the Status screen. With the feature on and no sensor to hand over, it
 * used to say "Watch – Off", which read as if the feature were off.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RobolectricConfig(sdk = [35])
class DexcomOnePlusWatchDirectCardTest {

    @get:Rule val compose = createComposeRule()

    private fun text(name: String): String =
        requireNotNull(SourceStringsValues.textOf(name, null)) { "no English text for $name" }

    @Before
    fun setUp() {
        TextRefIdRegistry.register("source") { name -> SourceStringIds.idOf(name) }
    }

    @Test
    fun `with no sensor to hand over the row says it waits for a sensor`() {
        val off = DexcomOnePlusWatchReport(EventData.CgmWatchStatus(state = EventData.CgmWatchState.OFF), receivedAtMs = 0L)
        compose.setContent { DexcomOnePlusWatchDirectCard(available = false, report = off) }

        compose.onNodeWithText(text("dexcom_oneplus_watch_state_waiting_sensor")).assertIsDisplayed()
        compose.onNodeWithText(text("dexcom_oneplus_watch_state_off")).assertDoesNotExist()
    }

    @Test
    fun `with a sensor handed over the row shows what the watch reports`() {
        val connected = DexcomOnePlusWatchReport(EventData.CgmWatchStatus(state = EventData.CgmWatchState.CONNECTED), receivedAtMs = 0L)
        compose.setContent { DexcomOnePlusWatchDirectCard(available = true, report = connected) }

        compose.onNodeWithText(text("dexcom_oneplus_watch_state_connected")).assertIsDisplayed()
    }

    @Test
    fun `the switch is not on the status screen any more`() {
        compose.setContent { DexcomOnePlusWatchDirectCard(available = true, report = null) }

        compose.onNodeWithText(text("dexcom_oneplus_watch_direct_summary")).assertDoesNotExist()
    }
}
