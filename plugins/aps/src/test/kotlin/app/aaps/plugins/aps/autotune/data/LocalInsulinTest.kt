package app.aaps.plugins.aps.autotune.data

import app.aaps.core.data.model.BS
import app.aaps.core.data.model.ICfg
import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class LocalInsulinTest : TestBase() {

    @Test
    fun `constructor with defaults uses default peak and DIA`() {
        val insulin = LocalInsulin("TestInsulin")

        assertThat(insulin.name).isEqualTo("TestInsulin")
        assertThat(insulin.peak).isEqualTo(75)
        assertThat(insulin.dia).isEqualTo(6.0)
    }

    @Test
    fun `constructor with custom peak sets peak`() {
        val insulin = LocalInsulin("TestInsulin", peak = 55)

        assertThat(insulin.peak).isEqualTo(55)
        assertThat(insulin.dia).isEqualTo(6.0)
    }

    @Test
    fun `constructor with custom DIA sets DIA`() {
        val insulin = LocalInsulin("TestInsulin", userDefinedDia = 7.0)

        assertThat(insulin.dia).isEqualTo(7.0)
    }

    @Test
    fun `constructor with all parameters sets all values`() {
        val insulin = LocalInsulin("TestInsulin", peak = 45, userDefinedDia = 5.5)

        assertThat(insulin.name).isEqualTo("TestInsulin")
        assertThat(insulin.peak).isEqualTo(45)
        assertThat(insulin.dia).isEqualTo(5.5)
    }

    @Test
    fun `dia returns minimum 5 hours if user defined DIA is too low`() {
        val insulin = LocalInsulin("TestInsulin", userDefinedDia = 3.0)

        assertThat(insulin.dia).isEqualTo(5.0)
    }

    @Test
    fun `dia returns user defined value if above minimum`() {
        val insulin = LocalInsulin("TestInsulin", userDefinedDia = 7.0)

        assertThat(insulin.dia).isEqualTo(7.0)
    }

    @Test
    fun `dia returns minimum exactly when user defined is exactly 5`() {
        val insulin = LocalInsulin("TestInsulin", userDefinedDia = 5.0)

        assertThat(insulin.dia).isEqualTo(5.0)
    }

    @Test
    fun `duration is calculated from DIA in milliseconds`() {
        val insulin = LocalInsulin("TestInsulin", userDefinedDia = 6.0)

        val expectedDuration = (60 * 60 * 1000L * 6.0).toLong()
        assertThat(insulin.duration).isEqualTo(expectedDuration)
    }

    @Test
    fun `duration respects minimum DIA`() {
        val insulin = LocalInsulin("TestInsulin", userDefinedDia = 3.0)

        // Should use 5.0 hours minimum
        val expectedDuration = (60 * 60 * 1000L * 5.0).toLong()
        assertThat(insulin.duration).isEqualTo(expectedDuration)
    }

    @Test
    fun `iobCalcForTreatment returns zero IOB when bolus amount is zero`() {
        val insulin = LocalInsulin("TestInsulin")
        val now = System.currentTimeMillis()
        val bolus = BS(
            timestamp = now - 3600000L,  // 1 hour ago
            amount = 0.0,
            type = BS.Type.NORMAL,
            isValid = true,
            iCfg = ICfg("", 0, 0.0, 0.0)
        )

        val iob = insulin.iobCalcForTreatment(bolus, now)

        assertThat(iob.iobContrib).isEqualTo(0.0)
        assertThat(iob.activityContrib).isEqualTo(0.0)
    }

    @Test
    fun `iobCalcForTreatment returns zero IOB after DIA has passed`() {
        val insulin = LocalInsulin("TestInsulin", userDefinedDia = 6.0)
        val now = System.currentTimeMillis()
        val bolus = BS(
            timestamp = now - (7 * 60 * 60 * 1000L),  // 7 hours ago (beyond DIA)
            amount = 5.0,
            type = BS.Type.NORMAL,
            isValid = true,
            iCfg = ICfg("", 0, 0.0, 0.0)
        )

        val iob = insulin.iobCalcForTreatment(bolus, now)

        assertThat(iob.iobContrib).isEqualTo(0.0)
        assertThat(iob.activityContrib).isEqualTo(0.0)
    }

    @Test
    fun `iobCalcForTreatment calculates IOB within DIA period`() {
        val insulin = LocalInsulin("TestInsulin", peak = 75, userDefinedDia = 6.0)
        val now = System.currentTimeMillis()
        val bolus = BS(
            timestamp = now - (1 * 60 * 60 * 1000L),  // 1 hour ago
            amount = 5.0,
            type = BS.Type.NORMAL,
            isValid = true,
            iCfg = ICfg("", 0, 0.0, 0.0)
        )

        val iob = insulin.iobCalcForTreatment(bolus, now)

        // IOB should be positive and less than the bolus amount
        assertThat(iob.iobContrib).isGreaterThan(0.0)
        assertThat(iob.iobContrib).isLessThan(5.0)
        // Activity should be positive
        assertThat(iob.activityContrib).isGreaterThan(0.0)
    }

    @Test
    fun `iobCalcForTreatment has maximum IOB immediately after bolus`() {
        val insulin = LocalInsulin("TestInsulin", peak = 75, userDefinedDia = 6.0)
        val now = System.currentTimeMillis()
        val bolus = BS(
            timestamp = now - 1000L,  // 1 second ago
            amount = 5.0,
            type = BS.Type.NORMAL,
            isValid = true,
            iCfg = ICfg("", 0, 0.0, 0.0)
        )

        val iob = insulin.iobCalcForTreatment(bolus, now)

        // IOB should be close to the full bolus amount
        assertThat(iob.iobContrib).isWithin(0.1).of(5.0)
    }

    @Test
    fun `iobCalcForTreatment handles ultra-rapid insulin peak`() {
        val insulin = LocalInsulin("UltraRapid", peak = 45, userDefinedDia = 6.0)
        val now = System.currentTimeMillis()
        val bolus = BS(
            timestamp = now - (45 * 60 * 1000L),  // 45 minutes ago (at peak)
            amount = 5.0,
            type = BS.Type.NORMAL,
            isValid = true,
            iCfg = ICfg("", 0, 0.0, 0.0)
        )

        val iob = insulin.iobCalcForTreatment(bolus, now)

        // At peak, activity should be at maximum
        assertThat(iob.activityContrib).isGreaterThan(0.0)
        assertThat(iob.iobContrib).isGreaterThan(0.0)
    }

    @Test
    fun `iobCalcForTreatment handles rapid-acting insulin peak`() {
        val insulin = LocalInsulin("RapidActing", peak = 75, userDefinedDia = 6.0)
        val now = System.currentTimeMillis()
        val bolus = BS(
            timestamp = now - (75 * 60 * 1000L),  // 75 minutes ago (at peak)
            amount = 5.0,
            type = BS.Type.NORMAL,
            isValid = true,
            iCfg = ICfg("", 0, 0.0, 0.0)
        )

        val iob = insulin.iobCalcForTreatment(bolus, now)

        // At peak, activity should be at maximum
        assertThat(iob.activityContrib).isGreaterThan(0.0)
        assertThat(iob.iobContrib).isGreaterThan(0.0)
    }

    @Test
    fun `iobCalcForTreatment handles free peak insulin`() {
        val insulin = LocalInsulin("FreePeak", peak = 100, userDefinedDia = 7.0)
        val now = System.currentTimeMillis()
        val bolus = BS(
            timestamp = now - (2 * 60 * 60 * 1000L),  // 2 hours ago
            amount = 5.0,
            type = BS.Type.NORMAL,
            isValid = true,
            iCfg = ICfg("", 0, 0.0, 0.0)
        )

        val iob = insulin.iobCalcForTreatment(bolus, now)

        assertThat(iob.activityContrib).isGreaterThan(0.0)
        assertThat(iob.iobContrib).isGreaterThan(0.0)
    }

    @Test
    fun `iobCalcForTreatment IOB decreases over time`() {
        val insulin = LocalInsulin("TestInsulin", peak = 75, userDefinedDia = 6.0)
        val bolusTime = System.currentTimeMillis() - (3 * 60 * 60 * 1000L)  // 3 hours ago
        val bolus = BS(
            timestamp = bolusTime,
            amount = 5.0,
            type = BS.Type.NORMAL,
            isValid = true,
            iCfg = ICfg("", 0, 0.0, 0.0)
        )

        // Calculate IOB at 1 hour after bolus
        val iob1h = insulin.iobCalcForTreatment(bolus, bolusTime + (1 * 60 * 60 * 1000L))
        // Calculate IOB at 3 hours after bolus
        val iob3h = insulin.iobCalcForTreatment(bolus, bolusTime + (3 * 60 * 60 * 1000L))

        // IOB should decrease over time
        assertThat(iob1h.iobContrib).isGreaterThan(iob3h.iobContrib)
    }

    @Test
    fun `iobCalcForTreatment handles small bolus amounts`() {
        val insulin = LocalInsulin("TestInsulin")
        val now = System.currentTimeMillis()
        val bolus = BS(
            timestamp = now - (1 * 60 * 60 * 1000L),  // 1 hour ago
            amount = 0.1,
            type = BS.Type.NORMAL,
            isValid = true,
            iCfg = ICfg("", 0, 0.0, 0.0)
        )

        val iob = insulin.iobCalcForTreatment(bolus, now)

        assertThat(iob.iobContrib).isGreaterThan(0.0)
        assertThat(iob.iobContrib).isLessThan(0.1)
    }

    @Test
    fun `iobCalcForTreatment handles large bolus amounts`() {
        val insulin = LocalInsulin("TestInsulin")
        val now = System.currentTimeMillis()
        val bolus = BS(
            timestamp = now - (1 * 60 * 60 * 1000L),  // 1 hour ago
            amount = 20.0,
            type = BS.Type.NORMAL,
            isValid = true,
            iCfg = ICfg("", 0, 0.0, 0.0)
        )

        val iob = insulin.iobCalcForTreatment(bolus, now)

        assertThat(iob.iobContrib).isGreaterThan(0.0)
        assertThat(iob.iobContrib).isLessThan(20.0)
    }

    @Test
    fun `iobCalcForTreatment with minimum DIA calculates correctly`() {
        val insulin = LocalInsulin("TestInsulin", userDefinedDia = 3.0)  // Will use 5.0 minimum
        val now = System.currentTimeMillis()
        val bolus = BS(
            timestamp = now - (1 * 60 * 60 * 1000L),  // 1 hour ago
            amount = 5.0,
            type = BS.Type.NORMAL,
            isValid = true,
            iCfg = ICfg("", 0, 0.0, 0.0)
        )

        val iob = insulin.iobCalcForTreatment(bolus, now)

        // Should still calculate IOB based on 5-hour DIA
        assertThat(iob.iobContrib).isGreaterThan(0.0)
        assertThat(iob.iobContrib).isLessThan(5.0)
    }

    @Test
    fun `iobCalcForTreatment at exactly DIA boundary returns zero`() {
        val insulin = LocalInsulin("TestInsulin", userDefinedDia = 6.0)
        val now = System.currentTimeMillis()
        val bolus = BS(
            timestamp = now - (6 * 60 * 60 * 1000L),  // Exactly 6 hours ago
            amount = 5.0,
            type = BS.Type.NORMAL,
            isValid = true,
            iCfg = ICfg("", 0, 0.0, 0.0)
        )

        val iob = insulin.iobCalcForTreatment(bolus, now)

        // At exactly DIA, IOB should be zero
        assertThat(iob.iobContrib).isEqualTo(0.0)
        assertThat(iob.activityContrib).isEqualTo(0.0)
    }

    @Test
    fun `handles null name`() {
        val insulin = LocalInsulin(null)

        assertThat(insulin.name).isNull()
        assertThat(insulin.peak).isEqualTo(75)
        assertThat(insulin.dia).isEqualTo(6.0)
    }

    @Test
    fun `handles very long DIA`() {
        val insulin = LocalInsulin("TestInsulin", userDefinedDia = 10.0)

        assertThat(insulin.dia).isEqualTo(10.0)
        val expectedDuration = (60 * 60 * 1000L * 10.0).toLong()
        assertThat(insulin.duration).isEqualTo(expectedDuration)
    }
}
