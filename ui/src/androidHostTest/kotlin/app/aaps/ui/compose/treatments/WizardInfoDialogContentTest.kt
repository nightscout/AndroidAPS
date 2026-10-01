package app.aaps.ui.compose.treatments

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import app.aaps.core.data.model.BCR
import app.aaps.core.interfaces.utils.DecimalFormatter
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Robolectric composable test for [WizardInfoDialogContent]: BCR-flag-gated calc rows render. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class WizardInfoDialogContentTest {

    @get:Rule
    val compose = createComposeRule()

    private val decimalFormatter: DecimalFormatter = mock()

    @Before
    fun setUp() {
        whenever(decimalFormatter.to1Decimal(any())).thenReturn("1.0")
        whenever(decimalFormatter.to0Decimal(any())).thenReturn("20")
    }

    private fun bcr() = BCR(
        timestamp = 1000L, targetBGLow = 90.0, targetBGHigh = 120.0, isf = 50.0, ic = 10.0,
        bolusIOB = 0.5, wasBolusIOBUsed = true, basalIOB = 0.2, wasBasalIOBUsed = true,
        glucoseValue = 150.0, wasGlucoseUsed = true, glucoseDifference = 30.0, glucoseInsulin = 0.6,
        glucoseTrend = 1.0, wasTrendUsed = true, trendInsulin = 0.1, cob = 20.0, wasCOBUsed = true,
        cobInsulin = 2.0, carbs = 20.0, wereCarbsUsed = true, carbsInsulin = 2.0, otherCorrection = 0.0,
        wasSuperbolusUsed = false, superbolusInsulin = 0.0, wasTempTargetUsed = false, totalInsulin = 5.0,
        percentageCorrection = 100, profileName = "Profile", note = "wizard"
    )

    @Test
    fun rendersGlucoseAndTrendRows() {
        compose.setContent {
            MaterialTheme {
                WizardInfoDialogContent(
                    bcr = bcr(), bgString = "MYBG", isfInUnits = 50.0, trendString = "MYTREND",
                    decimalFormatter = decimalFormatter
                )
            }
        }
        compose.onNodeWithText("MYBG", substring = true).assertIsDisplayed()
        compose.onNodeWithText("MYTREND", substring = true).assertIsDisplayed()
    }
}
