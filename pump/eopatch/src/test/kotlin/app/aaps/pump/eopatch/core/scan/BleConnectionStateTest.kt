package app.aaps.pump.eopatch.core.scan

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class BleConnectionStateTest {

    @Test
    fun `isConnected should be true only for CONNECTED`() {
        assertThat(BleConnectionState.CONNECTED.isConnected).isTrue()
        assertThat(BleConnectionState.CONNECTING.isConnected).isFalse()
        assertThat(BleConnectionState.CONNECTED_PREPARING.isConnected).isFalse()
        assertThat(BleConnectionState.DISCONNECTED.isConnected).isFalse()
        assertThat(BleConnectionState.DISCONNECTING.isConnected).isFalse()
    }

    @Test
    fun `isConnecting should be true only for CONNECTING`() {
        assertThat(BleConnectionState.CONNECTING.isConnecting).isTrue()
        assertThat(BleConnectionState.CONNECTED.isConnecting).isFalse()
        assertThat(BleConnectionState.DISCONNECTED.isConnecting).isFalse()
    }

    @Test
    fun `should have exactly 5 values`() {
        assertThat(BleConnectionState.entries).hasSize(5)
    }
}
