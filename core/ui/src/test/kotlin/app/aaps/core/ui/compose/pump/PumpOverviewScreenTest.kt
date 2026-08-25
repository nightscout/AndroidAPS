package app.aaps.core.ui.compose.pump

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import app.aaps.core.ui.compose.StatusLevel
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Render tests for the shared stateless [PumpOverviewScreen]. Every pump plugin's ComposeContent
 * builds a ViewModel (via hiltViewModel + heavy concrete pump collaborators, not unit-testable) and
 * hands the resulting [PumpOverviewUiState] to this one screen — so the rendering contract is covered
 * here, once, at its home in :core:ui instead of being duplicated across each pump module.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class PumpOverviewScreenTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun emptyState_showsNoSeededLabels() {
        compose.setContent {
            MaterialTheme { PumpOverviewScreen(state = PumpOverviewUiState()) }
        }

        compose.onNodeWithText("Reservoir").assertDoesNotExist()
    }

    @Test
    fun showsStatusBanner() {
        val state = PumpOverviewUiState(
            statusBanner = StatusBanner(text = "Pump connected now", level = StatusLevel.NORMAL)
        )
        compose.setContent {
            MaterialTheme { PumpOverviewScreen(state = state) }
        }

        compose.onNodeWithText("Pump connected now").assertIsDisplayed()
    }

    @Test
    fun showsQueueStatus() {
        val state = PumpOverviewUiState(
            statusBanner = StatusBanner(text = "Idle"),
            queueStatus = "Reading status"
        )
        compose.setContent {
            MaterialTheme { PumpOverviewScreen(state = state) }
        }

        compose.onNodeWithText("Reading status").assertIsDisplayed()
    }

    @Test
    fun showsInfoRows_labelAndValue() {
        val state = PumpOverviewUiState(
            infoRows = listOf(
                PumpInfoRow(label = "Reservoir", value = "120 U", level = StatusLevel.NORMAL),
                PumpInfoRow(label = "Battery", value = "80%", level = StatusLevel.WARNING)
            )
        )
        compose.setContent {
            MaterialTheme { PumpOverviewScreen(state = state) }
        }

        compose.onNodeWithText("Reservoir").assertIsDisplayed()
        compose.onNodeWithText("120 U").assertIsDisplayed()
        compose.onNodeWithText("Battery").assertIsDisplayed()
        compose.onNodeWithText("80%").assertIsDisplayed()
    }

    @Test
    fun showsActionButtons() {
        val state = PumpOverviewUiState(
            primaryActions = listOf(
                PumpAction(label = "Refresh", category = ActionCategory.PRIMARY, onClick = {})
            ),
            managementActions = listOf(
                PumpAction(label = "Pump history", category = ActionCategory.MANAGEMENT, onClick = {})
            )
        )
        compose.setContent {
            MaterialTheme { PumpOverviewScreen(state = state) }
        }

        compose.onNodeWithText("Refresh").assertIsDisplayed()
        compose.onNodeWithText("Pump history").assertIsDisplayed()
    }

    @Test
    fun hidesInvisibleAction() {
        val state = PumpOverviewUiState(
            managementActions = listOf(
                PumpAction(label = "Visible action", category = ActionCategory.MANAGEMENT, onClick = {}),
                PumpAction(label = "Hidden action", category = ActionCategory.MANAGEMENT, visible = false, onClick = {})
            )
        )
        compose.setContent {
            MaterialTheme { PumpOverviewScreen(state = state) }
        }

        compose.onNodeWithText("Visible action").assertIsDisplayed()
        compose.onNodeWithText("Hidden action").assertDoesNotExist()
    }
}
