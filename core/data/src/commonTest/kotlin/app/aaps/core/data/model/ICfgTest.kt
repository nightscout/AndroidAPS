package app.aaps.core.data.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * In `commonTest`, so this runs through Kotlin/Native as well as the JVM - which matters more here
 * than for most tests, because `iobCalcForTreatment` is dosing arithmetic and the whole point of
 * sharing it with a client is that it computes the same numbers everywhere.
 *
 * `kotlin.test` rather than Truth and JUnit 5, because neither of those exists off the JVM. Truth's
 * `isGreaterThan` / `isAtLeast` / `isFinite` become plain boolean assertions with a message, so a
 * failure still says what it expected.
 */
class ICfgTest {

    private fun bolus(amount: Double, iCfg: ICfg, timestamp: Long = 0L) =
        BS(timestamp = timestamp, amount = amount, type = BS.Type.NORMAL, iCfg = iCfg)

    @Test
    fun `valid config produces positive finite IOB shortly after bolus`() {
        val iCfg = ICfg(insulinLabel = "test", peak = 75, dia = 6.0, concentration = 1.0)
        // 30 min after a 1 U bolus
        val iob = iCfg.iobCalcForTreatment(bolus(1.0, iCfg, timestamp = 0L), time = 30 * 60 * 1000L)
        assertTrue(iob.iobContrib.isFinite(), "iobContrib was ${iob.iobContrib}")
        assertTrue(iob.iobContrib > 0.0, "expected > 0.0 but was ${iob.iobContrib}")
        assertTrue(iob.iobContrib < 1.0, "expected < 1.0 but was ${iob.iobContrib}")
    }

    @Test
    fun `migration sentinel -1 does not silently zero IOB`() {
        // v33 migration writes insulinEndTime = -1 / insulinPeakTime = -1 before the repair pass.
        // dia rounds to 0.0 -> td = 0 -> the `t < td` gate would never fire -> silent zero IOB.
        val iCfg = ICfg(insulinLabel = "sentinel", insulinEndTime = -1L, insulinPeakTime = -1L, concentration = 1.0)
        val iob = iCfg.iobCalcForTreatment(bolus(5.0, iCfg, timestamp = 0L), time = 30 * 60 * 1000L)
        assertTrue(iob.iobContrib.isFinite(), "iobContrib was ${iob.iobContrib}")
        // 5 U bolus 30 min ago must still be counted as on board, not silently dropped to 0.
        assertTrue(iob.iobContrib > 0.0, "expected > 0.0 but was ${iob.iobContrib}")
    }

    @Test
    fun `degenerate peak at dia-over-2 singularity does not produce NaN or Infinity`() {
        // dia 5h -> td = 300 min; peak 150 min makes 2*tp == td -> original formula divides by zero.
        val iCfg = ICfg(insulinLabel = "singular", peak = 150, dia = 5.0, concentration = 1.0)
        val iob = iCfg.iobCalcForTreatment(bolus(2.0, iCfg, timestamp = 0L), time = 60 * 60 * 1000L)
        assertTrue(iob.iobContrib.isFinite(), "iobContrib was ${iob.iobContrib}")
        assertTrue(iob.activityContrib.isFinite(), "activityContrib was ${iob.activityContrib}")
    }

    @Test
    fun `peak larger than dia-over-2 does not produce negative IOB`() {
        // 2*tp > td would yield a negative tau and negative iobContrib, inflating dosing.
        val iCfg = ICfg(insulinLabel = "negativeTau", peak = 200, dia = 5.0, concentration = 1.0)
        val iob = iCfg.iobCalcForTreatment(bolus(3.0, iCfg, timestamp = 0L), time = 60 * 60 * 1000L)
        assertTrue(iob.iobContrib.isFinite(), "iobContrib was ${iob.iobContrib}")
        assertTrue(iob.iobContrib >= 0.0, "expected >= 0.0 but was ${iob.iobContrib}")
    }

    @Test
    fun `IOB is zero only after DIA has fully elapsed`() {
        val iCfg = ICfg(insulinLabel = "test", peak = 75, dia = 5.0, concentration = 1.0)
        // 6 h after bolus, well past the 5 h DIA
        val iob = iCfg.iobCalcForTreatment(bolus(1.0, iCfg, timestamp = 0L), time = 6 * 60 * 60 * 1000L)
        assertEquals(0.0, iob.iobContrib)
    }

    // The DB v33 migration stamps this sentinel onto every pre-ICfg row, including the active profile's.
    // Read back as a value it yields DIA 0.0, which fails the APS hard-limit check and aborts every cycle.
    @Test
    fun `the v33 migration sentinel is not a usable insulin`() {
        val sentinel = ICfg(insulinLabel = "", insulinEndTime = -1, insulinPeakTime = -1, concentration = 1.0)

        assertFalse(sentinel.isUsable)
        assertEquals(0.0, sentinel.dia) // the value that blocks the loop
    }

    @Test
    fun `a real insulin is usable`() {
        assertTrue(ICfg(insulinLabel = "test", peak = 75, dia = 5.0, concentration = 1.0).isUsable)
    }

    @Test
    fun `a zero or negative DIA or peak is not usable`() {
        assertFalse(ICfg(insulinLabel = "", insulinEndTime = 0, insulinPeakTime = 4_500_000, concentration = 1.0).isUsable)
        assertFalse(ICfg(insulinLabel = "", insulinEndTime = 18_000_000, insulinPeakTime = 0, concentration = 1.0).isUsable)
    }
}
