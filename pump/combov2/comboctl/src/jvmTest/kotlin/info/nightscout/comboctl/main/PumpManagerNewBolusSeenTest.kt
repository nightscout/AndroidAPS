package info.nightscout.comboctl.main

import info.nightscout.comboctl.base.BluetoothAddress
import info.nightscout.comboctl.base.BluetoothDevice
import info.nightscout.comboctl.base.BluetoothInterface
import info.nightscout.comboctl.base.Cipher
import info.nightscout.comboctl.base.CurrentTbrState
import info.nightscout.comboctl.base.InvariantPumpData
import info.nightscout.comboctl.base.byteArrayOfInts
import info.nightscout.comboctl.base.testUtils.TestBluetoothDevice
import info.nightscout.comboctl.base.testUtils.TestComboIO
import info.nightscout.comboctl.base.testUtils.TestPumpStateStore
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.UtcOffset
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// Every connection gets a new Pump. A bolus that a history read found must still stop the
// bolus that waits in the queue when that connection was dropped first (#5377).
class PumpManagerNewBolusSeenTest {

    private val device = TestBluetoothDevice(TestComboIO())
    private val address = device.address
    private val pumpStateStore = TestPumpStateStore()

    private val bluetoothInterface = object : BluetoothInterface {
        override var onDeviceUnpaired: (deviceAddress: BluetoothAddress) -> Unit = { }
        override var deviceFilterCallback: (deviceAddress: BluetoothAddress) -> Boolean = { true }

        override fun startDiscovery(
            sdpServiceName: String,
            sdpServiceProvider: String,
            sdpServiceDescription: String,
            btPairingPin: String,
            discoveryDuration: Int,
            onDiscoveryStopped: (reason: BluetoothInterface.DiscoveryStoppedReason) -> Unit,
            onFoundNewPairedDevice: (deviceAddress: BluetoothAddress) -> Unit
        ) = Unit

        override fun stopDiscovery() = Unit
        override fun getDevice(deviceAddress: BluetoothAddress): BluetoothDevice = device
        override fun getAdapterFriendlyName() = "test"
        override fun getPairedDeviceAddresses() = setOf(address)
    }

    private val pumpManager = PumpManager(bluetoothInterface, pumpStateStore)

    private fun pair() {
        val cipher = Cipher(byteArrayOfInts(0x5a, 0x25, 0x0b, 0x75, 0xa9, 0x02, 0x21, 0xfa, 0xab, 0xbd, 0x36, 0x4d, 0x5c, 0xb8, 0x37, 0xd7))
        pumpStateStore.createPumpState(
            address,
            InvariantPumpData(keyResponseAddress = 0x10, clientPumpCipher = cipher, pumpClientCipher = cipher, pumpID = "testPump"),
            UtcOffset.ZERO,
            CurrentTbrState.NoTbrOngoing
        )
    }

    @Test
    fun aBolusFoundByOneConnectionIsKnownToTheNext() {
        runBlocking {
            pair()

            val first = pumpManager.acquirePump(address)
            assertFalse(first.newBolusSeen)
            first.newBolusSeen = true
            pumpManager.releasePump(address)

            assertTrue(pumpManager.acquirePump(address).newBolusSeen)
        }
    }

    @Test
    fun aBolusAttemptClearsItForTheNextConnectionToo() {
        runBlocking {
            pair()

            pumpManager.acquirePump(address).newBolusSeen = true
            pumpManager.releasePump(address)
            // deliverBolus() cleared it in this connection
            pumpManager.acquirePump(address).newBolusSeen = false
            pumpManager.releasePump(address)

            assertFalse(pumpManager.acquirePump(address).newBolusSeen)
        }
    }

    @Test
    fun anUnpairedPumpStartsAgainWithNothingSeen() {
        runBlocking {
            pair()

            pumpManager.acquirePump(address).newBolusSeen = true
            // Pump.unpair() deletes the state before the pump is released
            pumpStateStore.deletePumpState(address)
            pumpManager.releasePump(address)
            pair()

            assertFalse(pumpManager.acquirePump(address).newBolusSeen)
        }
    }
}
