package app.aaps.pump.omnipod.omnipod5.bledriver.comm

import android.content.Context
import app.aaps.core.interfaces.configuration.Config
import app.aaps.pump.omnipod.common.bledriver.comm.exceptions.FailedToConnectException
import app.aaps.pump.omnipod.common.bledriver.comm.exceptions.PairingException
import app.aaps.pump.omnipod.common.bledriver.comm.interfaces.device.BleDeviceManager
import app.aaps.pump.omnipod.omnipod5.bledriver.comm.legacy.O5BleConnectionFactory
import app.aaps.pump.omnipod.omnipod5.bledriver.comm.pair.O5RegistrationData
import app.aaps.pump.omnipod.common.bledriver.comm.session.NotConnected
import app.aaps.pump.omnipod.common.bledriver.pod.command.base.Command
import app.aaps.pump.omnipod.common.bledriver.pod.response.DefaultStatusResponse
import app.aaps.pump.omnipod.omnipod5.bledriver.pod.security.SecureO5RegistrationStorage
import app.aaps.pump.omnipod.omnipod5.bledriver.pod.state.O5PodStateManager
import app.aaps.pump.omnipod.omnipod5.bledriver.pod.util.P256KeyGenerator
import app.aaps.shared.tests.AAPSLoggerTest
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * [O5BleManagerImpl] - covers the guard/validation paths reachable without a live BLE
 * connection (missing pairing state, no connection established yet, the "already paired"
 * short-circuit). Deep BLE session establishment ([O5BleManagerImpl.establishSession] and
 * everything downstream of a real [app.aaps.pump.omnipod.common.bledriver.comm.interfaces
 * .session.BleConnection]) is out of scope for this pass - it would need mocking the whole
 * connection/session stack, not just this class's direct dependencies.
 */
class O5BleManagerImplTest {

    private val aapsLogger = AAPSLoggerTest()
    private val podState = mock<O5PodStateManager>()
    private val config = mock<Config>()
    private val context = mock<Context>()
    private val bleConnectionFactory = mock<O5BleConnectionFactory>()
    private val bleDeviceManager = mock<BleDeviceManager>()
    private val secureO5RegistrationStorage = mock<SecureO5RegistrationStorage>()
    private val p256KeyGenerator = P256KeyGenerator()

    private fun newManager() = O5BleManagerImpl(
        aapsLogger, podState, config, context, bleConnectionFactory,
        bleDeviceManager, secureO5RegistrationStorage, p256KeyGenerator
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
    fun `getStatus returns NotConnected before any connection has been established`() {
        val manager = newManager()

        assertThat(manager.getStatus()).isEqualTo(NotConnected)
    }

    @Test
    fun `constructor loads previously-imported credentials via SecureO5RegistrationStorage`() {
        newManager()

        verify(secureO5RegistrationStorage).loadAndInstallAll()
    }

    @Test
    fun `sendCommand fails with FailedToConnectException when there is no established connection`() {
        val manager = newManager()

        val observer = manager.sendCommand(mock<Command>(), DefaultStatusResponse::class).test()

        observer.assertError(FailedToConnectException::class.java)
    }

    @Test
    fun `connect fails with FailedToConnectException when the pod has no known bluetoothAddress`() {
        whenever(podState.bluetoothAddress).thenReturn(null)
        val manager = newManager()

        val observer = manager.connect(timeoutMs = 1000).test()

        observer.assertError(FailedToConnectException::class.java)
    }

    @Test
    fun `pairNewPod requires a known address when an LTK is already present`() {
        whenever(podState.ltk).thenReturn(byteArrayOf(1, 2, 3))
        whenever(podState.bluetoothAddress).thenReturn(null)
        val manager = newManager()

        val observer = manager.pairNewPod().test()

        observer.assertError(FailedToConnectException::class.java)
    }

    @Test
    fun `pairNewPod fails with PairingException when no O5 registration data is installed`() {
        whenever(podState.ltk).thenReturn(null)
        val manager = newManager()

        val observer = manager.pairNewPod().test()

        observer.assertError(PairingException::class.java)
    }

    @Test
    fun `removeBond does nothing and does not call the device manager when bluetoothAddress is unknown`() {
        whenever(podState.bluetoothAddress).thenReturn(null)
        val manager = newManager()

        manager.removeBond()

        verify(bleDeviceManager, never()).removeBond(org.mockito.kotlin.any())
    }

    @Test
    fun `removeBond delegates to the device manager with the pod's bluetoothAddress`() {
        whenever(podState.bluetoothAddress).thenReturn("AA:BB:CC:DD:EE:FF")
        val manager = newManager()

        manager.removeBond()

        verify(bleDeviceManager).removeBond("AA:BB:CC:DD:EE:FF")
    }

    @Test
    fun `disconnect does not throw when there is no active connection`() {
        val manager = newManager()

        manager.disconnect(closeGatt = false)
    }
}
