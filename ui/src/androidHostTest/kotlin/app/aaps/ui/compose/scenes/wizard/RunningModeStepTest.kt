package app.aaps.ui.compose.scenes.wizard

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.isToggleable
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
class RunningModeStepTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var title: String

    @Before
    fun setUp() {
        title = RuntimeEnvironment.getApplication().getString(R.string.running_mode)
    }

    @Test
    fun rendersTitle_andToggleReflectsDisabledState() {
        compose.setContent {
            MaterialTheme {
                RunningModeStep(
                    state = previewState.copy(runningModeEnabled = false),
                    onToggle = {}, onUpdate = {},
                    onBack = {}, onNext = {}
                )
            }
        }

        compose.onNodeWithText(title).assertIsDisplayed()
        compose.onNode(isToggleable()).assertIsOff()
    }

    @Test
    fun togglingSection_firesCallback_withNewValue() {
        var toggled: Boolean? = null
        compose.setContent {
            MaterialTheme {
                RunningModeStep(
                    state = previewState.copy(runningModeEnabled = false),
                    onToggle = { toggled = it }, onUpdate = {},
                    onBack = {}, onNext = {}
                )
            }
        }

        compose.onNode(isToggleable()).performClick()

        assertThat(toggled).isEqualTo(true)
    }
}
