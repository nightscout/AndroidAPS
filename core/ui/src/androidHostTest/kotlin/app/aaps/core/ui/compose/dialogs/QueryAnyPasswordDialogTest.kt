package app.aaps.core.ui.compose.dialogs

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
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
class QueryAnyPasswordDialogTest {

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

    private fun show(onConfirm: (String) -> Unit = {}, onCancel: () -> Unit = {}) {
        compose.setContent {
            MaterialTheme {
                QueryAnyPasswordDialog(
                    title = "Import Password",
                    passwordExplanation = "Enter the export password.",
                    passwordWarning = "Wrong password fails the import.",
                    onConfirm = onConfirm,
                    onCancel = onCancel
                )
            }
        }
    }

    @Test
    fun rendersExplanationAndWarning() {
        show()
        compose.onNodeWithText("Enter the export password.").assertIsDisplayed()
        compose.onNodeWithText("Wrong password fails the import.").assertIsDisplayed()
    }

    @Test
    fun enteringPassword_confirmsValue() {
        var confirmed: String? = null
        show(onConfirm = { confirmed = it })

        compose.onNode(hasSetTextAction()).performTextInput("hunter2")
        compose.onNodeWithText(okLabel).performClick()

        assertThat(confirmed).isEqualTo("hunter2")
    }

    @Test
    fun cancel_firesOnCancel() {
        var cancels = 0
        show(onCancel = { cancels++ })

        compose.onNodeWithText(cancelLabel).performClick()

        assertThat(cancels).isEqualTo(1)
    }
}
