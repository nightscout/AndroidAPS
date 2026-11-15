package app.aaps.core.ui.compose.dialogs

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.buildAnnotatedString
import app.aaps.core.ui.R
import com.google.common.truth.Truth.assertThat
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
class OkDialogTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var okLabel: String

    @Before
    fun setUp() {
        okLabel = RuntimeEnvironment.getApplication().getString(R.string.ok)
    }

    @Test
    fun rendersTitleMessageAndOk() {
        compose.setContent {
            MaterialTheme {
                OkDialog(title = "Information", message = "Operation completed.", onDismiss = {})
            }
        }

        compose.onNodeWithText("Information").assertIsDisplayed()
        compose.onNodeWithText("Operation completed.").assertIsDisplayed()
        compose.onNodeWithText(okLabel).assertIsDisplayed()
    }

    @Test
    fun okButton_firesOnDismiss() {
        var dismisses = 0
        compose.setContent {
            MaterialTheme {
                OkDialog(title = "Information", message = "Operation completed.", onDismiss = { dismisses++ })
            }
        }

        compose.onNodeWithText(okLabel).performClick()

        assertThat(dismisses).isEqualTo(1)
    }

    @Test
    fun annotatedString_rendersTitleMessageAndOk() {
        compose.setContent {
            MaterialTheme {
                OkDialog(
                    title = "Information",
                    message = buildAnnotatedString { append("Annotated body.") },
                    onDismiss = {}
                )
            }
        }

        compose.onNodeWithText("Information").assertIsDisplayed()
        compose.onNodeWithText("Annotated body.").assertIsDisplayed()
        compose.onNodeWithText(okLabel).assertIsDisplayed()
    }

    @Test
    fun annotatedString_okButton_firesOnDismiss() {
        var dismisses = 0
        compose.setContent {
            MaterialTheme {
                OkDialog(
                    title = "Information",
                    message = buildAnnotatedString { append("Annotated body.") },
                    onDismiss = { dismisses++ }
                )
            }
        }

        compose.onNodeWithText(okLabel).performClick()

        assertThat(dismisses).isEqualTo(1)
    }
}
