package app.aaps.ui.compose.scenes.wizard

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import app.aaps.core.ui.R
import app.aaps.ui.compose.scenes.SceneTemplate
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
class InfoStepTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var backLabel: String
    private lateinit var nextLabel: String
    private lateinit var exerciseName: String
    private lateinit var sleepName: String

    @Before
    fun setUp() {
        val app = RuntimeEnvironment.getApplication()
        backLabel = app.getString(R.string.back)
        nextLabel = app.getString(R.string.next)
        exerciseName = app.getString(R.string.scene_template_exercise)
        sleepName = app.getString(R.string.scene_template_sleep)
    }

    @Test
    fun rendersTemplateName_andNavigationButtons() {
        compose.setContent {
            MaterialTheme {
                InfoStep(state = previewState, onBack = {}, onNext = {})
            }
        }

        compose.onNodeWithText(exerciseName).assertIsDisplayed()
        compose.onNodeWithText(backLabel).assertIsDisplayed()
        compose.onNodeWithText(nextLabel).assertIsDisplayed()
    }

    @Test
    fun rendersDifferentTemplateName_whenTemplateChanges() {
        compose.setContent {
            MaterialTheme {
                InfoStep(state = previewState.copy(template = SceneTemplate.SLEEP), onBack = {}, onNext = {})
            }
        }

        compose.onNodeWithText(sleepName).assertIsDisplayed()
    }

    @Test
    fun rendersNothing_whenTemplateIsNull() {
        compose.setContent {
            MaterialTheme {
                InfoStep(state = previewState.copy(template = null), onBack = {}, onNext = {})
            }
        }

        compose.onNodeWithText(exerciseName).assertDoesNotExist()
        compose.onNodeWithText(backLabel).assertDoesNotExist()
    }
}
