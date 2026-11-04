package app.aaps.plugins.sync.nsclientV3.compose

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.aaps.core.interfaces.sync.SyncLogEntry
import app.aaps.core.interfaces.resources.TextRefIdRegistry
import app.aaps.plugins.sync.SyncStringIds
import com.google.common.truth.Truth.assertThat
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
        // What MainApp does at startup: a TextRef.Named is resolved through this registry, so
        // without it every label renders as its raw name.
        TextRefIdRegistry.register("sync") { name -> SyncStringIds.idOf(name) }
        queueLabel = RuntimeEnvironment.getApplication().getString(SyncStringIds.idOf("queue")!!)
        statusLabel = RuntimeEnvironment.getApplication().getString(SyncStringIds.idOf("status_label")!!)
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
                        logList = listOf(SyncLogEntry(action = "UPLOAD", text = "Uploading treatments"))
                    )
                )
            }
        }

        compose.onNodeWithText("Connected").assertIsDisplayed()
        compose.onNodeWithText("7").assertIsDisplayed()
        compose.onNodeWithText("UPLOAD", substring = true).assertIsDisplayed()
    }

    @Test
    fun actionsAreButtonsAtTheBottom() {
        val clicks = mutableListOf<String>()
        compose.setContent {
            MaterialTheme {
                NSClientScreenContent(
                    uiState = NSClientUiState(),
                    onSendNow = { clicks += "send" },
                    onFullSync = { clicks += "full" },
                    onClearLog = { clicks += "clear" }
                )
            }
        }

        compose.onNodeWithText(sync("deliver_now")).performClick()
        compose.onNodeWithText(sync("full_sync")).performClick()
        compose.onNodeWithText(sync("clear_log")).performClick()
        assertThat(clicks).containsExactly("send", "full", "clear").inOrder()
    }

    private fun sync(name: String): String = RuntimeEnvironment.getApplication().getString(SyncStringIds.idOf(name)!!)
}
