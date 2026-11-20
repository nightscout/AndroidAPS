package app.aaps.pump.omnipod.common.bledriver.comm.legacy.io

import android.bluetooth.BluetoothGattCharacteristic
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * [BleIO.usesIndicate] - the notify-vs-indicate CCCD decision `readyToRead()` uses to set
 * up the CMD/DATA characteristics. Real O5 pairing attempts consistently disconnect a few
 * seconds after SPS0 is sent with no response ever received - this used to unconditionally
 * assume indicate (correct for Dash, never verified for O5), so this pins the fix: the
 * choice must follow the characteristic's own declared properties, the same way
 * [app.aaps.pump.omnipod.common.bledriver.comm.legacy.session.O5Connection
 * .enableHeartbeatNotifications] already does for the heartbeat characteristic.
 */
class BleIOTest {

    @Test
    fun `usesIndicate is true when the characteristic declares PROPERTY_INDICATE`() {
        val characteristic = mock<BluetoothGattCharacteristic>()
        whenever(characteristic.properties).thenReturn(BluetoothGattCharacteristic.PROPERTY_INDICATE)

        assertThat(BleIO.usesIndicate(characteristic)).isTrue()
    }

    @Test
    fun `usesIndicate is false when the characteristic only declares PROPERTY_NOTIFY`() {
        val characteristic = mock<BluetoothGattCharacteristic>()
        whenever(characteristic.properties).thenReturn(BluetoothGattCharacteristic.PROPERTY_NOTIFY)

        assertThat(BleIO.usesIndicate(characteristic)).isFalse()
    }

    @Test
    fun `usesIndicate is true when the characteristic declares both properties - indicate is chosen`() {
        val characteristic = mock<BluetoothGattCharacteristic>()
        whenever(characteristic.properties)
            .thenReturn(BluetoothGattCharacteristic.PROPERTY_NOTIFY or BluetoothGattCharacteristic.PROPERTY_INDICATE)

        assertThat(BleIO.usesIndicate(characteristic)).isTrue()
    }

    @Test
    fun `usesIndicate is false when the characteristic declares neither property`() {
        val characteristic = mock<BluetoothGattCharacteristic>()
        whenever(characteristic.properties).thenReturn(0)

        assertThat(BleIO.usesIndicate(characteristic)).isFalse()
    }
}
