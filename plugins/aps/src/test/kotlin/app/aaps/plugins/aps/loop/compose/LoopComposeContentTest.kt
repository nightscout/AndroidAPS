package app.aaps.plugins.aps.loop.compose

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventLoopUpdateGui
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.R
import app.aaps.plugins.aps.loop.events.EventLoopSetLastRunGui
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
 * Integration test for the thin [LoopComposeContent.Render] glue: it builds the real
 * [LoopViewModel] from mocked collaborators and hands its state to [LoopScreen].
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class LoopComposeContentTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun render_showsNotAvailable_whenNoLastRun() {
        val loop = mock<Loop> { whenever(it.lastRun).thenReturn(null) }
        val rxBus = mock<RxBus> {
            whenever(it.toFlow(EventLoopUpdateGui::class.java)).thenReturn(emptyFlow())
            whenever(it.toFlow(EventLoopSetLastRunGui::class.java)).thenReturn(emptyFlow())
        }
        val rh = mock<ResourceHelper> { whenever(it.gs(R.string.not_available_full)).thenReturn("N/A") }
        val content = LoopComposeContent(
            loop = loop,
            rxBus = rxBus,
            rh = rh,
            dateUtil = mock<DateUtil>(),
            decimalFormatter = mock<DecimalFormatter>(),
            aapsLogger = mock<AAPSLogger>(),
            preferences = mock<Preferences>()
        )

        compose.setContent {
            MaterialTheme {
                content.Render(setToolbarConfig = {}, onNavigateBack = {}, onSettings = null)
            }
        }
        compose.waitForIdle()

        compose.onNodeWithText("N/A").assertIsDisplayed()
    }
}
