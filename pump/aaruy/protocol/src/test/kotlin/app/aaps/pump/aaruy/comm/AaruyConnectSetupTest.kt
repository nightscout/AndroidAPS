package app.aaps.pump.aaruy.comm

import android.bluetooth.BluetoothManager
import android.content.Context
import android.os.Looper
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.pump.ble.BleAdapter
import app.aaps.core.interfaces.pump.ble.BleGatt
import app.aaps.core.interfaces.pump.ble.BleTransportListener
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.atLeastOnce
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

/**
 * The driver asks to connect every few seconds until it is connected. On a real pump the setup includes pairing, which
 * waits for a button on the pump, so a request during the setup must leave the setup alone.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AaruyConnectSetupTest {

    private val address = "D1:61:D0:00:00:01"
    private val gatt = mock(BleGatt::class.java)
    private val transport = mock(AaruyBleTransport::class.java)
    private lateinit var client: AaruyBleClient

    private fun device() =
        (RuntimeEnvironment.getApplication().getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter.getRemoteDevice(address)

    private fun passTime(seconds: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(seconds))

    private fun linkUp() {
        val listener = ArgumentCaptor.forClass(BleTransportListener::class.java)
        verify(transport, atLeastOnce()).setListener(listener.capture())
        listener.value.onConnectionStateChanged(true)
        passTime(1)
    }

    @Before
    fun setUp() {
        `when`(gatt.connect(anyString())).thenReturn(true)
        `when`(transport.gatt).thenReturn(gatt)
        `when`(transport.adapter).thenReturn(mock(BleAdapter::class.java))
        client = AaruyBleClient(RuntimeEnvironment.getApplication(), mock(AAPSLogger::class.java), transport)
    }

    @Test
    fun askingAgainBeforeTheLinkIsUpChangesNothing() {
        client.connectWithoutBonding(device())
        client.connectWithoutBonding(device())

        verify(gatt, times(1)).connect(address)
        verify(gatt, never()).close()
    }

    @Test
    fun askingAgainWhileTheSetupWaitsForPairingChangesNothing() {
        client.connectWithoutBonding(device())
        linkUp()

        passTime(6)
        client.connectWithoutBonding(device())
        passTime(6)
        client.connectWithoutBonding(device())

        verify(gatt, times(1)).connect(address)
        verify(gatt, never()).close()
    }

    @Test
    fun aSetupThatNeverEndsIsStartedAgain() {
        client.connectWithoutBonding(device())
        linkUp()

        passTime(60)

        verify(gatt, atLeastOnce()).close()
        verify(gatt, times(2)).connect(address)
    }

    @Test
    fun askingAgainAfterAllRetriesFailedStartsANewConnection() {
        client.connectWithoutBonding(device())
        passTime(60)
        verify(gatt, times(3)).connect(address)

        client.connectWithoutBonding(device())

        verify(gatt, times(4)).connect(address)
    }
}
