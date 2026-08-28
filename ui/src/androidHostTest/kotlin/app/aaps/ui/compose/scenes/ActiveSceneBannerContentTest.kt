package app.aaps.ui.compose.scenes

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.aaps.core.data.model.ActiveSceneState
import app.aaps.core.data.model.Scene
import app.aaps.core.data.model.SceneLifecycle
import app.aaps.core.ui.R as CoreUiR
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Robolectric composable test for [ActiveSceneBannerContent]: active (name + End) vs expired (ended + Close). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class ActiveSceneBannerContentTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var deactivateLabel: String
    private lateinit var closeLabel: String
    private lateinit var endedLabel: String

    @Before
    fun setUp() {
        val ctx: Context = RuntimeEnvironment.getApplication()
        deactivateLabel = ctx.getString(CoreUiR.string.scene_deactivate)
        closeLabel = ctx.getString(CoreUiR.string.close)
        endedLabel = ctx.getString(CoreUiR.string.scene_ended)
    }

    private fun state(lifecycle: SceneLifecycle = SceneLifecycle.ACTIVE) =
        ActiveSceneState(scene = Scene(id = "s1", name = "Exercise"), activatedAt = 1000L, durationMs = 3_600_000L, lifecycle = lifecycle)

    @Test
    fun activeScene_showsNameAndEndButton_firesOnEnd() {
        var ended = false
        compose.setContent {
            MaterialTheme {
                ActiveSceneBannerContent(state = state(), expired = false, onEndClick = { ended = true })
            }
        }
        compose.onNodeWithText("Exercise").assertIsDisplayed()
        compose.onNodeWithText(deactivateLabel).assertIsDisplayed()
        compose.onNodeWithText(deactivateLabel).performClick()
        assertThat(ended).isTrue()
    }

    @Test
    fun expiredScene_showsEndedAndClose_firesOnDismiss() {
        var dismissed = false
        compose.setContent {
            MaterialTheme {
                ActiveSceneBannerContent(
                    state = state(SceneLifecycle.EXPIRED), expired = true,
                    onEndClick = {}, onDismiss = { dismissed = true }
                )
            }
        }
        compose.onNodeWithText(endedLabel).assertIsDisplayed()
        compose.onNodeWithText(closeLabel).performClick()
        assertThat(dismissed).isTrue()
    }
}
