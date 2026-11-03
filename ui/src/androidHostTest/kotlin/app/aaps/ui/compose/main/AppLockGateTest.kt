package app.aaps.ui.compose.main

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
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
class AppLockGateTest {

    @get:Rule
    val compose = createComposeRule()

    private var refused = 0

    private fun setGate(state: AppLockState) {
        compose.setContent {
            MaterialTheme {
                AppLockGate(state = state, refusedMessage = REFUSED_TEXT, onRefused = { refused++ }) {
                    Text(CONTENT_TEXT)
                }
            }
        }
    }

    @Test
    fun openShowsTheContent() {
        setGate(AppLockState.OPEN)
        compose.onNodeWithText(CONTENT_TEXT).assertIsDisplayed()
    }

    /** No semantics node also means a screen reader cannot read the content behind the prompt. */
    @Test
    fun checkingHidesTheContent() {
        setGate(AppLockState.CHECKING)
        compose.onNodeWithText(CONTENT_TEXT).assertDoesNotExist()
        compose.onNodeWithText(REFUSED_TEXT).assertDoesNotExist()
    }

    @Test
    fun refusedHidesTheContentAndSaysSo() {
        setGate(AppLockState.REFUSED)
        compose.onNodeWithText(CONTENT_TEXT).assertDoesNotExist()
        compose.onNodeWithText(REFUSED_TEXT).assertIsDisplayed()
    }

    @Test
    fun refusedAnswersWhenTheDialogIsConfirmed() {
        setGate(AppLockState.REFUSED)
        assertThat(refused).isEqualTo(0)

        compose.onNodeWithText("OK", ignoreCase = true).performClick()

        assertThat(refused).isEqualTo(1)
    }

    private companion object {

        const val CONTENT_TEXT = "BG 7.1"
        const val REFUSED_TEXT = "Authorization failed"
    }
}
