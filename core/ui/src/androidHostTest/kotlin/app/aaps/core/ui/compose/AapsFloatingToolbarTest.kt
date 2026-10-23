package app.aaps.core.ui.compose

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class AapsFloatingToolbarTest {

    @get:Rule
    val compose = createComposeRule()

    private var deletes = 0

    private fun setToolbar(deleteEnabled: Boolean) {
        compose.setContent {
            MaterialTheme {
                AapsFloatingToolbarRow {
                    AapsFloatingToolbar {
                        AapsFloatingToolbarDeleteButton(
                            onClick = { deletes++ },
                            enabled = deleteEnabled,
                            contentDescription = "Delete"
                        )
                    }
                    AapsFab(onClick = {}) { Text("Play") }
                }
            }
        }
    }

    @Test
    fun deleteWorksWhileEnabled() {
        setToolbar(deleteEnabled = true)

        compose.onNodeWithText("Play").assertIsDisplayed()
        compose.onNodeWithContentDescription("Delete").assertIsEnabled().performClick()

        assertThat(deletes).isEqualTo(1)
    }

    @Test
    fun deleteDoesNothingWhileDisabled() {
        // The icon has its own tint, so the button must still be disabled, not only look grey
        setToolbar(deleteEnabled = false)

        compose.onNodeWithContentDescription("Delete").assertIsNotEnabled().performClick()

        assertThat(deletes).isEqualTo(0)
    }
}
