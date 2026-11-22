package app.aaps.pump.omnipod.omnipod5.bledriver.comm

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import app.aaps.core.interfaces.configuration.Config
import app.aaps.pump.omnipod.common.bledriver.comm.exceptions.ConnectException
import app.aaps.pump.omnipod.common.bledriver.comm.exceptions.PairingException
import app.aaps.pump.omnipod.omnipod5.bledriver.comm.legacy.O5BleConnectionFactory
import app.aaps.pump.omnipod.omnipod5.bledriver.comm.pair.O5RegistrationData
import app.aaps.pump.omnipod.omnipod5.bledriver.pod.state.O5PodStateManager
import app.aaps.pump.omnipod.omnipod5.bledriver.pod.util.P256KeyGenerator
import app.aaps.shared.tests.AAPSLoggerTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * [O5PairingCoordinator] - covers the two guard checks that run before any real BLE
 * scanning/connection attempt. The rest of [O5PairingCoordinator.pairNewPod] (scan, connect,
 * negotiate LTK, establish session) needs a live or deeply-mocked BLE stack and is out of
 * scope for this pass - same boundary as [O5BleManagerImplTest].
 */
class O5PairingCoordinatorTest {

    private val aapsLogger = AAPSLoggerTest()
    private val config = mock<Config>()
    private val context = mock<Context>()
    private val podState = mock<O5PodStateManager>()
    private val bleConnectionFactory = mock<O5BleConnectionFactory>()
    private val p256KeyGenerator = P256KeyGenerator()

    private fun newCoordinator() = O5PairingCoordinator(
        aapsLogger, config, context, podState, bleConnectionFactory, p256KeyGenerator
    )

    @BeforeEach
    fun clearRegistrationData() {
        O5RegistrationData.allValues.forEach { O5RegistrationData.remove(it.controllerId) }
    }

    @AfterEach
    fun tearDown() {
        O5RegistrationData.allValues.forEach { O5RegistrationData.remove(it.controllerId) }
    }

    @Test
    fun `pairNewPod throws ConnectException when Bluetooth is unavailable`() {
        whenever(context.getSystemService(Context.BLUETOOTH_SERVICE)).thenReturn(null)

        assertThrows(ConnectException::class.java) {
            newCoordinator().pairNewPod()
        }
    }

    @Test
    fun `pairNewPod throws PairingException when no O5 registration data is installed`() {
        val bluetoothManager = mock<BluetoothManager>()
        val bluetoothAdapter = mock<BluetoothAdapter>()
        whenever(context.getSystemService(Context.BLUETOOTH_SERVICE)).thenReturn(bluetoothManager)
        whenever(bluetoothManager.adapter).thenReturn(bluetoothAdapter)

        assertThrows(PairingException::class.java) {
            newCoordinator().pairNewPod()
        }
    }
}
