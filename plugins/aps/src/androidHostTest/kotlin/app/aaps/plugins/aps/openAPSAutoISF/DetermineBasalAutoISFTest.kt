package app.aaps.plugins.aps.openAPSAutoISF

import app.aaps.core.interfaces.aps.APSResult
import app.aaps.core.interfaces.aps.AutosensResult
import app.aaps.core.interfaces.aps.CurrentTemp
import app.aaps.core.interfaces.aps.GlucoseStatusSMB
import app.aaps.core.interfaces.aps.IobTotal
import app.aaps.core.interfaces.aps.MealData
import app.aaps.core.interfaces.aps.OapsProfileAutoIsf
import app.aaps.core.interfaces.aps.RT
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Twin of `DetermineBasalSMBTest`. The two algorithms share this code almost line for line, but only
 * the SMB copy was hardened against non-finite values earlier, so AutoISF is the one where a NaN used
 * to reach rT.insulinReq and rT.rate and crash RT.serialize.
 */
class DetermineBasalAutoISFTest : TestBaseWithProfile() {

    private lateinit var sut: DetermineBasalAutoISF

    private val currentTime = 1656358822000L

    @BeforeEach
    fun setup() {
        sut = DetermineBasalAutoISF(profileUtil)
    }

    private fun glucoseStatus() = GlucoseStatusSMB(
        glucose = 150.0,
        noise = 0.0,
        delta = 5.0,
        shortAvgDelta = 5.0,
        longAvgDelta = 5.0,
        date = currentTime
    )

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

    private fun profile() = OapsProfileAutoIsf(
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
        autoISF_version = "3.0",
        enable_autoISF = false,
        autoISF_max = 1.0,
        autoISF_min = 1.0,
        bgAccel_ISF_weight = 0.0,
        bgBrake_ISF_weight = 0.0,
        pp_ISF_weight = 0.0,
        lower_ISFrange_weight = 0.0,
        higher_ISFrange_weight = 0.0,
        dura_ISF_weight = 0.0,
        smb_delivery_ratio = 0.5,
        smb_delivery_ratio_min = 0.5,
        smb_delivery_ratio_max = 0.5,
        smb_delivery_ratio_bg_range = 0.0,
        smb_max_range_extension = 1.0,
        enableSMB_EvenOn_OddOff_always = false,
        iob_threshold_percent = 100,
        profile_percentage = 100
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
            autoIsfMode = false,
            loop_wanted_smb = "AAPS",
            profile_percentage = 100,
            smb_ratio = 0.5,
            smb_max_range_extension = 1.0,
            iob_threshold_percent = 100,
            auto_isf_consoleError = mutableListOf(),
            auto_isf_consoleLog = mutableListOf()
        )

    private fun RT.console(): String = (consoleLog.orEmpty() + consoleError.orEmpty()).joinToString(" ")

    @Test
    fun `COB left after carbs aged out does not poison the prediction blend`() {
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

        assertThat(rT.console()).contains("minCOBPredBG")
        assertThat(rT.console()).contains("minUAMPredBG")

        assertThat(rT.console()).doesNotContain("avgPredBG: NaN")
        assertThat(rT.insulinReq).isNotNull()
        assertThat(rT.insulinReq!!.isFinite()).isTrue()
        assertThat(rT.rate?.isFinite() ?: true).isTrue()
    }

    @Test
    fun `non-finite COB aborts the run instead of dosing`() {
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

        assertThat(rT.reason.toString()).contains("Aborting run:")
        assertThat(rT.rate).isNull()
        assertThat(rT.duration).isNull()
        assertThat(rT.units).isNull()
    }

    @Test
    fun `non-finite COB replaces a running high temp with a neutral temp`() {
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

        assertThat(rT.reason.toString()).contains("Aborting run:")
        assertThat(rT.rate).isEqualTo(1.0)
        assertThat(rT.duration).isEqualTo(30)
    }

    @Test
    fun `setTempBasal falls back to profile basal for a non-finite rate`() {
        val rT = RT(algorithm = APSResult.Algorithm.AUTO_ISF, runningDynamicIsf = false)

        val result = sut.setTempBasal(Double.NaN, 30, profile(), rT, CurrentTemp(0, 0.0, null))

        assertThat(result.rate).isEqualTo(1.0)
        assertThat(result.rate).isNotEqualTo(0.0)
        assertThat(result.duration).isEqualTo(30)
        assertThat(result.reason.toString()).contains("Setting neutral temp basal")
    }
}
