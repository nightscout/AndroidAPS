package app.aaps.plugins.sync.tidepool.compose

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.aaps.core.interfaces.InterfacesStringIds
import app.aaps.core.interfaces.resources.TextRefIdRegistry
import app.aaps.core.interfaces.sync.SyncLogEntry
import app.aaps.plugins.sync.SyncStringIds
import app.aaps.plugins.sync.tidepool.auth.AuthFlowOut
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

    @Before
    fun setUp() {
        // What MainApp does at startup: without it every label renders as its raw name.
        TextRefIdRegistry.register("sync") { name -> SyncStringIds.idOf(name) }
        TextRefIdRegistry.register("interfaces") { name -> InterfacesStringIds.idOf(name) }
    }

    private fun sync(name: String): String = RuntimeEnvironment.getApplication().getString(SyncStringIds.idOf(name)!!)

    private fun setContent(state: TidepoolUiState, onFullSync: () -> Unit = {}) {
        compose.setContent {
            MaterialTheme {
                TidepoolScreenContent(uiState = state, dateUtil = null, onFullSync = onFullSync)
            }
        }
    }

    @Test
    fun showsConnectionStatusAndLogEntries() {
        setContent(
            TidepoolUiState(
                connectionStatus = AuthFlowOut.ConnectionStatus.SESSION_ESTABLISHED,
                logList = listOf(SyncLogEntry("Upload successful"), SyncLogEntry("Starting upload"))
            )
        )
        compose.onNodeWithText(RuntimeEnvironment.getApplication().getString(InterfacesStringIds.idOf("connected")!!)).assertIsDisplayed()
        compose.onNodeWithText("Upload successful", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Starting upload", substring = true).assertIsDisplayed()
    }

    @Test
    fun statusIsTranslatedTextNotTheEnumName() {
        setContent(TidepoolUiState(connectionStatus = AuthFlowOut.ConnectionStatus.NOT_LOGGED_IN, logList = emptyList()))
        compose.onNodeWithText(sync("tidepool_status_not_logged_in")).assertIsDisplayed()
        compose.onNodeWithText("NOT_LOGGED_IN").assertDoesNotExist()
    }

    @Test
    fun actionsAreButtonsAtTheBottom() {
        var fullSyncClicks = 0
        setContent(TidepoolUiState(), onFullSync = { fullSyncClicks++ })

        compose.onNodeWithText(sync("upload_now")).assertIsDisplayed()
        compose.onNodeWithText(sync("clear_log")).assertIsDisplayed()
        compose.onNodeWithText(sync("tidepool_purge_data")).assertIsDisplayed()
        compose.onNodeWithText(sync("full_sync")).assertIsDisplayed().performClick()
        assertThat(fullSyncClicks).isEqualTo(1)
    }
}
