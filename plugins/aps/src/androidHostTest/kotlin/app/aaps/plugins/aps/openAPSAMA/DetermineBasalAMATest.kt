package app.aaps.plugins.aps.openAPSAMA

import app.aaps.core.interfaces.aps.AutosensResult
import app.aaps.core.interfaces.aps.CurrentTemp
import app.aaps.core.interfaces.aps.GlucoseStatusSMB
import app.aaps.core.interfaces.aps.IobTotal
import app.aaps.core.interfaces.aps.MealData
import app.aaps.core.interfaces.aps.OapsProfile
import app.aaps.core.interfaces.aps.RT
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * The log line with the carb impact duration. With no COB and a carb impact of 0 the duration is 0 / 0 = NaN, and
 * `roundToLong()` threw on it, so the loop stopped (Crashlytics, 2026-11, "Cannot round NaN value."). The JS writes
 * `Math.round(10 * cid / 6) / 10`: one decimal, NaN passes through.
 */
class DetermineBasalAMATest : TestBaseWithProfile() {

    private lateinit var sut: DetermineBasalAMA

    private val currentTime = 1656358822000L

    @BeforeEach
    fun setup() {
        sut = DetermineBasalAMA(profileUtil)
    }

    // No insulin activity, so BGI is 0. With a flat BG the carb impact (minDelta - BGI) is 0 too.
    private fun iobArray() = Array(48) { i ->
        val time = currentTime + i * 5 * 60000L
        IobTotal(time = time, iob = 0.0, activity = 0.0, iobWithZeroTemp = IobTotal(time = time, iob = 0.0, activity = 0.0))
    }

    private fun profile() = OapsProfile(
        dia = 5.0,
        min_5m_carbimpact = 3.0,
        max_iob = 7.0,
        max_daily_basal = 1.0,
        max_basal = 4.0,
        min_bg = 100.0,
        max_bg = 100.0,
        target_bg = 100.0,
        carb_ratio = 10.0,
        sens = 50.0,
        autosens_adjust_targets = false,
        max_daily_safety_multiplier = 3.0,
        current_basal_safety_multiplier = 4.0,
        high_temptarget_raises_sensitivity = false,
        low_temptarget_lowers_sensitivity = false,
        sensitivity_raises_target = false,
        resistance_lowers_target = false,
        adv_target_adjustments = false,
        exercise_mode = false,
        half_basal_exercise_target = 160,
        maxCOB = 120,
        skip_neutral_temps = false,
        remainingCarbsCap = 90,
        enableUAM = false,
        A52_risk_enable = false,
        SMBInterval = 3,
        enableSMB_with_COB = false,
        enableSMB_with_temptarget = false,
        allowSMB_with_high_temptarget = false,
        enableSMB_always = false,
        enableSMB_after_carbs = false,
        maxSMBBasalMinutes = 30,
        maxUAMSMBBasalMinutes = 30,
        bolus_increment = 0.1,
        carbsReqThreshold = 1,
        current_basal = 1.0,
        temptargetSet = false,
        autosens_max = 1.2,
        out_units = "mg/dl",
        lgsThreshold = null,
        variable_sens = 50.0,
        insulinDivisor = 75,
        TDD = 40.0
    )

    private fun run(mealData: MealData, delta: Double): RT =
        sut.determine_basal(
            glucose_status = GlucoseStatusSMB(glucose = 150.0, delta = delta, shortAvgDelta = delta, longAvgDelta = delta, date = currentTime),
            currenttemp = CurrentTemp(0, 0.0, null),
            iob_data_array = iobArray(),
            profile = profile(),
            autosens_data = AutosensResult(ratio = 1.0),
            meal_data = mealData,
            currentTime = currentTime
        )

    private fun RT.line(start: String): String = consoleError.orEmpty().single { it.startsWith(start) }

    @Test
    fun `no COB and no carb impact gives a result instead of a crash`() {
        val rt = run(MealData(), delta = 0.0)

        assertThat(rt.line("Carb Impact:")).isEqualTo("Carb Impact: 0.0 mg/dL per 5m; CI Duration: NaN hours")
        assertThat(rt.reason.toString()).isNotEmpty()
    }

    @Test
    fun `the duration keeps one decimal as in the JS`() {
        // cid = 20 g * (50 / 10) / 3 = 33.3 five-minute steps, / 6 = 5.6 hours. acid = 20 * 5 / 10 = 10, / 6 = 1.7 hours.
        val rt = run(MealData(carbs = 30.0, mealCOB = 20.0), delta = 0.0)

        assertThat(rt.line("Carb Impact:")).isEqualTo("Carb Impact: 3.0 mg/dL per 5m; CI Duration: 5.6 hours")
        assertThat(rt.line("Accel. Carb Impact:")).isEqualTo("Accel. Carb Impact: 10 mg/dL per 5m; ACI Duration: 1.7 hours")
    }
}
