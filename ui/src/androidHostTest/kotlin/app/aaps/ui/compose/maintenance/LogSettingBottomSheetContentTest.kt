package app.aaps.ui.compose.maintenance

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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

/** Robolectric composable test for [LogSettingBottomSheetContent] (empty log list): renders and fires
 *  the reset-to-defaults callback — headless JVM, no emulator. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class LogSettingBottomSheetContentTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var resetLabel: String

    @Before
    fun setUp() {
        val ctx: Context = RuntimeEnvironment.getApplication()
        resetLabel = ctx.getString(CoreUiR.string.resettodefaults)
    }

    @Test
    fun rendersAndFiresResetToDefaults() {
        var resetClicked = false
        compose.setContent {
            MaterialTheme {
                LogSettingBottomSheetContent(
                    logElements = emptyList(),
                    onToggle = { _, _ -> },
                    onResetToDefaults = { resetClicked = true }
                )
            }
        }

        compose.onNodeWithText(resetLabel).performClick()
        assertThat(resetClicked).isTrue()
    }
}
