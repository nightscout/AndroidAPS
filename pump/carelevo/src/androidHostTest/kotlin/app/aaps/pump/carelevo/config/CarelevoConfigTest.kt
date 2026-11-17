package app.aaps.pump.carelevo.config

import app.aaps.pump.carelevo.common.keys.CarelevoStringNonKey
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * Guards the Carelevo config constants — reservoir fill limits and the preference storage keys —
 * against accidental drift. The Bluetooth ids are pinned by `CarelevoGattTest` in the protocol module.
 */
internal class CarelevoConfigTest {

    // ---------- FillConfig ----------

    @Test
    fun `FillConfig fill limits hold the expected values`() {
        assertThat(FillConfig.FILL_MIN_UNITS).isEqualTo(50)
        assertThat(FillConfig.FILL_MAX_UNITS).isEqualTo(300)
        assertThat(FillConfig.FILL_STEP_UNITS).isEqualTo(10)
    }

    @Test
    fun `FillConfig max stays in sync with the CAREMEDI maxReservoirReading of 300`() {
        assertThat(FillConfig.FILL_MAX_UNITS).isEqualTo(300)
    }

    @Test
    fun `FillConfig min is below max and both are positive`() {
        assertThat(FillConfig.FILL_MIN_UNITS).isGreaterThan(0)
        assertThat(FillConfig.FILL_MIN_UNITS).isLessThan(FillConfig.FILL_MAX_UNITS)
    }

    @Test
    fun `FillConfig step evenly divides the fillable range and both bounds`() {
        assertThat(FillConfig.FILL_STEP_UNITS).isGreaterThan(0)
        assertThat((FillConfig.FILL_MAX_UNITS - FillConfig.FILL_MIN_UNITS) % FillConfig.FILL_STEP_UNITS).isEqualTo(0)
        assertThat(FillConfig.FILL_MIN_UNITS % FillConfig.FILL_STEP_UNITS).isEqualTo(0)
        assertThat(FillConfig.FILL_MAX_UNITS % FillConfig.FILL_STEP_UNITS).isEqualTo(0)
    }

    // ---------- CarelevoStringNonKey ----------
    //
    // These three moved here from `PrefEnvConfig`, which is gone: the keys are registered enum entries
    // now. The tests themselves matter more than where they live - they pin the STORED STRINGS, and a
    // stored string is what connects a running patch to its own state. Changing one does not migrate a
    // value, it abandons it, and for PatchInfo or the infusion records that means a live patch coming
    // back as if it were new.

    @Test
    fun `stored keys hold the expected values`() {
        assertThat(CarelevoStringNonKey.PatchInfo.key).isEqualTo("carelevo_patch_info")
        assertThat(CarelevoStringNonKey.BasalInfusionInfo.key).isEqualTo("carelevo_basal_infusion_info")
        assertThat(CarelevoStringNonKey.TempBasalInfusionInfo.key).isEqualTo("carelevo_temp_basal_infusion_info")
        assertThat(CarelevoStringNonKey.ImmeBolusInfusionInfo.key).isEqualTo("carelevo_imme_bolus_infusion_info")
        assertThat(CarelevoStringNonKey.ExtendBolusInfusionInfo.key).isEqualTo("carelevo_extend_bolus_infusion_info")
        assertThat(CarelevoStringNonKey.UserSettingInfo.key).isEqualTo("carelevo_user_setting_info")
        assertThat(CarelevoStringNonKey.AlarmInfoList.key).isEqualTo("carelevo_alarm_info_list")
        assertThat(CarelevoStringNonKey.LastSnapshotAlarmCauses.key).isEqualTo("carelevo_last_snapshot_alarm_causes")
    }

    @Test
    fun `stored keys are all carelevo-namespaced`() {
        CarelevoStringNonKey.entries.forEach { assertThat(it.key).startsWith("carelevo_") }
    }

    @Test
    fun `stored keys are unique`() {
        val keys = CarelevoStringNonKey.entries.map { it.key }
        assertThat(keys.toSet()).hasSize(keys.size)
    }

    /**
     * None of these was ever in an export file - they were unregistered, and `isExportableKey` answers
     * false for anything it does not know. Registering them must not change that: it is plumbing, not a
     * decision about what an export carries. See the enum's own KDoc.
     */
    @Test
    fun `stored keys are not exportable`() {
        CarelevoStringNonKey.entries.forEach { assertThat(it.exportable).isFalse() }
    }
}
