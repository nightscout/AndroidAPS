package app.aaps.ui.compose.profileManagement

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import app.aaps.core.data.model.EPS
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.ICfg
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.ui.compose.LocalDateUtil
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
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

    @Test
    fun temporaryProfile_showsPercentTimeshiftAndRemainingTime() {
        // Switched 20 min ago for 3 h -> 2 h 40 min left.
        whenever(dateUtil.now()).thenReturn(NOW)
        compose.setContent {
            CompositionLocalProvider(LocalDateUtil provides dateUtil) {
                MaterialTheme {
                    ProfileCarouselCard(
                        profileName = "Weekday Profile",
                        basalSum = 24.0,
                        isActive = true,
                        hasErrors = false,
                        pumpIncompatible = false,
                        activeProfileSwitch = switch(percentage = 110, timeshiftHours = 2, durationMinutes = 180),
                        nextProfileName = null,
                        formatBasalSum = { "24.0 U" }
                    )
                }
            }
        }

        compose.onNodeWithText("110% +2h · 2 h 40 min").assertIsDisplayed()
    }

    @Test
    fun temporaryProfile_withoutPercentOrTimeshift_stillShowsRemainingTime() {
        // Before, this case had only the progress bar and no text at all.
        whenever(dateUtil.now()).thenReturn(NOW)
        compose.setContent {
            CompositionLocalProvider(LocalDateUtil provides dateUtil) {
                MaterialTheme {
                    ProfileCarouselCard(
                        profileName = "Weekday Profile",
                        basalSum = 24.0,
                        isActive = true,
                        hasErrors = false,
                        pumpIncompatible = false,
                        activeProfileSwitch = switch(percentage = 100, timeshiftHours = 0, durationMinutes = 180),
                        nextProfileName = null,
                        formatBasalSum = { "24.0 U" }
                    )
                }
            }
        }

        compose.onNodeWithText("2 h 40 min").assertIsDisplayed()
    }

    private fun switch(percentage: Int, timeshiftHours: Int, durationMinutes: Int) = EPS(
        timestamp = NOW - T.mins(20).msecs(),
        basalBlocks = emptyList(),
        isfBlocks = emptyList(),
        icBlocks = emptyList(),
        targetBlocks = emptyList(),
        glucoseUnit = GlucoseUnit.MMOL,
        originalProfileName = "Weekday Profile",
        originalCustomizedName = "Weekday Profile",
        originalTimeshift = T.hours(timeshiftHours.toLong()).msecs(),
        originalPercentage = percentage,
        originalDuration = T.mins(durationMinutes.toLong()).msecs(),
        originalEnd = 0,
        iCfg = ICfg("test", 0, 0)
    )

    companion object {

        private const val NOW = 1_700_000_000_000L
    }
}
