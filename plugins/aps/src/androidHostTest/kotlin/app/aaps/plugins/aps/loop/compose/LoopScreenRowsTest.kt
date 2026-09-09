package app.aaps.plugins.aps.loop.compose

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import app.aaps.core.interfaces.resources.TextRefIdRegistry
import app.aaps.plugins.aps.ApsStringIds
import app.aaps.plugins.aps.R
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Covers which rows [LoopScreen] draws and which it leaves out.
 *
 * Every optional row is behind an `isNotEmpty()` guard. A label that starts appearing with an empty
 * value, or stops appearing when it has one, is the kind of change that compiles cleanly and is only
 * visible on a device - so it is asserted here instead.
 *
 * The labels are read from the real string table so that a wrong string reference in the screen
 * shows up as a missing node, not as a test that quietly compares two identical typos.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class LoopScreenRowsTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var lastRunLabel: String
    private lateinit var apsLabel: String
    private lateinit var requestLabel: String
    private lateinit var constraintsProcessedLabel: String
    private lateinit var constraintsLabel: String
    private lateinit var tbrRequestLabel: String
    private lateinit var tbrExecutionLabel: String
    private lateinit var tbrSetByPumpLabel: String
    private lateinit var smbRequestLabel: String
    private lateinit var smbExecutionLabel: String
    private lateinit var smbSetByPumpLabel: String

    @Before
    fun setUp() {
        // What MainApp does at startup: a TextRef.Named is resolved through this registry, so
        // without it every label renders as its raw name.
        TextRefIdRegistry.register("aps") { name -> ApsStringIds.idOf(name) }
        val app = RuntimeEnvironment.getApplication()
        lastRunLabel = app.getString(R.string.last_run_label)
        apsLabel = app.getString(R.string.loop_aps_label)
        requestLabel = app.getString(R.string.request_label)
        constraintsProcessedLabel = app.getString(R.string.loop_constraints_processed_label)
        constraintsLabel = app.getString(R.string.constraints)
        tbrRequestLabel = app.getString(R.string.loop_tbr_request_time_label)
        tbrExecutionLabel = app.getString(R.string.loop_tbr_execution_time_label)
        tbrSetByPumpLabel = app.getString(R.string.loop_tbr_set_by_pump_label)
        smbRequestLabel = app.getString(R.string.loop_smb_request_time_label)
        smbExecutionLabel = app.getString(R.string.loop_smb_execution_time_label)
        smbSetByPumpLabel = app.getString(R.string.loop_smb_set_by_pump_label)
    }

    private fun show(state: LoopUiState) {
        compose.setContent { MaterialTheme { LoopScreen(state = state, onRefresh = {}) } }
    }

    @Test
    fun emptyState_drawsNoCardsAtAll() {
        show(LoopUiState())

        compose.onNodeWithText(lastRunLabel).assertDoesNotExist()
        compose.onNodeWithText(tbrRequestLabel).assertDoesNotExist()
        compose.onNodeWithText(smbRequestLabel).assertDoesNotExist()
    }

    @Test
    fun sourceAlone_isEnoughToDrawTheCards() {
        // The cards are gated on lastRun OR source, not on lastRun alone.
        show(LoopUiState(source = "OpenAPS SMB"))

        compose.onNodeWithText(apsLabel).assertIsDisplayed()
        compose.onNodeWithText("OpenAPS SMB").assertIsDisplayed()
        compose.onNodeWithText(tbrRequestLabel).assertIsDisplayed()
        compose.onNodeWithText(smbRequestLabel).assertIsDisplayed()
    }

    @Test
    fun optionalLabels_areHidden_whenTheirValuesAreEmpty() {
        show(LoopUiState(lastRun = "12:34"))

        compose.onNodeWithText(lastRunLabel).assertIsDisplayed()
        compose.onNodeWithText(apsLabel).assertDoesNotExist()
        compose.onNodeWithText(requestLabel).assertDoesNotExist()
        compose.onNodeWithText(constraintsProcessedLabel).assertDoesNotExist()
        compose.onNodeWithText(constraintsLabel).assertDoesNotExist()
        compose.onNodeWithText(tbrSetByPumpLabel).assertDoesNotExist()
        compose.onNodeWithText(smbSetByPumpLabel).assertDoesNotExist()
    }

    @Test
    fun requiredTimeLabels_areShownEvenWithEmptyValues() {
        // These four have no isNotEmpty() guard, so they appear as soon as the cards do.
        show(LoopUiState(lastRun = "12:34"))

        compose.onNodeWithText(tbrRequestLabel).assertIsDisplayed()
        compose.onNodeWithText(tbrExecutionLabel).assertIsDisplayed()
        compose.onNodeWithText(smbRequestLabel).assertIsDisplayed()
        compose.onNodeWithText(smbExecutionLabel).assertIsDisplayed()
    }

    @Test
    fun everyOptionalRow_isShown_whenItsValueIsPresent() {
        show(
            LoopUiState(
                lastRun = "12:34",
                source = "OpenAPS SMB",
                request = "rate 0.5 U/h",
                constraintsProcessed = "processed text",
                constraints = "loop disabled",
                tbrRequestTime = "10:00:01",
                tbrExecutionTime = "10:00:02",
                tbrSetByPump = "TBR enacted",
                smbRequestTime = "10:00:03",
                smbExecutionTime = "10:00:04",
                smbSetByPump = "SMB enacted"
            )
        )

        compose.onNodeWithText(requestLabel).assertIsDisplayed()
        compose.onNodeWithText("rate 0.5 U/h").assertIsDisplayed()
        compose.onNodeWithText(constraintsProcessedLabel).assertIsDisplayed()
        compose.onNodeWithText("processed text").assertIsDisplayed()
        compose.onNodeWithText(constraintsLabel).assertIsDisplayed()
        compose.onNodeWithText("loop disabled").assertIsDisplayed()
        compose.onNodeWithText(tbrSetByPumpLabel).assertIsDisplayed()
        compose.onNodeWithText("TBR enacted").assertIsDisplayed()
        compose.onNodeWithText(smbSetByPumpLabel).assertIsDisplayed()
        compose.onNodeWithText("10:00:01").assertIsDisplayed()
        // The last rows of the SMB card fall below the fold of the scrolling column at the test
        // window size, so they are asserted as rendered rather than as on screen.
        compose.onNodeWithText("SMB enacted").assertExists()
        compose.onNodeWithText("10:00:04").assertExists()
    }

    @Test
    fun statusMessage_isShown_onlyWhileLastRunIsStillEmpty() {
        show(LoopUiState(statusMessage = "Not available", source = "OpenAPS SMB"))

        // source alone draws the cards, and the status message stays because lastRun is empty.
        compose.onNodeWithText("Not available").assertIsDisplayed()
        compose.onNodeWithText(apsLabel).assertIsDisplayed()
    }
}
