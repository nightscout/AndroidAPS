package info.nightscout.comboctl.base

import app.aaps.shared.tests.TestBase
import info.nightscout.comboctl.base.testUtils.TestPumpStateStore
import info.nightscout.comboctl.base.testUtils.runBlockingWithWatchdog
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

class PairingCancelTest : TestBase() {

    /**
     * A device whose connect() behaves like BluetoothSocket.connect(): it blocks its thread, does
     * not react to a coroutine cancellation, and only ends early when disconnect() closes the socket.
     */
    private class BlockingConnectBluetoothDevice : BluetoothDevice(Dispatchers.IO) {

        override val address = BluetoothAddress(byteArrayListOfInts(1, 2, 3, 4, 5, 6))

        val connectStarted = CompletableDeferred<Unit>()
        private val socketClosed = CountDownLatch(1)

        @Volatile var disconnectCalled = false

        override fun connect() {
            connectStarted.complete(Unit)
            // Longer than the watchdog, so the test fails when nothing closes the socket.
            if (!socketClosed.await(20, TimeUnit.SECONDS)) error("connect() was never stopped")
            throw IOException("Socket closed")
        }

        override fun disconnect() {
            disconnectCalled = true
            socketClosed.countDown()
        }

        override fun unpair() {}

        override fun blockingSend(dataToSend: List<Byte>) {}

        override fun blockingReceive(): List<Byte> = listOf()
    }

    @Test
    fun cancellingPairingStopsBlockedConnect() {
        val device = BlockingConnectBluetoothDevice()
        val pumpIO = PumpIO(TestPumpStateStore(), device, onNewDisplayFrame = {}, onPacketReceiverException = {})

        // Before the fix, the cancel waited until connect() ended by itself, and that never
        // happens here, so the watchdog failed the test.
        runBlockingWithWatchdog(10000) {
            val pairing = launch {
                pumpIO.performPairing("test", null) { _, _ -> fail("no PIN is asked before a connection") }
            }
            device.connectStarted.await()
            pairing.cancelAndJoin()
        }

        assertTrue(device.disconnectCalled)
    }
}
