package app.aaps.plugins.aps.openAPSSMB

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The autosens ratio of dynamic ISF with "adjust sensitivity": the TDD of the last 24 h against the 7 day average.
 *
 * A TDD of 0 is real data (a pump off or under a zero temp basal for a whole day). The ratio was 0 then, and the SMB
 * plugin refused to run the loop at all ("invalid ISF inputs", Crashlytics 2026-11).
 */
class DynIsfAutosensRatioTest {

    private fun result(last24H: Double, average7D: Double) = OpenAPSSMBPlugin.DynIsfResult().apply {
        tdd1D = 30.0
        tdd7D = average7D
        tddLast24H = last24H
        tddLast4H = 5.0
        tddLast8to4H = 5.0
    }

    @Test
    fun `the ratio is the last 24 hours against the 7 day average`() {
        val autosens = result(last24H = 36.0, average7D = 30.0).autosensResult(adjustSensitivity = true)

        assertEquals(1.2, autosens.ratio, 1e-9)
        assertEquals(1.2, autosens.ratioFromTdd, 1e-9)
    }

    @Test
    fun `no insulin in the last 24 hours gives no adjustment instead of 0`() {
        val autosens = result(last24H = 0.0, average7D = 30.0).autosensResult(adjustSensitivity = true)

        assertEquals(1.0, autosens.ratio, 1e-9)
    }

    @Test
    fun `a 7 day average of 0 gives no adjustment instead of infinity`() {
        val autosens = result(last24H = 12.0, average7D = 0.0).autosensResult(adjustSensitivity = true)

        assertEquals(1.0, autosens.ratio, 1e-9)
    }

    @Test
    fun `without adjust sensitivity the ratio is 1`() {
        val autosens = result(last24H = 36.0, average7D = 30.0).autosensResult(adjustSensitivity = false)

        assertEquals(1.0, autosens.ratio, 1e-9)
    }

    @Test
    fun `more carbs than usual lowers the ratio`() {
        val dynIsf = result(last24H = 30.0, average7D = 30.0).apply {
            tddLast24HCarbs = 300.0
            tdd7DDataCarbs = 200.0
            tdd7DAllDaysHaveCarbs = true
        }

        val autosens = dynIsf.autosensResult(adjustSensitivity = true)

        // carbs ratio = (300 / 200 - 1) * 0.6 + 1 = 1.3, so 1.0 / 1.3
        assertEquals(1.3, autosens.ratioFromCarbs, 1e-9)
        assertEquals(1.0 / 1.3, autosens.ratio, 1e-9)
    }
}
