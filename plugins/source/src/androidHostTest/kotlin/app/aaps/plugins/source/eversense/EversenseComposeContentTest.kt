package app.aaps.plugins.source.eversense

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import app.aaps.core.interfaces.resources.ResourceHelper
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowToast

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "en")
class EversenseComposeContentTest {
    @get:Rule val compose = createComposeRule()
    private val rh: ResourceHelper = mock()

    @Test fun settingsEntryOpensPluginPreferences() {
        var opened = false
        compose.setContent {
            EversenseComposeContent(rh).Render({}, {}, { opened = true })
        }
        compose.onNodeWithText("Glucose settings").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(opened) }
    }

    @Test fun unavailableHistoryIsDisabledAndConnectRetainsPlaceholderBehavior() {
        compose.setContent { EversenseComposeContent(rh).Render({}, {}, null) }
        compose.onNodeWithText("Alert History").assertIsNotEnabled()
        compose.onNodeWithText("Glucose settings").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Connect").performScrollTo().performClick()
        compose.runOnIdle {
            assertTrue(ShadowToast.getTextOfLatestToast().toString().startsWith("Scanning for Smart Transmitter"))
        }
    }
}
