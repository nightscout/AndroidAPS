package app.aaps.core.ui.compose

import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class PluginTabsContentTest {

    @get:Rule
    val compose = createComposeRule()

    /** Which tab last ran its "set the toolbar" effect, as a tab of a plugin screen does. */
    private var toolbarOwner = ""

    private fun show() {
        compose.setContent {
            PluginTabsContent(
                tabs = listOf(
                    PluginTab("Status") {
                        LaunchedEffect(Unit) { toolbarOwner = "status" }
                        Text("status content")
                    },
                    PluginTab("BG") {
                        LaunchedEffect(Unit) { toolbarOwner = "bg" }
                        Text("bg content")
                    }
                )
            )
        }
    }

    @Test
    fun opensOnTheFirstTab() {
        show()
        compose.onNodeWithText("status content").assertIsDisplayed()
        compose.onNodeWithText("bg content").assertDoesNotExist()
    }

    /** Only the selected tab is on screen, so it alone sets the toolbar - and sets it again when chosen again. */
    @Test
    fun aChosenTabReplacesTheOtherAndOwnsTheToolbar() {
        show()
        compose.runOnIdle { assertThat(toolbarOwner).isEqualTo("status") }

        compose.onNodeWithText("BG").performClick()
        compose.onNodeWithText("bg content").assertIsDisplayed()
        compose.onNodeWithText("status content").assertDoesNotExist()
        compose.runOnIdle { assertThat(toolbarOwner).isEqualTo("bg") }

        compose.onNodeWithText("Status").performClick()
        compose.runOnIdle { assertThat(toolbarOwner).isEqualTo("status") }
    }
}
