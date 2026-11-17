package app.aaps.pump.carelevo.ble

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/** The ids of the patch: a wrong one finds no patch at all. They were `BleEnvConfig` in the driver before. */
internal class CarelevoGattTest {

    @Test
    fun `the ids are the ones of the patch`() {
        assertThat(CarelevoGatt.SERVICE.value).isEqualTo("e1b40001-ffc4-4daa-a49b-1c92f99072ab")
        assertThat(CarelevoGatt.WRITE.characteristic.value).isEqualTo("e1b40002-ffc4-4daa-a49b-1c92f99072ab")
        assertThat(CarelevoGatt.NOTIFY.characteristic.value).isEqualTo("e1b40003-ffc4-4daa-a49b-1c92f99072ab")
    }

    @Test
    fun `both characteristics are in the service and differ`() {
        assertThat(CarelevoGatt.WRITE.service).isEqualTo(CarelevoGatt.SERVICE)
        assertThat(CarelevoGatt.NOTIFY.service).isEqualTo(CarelevoGatt.SERVICE)
        assertThat(CarelevoGatt.WRITE).isNotEqualTo(CarelevoGatt.NOTIFY)
    }
}
