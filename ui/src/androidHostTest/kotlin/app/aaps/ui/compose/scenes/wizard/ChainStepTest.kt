package app.aaps.ui.compose.scenes.wizard

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.aaps.core.data.model.Scene
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
class ChainStepTest {

    @get:Rule
    val compose = createComposeRule()

    private val targets = listOf(
        Scene(id = "s1", name = "Post-Meal"),
        Scene(id = "s2", name = "Recovery")
    )

    private lateinit var noneLabel: String
    private lateinit var chainTitle: String

    @Before
    fun setUp() {
        val app = RuntimeEnvironment.getApplication()
        noneLabel = app.getString(R.string.scene_chain_none)
        chainTitle = app.getString(R.string.scene_wizard_chain_title)
    }

    @Test
    fun rendersTitle_noneOption_andAvailableTargets() {
        compose.setContent {
            MaterialTheme {
                ChainStep(
                    state = previewState,
                    availableTargets = targets,
                    onSetChainTarget = {},
                    onBack = {}, onNext = {}
                )
            }
        }

        compose.onNodeWithText(chainTitle).assertIsDisplayed()
        compose.onNodeWithText(noneLabel).assertIsDisplayed()
        compose.onNodeWithText("Post-Meal").assertIsDisplayed()
        compose.onNodeWithText("Recovery").assertIsDisplayed()
    }

    @Test
    fun rendersOnlyNone_whenNoAvailableTargets() {
        compose.setContent {
            MaterialTheme {
                ChainStep(
                    state = previewState,
                    availableTargets = emptyList(),
                    onSetChainTarget = {},
                    onBack = {}, onNext = {}
                )
            }
        }

        compose.onNodeWithText(noneLabel).assertIsDisplayed()
        compose.onNodeWithText("Post-Meal").assertDoesNotExist()
    }

    @Test
    fun selectingTarget_firesCallback_withSceneId() {
        var selected: String? = "unset"
        compose.setContent {
            MaterialTheme {
                ChainStep(
                    state = previewState,
                    availableTargets = targets,
                    onSetChainTarget = { selected = it },
                    onBack = {}, onNext = {}
                )
            }
        }

        compose.onNodeWithText("Post-Meal").performClick()

        assertThat(selected).isEqualTo("s1")
    }
}
