package app.aaps.core.ui.compose

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Proof-of-concept Compose UI test running on the JVM (no device/emulator) via Robolectric.
 *
 * Demonstrates the testing stack we want to roll out for Compose screens:
 *  - [createComposeRule] (a JUnit4 TestRule) + [RobolectricTestRunner] (a JUnit4 runner) run the
 *    composable headlessly on the JVM.
 *  - These JUnit4-style tests are bridged onto the JUnit Platform by the vintage engine, so they
 *    run alongside the module's existing Jupiter tests (see [VintageJupiterCoexistenceTest]).
 *
 * NATIVE graphics mode renders real pixels so layout/visibility assertions behave like on-device.
 * SDK is pinned for deterministic Robolectric framework selection.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class AapsFabTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun aapsFab_rendersContent_andFiresOnClick() {
        var clicks = 0
        compose.setContent {
            MaterialTheme {
                AapsFab(onClick = { clicks++ }, modifier = Modifier.testTag("fab")) {
                    Text("Add")
                }
            }
        }

        compose.onNodeWithText("Add").assertIsDisplayed()
        compose.onNodeWithTag("fab").performClick()

        assertThat(clicks).isEqualTo(1)
    }
}
