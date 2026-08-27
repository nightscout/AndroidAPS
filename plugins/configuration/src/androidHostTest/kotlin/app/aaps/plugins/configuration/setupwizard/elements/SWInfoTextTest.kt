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
import app.aaps.core.ui.R
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class SWInfoTextTest {

    @get:Rule
    val compose = createComposeRule()

    private fun newInfoText(): SWInfoText =
        SWInfoText(
            aapsLogger = mock<AAPSLogger>(),
            rh = mock<ResourceHelper>(),
            rxBus = mock<RxBus>(),
            preferences = mock<Preferences>(),
            passwordCheck = mock<PasswordCheck>()
        )

    private lateinit var resourceLabel: String

    @Before
    fun setUp() {
        resourceLabel = RuntimeEnvironment.getApplication().getString(R.string.result)
    }

    @Test
    fun rendersLiteralTextLabel() {
        val item = newInfoText().label("Welcome to the setup wizard")
        compose.setContent {
            MaterialTheme { item.Compose() }
        }

        compose.onNodeWithText("Welcome to the setup wizard").assertIsDisplayed()
    }

    @Test
    fun rendersResourceLabel_whenNoLiteral() {
        val item = newInfoText().label(R.string.result)
        compose.setContent {
            MaterialTheme { item.Compose() }
        }

        compose.onNodeWithText(resourceLabel).assertIsDisplayed()
    }

    @Test
    fun hiddenByVisibility_rendersNothing() {
        val item = newInfoText().label("Hidden text").visibility { false }
        compose.setContent {
            MaterialTheme { item.Compose() }
        }

        compose.onNodeWithText("Hidden text").assertDoesNotExist()
    }
}
