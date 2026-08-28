package app.aaps.ui.compose.profileManagement

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.runtime.CompositionLocalProvider
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.ui.compose.LocalDateUtil
import org.mockito.kotlin.mock
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class ProfileCarouselCardTest {

    private val dateUtil: DateUtil = mock()

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun showsProfileName() {
        compose.setContent {
            CompositionLocalProvider(LocalDateUtil provides dateUtil) {
            MaterialTheme {
                ProfileCarouselCard(
                    profileName = "Weekday Profile",
                    basalSum = 24.0,
                    isActive = false,
                    hasErrors = false,
                    pumpIncompatible = false,
                    activeProfileSwitch = null,
                    nextProfileName = null,
                    formatBasalSum = { "24.0 U" }
                )
            }
            }
        }

        compose.onNodeWithText("Weekday Profile").assertIsDisplayed()
    }

    @Test
    fun showsFormattedBasalSum() {
        compose.setContent {
            CompositionLocalProvider(LocalDateUtil provides dateUtil) {
            MaterialTheme {
                ProfileCarouselCard(
                    profileName = "Weekday Profile",
                    basalSum = 24.0,
                    isActive = false,
                    hasErrors = false,
                    pumpIncompatible = false,
                    activeProfileSwitch = null,
                    nextProfileName = null,
                    formatBasalSum = { "24.0 U" }
                )
            }
            }
        }

        compose.onNodeWithText("24.0 U", substring = true).assertIsDisplayed()
    }
}
