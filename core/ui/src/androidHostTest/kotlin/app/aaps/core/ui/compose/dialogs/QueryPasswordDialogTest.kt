package app.aaps.core.ui.compose.dialogs

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
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
class QueryPasswordDialogTest {

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
                QueryPasswordDialog(
                    title = "Enter Password",
                    pinInput = false,
                    onConfirm = onConfirm,
                    onCancel = onCancel
                )
            }
        }
    }

    @Test
    fun ok_disabledWhenBlank() {
        show()
        compose.onNodeWithText("Enter Password").assertIsDisplayed()
        compose.onNodeWithText(okLabel).assertIsNotEnabled()
    }

    @Test
    fun enteringPassword_enablesOk_andConfirmsValue() {
        var confirmed: String? = null
        show(onConfirm = { confirmed = it })

        compose.onNode(hasSetTextAction()).performTextInput("secret")
        compose.onNodeWithText(okLabel).performClick()

        assertThat(confirmed).isEqualTo("secret")
    }

    @Test
    fun cancel_firesOnCancel() {
        var cancels = 0
        var confirmed: String? = null
        show(onConfirm = { confirmed = it }, onCancel = { cancels++ })

        compose.onNodeWithText(cancelLabel).performClick()

        assertThat(cancels).isEqualTo(1)
        assertThat(confirmed).isNull()
    }
}
