package app.aaps.core.ui.compose

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import app.aaps.core.ui.R as CoreUiR

/**
 * The stepper is used once per row in the profile editor (basal, ISF, IC, targets). Without a
 * spoken name every row read "0.85, edit box", "decrement", "increment".
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class PlusMinusEditTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun fieldAndButtonsCarryTheGivenName() {
        val context = RuntimeEnvironment.getApplication()
        compose.setContent {
            MaterialTheme {
                PlusMinusEdit(value = 0.85, onValueChange = {}, valueRange = 0.0..10.0, step = 0.1, label = "Basal at 08:00")
            }
        }

        compose.onNodeWithContentDescription("Basal at 08:00").assertIsDisplayed()
        compose.onNodeWithContentDescription(context.getString(CoreUiR.string.a11y_plus_button_description, "Basal at 08:00", "0.1"))
            .assertIsDisplayed()
    }
}
