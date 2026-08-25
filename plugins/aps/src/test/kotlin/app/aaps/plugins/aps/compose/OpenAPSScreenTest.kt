package app.aaps.plugins.aps.compose

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import app.aaps.core.ui.R
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
class OpenAPSScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var resultLabel: String

    @Before
    fun setUp() {
        resultLabel = RuntimeEnvironment.getApplication().getString(R.string.result)
    }

    @Test
    fun showsStatusMessage_whenNoSections() {
        compose.setContent {
            MaterialTheme {
                OpenAPSScreen(state = OpenAPSUiState(statusMessage = "No data available"), onRefresh = {})
            }
        }

        compose.onNodeWithText("No data available").assertIsDisplayed()
    }

    @Test
    fun showsLastRun_andExpandedSectionRows() {
        val state = OpenAPSUiState(
            lastRun = "12:34",
            sections = listOf(
                OpenAPSSection(
                    titleResId = R.string.result,
                    rows = listOf(KeyValueRow(key = "IOB", value = "1.5 U")),
                    collapsedByDefault = false
                )
            )
        )
        compose.setContent {
            MaterialTheme { OpenAPSScreen(state = state, onRefresh = {}) }
        }

        compose.onNodeWithText("12:34").assertIsDisplayed()
        compose.onNodeWithText("IOB").assertIsDisplayed()
        compose.onNodeWithText("1.5 U").assertIsDisplayed()
    }

    @Test
    fun collapsedSection_showsTitle_butHidesRows() {
        val state = OpenAPSUiState(
            sections = listOf(
                OpenAPSSection(
                    titleResId = R.string.result,
                    rows = listOf(KeyValueRow(key = "IOB", value = "1.5 U")),
                    collapsedByDefault = true
                )
            )
        )
        compose.setContent {
            MaterialTheme { OpenAPSScreen(state = state, onRefresh = {}) }
        }

        compose.onNodeWithText(resultLabel).assertIsDisplayed()
        compose.onNodeWithText("IOB").assertDoesNotExist()
        compose.onNodeWithText("1.5 U").assertDoesNotExist()
    }

    @Test
    fun groupHeaderSection_rendersTitle() {
        val state = OpenAPSUiState(
            sections = listOf(
                OpenAPSSection(titleResId = R.string.result, isGroupHeader = true)
            )
        )
        compose.setContent {
            MaterialTheme { OpenAPSScreen(state = state, onRefresh = {}) }
        }

        compose.onNodeWithText(resultLabel).assertIsDisplayed()
    }
}
