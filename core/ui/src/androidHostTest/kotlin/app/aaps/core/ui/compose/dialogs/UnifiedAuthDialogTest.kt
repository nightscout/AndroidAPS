package app.aaps.core.ui.compose.dialogs

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import app.aaps.core.interfaces.protection.AuthMethod
import app.aaps.core.interfaces.protection.AuthorizationResult
import app.aaps.core.interfaces.protection.ProtectionCheck
import app.aaps.core.interfaces.protection.ProtectionResult
import app.aaps.core.interfaces.protection.ProtectionType
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
class UnifiedAuthDialogTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var okLabel: String
    private lateinit var cancelLabel: String

    private val methods = listOf(
        AuthMethod(
            level = ProtectionCheck.Protection.MASTER,
            type = ProtectionType.CUSTOM_PASSWORD,
            credentialHash = "hash",
            isPinInput = false
        )
    )

    // Accepts "secret" against the stored "hash".
    private val checkPassword: (String, String) -> Boolean = { password, hash -> password == "secret" && hash == "hash" }

    @Before
    fun setUp() {
        val context: Context = RuntimeEnvironment.getApplication()
        okLabel = context.getString(R.string.ok)
        cancelLabel = context.getString(R.string.cancel)
    }

    private fun show(onResult: (AuthorizationResult) -> Unit) {
        compose.setContent {
            MaterialTheme {
                UnifiedAuthDialog(methods = methods, checkPassword = checkPassword, onResult = onResult)
            }
        }
    }

    @Test
    fun ok_disabledWhenBlank() {
        show {}
        compose.onNodeWithText(okLabel).assertIsNotEnabled()
    }

    @Test
    fun correctPassword_grantsHighestLevel() {
        var result: AuthorizationResult? = null
        show { result = it }

        compose.onNode(hasSetTextAction()).performTextInput("secret")
        compose.onNodeWithText(okLabel).performClick()

        assertThat(result).isEqualTo(
            AuthorizationResult(ProtectionCheck.Protection.MASTER, ProtectionResult.GRANTED)
        )
    }

    @Test
    fun wrongPassword_isDenied() {
        var result: AuthorizationResult? = null
        show { result = it }

        compose.onNode(hasSetTextAction()).performTextInput("wrong")
        compose.onNodeWithText(okLabel).performClick()

        assertThat(result).isEqualTo(AuthorizationResult(null, ProtectionResult.DENIED))
    }

    @Test
    fun cancel_isCancelled() {
        var result: AuthorizationResult? = null
        show { result = it }

        compose.onNodeWithText(cancelLabel).performClick()

        assertThat(result).isEqualTo(AuthorizationResult(null, ProtectionResult.CANCELLED))
    }
}
