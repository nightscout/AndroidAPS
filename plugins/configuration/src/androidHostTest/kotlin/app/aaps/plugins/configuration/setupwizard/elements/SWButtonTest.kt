package app.aaps.plugins.configuration.setupwizard.elements

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.protection.PasswordCheck
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.R
import com.google.common.truth.Truth.assertThat
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
class SWButtonTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var buttonLabel: String

    private fun newButton(): SWButton =
        SWButton(
            aapsLogger = mock<AAPSLogger>(),
            rh = mock<ResourceHelper>(),
            rxBus = mock<RxBus>(),
            preferences = mock<Preferences>(),
            passwordCheck = mock<PasswordCheck>()
        )

    @Before
    fun setUp() {
        buttonLabel = RuntimeEnvironment.getApplication().getString(R.string.result)
    }

    @Test
    fun rendersButtonLabel_fromResource() {
        val item = newButton().text(R.string.result).action { }
        compose.setContent {
            MaterialTheme { item.Compose() }
        }

        compose.onNodeWithText(buttonLabel).assertIsDisplayed()
    }

    @Test
    fun click_runsAction() {
        var runs = 0
        val item = newButton().text(R.string.result).action { runs++ }
        compose.setContent {
            MaterialTheme { item.Compose() }
        }

        compose.onNodeWithText(buttonLabel).performClick()

        assertThat(runs).isEqualTo(1)
    }

    @Test
    fun disabledByVisibility_doesNotRunAction() {
        var runs = 0
        val item = newButton().text(R.string.result).action { runs++ }.visibility { false }
        compose.setContent {
            MaterialTheme { item.Compose() }
        }

        compose.onNodeWithText(buttonLabel).performClick()

        assertThat(runs).isEqualTo(0)
    }
}
