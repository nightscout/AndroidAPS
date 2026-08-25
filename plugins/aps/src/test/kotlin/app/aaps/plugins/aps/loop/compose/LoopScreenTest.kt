package app.aaps.plugins.aps.loop.compose

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import app.aaps.plugins.aps.R
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class LoopScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var lastRunLabel: String

    @Before
    fun setUp() {
        lastRunLabel = RuntimeEnvironment.getApplication().getString(R.string.last_run_label)
    }

    @Test
    fun showsStatusMessage_whenNoLastRun() {
        compose.setContent {
            MaterialTheme {
                LoopScreen(state = LoopUiState(statusMessage = "N/A"), onRefresh = {})
            }
        }

        compose.onNodeWithText("N/A").assertIsDisplayed()
    }

    @Test
    fun showsLastRun_andSeededCardValues() {
        val state = LoopUiState(
            lastRun = "12:34",
            source = "OpenAPS AMA",
            request = "rate 0.5 U/h",
            tbrSetByPump = "TBR enacted",
            smbSetByPump = "SMB enacted"
        )
        compose.setContent {
            MaterialTheme { LoopScreen(state = state, onRefresh = {}) }
        }

        compose.onNodeWithText(lastRunLabel).assertIsDisplayed()
        compose.onNodeWithText("12:34").assertIsDisplayed()
        compose.onNodeWithText("OpenAPS AMA").assertIsDisplayed()
        compose.onNodeWithText("rate 0.5 U/h").assertIsDisplayed()
    }

    @Test
    fun statusMessage_hidden_whenLastRunPresent() {
        val state = LoopUiState(lastRun = "12:34", statusMessage = "should not show")
        compose.setContent {
            MaterialTheme { LoopScreen(state = state, onRefresh = {}) }
        }

        compose.onNodeWithText("should not show").assertDoesNotExist()
        compose.onNodeWithText("12:34").assertIsDisplayed()
    }
}
