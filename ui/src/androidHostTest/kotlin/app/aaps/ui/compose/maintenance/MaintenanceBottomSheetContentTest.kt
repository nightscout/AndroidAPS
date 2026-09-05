package app.aaps.ui.compose.maintenance

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
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

/** Robolectric composable test for [MaintenanceBottomSheetContent] (all-default params): renders the
 *  file-management section + rows and fires the Log-settings row callback — headless JVM, no emulator. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class MaintenanceBottomSheetContentTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var fileManagement: String
    private lateinit var logSettings: String

    @Before
    fun setUp() {
        val ctx: Context = RuntimeEnvironment.getApplication()
        fileManagement = ctx.getString(CoreUiR.string.file_management)
        logSettings = ctx.getString(CoreUiR.string.nav_logsettings)
    }

    @Test
    fun rendersSectionsAndFiresLogSettingsRow() {
        var logSettingsClicked = false
        compose.setContent {
            MaterialTheme {
                MaintenanceBottomSheetContent(onLogSettingsClick = { logSettingsClicked = true })
            }
        }

        compose.onNodeWithText(fileManagement).assertIsDisplayed()
        compose.onNodeWithText(logSettings).assertIsDisplayed()

        compose.onNodeWithText(logSettings).performClick()
        assertThat(logSettingsClicked).isTrue()
    }
}
