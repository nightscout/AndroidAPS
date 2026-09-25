package app.aaps.core.ui.compose.dialogs

import android.content.Context
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

/**
 * First real screen-level Compose UI test: a state-hoisted dialog driven entirely by parameters.
 *
 * Exercises the full intended pattern for the rollout:
 *  - renders a real component ([OkCancelDialog]) headlessly on the JVM via Robolectric (v2 rule),
 *  - resolves localized button labels from Android resources (proves Robolectric resource loading),
 *  - finds nodes inside the AlertDialog's separate window (multi-window semantics traversal),
 *  - asserts callbacks fire on the correct button and nowhere else.
 *
 * Button labels are resolved from resources rather than hardcoded so the test survives label/locale
 * changes.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class OkCancelDialogTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var okLabel: String
    private lateinit var cancelLabel: String

    @Before
    fun setUp() {
        val context: Context = RuntimeEnvironment.getApplication()
        okLabel = context.getString(R.string.ok)
        cancelLabel = context.getString(R.string.cancel)
    }

    @Test
    fun rendersTitleMessageAndBothButtons() {
        compose.setContent {
            MaterialTheme {
                OkCancelDialog(
                    title = "Confirm delete",
                    message = "Are you sure?",
                    secondMessage = "Second message",
                    onConfirm = {},
                    onDismiss = {}
                )
            }
        }

        compose.onNodeWithText("Confirm delete").assertIsDisplayed()
        compose.onNodeWithText("Are you sure?").assertIsDisplayed()
        compose.onNodeWithText("Second message").assertIsDisplayed()
        compose.onNodeWithText(okLabel).assertIsDisplayed()
        compose.onNodeWithText(cancelLabel).assertIsDisplayed()
    }

    @Test
    fun okButton_firesOnConfirmOnly() {
        var confirms = 0
        var dismisses = 0
        compose.setContent {
            MaterialTheme {
                OkCancelDialog(
                    message = "Are you sure?",
                    onConfirm = { confirms++ },
                    onDismiss = { dismisses++ }
                )
            }
        }

        compose.onNodeWithText(okLabel).performClick()

        assertThat(confirms).isEqualTo(1)
        assertThat(dismisses).isEqualTo(0)
    }

    @Test
    fun cancelButton_firesOnDismissOnly() {
        var confirms = 0
        var dismisses = 0
        compose.setContent {
            MaterialTheme {
                OkCancelDialog(
                    message = "Are you sure?",
                    onConfirm = { confirms++ },
                    onDismiss = { dismisses++ }
                )
            }
        }

        compose.onNodeWithText(cancelLabel).performClick()

        assertThat(confirms).isEqualTo(0)
        assertThat(dismisses).isEqualTo(1)
    }

    @Test
    fun annotatedString_rendersTitleMessageAndBothButtons() {
        compose.setContent {
            MaterialTheme {
                OkCancelDialog(
                    title = "Confirm delete",
                    message = buildAnnotatedString { append("Annotated body?") },
                    secondMessage = "Second message",
                    onConfirm = {},
                    onDismiss = {}
                )
            }
        }

        compose.onNodeWithText("Confirm delete").assertIsDisplayed()
        compose.onNodeWithText("Annotated body?").assertIsDisplayed()
        compose.onNodeWithText("Second message").assertIsDisplayed()
        compose.onNodeWithText(okLabel).assertIsDisplayed()
        compose.onNodeWithText(cancelLabel).assertIsDisplayed()
    }

    @Test
    fun annotatedString_okButton_firesOnConfirmOnly() {
        var confirms = 0
        var dismisses = 0
        compose.setContent {
            MaterialTheme {
                OkCancelDialog(
                    message = buildAnnotatedString { append("Annotated body?") },
                    onConfirm = { confirms++ },
                    onDismiss = { dismisses++ }
                )
            }
        }

        compose.onNodeWithText(okLabel).performClick()

        assertThat(confirms).isEqualTo(1)
        assertThat(dismisses).isEqualTo(0)
    }

    @Test
    fun annotatedString_cancelButton_firesOnDismissOnly() {
        var confirms = 0
        var dismisses = 0
        compose.setContent {
            MaterialTheme {
                OkCancelDialog(
                    message = buildAnnotatedString { append("Annotated body?") },
                    onConfirm = { confirms++ },
                    onDismiss = { dismisses++ }
                )
            }
        }

        compose.onNodeWithText(cancelLabel).performClick()

        assertThat(confirms).isEqualTo(0)
        assertThat(dismisses).isEqualTo(1)
    }
}
