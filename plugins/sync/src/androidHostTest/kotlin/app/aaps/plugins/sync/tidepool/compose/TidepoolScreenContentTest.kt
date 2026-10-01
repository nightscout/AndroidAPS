package app.aaps.plugins.sync.tidepool.compose

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Compose UI smoke test for the state-hoisted [TidepoolScreenContent], rendered headlessly on the
 * JVM via Robolectric (mirroring the core:ui / calibration screen tests). Passing a null DateUtil
 * exercises the built-in preview time formatter.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class TidepoolScreenContentTest {

    @get:Rule
    val compose = createComposeRule()

    private fun setContent(state: TidepoolUiState) {
        compose.setContent {
            MaterialTheme {
                TidepoolScreenContent(uiState = state, dateUtil = null)
            }
        }
    }

    @Test
    fun showsConnectionStatusAndLogEntries() {
        setContent(
            TidepoolUiState(
                connectionStatus = "SESSION_ESTABLISHED",
                logList = listOf(TidepoolLog("Upload successful"), TidepoolLog("Starting upload"))
            )
        )
        compose.onNodeWithText("SESSION_ESTABLISHED").assertIsDisplayed()
        compose.onNodeWithText("Upload successful", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Starting upload", substring = true).assertIsDisplayed()
    }

    @Test
    fun emptyLog_stillShowsStatus() {
        setContent(TidepoolUiState(connectionStatus = "NOT_LOGGED_IN", logList = emptyList()))
        compose.onNodeWithText("NOT_LOGGED_IN").assertIsDisplayed()
    }
}
