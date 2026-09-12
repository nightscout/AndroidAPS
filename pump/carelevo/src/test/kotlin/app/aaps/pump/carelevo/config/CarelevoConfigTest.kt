package app.aaps.pump.carelevo.config

import com.google.common.truth.Truth.assertThat
import java.util.UUID
import org.junit.jupiter.api.Test

/**
 * Guards the Carelevo config constants — BLE UUIDs, reservoir fill limits, and the
 * preference storage keys — against accidental drift. Values here are the single source
 * of truth referenced across the driver.
 */
internal class CarelevoConfigTest {

    // ---------- BleEnvConfig ----------

    @Test
    fun `BleEnvConfig UUID constants hold the exact expected values`() {
        assertThat(BleEnvConfig.BLE_CCC_DESCRIPTOR).isEqualTo("00002902-0000-1000-8000-00805f9b34fb")
        assertThat(BleEnvConfig.BLE_SERVICE_UUID).isEqualTo("e1b40001-ffc4-4daa-a49b-1c92f99072ab")
        assertThat(BleEnvConfig.BLE_TX_CHAR_UUID).isEqualTo("e1b40003-ffc4-4daa-a49b-1c92f99072ab")
        assertThat(BleEnvConfig.BLE_RX_CHAR_UUID).isEqualTo("e1b40002-ffc4-4daa-a49b-1c92f99072ab")
    }

    @Test
    fun `BleEnvConfig UUID constants are well-formed UUIDs`() {
        listOf(
            BleEnvConfig.BLE_CCC_DESCRIPTOR,
            BleEnvConfig.BLE_SERVICE_UUID,
            BleEnvConfig.BLE_TX_CHAR_UUID,
            BleEnvConfig.BLE_RX_CHAR_UUID
        ).forEach { assertThat(UUID.fromString(it).toString()).isEqualTo(it) }
    }

    @Test
    fun `BleEnvConfig TX and RX characteristics differ from each other and the service`() {
        assertThat(BleEnvConfig.BLE_TX_CHAR_UUID).isNotEqualTo(BleEnvConfig.BLE_RX_CHAR_UUID)
        assertThat(BleEnvConfig.BLE_TX_CHAR_UUID).isNotEqualTo(BleEnvConfig.BLE_SERVICE_UUID)
        assertThat(BleEnvConfig.BLE_RX_CHAR_UUID).isNotEqualTo(BleEnvConfig.BLE_SERVICE_UUID)
    }

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

    // ---------- PrefEnvConfig ----------

    @Test
    fun `PrefEnvConfig keys hold the expected values`() {
        assertThat(PrefEnvConfig.PATCH_INFO).isEqualTo("carelevo_patch_info")
        assertThat(PrefEnvConfig.BASAL_INFUSION_INFO).isEqualTo("carelevo_basal_infusion_info")
        assertThat(PrefEnvConfig.TEMP_BASAL_INFUSION_INFO).isEqualTo("carelevo_temp_basal_infusion_info")
        assertThat(PrefEnvConfig.IMME_BOLUS_INFUSION_INFO).isEqualTo("carelevo_imme_bolus_infusion_info")
        assertThat(PrefEnvConfig.EXTEND_BOLUS_INFUSION_INFO).isEqualTo("carelevo_extend_bolus_infusion_info")
        assertThat(PrefEnvConfig.USER_SETTING_INFO).isEqualTo("carelevo_user_setting_info")
        assertThat(PrefEnvConfig.CARELEVO_ALARM_INFO_LIST).isEqualTo("carelevo_alarm_info_list")
    }

    @Test
    fun `PrefEnvConfig keys are all carelevo-namespaced`() {
        listOf(
            PrefEnvConfig.PATCH_INFO,
            PrefEnvConfig.BASAL_INFUSION_INFO,
            PrefEnvConfig.TEMP_BASAL_INFUSION_INFO,
            PrefEnvConfig.IMME_BOLUS_INFUSION_INFO,
            PrefEnvConfig.EXTEND_BOLUS_INFUSION_INFO,
            PrefEnvConfig.USER_SETTING_INFO,
            PrefEnvConfig.CARELEVO_ALARM_INFO_LIST
        ).forEach { assertThat(it).startsWith("carelevo_") }
    }

    @Test
    fun `PrefEnvConfig keys are unique`() {
        val keys = listOf(
            PrefEnvConfig.PATCH_INFO,
            PrefEnvConfig.BASAL_INFUSION_INFO,
            PrefEnvConfig.TEMP_BASAL_INFUSION_INFO,
            PrefEnvConfig.IMME_BOLUS_INFUSION_INFO,
            PrefEnvConfig.EXTEND_BOLUS_INFUSION_INFO,
            PrefEnvConfig.USER_SETTING_INFO,
            PrefEnvConfig.CARELEVO_ALARM_INFO_LIST
        )
        assertThat(keys.toSet()).hasSize(keys.size)
    }
}
