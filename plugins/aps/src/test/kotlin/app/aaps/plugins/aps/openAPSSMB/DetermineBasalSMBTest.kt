package app.aaps.plugins.aps.openAPSSMB

import app.aaps.core.interfaces.aps.APSResult
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
 * Guards against non-finite values escaping the algorithm.
 *
 * These are the first tests that run `determine_basal` itself - the plugin tests mock this class out,
 * and every replay fixture in `app/src/androidTest/assets/results/` has both carbs and mealCOB at 0,
 * so the whole COB prediction path had no coverage at all.
 */
class DetermineBasalSMBTest : TestBaseWithProfile() {

    private lateinit var sut: DetermineBasalSMB

    private val currentTime = 1656358822000L

    @BeforeEach
    fun setup() {
        sut = DetermineBasalSMB(profileUtil, fabricPrivacy)
    }

    // BG well above target and steady, timestamped now so the bad-CGM guard does not return early.
    private fun glucoseStatus() = GlucoseStatusSMB(
        glucose = 150.0,
        noise = 0.0,
        delta = 5.0,
        shortAvgDelta = 5.0,
        longAvgDelta = 5.0,
        date = currentTime
    )

    // 48 ticks is what IobCobCalculatorPlugin.calculateIobArrayForSMB produces. The COB prediction
    // only lowers minCOBPredBG after 18 ticks and UAM after 12, so a shorter array would silently
    // skip the blend this test is about. Every tick needs iobWithZeroTemp or determine_basal throws.
    private fun iobArray() = Array(48) { i ->
        val time = currentTime + i * 5 * 60000L
        IobTotal(
            time = time,
            iob = 1.0,
            activity = 0.0,
            lastBolusTime = currentTime - 3600000L,
            iobWithZeroTemp = IobTotal(time = time, iob = 1.0, activity = 0.0)
        )
    }

    private fun profile() = OapsProfile(
        dia = 0.0,
        min_5m_carbimpact = 0.0,
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
        enableUAM = true,
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

    private fun run(mealData: MealData, currentTemp: CurrentTemp = CurrentTemp(0, 0.0, null)): RT =
        sut.determine_basal(
            glucose_status = glucoseStatus(),
            currenttemp = currentTemp,
            iob_data_array = iobArray(),
            profile = profile(),
            autosens_data = AutosensResult(ratio = 1.0),
            meal_data = mealData,
            microBolusAllowed = false,
            currentTime = currentTime,
            flatBGsDetected = false,
            dynIsfMode = false
        )

    /** SMB logs the two min*PredBG lines to consoleLog, AutoISF to consoleError - join both. */
    private fun RT.console(): String = (consoleLog.orEmpty() + consoleError.orEmpty()).joinToString(" ")

    @Test
    fun `COB left after carbs aged out does not poison the prediction blend`() {
        // carbs is summed over a window measured from now, mealCOB comes from the last autosens
        // bucket, so carbs can reach 0 while COB is still on board. mealCOB / 0 used to be Infinity,
        // and (1 - Infinity) * UAMpredBG + Infinity * COBpredBG is NaN.
        val rT = run(
            MealData(
                carbs = 0.0,
                mealCOB = 20.0,
                slopeFromMaxDeviation = 0.0,
                slopeFromMinDeviation = 0.0,
                lastBolusTime = currentTime - 3600000L,
                lastCarbTime = currentTime - 4 * 3600000L
            )
        )

        // Fail loudly if the fixture stopped reaching the blend, otherwise this test would pass for
        // the wrong reason.
        assertThat(rT.console()).contains("minCOBPredBG")
        assertThat(rT.console()).contains("minUAMPredBG")

        assertThat(rT.console()).doesNotContain("avgPredBG: NaN")
        assertThat(rT.insulinReq).isNotNull()
        assertThat(rT.insulinReq!!.isFinite()).isTrue()
    }

    @Test
    fun `non-finite minGuardBG aborts the run instead of dosing`() {
        // An infinite mealCOB with carbs on board makes the minGuardBG blend NaN. Every guard that
        // reads minGuardBG is a "<" comparison, and those are false for NaN, so without the abort the
        // SMB suppression and the predictive low glucose suspend would both be skipped in silence.
        val rT = run(
            MealData(
                carbs = 20.0,
                mealCOB = Double.POSITIVE_INFINITY,
                slopeFromMaxDeviation = 0.0,
                slopeFromMinDeviation = 0.0,
                lastBolusTime = currentTime - 3600000L,
                lastCarbTime = currentTime - 3600000L
            )
        )

        assertThat(rT.reason.toString()).contains("Aborting run: minGuardBG=NaN")
        // No temp is running, so the abort must leave the pump alone rather than invent a dose.
        assertThat(rT.rate).isNull()
        assertThat(rT.duration).isNull()
        assertThat(rT.units).isNull()
    }

    @Test
    fun `non-finite minGuardBG replaces a running high temp with a neutral temp`() {
        val rT = run(
            MealData(
                carbs = 20.0,
                mealCOB = Double.POSITIVE_INFINITY,
                slopeFromMaxDeviation = 0.0,
                slopeFromMinDeviation = 0.0,
                lastBolusTime = currentTime - 3600000L,
                lastCarbTime = currentTime - 3600000L
            ),
            currentTemp = CurrentTemp(duration = 30, rate = 3.0, minutesrunning = 5)
        )

        assertThat(rT.reason.toString()).contains("Aborting run: minGuardBG=NaN")
        assertThat(rT.rate).isEqualTo(1.0) // profile basal, not a zero temp
        assertThat(rT.duration).isEqualTo(30)
    }

    @Test
    fun `setTempBasal falls back to profile basal for a non-finite rate`() {
        // A NaN rate passes both clamps untouched, because every comparison with NaN is false, and
        // DetermineBasalResult turns rT.rate into a real pump command. Zero would be worse than the
        // fallback: it withholds basal for the whole duration.
        val rT = RT(algorithm = APSResult.Algorithm.SMB, runningDynamicIsf = false)

        val result = sut.setTempBasal(Double.NaN, 30, profile(), rT, CurrentTemp(0, 0.0, null))

        assertThat(result.rate).isEqualTo(1.0)
        assertThat(result.rate).isNotEqualTo(0.0)
        assertThat(result.duration).isEqualTo(30)
        assertThat(result.reason.toString()).contains("Setting neutral temp basal")
    }
}
