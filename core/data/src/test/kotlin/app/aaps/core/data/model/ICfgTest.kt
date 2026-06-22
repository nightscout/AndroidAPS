package app.aaps.core.data.model

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class ICfgTest {

    private fun bolus(amount: Double, iCfg: ICfg, timestamp: Long = 0L) =
        BS(timestamp = timestamp, amount = amount, type = BS.Type.NORMAL, iCfg = iCfg)

    @Test
    fun `valid config produces positive finite IOB shortly after bolus`() {
        val iCfg = ICfg(insulinLabel = "test", peak = 75, dia = 6.0, concentration = 1.0)
        // 30 min after a 1 U bolus
        val iob = iCfg.iobCalcForTreatment(bolus(1.0, iCfg, timestamp = 0L), time = 30 * 60 * 1000L)
        assertThat(iob.iobContrib).isFinite()
        assertThat(iob.iobContrib).isGreaterThan(0.0)
        assertThat(iob.iobContrib).isLessThan(1.0)
    }

    @Test
    fun `migration sentinel -1 does not silently zero IOB`() {
        // v33 migration writes insulinEndTime = -1 / insulinPeakTime = -1 before the repair pass.
        // dia rounds to 0.0 -> td = 0 -> the `t < td` gate would never fire -> silent zero IOB.
        val iCfg = ICfg(insulinLabel = "sentinel", insulinEndTime = -1L, insulinPeakTime = -1L, concentration = 1.0)
        val iob = iCfg.iobCalcForTreatment(bolus(5.0, iCfg, timestamp = 0L), time = 30 * 60 * 1000L)
        assertThat(iob.iobContrib).isFinite()
        // 5 U bolus 30 min ago must still be counted as on board, not silently dropped to 0.
        assertThat(iob.iobContrib).isGreaterThan(0.0)
    }

    @Test
    fun `degenerate peak at dia-over-2 singularity does not produce NaN or Infinity`() {
        // dia 5h -> td = 300 min; peak 150 min makes 2*tp == td -> original formula divides by zero.
        val iCfg = ICfg(insulinLabel = "singular", peak = 150, dia = 5.0, concentration = 1.0)
        val iob = iCfg.iobCalcForTreatment(bolus(2.0, iCfg, timestamp = 0L), time = 60 * 60 * 1000L)
        assertThat(iob.iobContrib).isFinite()
        assertThat(iob.activityContrib).isFinite()
    }

    @Test
    fun `peak larger than dia-over-2 does not produce negative IOB`() {
        // 2*tp > td would yield a negative tau and negative iobContrib, inflating dosing.
        val iCfg = ICfg(insulinLabel = "negativeTau", peak = 200, dia = 5.0, concentration = 1.0)
        val iob = iCfg.iobCalcForTreatment(bolus(3.0, iCfg, timestamp = 0L), time = 60 * 60 * 1000L)
        assertThat(iob.iobContrib).isFinite()
        assertThat(iob.iobContrib).isAtLeast(0.0)
    }

    @Test
    fun `IOB is zero only after DIA has fully elapsed`() {
        val iCfg = ICfg(insulinLabel = "test", peak = 75, dia = 5.0, concentration = 1.0)
        // 6 h after bolus, well past the 5 h DIA
        val iob = iCfg.iobCalcForTreatment(bolus(1.0, iCfg, timestamp = 0L), time = 6 * 60 * 60 * 1000L)
        assertThat(iob.iobContrib).isEqualTo(0.0)
    }
}
