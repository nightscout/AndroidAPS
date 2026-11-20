package app.aaps.ui.compose.eversenseCalibrationDialog

import app.aaps.core.data.configuration.Constants
import app.aaps.core.data.iob.InMemoryGlucoseValue
import app.aaps.core.data.model.TrendArrow
import app.aaps.core.interfaces.overview.LastBgData
import app.aaps.core.interfaces.utils.DateUtil
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class EversenseCalibrationDynamicsTest {
    private val last: LastBgData = mock()
    private val clock: DateUtil = mock()
    private val now = 1_794_182_400_000L
    private val assessment = EversenseCalibrationDynamics(last, clock)
    init { whenever(clock.now()).thenReturn(now) }

    @Test fun verticalDoubleAndTripleArrowsWarnInBothDirections() {
        val rapid = setOf(TrendArrow.SINGLE_UP, TrendArrow.SINGLE_DOWN, TrendArrow.DOUBLE_UP,
            TrendArrow.DOUBLE_DOWN, TrendArrow.TRIPLE_UP, TrendArrow.TRIPLE_DOWN)
        for (arrow in TrendArrow.entries) {
            whenever(last.lastBg()).thenReturn(InMemoryGlucoseValue(now, 120.0, arrow))
            assertThat(assessment.hasRapidChange()).isEqualTo(arrow in rapid)
        }
    }

    @Test fun switchingToADiagonalArrowClearsTheWarning() {
        val reading = InMemoryGlucoseValue(now, 120.0, TrendArrow.SINGLE_UP)
        whenever(last.lastBg()).thenReturn(reading)
        assertThat(assessment.hasRapidChange()).isTrue()
        for (arrow in listOf(TrendArrow.FORTY_FIVE_UP, TrendArrow.FORTY_FIVE_DOWN)) {
            reading.trendArrow = arrow
            assertThat(assessment.hasRapidChange()).isFalse()
        }
    }

    @Test fun absentStaleAndFutureReadingsAreNotTreatedAsCurrentDynamics() {
        assertThat(assessment.hasRapidChange()).isFalse()
        for (timestamp in listOf(0L, now - Constants.OLD_BG_MINUTES * 60_000L - 1, now + 1)) {
            whenever(last.lastBg()).thenReturn(InMemoryGlucoseValue(timestamp, 120.0, TrendArrow.DOUBLE_UP))
            assertThat(assessment.hasRapidChange()).isFalse()
        }
    }

    @Test fun freshnessBoundaryUsesTheSameAgeLimitAsAaps() {
        whenever(last.lastBg()).thenReturn(InMemoryGlucoseValue(now - Constants.OLD_BG_MINUTES * 60_000L, 120.0, TrendArrow.SINGLE_DOWN))
        assertThat(assessment.hasRapidChange()).isTrue()
        whenever(last.lastBg()).thenReturn(InMemoryGlucoseValue(now - Constants.OLD_BG_MINUTES * 60_000L - 1, 120.0, TrendArrow.SINGLE_DOWN))
        assertThat(assessment.hasRapidChange()).isFalse()
    }
}
