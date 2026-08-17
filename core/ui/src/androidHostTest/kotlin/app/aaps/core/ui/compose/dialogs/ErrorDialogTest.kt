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
class ErrorDialogTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var dismissLabel: String

    @Before
    fun setUp() {
        dismissLabel = RuntimeEnvironment.getApplication().getString(R.string.dismiss)
    }

    @Test
    fun withPositiveButton_rendersBoth_andFiresCorrectCallbacks() {
        var positive = 0; var dismiss = 0
        compose.setContent {
            MaterialTheme {
                ErrorDialog(
                    title = "Error",
                    message = "Something went wrong.",
                    positiveButton = "Retry",
                    onPositive = { positive++ },
                    onDismiss = { dismiss++ }
                )
            }
        }

        compose.onNodeWithText("Error").assertIsDisplayed()
        compose.onNodeWithText("Something went wrong.").assertIsDisplayed()
        compose.onNodeWithText("Retry").assertIsDisplayed()
        compose.onNodeWithText(dismissLabel).assertIsDisplayed()

        compose.onNodeWithText("Retry").performClick()
        assertThat(positive).isEqualTo(1)
        assertThat(dismiss).isEqualTo(0)
    }

    @Test
    fun withoutPositiveButton_onlyDismissShown() {
        var dismiss = 0
        compose.setContent {
            MaterialTheme {
                ErrorDialog(
                    title = "Error",
                    message = "Something went wrong.",
                    onDismiss = { dismiss++ }
                )
            }
        }

        compose.onNodeWithText("Retry").assertDoesNotExist()
        compose.onNodeWithText(dismissLabel).performClick()
        assertThat(dismiss).isEqualTo(1)
    }

    @Test
    fun annotatedString_withPositiveButton_rendersBoth_andFiresCorrectCallbacks() {
        var positive = 0; var dismiss = 0
        compose.setContent {
            MaterialTheme {
                ErrorDialog(
                    title = "Error",
                    message = buildAnnotatedString { append("Annotated failure.") },
                    positiveButton = "Retry",
                    onPositive = { positive++ },
                    onDismiss = { dismiss++ }
                )
            }
        }

        compose.onNodeWithText("Error").assertIsDisplayed()
        compose.onNodeWithText("Annotated failure.").assertIsDisplayed()
        compose.onNodeWithText("Retry").assertIsDisplayed()
        compose.onNodeWithText(dismissLabel).assertIsDisplayed()

        compose.onNodeWithText("Retry").performClick()
        assertThat(positive).isEqualTo(1)
        assertThat(dismiss).isEqualTo(0)
    }

    @Test
    fun annotatedString_withoutPositiveButton_onlyDismissShown() {
        var dismiss = 0
        compose.setContent {
            MaterialTheme {
                ErrorDialog(
                    title = "Error",
                    message = buildAnnotatedString { append("Annotated failure.") },
                    onDismiss = { dismiss++ }
                )
            }
        }

        compose.onNodeWithText("Retry").assertDoesNotExist()
        compose.onNodeWithText(dismissLabel).performClick()
        assertThat(dismiss).isEqualTo(1)
    }
}
