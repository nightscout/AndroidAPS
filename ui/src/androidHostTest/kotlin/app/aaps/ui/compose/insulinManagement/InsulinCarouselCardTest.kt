package app.aaps.ui.compose.insulinManagement

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import app.aaps.core.data.model.ICfg
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

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
}
