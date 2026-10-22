package app.aaps.ui.compose.stats

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.resources.TextRefIdRegistry
import app.aaps.core.ui.compose.LocalProfileUtil
import app.aaps.ui.UiStringIds
import com.google.common.truth.Truth.assertThat
import kotlin.math.abs
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

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class AGPStatsTest {

    @get:Rule
    val compose = createComposeRule()

    @Before
    fun setUp() {
        TextRefIdRegistry.register("ui") { name -> UiStringIds.idOf(name) }
    }

    @Test
    fun rangeTabsSelectTheRequestedRange() {
        var selected = AgpStatsRange.TODAY
        compose.setContent {
            MaterialTheme {
                AGPStats(
                    selectedRange = AgpStatsRange.TODAY,
                    onSelectRange = { selected = it },
                    data = null,
                    isLoading = true,
                    hasError = false
                )
            }
        }

        compose.onNodeWithText("7d").performClick()
        assertThat(selected).isEqualTo(AgpStatsRange.DAYS_7)
    }

    @Test
    fun emptyRangeShowsNoDataMessage() {
        compose.setContent {
            MaterialTheme {
                AGPStats(AgpStatsRange.TODAY, {}, AgpStatsData(70.0, 180.0), false, false)
            }
        }

        compose.onNodeWithText("No glucose data is available for this range.").assertIsDisplayed()
    }

    @Test
    fun failedLoadShowsAnErrorAndRetryUsesTheSelectedRange() {
        var retried: AgpStatsRange? = null
        compose.setContent {
            MaterialTheme {
                AGPStats(AgpStatsRange.DAYS_30, { retried = it }, null, false, true)
            }
        }

        compose.onNodeWithText("Could not load AGP statistics. Please try again.").assertIsDisplayed()
        compose.onNodeWithText("Recalculate").performClick()
        assertThat(retried).isEqualTo(AgpStatsRange.DAYS_30)
    }

    @Test
    fun loadedDataShowsTheChartOverviewAndMetrics() {
        showLoadedData(GlucoseUnit.MGDL)

        compose.onNodeWithContentDescription("Glucose percentile chart").assertIsDisplayed()
        compose.onNodeWithText("Glycemic overview").performScrollTo().assertIsDisplayed()
        val chartBounds = compose.onNodeWithContentDescription("Time in range chart").fetchSemanticsNode().boundsInRoot
        val dataBounds = compose.onNodeWithText("Average").fetchSemanticsNode().boundsInRoot
        assertThat(chartBounds.left).isAtLeast(dataBounds.right)
        assertThat(chartBounds.top).isLessThan(dataBounds.bottom)
        compose.onNodeWithText("5.7").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("6.2").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("25").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Days").performScrollTo().assertIsDisplayed()
        val metricBounds = listOf("eA1c", "GMI", "SD", "CV", "Days").map {
            compose.onNodeWithText(it).fetchSemanticsNode().boundsInRoot
        }
        val spacing = metricBounds[1].left - metricBounds[0].left
        metricBounds.zipWithNext().forEach { (left, right) ->
            assertThat(abs(right.left - left.left - spacing)).isAtMost(1f)
            assertThat(right.top).isEqualTo(left.top)
        }
    }

    @Test
    fun mmolMetricsKeepTheirConversionWithoutUnits() {
        showLoadedData(GlucoseUnit.MMOL)

        compose.onNodeWithText("38.8").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("44.3").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("1.4").performScrollTo().assertIsDisplayed()
    }

    private fun showLoadedData(units: GlucoseUnit) {
        val profileUtil: ProfileUtil = mock()
        whenever(profileUtil.units).thenReturn(units)
        whenever(profileUtil.fromMgdlToStringInUnits(any(), any())).thenReturn("120")
        whenever(profileUtil.fromMgdlToStringInUnits(25.0)).thenReturn(if (units == GlucoseUnit.MGDL) "25" else "1.4")
        whenever(profileUtil.fromMgdlToStringWithUnits(any())).thenReturn("120 mg/dL")
        val data = AgpStatsData(
            lowMgdl = 70.0,
            highMgdl = 180.0,
            readingCount = 10,
            averageMgdl = 120.0,
            medianMgdl = 120.0,
            eA1cPercent = 5.7,
            gmiPercent = 6.2,
            standardDeviationMgdl = 25.0,
            tir = AgpTirBreakdown(inRange = 100.0),
            hourlyPercentiles = listOf(AgpHourlyPercentile(6, 90.0, 100.0, 120.0, 140.0, 160.0))
        )
        compose.setContent {
            CompositionLocalProvider(LocalProfileUtil provides profileUtil) {
                MaterialTheme {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        AGPStats(AgpStatsRange.DAYS_7, {}, data, false, false)
                    }
                }
            }
        }
    }
}
