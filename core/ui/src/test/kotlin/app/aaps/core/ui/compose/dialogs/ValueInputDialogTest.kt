package app.aaps.core.ui.compose.dialogs

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
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
class ValueInputDialogTest {

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

    private fun show(
        currentValue: Double = 5.0,
        onValueConfirm: (Double) -> Unit = {},
        onDismiss: () -> Unit = {}
    ) {
        compose.setContent {
            MaterialTheme {
                ValueInputDialog(
                    currentValue = currentValue,
                    valueRange = 0.0..10.0,
                    label = "Insulin",
                    unitLabel = "U",
                    onValueConfirm = onValueConfirm,
                    onDismiss = onDismiss
                )
            }
        }
    }

    @Test
    fun rendersLabel() {
        show()
        compose.onNodeWithText("Insulin").assertIsDisplayed()
    }

    @Test
    fun ok_confirmsCurrentValue_andDismisses() {
        var confirmed: Double? = null
        var dismisses = 0
        show(currentValue = 5.0, onValueConfirm = { confirmed = it }, onDismiss = { dismisses++ })

        compose.onNodeWithText(okLabel).performClick()

        assertThat(confirmed).isEqualTo(5.0)
        assertThat(dismisses).isEqualTo(1)
    }

    @Test
    fun ok_confirmsEditedValue() {
        var confirmed: Double? = null
        show(currentValue = 5.0, onValueConfirm = { confirmed = it })

        compose.onNode(hasSetTextAction()).performTextReplacement("8")
        compose.onNodeWithText(okLabel).performClick()

        assertThat(confirmed).isEqualTo(8.0)
    }

    @Test
    fun outOfRangeValue_doesNotConfirm() {
        var confirmed: Double? = null
        var dismisses = 0
        show(onValueConfirm = { confirmed = it }, onDismiss = { dismisses++ })

        compose.onNode(hasSetTextAction()).performTextReplacement("20")
        compose.onNodeWithText(okLabel).performClick()

        assertThat(confirmed).isNull()
        assertThat(dismisses).isEqualTo(0)
    }

    @Test
    fun cancel_dismissesWithoutConfirming() {
        var confirmed: Double? = null
        var dismisses = 0
        show(onValueConfirm = { confirmed = it }, onDismiss = { dismisses++ })

        compose.onNodeWithText(cancelLabel).performClick()

        assertThat(confirmed).isNull()
        assertThat(dismisses).isEqualTo(1)
    }
}
