package app.aaps.core.ui.compose.dialogs

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performImeAction
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
class SetPasswordDialogTest {

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

    private fun show(onConfirm: (String, String) -> Unit = { _, _ -> }, onCancel: () -> Unit = {}, requireConfirmation: Boolean = true) {
        compose.setContent {
            MaterialTheme {
                SetPasswordDialog(
                    title = "Set New Password",
                    pinInput = false,
                    onConfirm = onConfirm,
                    onCancel = onCancel,
                    requireConfirmation = requireConfirmation
                )
            }
        }
    }

    @Test
    fun enteringBothFields_confirmsBothValues() {
        var first: String? = null
        var second: String? = null
        show(onConfirm = { a, b -> first = a; second = b })

        val fields = compose.onAllNodes(hasSetTextAction())
        fields[0].performTextInput("alpha")
        fields[1].performTextInput("beta")
        compose.onNodeWithText(okLabel).performClick()

        assertThat(first).isEqualTo("alpha")
        assertThat(second).isEqualTo("beta")
    }

    @Test
    fun singleEntry_submitsFromKeyboardWithoutConfirmationField() {
        var submitted: Pair<String, String>? = null
        show(onConfirm = { a, b -> submitted = a to b }, requireConfirmation = false)

        val fields = compose.onAllNodes(hasSetTextAction())
        fields.assertCountEquals(1)
        fields[0].performTextInput("dms-password")
        fields[0].performImeAction()

        assertThat(submitted).isEqualTo("dms-password" to "dms-password")
    }

    @Test
    fun cancel_firesOnCancel() {
        var cancels = 0
        show(onCancel = { cancels++ })

        compose.onNodeWithText(cancelLabel).performClick()

        assertThat(cancels).isEqualTo(1)
    }
}
