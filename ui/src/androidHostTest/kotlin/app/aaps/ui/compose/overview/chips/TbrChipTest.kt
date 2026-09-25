package app.aaps.ui.compose.overview.chips

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import app.aaps.core.interfaces.overview.graph.TbrState
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
class TbrChipTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun rendersHighState() {
        compose.setContent {
            MaterialTheme {
                TbrChip(state = TbrState.HIGH, onClick = {}, modifier = Modifier.testTag("tbrChip"))
            }
        }

        compose.onNodeWithTag("tbrChip").assertIsDisplayed()
    }

    @Test
    fun rendersNoneState() {
        compose.setContent {
            MaterialTheme {
                TbrChip(state = TbrState.NONE, onClick = {}, modifier = Modifier.testTag("tbrChip"))
            }
        }

        compose.onNodeWithTag("tbrChip").assertIsDisplayed()
    }

    @Test
    fun click_firesCallback() {
        var clicks = 0
        compose.setContent {
            MaterialTheme {
                TbrChip(state = TbrState.LOW, onClick = { clicks++ }, modifier = Modifier.testTag("tbrChip"))
            }
        }

        compose.onNodeWithTag("tbrChip").performClick()

        assertThat(clicks).isEqualTo(1)
    }
}
