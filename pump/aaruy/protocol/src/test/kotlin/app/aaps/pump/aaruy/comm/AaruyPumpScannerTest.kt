package app.aaps.pump.aaruy.comm

import app.aaps.core.interfaces.pump.ble.ScannedDevice
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.concurrent.CopyOnWriteArrayList

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AaruyPumpScannerTest {

    private val scanner = AaruyBleTransportImpl(RuntimeEnvironment.getApplication(), mock()).scanner as AaruyBleTransportImpl.PumpScanner
    private val listed = CopyOnWriteArrayList<ScannedDevice>()

    private fun TestScope.listen() {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { scanner.scannedDevices.toList(listed) }
    }

    @Test
    fun onlyPumpsAreListedAndEachOnce() = runTest {
        listen()
        scanner.onFound("aaruy_X10001Y", PUMP, -50)
        scanner.onFound("Galaxy Watch", OTHER, -40)
        scanner.onFound(null, OTHER, -40)
        scanner.onFound("", OTHER, -40)
        scanner.onFound("aaruy_X10001Y", PUMP, -48)

        assertEquals(listOf(ScannedDevice("aaruy_X10001Y", PUMP, rssi = -50)), listed)
    }

    @Test
    fun twoPumpsAreBothListed() = runTest {
        listen()
        scanner.onFound("aaruy_X10001Y", PUMP, -50)
        scanner.onFound("aaruy_X10002Y", OTHER, -60)

        assertEquals(listOf(PUMP, OTHER), listed.map { it.address })
    }

    /** The wizard starts a new scan with an empty list, so a pump found before is listed again. */
    @Test
    fun aNewScanListsThePumpAgain() = runTest {
        listen()
        scanner.onFound("aaruy_X10001Y", PUMP, -50)
        scanner.startScan()
        scanner.onFound("aaruy_X10001Y", PUMP, -50)

        assertEquals(2, listed.size)
    }

    private companion object {

        const val PUMP = "D1:61:D0:5E:72:E5"
        const val OTHER = "11:22:33:44:55:66"
    }
}
