package app.aaps.ui.compose.scenes.wizard

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import app.aaps.core.ui.R
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
class DurationStepTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var durationInfo: String
    private lateinit var indefiniteLabel: String

    @Before
    fun setUp() {
        val app = RuntimeEnvironment.getApplication()
        durationInfo = app.getString(R.string.scene_wizard_duration_info)
        indefiniteLabel = app.getString(R.string.scene_duration_indefinite)
    }

    @Test
    fun rendersDurationInfo() {
        compose.setContent {
            MaterialTheme {
                DurationStep(state = previewState, onSetDuration = {}, onBack = {}, onNext = {})
            }
        }

        compose.onNodeWithText(durationInfo).assertIsDisplayed()
    }

    @Test
    fun showsIndefinite_whenDurationIsZero() {
        compose.setContent {
            MaterialTheme {
                DurationStep(
                    state = previewState.copy(durationMinutes = 0),
                    onSetDuration = {},
                    onBack = {}, onNext = {}
                )
            }
        }

        compose.onNodeWithText(indefiniteLabel).assertIsDisplayed()
    }

    @Test
    fun doesNotShowIndefinite_whenDurationIsPositive() {
        compose.setContent {
            MaterialTheme {
                DurationStep(
                    state = previewState.copy(durationMinutes = 60),
                    onSetDuration = {},
                    onBack = {}, onNext = {}
                )
            }
        }

        compose.onNodeWithText(indefiniteLabel).assertDoesNotExist()
    }
}
