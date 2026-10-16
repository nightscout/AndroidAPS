package app.aaps.core.ui.compose.dialogs

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
class TimePickerModalTest {

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
    fun ok_returnsInitialTime_andDismisses() {
        var hour: Int? = null
        var minute: Int? = null
        var dismisses = 0
        compose.setContent {
            MaterialTheme {
                TimePickerModal(
                    onTimeSelected = { h, m -> hour = h; minute = m },
                    onDismiss = { dismisses++ },
                    initialHour = 14,
                    initialMinute = 30
                )
            }
        }

        compose.onNodeWithText(okLabel).performClick()

        assertThat(hour).isEqualTo(14)
        assertThat(minute).isEqualTo(30)
        assertThat(dismisses).isEqualTo(1)
    }

    @Test
    fun cancel_dismissesWithoutSelecting() {
        var hour: Int? = null
        var dismisses = 0
        compose.setContent {
            MaterialTheme {
                TimePickerModal(
                    onTimeSelected = { h, _ -> hour = h },
                    onDismiss = { dismisses++ },
                    initialHour = 14,
                    initialMinute = 30
                )
            }
        }

        compose.onNodeWithText(cancelLabel).performClick()

        assertThat(hour).isNull()
        assertThat(dismisses).isEqualTo(1)
    }
}
