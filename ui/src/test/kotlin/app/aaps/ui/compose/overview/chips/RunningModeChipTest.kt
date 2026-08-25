package app.aaps.ui.compose.overview.chips

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.aaps.core.data.model.RM
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
class RunningModeChipTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun showsRemainingText() {
        compose.setContent {
            MaterialTheme {
                RunningModeChip(
                    mode = RM.Mode.SUSPENDED_BY_USER,
                    text = "Suspended",
                    progress = 0.4f,
                    remaining = "30 min"
                )
            }
        }

        compose.onNodeWithText("30 min").assertIsDisplayed()
    }

    @Test
    fun click_firesCallback() {
        var clicks = 0
        compose.setContent {
            MaterialTheme {
                RunningModeChip(
                    mode = RM.Mode.CLOSED_LOOP,
                    text = "Closed Loop",
                    progress = 0f,
                    onClick = { clicks++ },
                    modifier = Modifier.testTag("rmChip")
                )
            }
        }

        compose.onNodeWithTag("rmChip").performClick()

        assertThat(clicks).isEqualTo(1)
    }

    @Test
    fun disabled_doesNotFireCallback() {
        var clicks = 0
        compose.setContent {
            MaterialTheme {
                RunningModeChip(
                    mode = RM.Mode.CLOSED_LOOP,
                    text = "Closed Loop",
                    progress = 0f,
                    enabled = false,
                    onClick = { clicks++ },
                    modifier = Modifier.testTag("rmChip")
                )
            }
        }

        compose.onNodeWithTag("rmChip").performClick()

        assertThat(clicks).isEqualTo(0)
    }
}
