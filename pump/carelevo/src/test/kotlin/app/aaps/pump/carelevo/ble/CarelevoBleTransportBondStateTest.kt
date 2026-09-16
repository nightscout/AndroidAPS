package app.aaps.pump.carelevo.ble

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import app.aaps.core.interfaces.logging.AAPSLogger
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * Regression guard for issue #4990 (CareLevo pairing fails on Android 13).
 *
 * [CarelevoBleSession.open] polls [BleAdapter.isDeviceBonded] to wait for the SMP bond to **complete**
 * before discovering services (the patch requires bond-then-discover). The predicate must therefore be
 * true ONLY for `BOND_BONDED`: `BOND_BONDING` (11) also satisfies the old `!= BOND_NONE` check, which
 * let discovery start mid-bonding and dropped the link on Android <= 13
 * (`GattDiscoveryException: disconnected`). Android 14+ happened to tolerate the early discovery, which
 * is why the bug only surfaced on 13.
 *
 * The predicate is SDK-independent, so the SDK here only needs to be a version Robolectric provides;
 * the Android-13 behaviour it guards against is the disconnect, not the bond-state mapping itself.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CarelevoBleTransportBondStateTest {

    private val context: Context = RuntimeEnvironment.getApplication()
    private val aapsLogger: AAPSLogger = mock()
    private lateinit var transport: CarelevoBleTransportImpl

    @Before
    fun setup() {
        Shadows.shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(Manifest.permission.BLUETOOTH_CONNECT)
        transport = CarelevoBleTransportImpl(context, aapsLogger)
    }

    private fun setBondState(state: Int) {
        val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
        Shadows.shadowOf(adapter.getRemoteDevice(ADDRESS)).setBondState(state)
    }

    @Test
    fun `BOND_BONDED reports bonded`() {
        setBondState(BluetoothDevice.BOND_BONDED)
        assertThat(transport.adapter.isDeviceBonded(ADDRESS)).isTrue()
    }

    @Test
    fun `BOND_BONDING is not yet bonded so discovery waits`() {
        setBondState(BluetoothDevice.BOND_BONDING)
        assertThat(transport.adapter.isDeviceBonded(ADDRESS)).isFalse()
    }

    @Test
    fun `BOND_NONE reports not bonded`() {
        setBondState(BluetoothDevice.BOND_NONE)
        assertThat(transport.adapter.isDeviceBonded(ADDRESS)).isFalse()
    }

    @Test
    fun `missing BLUETOOTH_CONNECT permission reports not bonded`() {
        Shadows.shadowOf(RuntimeEnvironment.getApplication()).denyPermissions(Manifest.permission.BLUETOOTH_CONNECT)
        setBondState(BluetoothDevice.BOND_BONDED)
        assertThat(transport.adapter.isDeviceBonded(ADDRESS)).isFalse()
    }

    private companion object {

        const val ADDRESS = "94:B2:16:1D:40:5C"
    }
}
