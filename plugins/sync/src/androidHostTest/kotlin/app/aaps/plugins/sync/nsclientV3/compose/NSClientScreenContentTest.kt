package app.aaps.plugins.sync.nsclientV3.compose

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import app.aaps.core.interfaces.nsclient.NSClientLog
import app.aaps.plugins.sync.R
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Compose UI smoke test for the public, state-hoisted [NSClientScreenContent], rendered headlessly on
 * the JVM via Robolectric. Resource labels are resolved from the real resources, while seeded values
 * are asserted as the literal strings put into the state.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class NSClientScreenContentTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var queueLabel: String
    private lateinit var statusLabel: String

    @Before
    fun setUp() {
        queueLabel = RuntimeEnvironment.getApplication().getString(R.string.queue)
        statusLabel = RuntimeEnvironment.getApplication().getString(R.string.status_label)
    }

    @Test
    fun showsStaticLabels_withDefaultState() {
        compose.setContent {
            MaterialTheme {
                NSClientScreenContent(uiState = NSClientUiState())
            }
        }

        compose.onNodeWithText(queueLabel).assertIsDisplayed()
        compose.onNodeWithText(statusLabel).assertIsDisplayed()
    }

    @Test
    fun showsSeededStatusQueueAndLog() {
        compose.setContent {
            MaterialTheme {
                NSClientScreenContent(
                    uiState = NSClientUiState(
                        status = "Connected",
                        queue = "7",
                        logList = listOf(NSClientLog(action = "UPLOAD", logText = "Uploading treatments"))
                    )
                )
            }
        }

        compose.onNodeWithText("Connected").assertIsDisplayed()
        compose.onNodeWithText("7").assertIsDisplayed()
        compose.onNodeWithText("UPLOAD", substring = true).assertIsDisplayed()
    }
}
