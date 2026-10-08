package app.aaps.ui.compose.insulinManagement

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import app.aaps.core.data.model.ICfg
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import app.aaps.core.interfaces.R as InterfacesR
import app.aaps.core.ui.R as CoreUiR

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class InsulinCarouselCardTest {

    @get:Rule
    val compose = createComposeRule()

    private val iCfg = ICfg(insulinLabel = "Rapid Test Insulin", peak = 75, dia = 5.0, concentration = 1.0)

    @Test
    fun showsInsulinLabel() {
        compose.setContent {
            MaterialTheme {
                InsulinCarouselCard(iCfg = iCfg, isActive = false, isSelected = false)
            }
        }

        compose.onNodeWithText("Rapid Test Insulin").assertIsDisplayed()
    }

    @Test
    fun showsInsulinLabel_whenActive() {
        compose.setContent {
            MaterialTheme {
                InsulinCarouselCard(iCfg = iCfg, isActive = true, isSelected = false)
            }
        }

        compose.onNodeWithText("Rapid Test Insulin").assertIsDisplayed()
    }

    /** On screen the place says which number is which; a screen reader heard only "75 min" and "5 h". */
    @Test
    fun peakDiaAndConcentrationAreSpokenWithTheirNames() {
        val context = RuntimeEnvironment.getApplication()
        fun line(label: String, value: String) = context.getString(InterfacesR.string.confirmation_line, label, value)

        compose.setContent {
            MaterialTheme {
                InsulinCarouselCard(iCfg = iCfg, isActive = false, isSelected = false)
            }
        }

        compose.onNodeWithContentDescription(line(context.getString(CoreUiR.string.peak_label), context.getString(CoreUiR.string.format_mins, 75)))
            .assertIsDisplayed()
        compose.onNodeWithContentDescription(line(context.getString(CoreUiR.string.dia_label), context.getString(CoreUiR.string.format_hours, 5.0)))
            .assertIsDisplayed()
        compose.onNodeWithContentDescription(line(context.getString(CoreUiR.string.concentration_label), context.getString(InterfacesR.string.u100)))
            .assertIsDisplayed()
    }
}
