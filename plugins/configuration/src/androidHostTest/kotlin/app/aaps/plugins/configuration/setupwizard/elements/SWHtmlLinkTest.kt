package app.aaps.plugins.configuration.setupwizard.elements

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.protection.PasswordCheck
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.keys.interfaces.Preferences
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class SWHtmlLinkTest {

    @get:Rule
    val compose = createComposeRule()

    private fun newHtmlLink(): SWHtmlLink =
        SWHtmlLink(
            aapsLogger = mock<AAPSLogger>(),
            rh = mock<ResourceHelper>(),
            rxBus = mock<RxBus>(),
            preferences = mock<Preferences>(),
            passwordCheck = mock<PasswordCheck>()
        )

    @Test
    fun rendersLiteralLinkText() {
        val item = newHtmlLink().label("https://androidaps.readthedocs.io")
        compose.setContent {
            MaterialTheme { item.Compose() }
        }

        compose.onNodeWithText("https://androidaps.readthedocs.io").assertIsDisplayed()
    }

    @Test
    fun hiddenByVisibility_rendersNothing() {
        val item = newHtmlLink().label("https://hidden.example.com").visibility { false }
        compose.setContent {
            MaterialTheme { item.Compose() }
        }

        compose.onNodeWithText("https://hidden.example.com").assertDoesNotExist()
    }
}
