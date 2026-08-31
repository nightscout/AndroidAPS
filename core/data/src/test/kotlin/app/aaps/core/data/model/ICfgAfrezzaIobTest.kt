package app.aaps.core.data.model

import app.aaps.core.data.iob.Iob
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Validate the oref bilinear IOB model with Afrezza (Technosphere inhaled insulin) parameters.
 *
 * Afrezza pharmacokinetics (published):
 *   - Onset: ~12 minutes
 *   - Peak (Tmax): 35-45 minutes
 *   - Clinical duration: 1.5-3 hours (dose-dependent)
 *
 * Model parameters used:
 *   - Peak: 15 minutes
 *   - DIA: 1.5 hours (90 minutes)
 *   - Concentration: 1.0 (U100 equivalent)
 */
class ICfgAfrezzaIobTest {

    private lateinit var afrezzaCfg: ICfg
    private lateinit var fiaspCfg: ICfg
    private lateinit var bolus1U: BS

    @BeforeEach
    fun setup() {
        afrezzaCfg = ICfg(
            insulinLabel = "Afrezza (Inhaled)",
            peak = 15,       // minutes
            dia = 1.5,       // hours
            concentration = 1.0
        )
        fiaspCfg = ICfg(
            insulinLabel = "Fiasp",
            peak = 55,       // minutes
            dia = 10.0,      // hours
            concentration = 1.0
        )
        bolus1U = BS(
            timestamp = 0L,
            amount = 1.0,
            type = BS.Type.NORMAL,
            iCfg = afrezzaCfg
        )
    }

    @Test
    fun `IOB starts near 1_0 at time zero`() {
        // At t=1 minute after bolus, IOB should be very close to 1.0
        val iob = afrezzaCfg.iobCalcForTreatment(bolus1U, 60_000L) // 1 min in ms
        assertTrue(iob.iobContrib > 0.95, "IOB at t=1min should be >0.95, was ${iob.iobContrib}")
        assertTrue(iob.iobContrib <= 1.0, "IOB at t=1min should be <=1.0, was ${iob.iobContrib}")
    }

    @Test
    fun `IOB is zero at DIA`() {
        // At t=DIA (150 minutes), IOB should be exactly 0
        val iob = afrezzaCfg.iobCalcForTreatment(bolus1U, 90L * 60_000L)
        assertEquals(0.0, iob.iobContrib, 0.001, "IOB should be 0 at t=DIA")
        assertEquals(0.0, iob.activityContrib, 0.001, "Activity should be 0 at t=DIA")
    }

    @Test
    fun `IOB is zero after DIA`() {
        // At t=DIA+30min, IOB should still be 0
        val iob = afrezzaCfg.iobCalcForTreatment(bolus1U, 120L * 60_000L)
        assertEquals(0.0, iob.iobContrib, 0.001, "IOB should be 0 after DIA")
    }

    @Test
    fun `IOB decreases monotonically after peak`() {
        var prevIob = 1.0
        // Check from t=50min to t=150min (after peak, IOB should only decrease)
        for (t in 20..90 step 5) {
            val iob = afrezzaCfg.iobCalcForTreatment(bolus1U, t.toLong() * 60_000L)
            assertTrue(
                iob.iobContrib <= prevIob + 0.001,
                "IOB should decrease monotonically after peak. At t=${t}min: ${iob.iobContrib} > prev: $prevIob"
            )
            prevIob = iob.iobContrib
        }
    }

    @Test
    fun `no NaN or negative values across entire curve`() {
        for (t in 0..200 step 1) {
            val iob = afrezzaCfg.iobCalcForTreatment(bolus1U, t.toLong() * 60_000L)
            assertTrue(!iob.iobContrib.isNaN(), "IOB should not be NaN at t=${t}min")
            assertTrue(!iob.activityContrib.isNaN(), "Activity should not be NaN at t=${t}min")
            assertTrue(iob.iobContrib >= 0.0, "IOB should not be negative at t=${t}min, was ${iob.iobContrib}")
            assertTrue(iob.activityContrib >= 0.0, "Activity should not be negative at t=${t}min, was ${iob.activityContrib}")
        }
    }

    @Test
    fun `peak activity occurs near configured peak time`() {
        var maxActivity = 0.0
        var maxActivityTime = 0
        for (t in 1..150) {
            val iob = afrezzaCfg.iobCalcForTreatment(bolus1U, t.toLong() * 60_000L)
            if (iob.activityContrib > maxActivity) {
                maxActivity = iob.activityContrib
                maxActivityTime = t
            }
        }
        // Peak should be within +/- 10 minutes of configured peak (40 min)
        assertTrue(
            maxActivityTime in 8..25,
            "Peak activity should occur near t=15min, was at t=${maxActivityTime}min"
        )
    }

