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
class DatePickerModalTest {

    @get:Rule
    val compose = createComposeRule()

    // 2024-01-01T00:00:00Z — UTC midnight so the picker round-trips it unchanged.
    private val initialDate = 1_704_067_200_000L

    private lateinit var okLabel: String
    private lateinit var cancelLabel: String

    @Before
    fun setUp() {
        val context: Context = RuntimeEnvironment.getApplication()
        okLabel = context.getString(R.string.ok)
        cancelLabel = context.getString(R.string.cancel)
    }

    @Test
    fun ok_returnsSelectedDate_andDismisses() {
        var selected: Long? = null
        var dismisses = 0
        compose.setContent {
            MaterialTheme {
                DatePickerModal(
                    onDateSelected = { selected = it },
                    onDismiss = { dismisses++ },
                    initialDateMillis = initialDate
                )
            }
        }

        compose.onNodeWithText(okLabel).performClick()

        assertThat(selected).isEqualTo(initialDate)
        assertThat(dismisses).isEqualTo(1)
    }

    @Test
    fun cancel_dismissesWithoutSelecting() {
        var selected: Long? = null
        var dismisses = 0
        compose.setContent {
            MaterialTheme {
                DatePickerModal(
                    onDateSelected = { selected = it },
                    onDismiss = { dismisses++ },
                    initialDateMillis = initialDate
                )
            }
        }

        compose.onNodeWithText(cancelLabel).performClick()

        assertThat(selected).isNull()
        assertThat(dismisses).isEqualTo(1)
    }
}
