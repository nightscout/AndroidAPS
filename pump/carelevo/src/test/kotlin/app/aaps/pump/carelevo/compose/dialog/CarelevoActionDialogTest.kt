package app.aaps.pump.carelevo.compose.dialog

import androidx.compose.material3.MaterialTheme
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

/**
 * Compose UI test for [CarelevoActionDialog].
 *
 * The dialog is fully state-hoisted (every input is a parameter), so no ViewModel is involved.
 * It only reads `MaterialTheme.typography`, hence the plain `MaterialTheme` wrapper rather than
 * `AapsTheme` (which additionally requires the `LocalPreferences` CompositionLocal).
 *
 * Covered branches:
 *  - `content.isNotBlank()` -> rendered / skipped
 *  - `content.contains('<') || content.contains("<br>")` -> HTML parsing vs plain AnnotatedString
 *  - the `"\n"` -> `"<br>"` replacement inside the HTML branch
 *  - `subContent.isNotBlank()` -> rendered / skipped (incl. the default `""`)
 *  - `secondaryText != null && onSecondaryClick != null` -> dismiss button rendered / skipped,
 *    including both half-supplied combinations
 *  - primary / secondary click callbacks fire, and fire only on their own button
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class CarelevoActionDialogTest {

    @get:Rule
    val compose = createComposeRule()

    private val title = "Deactivate patch?"
    private val primaryLabel = "Deactivate"
    private val secondaryLabel = "Cancel"

    @Test
    fun rendersTitleContentAndPrimaryButton() {
        compose.setContent {
            MaterialTheme {
                CarelevoActionDialog(
                    title = title,
                    content = "Current patch will be deactivated.",
                    onDismissRequest = {},
                    primaryText = primaryLabel,
                    onPrimaryClick = {}
                )
            }
        }

        compose.onNodeWithText(title).assertIsDisplayed()
        compose.onNodeWithText("Current patch will be deactivated.").assertIsDisplayed()
        compose.onNodeWithText(primaryLabel).assertIsDisplayed()
    }

    @Test
    fun rendersSecondaryButton_whenBothSecondaryTextAndCallbackProvided() {
        compose.setContent {
            MaterialTheme {
                CarelevoActionDialog(
                    title = title,
                    content = "Body",
                    onDismissRequest = {},
                    primaryText = primaryLabel,
                    onPrimaryClick = {},
                    secondaryText = secondaryLabel,
                    onSecondaryClick = {}
                )
            }
        }

        compose.onNodeWithText(primaryLabel).assertIsDisplayed()
        compose.onNodeWithText(secondaryLabel).assertIsDisplayed()
    }

    @Test
    fun hidesSecondaryButton_whenSecondaryTextAndCallbackOmitted() {
        compose.setContent {
            MaterialTheme {
                CarelevoActionDialog(
                    title = title,
                    content = "Body",
                    onDismissRequest = {},
                    primaryText = primaryLabel,
                    onPrimaryClick = {}
                )
            }
        }

        compose.onNodeWithText(primaryLabel).assertIsDisplayed()
        compose.onNodeWithText(secondaryLabel).assertDoesNotExist()
    }

    @Test
    fun hidesSecondaryButton_whenCallbackMissing() {
        compose.setContent {
            MaterialTheme {
                CarelevoActionDialog(
                    title = title,
                    content = "Body",
                    onDismissRequest = {},
                    primaryText = primaryLabel,
                    onPrimaryClick = {},
                    secondaryText = secondaryLabel,
                    onSecondaryClick = null
                )
            }
        }

        compose.onNodeWithText(secondaryLabel).assertDoesNotExist()
    }

    @Test
    fun hidesSecondaryButton_whenTextMissing() {
        compose.setContent {
            MaterialTheme {
                CarelevoActionDialog(
                    title = title,
                    content = "Body",
                    onDismissRequest = {},
                    primaryText = primaryLabel,
                    onPrimaryClick = {},
                    secondaryText = null,
                    onSecondaryClick = {}
                )
            }
        }

        compose.onNodeWithText(secondaryLabel).assertDoesNotExist()
    }

    @Test
    fun hidesContent_whenContentIsBlank_butStillRendersSubContent() {
        val blank = "   "
        compose.setContent {
            MaterialTheme {
                CarelevoActionDialog(
                    title = title,
                    content = blank,
                    onDismissRequest = {},
                    primaryText = primaryLabel,
                    onPrimaryClick = {},
                    subContent = "Sub only"
                )
            }
        }

        compose.onNodeWithText(title).assertIsDisplayed()
        // A whitespace-only content must not be emitted as a Text node at all.
        compose.onNodeWithText(blank).assertDoesNotExist()
        compose.onNodeWithText("Sub only").assertIsDisplayed()
    }

    @Test
    fun rendersSubContent_whenProvided() {
        compose.setContent {
            MaterialTheme {
                CarelevoActionDialog(
                    title = title,
                    content = "Main body",
                    onDismissRequest = {},
                    primaryText = primaryLabel,
                    onPrimaryClick = {},
                    subContent = "Extra details"
                )
            }
        }

        compose.onNodeWithText("Main body").assertIsDisplayed()
        compose.onNodeWithText("Extra details").assertIsDisplayed()
    }

    @Test
    fun rendersContent_whenSubContentLeftAtDefaultEmpty() {
        // Exercises the `subContent.isNotBlank()` false branch via the default argument: the dialog
        // still renders its content and primary action, and emits no subContent Text.
        compose.setContent {
            MaterialTheme {
                CarelevoActionDialog(
                    title = title,
                    content = "Main body",
                    onDismissRequest = {},
                    primaryText = primaryLabel,
                    onPrimaryClick = {}
                )
            }
        }

        compose.onNodeWithText(title).assertIsDisplayed()
        compose.onNodeWithText("Main body").assertIsDisplayed()
        compose.onNodeWithText(primaryLabel).assertIsDisplayed()
    }

    @Test
    fun plainContent_withNewline_isRenderedVerbatim() {
        // No '<' anywhere -> plain AnnotatedString branch, newline preserved as-is.
        compose.setContent {
            MaterialTheme {
                CarelevoActionDialog(
                    title = title,
                    content = "Alpha\nBravo",
                    onDismissRequest = {},
                    primaryText = primaryLabel,
                    onPrimaryClick = {}
                )
            }
        }

        compose.onNodeWithText("Alpha\nBravo").assertIsDisplayed()
    }

    @Test
    fun htmlContent_isParsed_andTagsAreStripped() {
        compose.setContent {
            MaterialTheme {
                CarelevoActionDialog(
                    title = title,
                    content = "<b>Bold warning</b>",
                    onDismissRequest = {},
                    primaryText = primaryLabel,
                    onPrimaryClick = {}
                )
            }
        }

        compose.onNodeWithText("Bold warning", substring = true).assertIsDisplayed()
        // The raw markup must not survive into the rendered text.
        compose.onNodeWithText("<b>Bold warning</b>").assertDoesNotExist()
    }

    @Test
    fun htmlContent_newlinesAreConvertedToLineBreaks() {
        // Contains '<' -> HTML branch, so the "\n" is replaced by "<br>" before parsing.
        compose.setContent {
            MaterialTheme {
                CarelevoActionDialog(
                    title = title,
                    content = "<i>Alpha</i>\nBravo",
                    onDismissRequest = {},
                    primaryText = primaryLabel,
                    onPrimaryClick = {}
                )
            }
        }

        compose.onNodeWithText("Alpha", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Bravo", substring = true).assertIsDisplayed()
        compose.onNodeWithText("<i>Alpha</i>", substring = true).assertDoesNotExist()
    }

    @Test
    fun contentWithOnlyBrTag_takesHtmlBranch() {
        compose.setContent {
            MaterialTheme {
                CarelevoActionDialog(
                    title = title,
                    content = "First<br>Second",
                    onDismissRequest = {},
                    primaryText = primaryLabel,
                    onPrimaryClick = {}
                )
            }
        }

        compose.onNodeWithText("First", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Second", substring = true).assertIsDisplayed()
        compose.onNodeWithText("First<br>Second").assertDoesNotExist()
    }

    @Test
    fun primaryButton_firesOnPrimaryClickOnly() {
        var primary = 0
        var secondary = 0
        var dismiss = 0
        compose.setContent {
            MaterialTheme {
                CarelevoActionDialog(
                    title = title,
                    content = "Body",
                    onDismissRequest = { dismiss++ },
                    primaryText = primaryLabel,
                    onPrimaryClick = { primary++ },
                    secondaryText = secondaryLabel,
                    onSecondaryClick = { secondary++ }
                )
            }
        }

        compose.onNodeWithText(primaryLabel).performClick()

        assertThat(primary).isEqualTo(1)
        assertThat(secondary).isEqualTo(0)
        assertThat(dismiss).isEqualTo(0)
    }

    @Test
    fun secondaryButton_firesOnSecondaryClickOnly() {
        var primary = 0
        var secondary = 0
        var dismiss = 0
        compose.setContent {
            MaterialTheme {
                CarelevoActionDialog(
                    title = title,
                    content = "Body",
                    onDismissRequest = { dismiss++ },
                    primaryText = primaryLabel,
                    onPrimaryClick = { primary++ },
                    secondaryText = secondaryLabel,
                    onSecondaryClick = { secondary++ }
                )
            }
        }

        compose.onNodeWithText(secondaryLabel).performClick()

        assertThat(secondary).isEqualTo(1)
        assertThat(primary).isEqualTo(0)
        assertThat(dismiss).isEqualTo(0)
    }

    @Test
    fun primaryButton_isClickableRepeatedly() {
        var primary = 0
        compose.setContent {
            MaterialTheme {
                CarelevoActionDialog(
                    title = title,
                    content = "Body",
                    onDismissRequest = {},
                    primaryText = primaryLabel,
                    onPrimaryClick = { primary++ }
                )
            }
        }

        compose.onNodeWithText(primaryLabel).performClick()
        compose.onNodeWithText(primaryLabel).performClick()

        assertThat(primary).isEqualTo(2)
    }
}
