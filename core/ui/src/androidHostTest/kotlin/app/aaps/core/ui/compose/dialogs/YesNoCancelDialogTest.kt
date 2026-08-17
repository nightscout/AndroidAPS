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

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class YesNoCancelDialogTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var yesLabel: String
    private lateinit var noLabel: String
    private lateinit var cancelLabel: String

    @Before
    fun setUp() {
        val context: Context = RuntimeEnvironment.getApplication()
        yesLabel = context.getString(R.string.yes)
        noLabel = context.getString(R.string.no)
        cancelLabel = context.getString(R.string.cancel)
    }

    private fun show(onYes: () -> Unit = {}, onNo: () -> Unit = {}, onCancel: () -> Unit = {}) {
        compose.setContent {
            MaterialTheme {
                YesNoCancelDialog(
                    title = "Save Changes",
                    message = "Save your changes?",
                    onYes = onYes,
                    onNo = onNo,
                    onCancel = onCancel
                )
            }
        }
    }

    @Test
    fun rendersAllThreeButtons() {
        show()
        compose.onNodeWithText("Save Changes").assertIsDisplayed()
        compose.onNodeWithText(yesLabel).assertIsDisplayed()
        compose.onNodeWithText(noLabel).assertIsDisplayed()
        compose.onNodeWithText(cancelLabel).assertIsDisplayed()
    }

    @Test
    fun yes_firesOnYesOnly() {
        var yes = 0; var no = 0; var cancel = 0
        show(onYes = { yes++ }, onNo = { no++ }, onCancel = { cancel++ })
        compose.onNodeWithText(yesLabel).performClick()
        assertThat(yes).isEqualTo(1)
        assertThat(no).isEqualTo(0)
        assertThat(cancel).isEqualTo(0)
    }

    @Test
    fun no_firesOnNoOnly() {
        var yes = 0; var no = 0; var cancel = 0
        show(onYes = { yes++ }, onNo = { no++ }, onCancel = { cancel++ })
        compose.onNodeWithText(noLabel).performClick()
        assertThat(no).isEqualTo(1)
        assertThat(yes).isEqualTo(0)
        assertThat(cancel).isEqualTo(0)
    }

    @Test
    fun cancel_firesOnCancelOnly() {
        var yes = 0; var no = 0; var cancel = 0
        show(onYes = { yes++ }, onNo = { no++ }, onCancel = { cancel++ })
        compose.onNodeWithText(cancelLabel).performClick()
        assertThat(cancel).isEqualTo(1)
        assertThat(yes).isEqualTo(0)
        assertThat(no).isEqualTo(0)
    }

    private fun showAnnotated(onYes: () -> Unit = {}, onNo: () -> Unit = {}, onCancel: () -> Unit = {}) {
        compose.setContent {
            MaterialTheme {
                YesNoCancelDialog(
                    title = "Save Changes",
                    message = buildAnnotatedString { append("Annotated body?") },
                    onYes = onYes,
                    onNo = onNo,
                    onCancel = onCancel
                )
            }
        }
    }

    @Test
    fun annotatedString_rendersTitleMessageAndAllButtons() {
        showAnnotated()
        compose.onNodeWithText("Save Changes").assertIsDisplayed()
        compose.onNodeWithText("Annotated body?").assertIsDisplayed()
        compose.onNodeWithText(yesLabel).assertIsDisplayed()
        compose.onNodeWithText(noLabel).assertIsDisplayed()
        compose.onNodeWithText(cancelLabel).assertIsDisplayed()
    }

    @Test
    fun annotatedString_yes_firesOnYesOnly() {
        var yes = 0; var no = 0; var cancel = 0
        showAnnotated(onYes = { yes++ }, onNo = { no++ }, onCancel = { cancel++ })
        compose.onNodeWithText(yesLabel).performClick()
        assertThat(yes).isEqualTo(1)
        assertThat(no).isEqualTo(0)
        assertThat(cancel).isEqualTo(0)
    }

    @Test
    fun annotatedString_no_firesOnNoOnly() {
        var yes = 0; var no = 0; var cancel = 0
        showAnnotated(onYes = { yes++ }, onNo = { no++ }, onCancel = { cancel++ })
        compose.onNodeWithText(noLabel).performClick()
        assertThat(no).isEqualTo(1)
        assertThat(yes).isEqualTo(0)
        assertThat(cancel).isEqualTo(0)
    }

    @Test
    fun annotatedString_cancel_firesOnCancelOnly() {
        var yes = 0; var no = 0; var cancel = 0
        showAnnotated(onYes = { yes++ }, onNo = { no++ }, onCancel = { cancel++ })
        compose.onNodeWithText(cancelLabel).performClick()
        assertThat(cancel).isEqualTo(1)
        assertThat(yes).isEqualTo(0)
        assertThat(no).isEqualTo(0)
    }
}
