package app.aaps.ui.compose.stats

import app.aaps.core.interfaces.stats.DexcomTIR
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GlucosePentagonTest {

    private class Row(val name: String, val mean: Double, val cv: Double, val tor: Double, val hyper: Double, val hypo: Double, val published: Double)

    /**
     * Tables 1 and 2 of Vigersky et al. 2018. The paper gives the mean of the PGR of each person, and here the only input is the mean
     * of each group, so the numbers are close and not equal.
     */
    private val paper = listOf(
        Row("STAR 3 SAP baseline", 184.1, 33.2, 708.3, 46408.0, 517.0, 2.41),
        Row("STAR 3 SAP end", 164.7, 33.0, 532.8, 29269.0, 495.0, 2.15),
        Row("STAR 3 MDI baseline", 185.0, 28.2, 708.9, 47537.0, 546.0, 2.72),
        Row("STAR 3 MDI end", 182.2, 37.2, 676.1, 46163.0, 517.0, 2.62),
        Row("ASPIRE SIP+TS baseline", 146.6, 41.4, 507.5, 19670.0, 1620.0, 2.27),
        Row("ASPIRE SIP+TS end", 158.1, 40.2, 551.0, 24891.0, 895.0, 2.35),
        Row("ASPIRE SAP-TS baseline", 147.2, 41.8, 503.2, 20632.0, 1642.0, 2.42),
        Row("ASPIRE SAP-TS end", 155.6, 41.8, 561.8, 24908.0, 1557.0, 2.51)
    )

    @Test
    fun theFormulaGivesTheResultsOfThePaper() {
        for (row in paper) {
            val pgr = pgrOf(cgpAxesMm(row.tor, row.cv, row.hypo, row.hyper, row.mean))
            assertTrue(abs(pgr - row.published) < 0.3, "${row.name}: $pgr, the paper has ${row.published}")
        }
    }

    @Test
    fun aHealthyPersonHasOne() {
        assertEquals(1.0, pgrOf(cgpAxesMm(0.0, 16.7, 0.0, 0.0, 90.0)), 1e-9)
    }

    @Test
    fun onePercentOutOfRangeIsNotAHighRisk() {
        val data = computeCgpData(FakeTir(below = 0.5, above = 0.5, mean = 110.0, sd = 20.0))
        assertTrue(data.pgr < 1.2, "PGR ${data.pgr}")
    }

    @Test
    fun theDrawingIsLimitedButTheScoreIsNot() {
        val data = computeCgpData(FakeTir(below = 0.0, above = 90.0, mean = 400.0, sd = 200.0, hyperIntensity = 300_000.0))
        assertTrue(data.normalizedValues.all { it <= 1.0 })
        assertEquals(1.0, data.normalizedValues[4], 1e-9)
        assertTrue(data.pgr > 4.5, "PGR ${data.pgr}")
    }

    @Test
    fun outOfRangeIsBelowPlusAbove() {
        val data = computeCgpData(FakeTir(below = 4.0, above = 21.0, mean = 150.0, sd = 45.0))
        assertEquals(25.0, data.torPct, 1e-9)
        assertEquals(4.0, data.hypoPct, 1e-9)
        assertEquals(21.0, data.hyperPct, 1e-9)
        assertEquals(30.0, data.cvPct, 1e-9)
    }

    private class FakeTir(
        private val below: Double,
        private val above: Double,
        private val mean: Double,
        private val sd: Double,
        private val hypoIntensity: Double = 0.0,
        private val hyperIntensity: Double = 0.0
    ) : DexcomTIR {

        override fun calculateSD() = sd
        override fun veryLowPct() = 0.0
        override fun lowPct() = 0.0
        override fun inRangePct() = 0.0
        override fun highPct() = 0.0
        override fun veryHighPct() = 0.0
        override fun mean() = mean
        override fun count() = 1
        override fun veryLowTirMgdl() = 54.0
        override fun lowTirMgdl() = 70.0
        override fun highTirMgdl() = 180.0
        override fun highNightTirMgdl() = 150.0
        override fun veryHighTirMgdl() = 250.0
        override fun cgpBelowRangePct() = below
        override fun cgpAboveRangePct() = above
        override fun cgpHypoIntensity() = hypoIntensity
        override fun cgpHyperIntensity() = hyperIntensity
    }
}
