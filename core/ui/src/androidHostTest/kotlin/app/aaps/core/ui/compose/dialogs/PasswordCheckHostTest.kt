package app.aaps.core.ui.compose.dialogs

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import app.aaps.core.interfaces.protection.PasswordCheck
import app.aaps.core.interfaces.protection.PasswordRequest
import app.aaps.core.keys.interfaces.StringPreferenceKey
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.ui.R
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Tests the [PasswordCheckHost] router: it watches [PasswordCheck.request] and renders the matching
 * dialog, handing what the user typed back through the request's callbacks.
 *
 * The fake [PasswordCheck] only publishes requests - deciding whether a password is CORRECT belongs
 * to the implementation, and this test covers the wiring between the two.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class PasswordCheckHostTest {

    @get:Rule
    val compose = createComposeRule()

    private val passwordCheck = FakePasswordCheck()

    private lateinit var okLabel: String
    private lateinit var cancelLabel: String

    @Before
    fun setUp() {
        val context: Context = RuntimeEnvironment.getApplication()
        okLabel = context.getString(R.string.ok)
        cancelLabel = context.getString(R.string.cancel)
        compose.setContent {
            MaterialTheme {
                PasswordCheckHost(passwordCheck)
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun noRequest_showsNothing() {
        compose.onNodeWithText(okLabel).assertDoesNotExist()
    }

    @Test
    fun queryRequest_passesTypedPasswordToOnConfirm() {
        var entered: String? = null
        passwordCheck.publish(
            PasswordRequest.Query(
                label = TextRef.Literal("Enter password"),
                pinInput = false,
                onConfirm = { entered = it },
                onCancel = {}
            )
        )
        compose.waitForIdle()

        compose.onNodeWithText("Enter password").assertIsDisplayed()
        compose.onNode(hasSetTextAction()).performTextInput("secret")
        compose.onNodeWithText(okLabel).performClick()
        compose.waitForIdle()

        assertThat(entered).isEqualTo("secret")
    }

    @Test
    fun queryRequest_cancel_firesOnCancel() {
        var cancelled = 0
        passwordCheck.publish(
            PasswordRequest.Query(
                label = TextRef.Literal("Enter password"),
                pinInput = false,
                onConfirm = {},
                onCancel = { cancelled++ }
            )
        )
        compose.waitForIdle()

        compose.onNodeWithText(cancelLabel).performClick()
        compose.waitForIdle()

        assertThat(cancelled).isEqualTo(1)
    }

    @Test
    fun clearedRequest_hidesDialog() {
        passwordCheck.publish(
            PasswordRequest.Query(
                label = TextRef.Literal("Enter password"),
                pinInput = false,
                onConfirm = {},
                onCancel = {}
            )
        )
        compose.waitForIdle()
        compose.onNodeWithText("Enter password").assertIsDisplayed()

        passwordCheck.publish(null)
        compose.waitForIdle()

        compose.onNodeWithText("Enter password").assertDoesNotExist()
    }

    @Test
    fun queryAnyRequest_showsExplanationAndWarning() {
        passwordCheck.publish(
            PasswordRequest.QueryAny(
                label = TextRef.Literal("Import password"),
                explanation = TextRef.Literal("Password used to encrypt the file"),
                warning = TextRef.Literal("A wrong password fails the import"),
                onConfirm = {},
                onCancel = {}
            )
        )
        compose.waitForIdle()

        compose.onNodeWithText("Password used to encrypt the file").assertIsDisplayed()
        compose.onNodeWithText("A wrong password fails the import").assertIsDisplayed()
    }

    private class FakePasswordCheck : PasswordCheck {

        private val _request = MutableStateFlow<PasswordRequest?>(null)
        override val request: StateFlow<PasswordRequest?> = _request.asStateFlow()

        fun publish(value: PasswordRequest?) {
            _request.value = value
        }

        override fun queryPassword(
            label: TextRef,
            preference: StringPreferenceKey,
            ok: ((String) -> Unit)?,
            cancel: (() -> Unit)?,
            fail: (() -> Unit)?,
            pinInput: Boolean
        ) = Unit

        override fun setPassword(
            label: TextRef,
            preference: StringPreferenceKey,
            ok: ((String) -> Unit)?,
            cancel: (() -> Unit)?,
            clear: (() -> Unit)?,
            pinInput: Boolean
        ) = Unit

        override fun queryAnyPassword(
            label: TextRef,
            preference: StringPreferenceKey,
            passwordExplanation: TextRef?,
            passwordWarning: TextRef?,
            ok: ((String) -> Unit)?,
            cancel: (() -> Unit)?
        ) = Unit
    }
}
