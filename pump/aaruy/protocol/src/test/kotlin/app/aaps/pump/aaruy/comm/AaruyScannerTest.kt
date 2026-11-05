package app.aaps.pump.aaruy.comm

import android.bluetooth.BluetoothDevice
import no.nordicsemi.android.support.v18.scanner.ScanResult
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AaruyScannerTest {

    private fun result(name: String?): ScanResult {
        val device = mock<BluetoothDevice> { on { this.name } doReturn name }
        return mock { on { this.device } doReturn device }
    }

    @Test
    fun onlyPumpsAreListedAndEachOnce() {
        val pump = result("aaruy_X10001Y")
        var listed: List<ScanResult> = emptyList()
        val scanner = AaruyScanner().apply { listener = ScanResultListener { listed = it.toList() } }

        scanner.onBatch(listOf(pump, result("Galaxy Watch"), result(null), result("")))
        scanner.onBatch(listOf(pump))

        assertEquals(listOf(pump), listed)
    }

    @Test
    fun twoPumpsAreBothListed() {
        var listed: List<ScanResult> = emptyList()
        val scanner = AaruyScanner().apply { listener = ScanResultListener { listed = it.toList() } }
        scanner.onBatch(listOf(result("aaruy_X10001Y"), result("aaruy_X10002Y")))
        assertEquals(2, listed.size)
    }
}
