package app.aaps.plugins.automation.compose

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import app.aaps.core.ui.R as CoreUiR
import app.aaps.plugins.automation.R
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
class AutomationScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var emptyDesc: String
    private lateinit var masterOfflineBanner: String

    @Before
    fun setUp() {
        val app = RuntimeEnvironment.getApplication()
        emptyDesc = app.getString(R.string.automation_empty_desc)
        // core:ui R collides with automation R, so it is imported aliased as CoreUiR.
        masterOfflineBanner = app.getString(CoreUiR.string.master_offline_banner)
    }

    private fun event(key: Long, position: Int, title: String) =
        AutomationEventUi(
            key = key,
            position = position,
            title = title,
            isEnabled = true,
            readOnly = false,
            userAction = false,
            systemAction = false,
            actionsValid = true,
            triggerIcons = emptyList(),
            actionIcons = emptyList()
        )

    @Test
    fun emptyState_showsEmptyDescription() {
        compose.setContent {
            MaterialTheme {
                AutomationScreen(
                    state = AutomationUiState(),
                    onToggleEnabled = { _, _ -> },
                    onEditEvent = {},
                    onDeleteEvent = {},
                    onMove = { _, _ -> },
                    onMoveFinished = {},
                    onAddClick = {}
                )
            }
        }

        compose.onNodeWithText(emptyDesc).assertIsDisplayed()
    }

    @Test
    fun seededEvents_renderTitles() {
        val state = AutomationUiState(
            events = listOf(
                event(key = 1L, position = 0, title = "Morning wakeup TT"),
                event(key = 2L, position = 1, title = "User: Snack reminder")
            )
        )
        compose.setContent {
            MaterialTheme {
                AutomationScreen(
                    state = state,
                    onToggleEnabled = { _, _ -> },
                    onEditEvent = {},
                    onDeleteEvent = {},
                    onMove = { _, _ -> },
                    onMoveFinished = {},
                    onAddClick = {}
                )
            }
        }

        compose.onNodeWithText("Morning wakeup TT").assertIsDisplayed()
        compose.onNodeWithText("User: Snack reminder").assertIsDisplayed()
    }

    @Test
    fun masterOfflineBanner_shownWhenEditingDisabled() {
        compose.setContent {
            MaterialTheme {
                AutomationScreen(
                    state = AutomationUiState(),
                    onToggleEnabled = { _, _ -> },
                    onEditEvent = {},
                    onDeleteEvent = {},
                    onMove = { _, _ -> },
                    onMoveFinished = {},
                    onAddClick = {},
                    editingEnabled = false
                )
            }
        }

        compose.onNodeWithText(masterOfflineBanner).assertIsDisplayed()
    }

    @Test
    fun masterOfflineBanner_hiddenWhenEditingEnabled() {
        compose.setContent {
            MaterialTheme {
                AutomationScreen(
                    state = AutomationUiState(),
                    onToggleEnabled = { _, _ -> },
                    onEditEvent = {},
                    onDeleteEvent = {},
                    onMove = { _, _ -> },
                    onMoveFinished = {},
                    onAddClick = {},
                    editingEnabled = true
                )
            }
        }

        compose.onNodeWithText(masterOfflineBanner).assertDoesNotExist()
    }
}