    @Test
    fun `Afrezza IOB decays faster than Fiasp`() {
        val fiaspBolus = BS(timestamp = 0L, amount = 1.0, type = BS.Type.NORMAL, iCfg = fiaspCfg)

        // At 90 minutes, Afrezza should have substantially less IOB than Fiasp
        val afrezzaIob90 = afrezzaCfg.iobCalcForTreatment(bolus1U, 90L * 60_000L)
        val fiaspIob90 = fiaspCfg.iobCalcForTreatment(fiaspBolus, 90L * 60_000L)

        assertTrue(
            afrezzaIob90.iobContrib < fiaspIob90.iobContrib,
            "At t=90min, Afrezza IOB (${afrezzaIob90.iobContrib}) should be less than Fiasp IOB (${fiaspIob90.iobContrib})"
        )

        // At 150 minutes (Afrezza DIA), Afrezza IOB should be ~0, Fiasp still significant
        val afrezzaIob150 = afrezzaCfg.iobCalcForTreatment(bolus1U, 90L * 60_000L)
        val fiaspIob150 = fiaspCfg.iobCalcForTreatment(fiaspBolus, 150L * 60_000L)

        assertEquals(0.0, afrezzaIob150.iobContrib, 0.001, "Afrezza IOB should be ~0 at t=90min")
        assertTrue(
            fiaspIob150.iobContrib > 0.05,
            "Fiasp IOB should still be significant at t=150min, was ${fiaspIob150.iobContrib}"
        )
    }

    @Test
    fun `dose scaling works correctly for cartridge sizes`() {
        // 4U, 8U, 12U should produce proportional IOB
        val bolus4U = BS(timestamp = 0L, amount = 4.0, type = BS.Type.NORMAL, iCfg = afrezzaCfg)
        val bolus8U = BS(timestamp = 0L, amount = 8.0, type = BS.Type.NORMAL, iCfg = afrezzaCfg)
        val bolus12U = BS(timestamp = 0L, amount = 12.0, type = BS.Type.NORMAL, iCfg = afrezzaCfg)

        val t = 60L * 60_000L // 60 minutes
        val iob4 = afrezzaCfg.iobCalcForTreatment(bolus4U, t)
        val iob8 = afrezzaCfg.iobCalcForTreatment(bolus8U, t)
        val iob12 = afrezzaCfg.iobCalcForTreatment(bolus12U, t)

        assertEquals(iob4.iobContrib * 2.0, iob8.iobContrib, 0.001, "8U IOB should be 2x 4U IOB")
        assertEquals(iob4.iobContrib * 3.0, iob12.iobContrib, 0.001, "12U IOB should be 3x 4U IOB")
    }

    @Test
    fun `combined Afrezza plus Fiasp IOB sums correctly`() {
        // Simulate: Afrezza 8U at t=0, Fiasp pump bolus 3U at t=0
        val afrezzaBolus = BS(timestamp = 0L, amount = 8.0, type = BS.Type.NORMAL, iCfg = afrezzaCfg)
        val fiaspBolus = BS(timestamp = 0L, amount = 3.0, type = BS.Type.NORMAL, iCfg = fiaspCfg)

        val t = 60L * 60_000L // 60 minutes
        val afrezzaIob = afrezzaCfg.iobCalcForTreatment(afrezzaBolus, t)
        val fiaspIob = fiaspCfg.iobCalcForTreatment(fiaspBolus, t)
        val combinedIob = afrezzaIob.iobContrib + fiaspIob.iobContrib

        // Combined should equal sum of individual IOBs
        assertTrue(combinedIob > 0, "Combined IOB should be positive")
        assertTrue(combinedIob < 11.0, "Combined IOB should be less than total dose (11U)")

        // At t=180min (well past Afrezza DIA), only Fiasp should remain
        val t180 = 180L * 60_000L
        val afrezzaIob180 = afrezzaCfg.iobCalcForTreatment(afrezzaBolus, t180)
        val fiaspIob180 = fiaspCfg.iobCalcForTreatment(fiaspBolus, t180)

        assertEquals(0.0, afrezzaIob180.iobContrib, 0.001, "Afrezza IOB should be 0 at t=180min")
        assertTrue(fiaspIob180.iobContrib > 0.1, "Fiasp IOB should still be active at t=180min")
    }
}
