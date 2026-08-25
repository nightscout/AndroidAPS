package app.aaps.plugins.aps.compose

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import app.aaps.core.interfaces.aps.APS
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.ui.R
import app.aaps.plugins.aps.events.EventOpenAPSUpdateGui
import app.aaps.plugins.aps.events.EventResetOpenAPSGui
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Integration test for the thin [OpenAPSComposeContent.Render] glue: it builds the real
 * [OpenAPSViewModel] from mocked collaborators and hands its state to [OpenAPSScreen].
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class OpenAPSComposeContentTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun render_showsNotAvailable_whenNoApsResult() {
        val apsPlugin = mock<APS> { whenever(it.lastAPSResult).thenReturn(null) }
        val rxBus = mock<RxBus> {
            whenever(it.toFlow(EventOpenAPSUpdateGui::class.java)).thenReturn(emptyFlow())
            whenever(it.toFlow(EventResetOpenAPSGui::class.java)).thenReturn(emptyFlow())
        }
        val rh = mock<ResourceHelper> { whenever(it.gs(R.string.not_available_full)).thenReturn("N/A") }
        val content = OpenAPSComposeContent(apsPlugin, rxBus, rh, mock<DateUtil>())

        compose.setContent {
            MaterialTheme {
                content.Render(setToolbarConfig = {}, onNavigateBack = {}, onSettings = null)
            }
        }
        compose.waitForIdle()

        compose.onNodeWithText("N/A").assertIsDisplayed()
    }
}
