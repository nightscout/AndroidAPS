package info.nightscout.comboctl.base

import app.aaps.shared.tests.TestBase
import info.nightscout.comboctl.base.testUtils.TestBluetoothDevice
import info.nightscout.comboctl.base.testUtils.TestPumpStateStore
import info.nightscout.comboctl.base.testUtils.newConditionVariable
import info.nightscout.comboctl.base.testUtils.runBlockingWithWatchdog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

class PairingSessionTest : TestBase() {
    enum class PacketDirection {
        SEND,
        RECEIVE
    }

    data class PairingTestSequenceEntry(val direction: PacketDirection, val packet: TransportLayer.Packet) {

        override fun toString(): String {
            return if (packet.command == TransportLayer.Command.DATA) {
                try {
                    // Use the ApplicationLayer.Packet constructor instead
                    // of the toAppLayerPacket() function, since the latter
                    // performs additional sanity checks. These checks are
                    // unnecessary here - we just want to dump the packet
                    // contents to a string.
                    val appLayerPacket = ApplicationLayer.Packet(packet)
                    "direction: $direction  app layer packet: $appLayerPacket"
                } catch (ignored: Throwable) {
                    "direction: $direction  tp layer packet: $packet"
                }
            } else
                "direction: $direction  tp layer packet: $packet"
        }
    }

    private class PairingTestComboIO(val pairingTestSequence: List<PairingTestSequenceEntry>) : ComboIO {

        private var curSequenceIndex = 0
        private val mutex = Mutex()
        private val conditionVariable = mutex.newConditionVariable()

        var expectedEndOfSequenceReached: Boolean = false
            private set

        var testErrorOccurred: Boolean = false
            private set

        // The pairingTestSequence contains entries for when a packet
        // is expected to be sent and to be received in this simulated
        // Combo<->Client communication. When the "sender" transmits
        // packets, the "receiver" is supposed to wait. This is accomplished
        // by letting getNextSequenceEntry() suspend its coroutine until
        // _another_ getNextSequenceEntry() call advances the sequence
        // so that the first call's expected packet direction matches.
        // For example: coroutine A simulates the receiver, B the sender.
        // A calls getNextSequenceEntry(). The next sequence entry has
        // "SEND" as its packet direction, meaning that at this point, the
        // sender is supposed to be active. Consequently, A is suspended
        // by getNextSequenceEntry(). B calls getNextSequenceEntry() and
        // advances the sequence until an entry is reached with packet
        // direction "RECEIVE". This now suspends B. A is woken up by
        // the barrier and resumes its work etc.
        // The "barrier" is actually a Channel which "transmits" Unit
        // values. We aren't actually interested in these "values", just
        // in the ability of Channel to suspend coroutines.
        private suspend fun getNextSequenceEntry(expectedPacketDirection: PacketDirection): PairingTestSequenceEntry {
            while (true) {
                // Suspend indefinitely if we reached the expected
                // end of sequence. See send() below for details.
                if (expectedEndOfSequenceReached) {
                    mutex.unlock()
                    try {
                        awaitCancellation()
                    } finally {
                        mutex.lock()
                    }
                }

                if (curSequenceIndex >= pairingTestSequence.size) {
                    testErrorOccurred = true
                    throw ComboException("End of test sequence unexpectedly reached")
                }

                val sequenceEntry = pairingTestSequence[curSequenceIndex]
                if (sequenceEntry.direction != expectedPacketDirection) {
                    // Wait until we get the signal from a send() or receive()
                    // call that we can resume here.
                    conditionVariable.await()
                    continue
                }

                curSequenceIndex++

                return sequenceEntry
            }
        }

        override suspend fun send(dataToSend: List<Byte>) = mutex.withLock {
            try {
                val sequenceEntry = getNextSequenceEntry(PacketDirection.SEND)
                println("Next sequence entry: $sequenceEntry")

                val expectedPacketData = sequenceEntry.packet.toByteList()
                assertEquals(expectedPacketData, dataToSend)

                // Check if this is the last packet in the sequence.
                // That's CTRL_DISCONNECT. If it is, switch to a
                // special mode that suspends receive() calls indefinitely.
                // This is necessary because the packet receiver inside
                // the transport layer IO class will keep trying to receive
                // packets from the Combo even though our sequence here
                // ended and thus has no more data that can be "received".
                if (sequenceEntry.packet.command == TransportLayer.Command.DATA) {
                    try {
                        // Don't use toAppLayerPacket() here. Instead, use
                        // the ApplicationLayer.Packet constructor directly.
                        // This way we circumvent error code checks, which
                        // are undesirable in this very case.
                        val appLayerPacket = ApplicationLayer.Packet(sequenceEntry.packet)
                        if (appLayerPacket.command == ApplicationLayer.Command.CTRL_DISCONNECT)
                            expectedEndOfSequenceReached = true
                    } catch (ignored: Throwable) {
                    }
                }

                // Signal to the other, suspended coroutine that it can resume now.
                conditionVariable.signal()
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                testErrorOccurred = true
                throw t
            }
        }

        override suspend fun receive(): List<Byte> = mutex.withLock {
            try {
                val sequenceEntry = getNextSequenceEntry(PacketDirection.RECEIVE)
                println("Next sequence entry: $sequenceEntry")

                // Signal to the other, suspended coroutine that it can resume now.
                conditionVariable.signal()
                return@withLock sequenceEntry.packet.toByteList()
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                testErrorOccurred = true
                throw t
            }
        }
    }

    @Test
    fun verifyPairingProcess() {
        // Test the pairing coroutine by feeding in data that was recorded from
        // pairing an actual Combo with Ruffy (using an nVidia SHIELD Tablet as
        // client). Check that the outgoing packets match those that Ruffy sent
        // to the Combo.

        val testBtFriendlyName = "SHIELD Tablet"
        val testPIN = PairingPIN(intArrayOf(2, 6, 0, 6, 8, 1, 9, 2, 7, 3))

        val expectedTestSequence = listOf(
            PairingTestSequenceEntry(
                PacketDirection.SEND,
                TransportLayer.Packet(
                    command = TransportLayer.Command.REQUEST_PAIRING_CONNECTION,
                    version = 0x10.toByte(),
                    sequenceBit = false,
                    reliabilityBit = false,
                    address = 0xf0.toByte(),
                    nonce = Nonce(byteArrayListOfInts(0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00)),
                    payload = byteArrayListOfInts(0xB2, 0x11),
                    machineAuthenticationCode = MachineAuthCode(byteArrayListOfInts(0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00))
                )
            ),
            PairingTestSequenceEntry(
                PacketDirection.RECEIVE,
                TransportLayer.Packet(
                    command = TransportLayer.Command.PAIRING_CONNECTION_REQUEST_ACCEPTED,
                    version = 0x10.toByte(),
                    sequenceBit = false,
                    reliabilityBit = false,
                    address = 0x0f.toByte(),
                    nonce = Nonce(byteArrayListOfInts(0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00)),
                    payload = byteArrayListOfInts(0x00, 0xF0, 0x6D),
                    machineAuthenticationCode = MachineAuthCode(byteArrayListOfInts(0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00))
                )
            ),
            PairingTestSequenceEntry(
                PacketDirection.SEND,
                TransportLayer.Packet(
                    command = TransportLayer.Command.REQUEST_KEYS,
                    version = 0x10.toByte(),
                    sequenceBit = false,
                    reliabilityBit = false,
                    address = 0xf0.toByte(),
                    nonce = Nonce(byteArrayListOfInts(0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00)),
                    payload = byteArrayListOfInts(0x81, 0x41),
                    machineAuthenticationCode = MachineAuthCode(byteArrayListOfInts(0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00))
                )
            ),
            PairingTestSequenceEntry(
                PacketDirection.SEND,
                TransportLayer.Packet(
                    command = TransportLayer.Command.GET_AVAILABLE_KEYS,
                    version = 0x10.toByte(),
                    sequenceBit = false,
                    reliabilityBit = false,
                    address = 0xf0.toByte(),
                    nonce = Nonce(byteArrayListOfInts(0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00)),
                    payload = byteArrayListOfInts(0x90, 0x71),
                    machineAuthenticationCode = MachineAuthCode(byteArrayListOfInts(0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00))
                )
            ),
            PairingTestSequenceEntry(
                PacketDirection.RECEIVE,
                TransportLayer.Packet(
                    command = TransportLayer.Command.KEY_RESPONSE,
                    version = 0x10.toByte(),
                    sequenceBit = false,
                    reliabilityBit = false,
                    address = 0x01.toByte(),
                    nonce = Nonce(byteArrayListOfInts(0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00)),
                    payload = byteArrayListOfInts(
                        0x54, 0x9E, 0xF7, 0x7D, 0x8D, 0x27, 0x48, 0x0C, 0x1D, 0x11, 0x43, 0xB8, 0xF7, 0x08, 0x92, 0x7B,
                        0xF0, 0xA3, 0x75, 0xF3, 0xB4, 0x5F, 0xE2, 0xF3, 0x46, 0x63, 0xCD, 0xDD, 0xC4, 0x96, 0x37, 0xAC
                    ),
                    machineAuthenticationCode = MachineAuthCode(byteArrayListOfInts(0x25, 0xA0, 0x26, 0x47, 0x29, 0x37, 0xFF, 0x66))
                )
            ),
            PairingTestSequenceEntry(
                PacketDirection.SEND,
                TransportLayer.Packet(
                    command = TransportLayer.Command.REQUEST_ID,
                    version = 0x10.toByte(),
                    sequenceBit = false,
                    reliabilityBit = false,
                    address = 0x10.toByte(),
                    nonce = Nonce(byteArrayListOfInts(0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00)),
                    payload = byteArrayListOfInts(
                        0x08, 0x29, 0x00, 0x00, 0x53, 0x48, 0x49, 0x45, 0x4C, 0x44, 0x20, 0x54, 0x61, 0x62, 0x6C, 0x65, 0x74
                    ),
                    machineAuthenticationCode = MachineAuthCode(byteArrayListOfInts(0x99, 0xED, 0x58, 0x29, 0x54, 0x6A, 0xBB, 0x35))
                )
            ),
            PairingTestSequenceEntry(
                PacketDirection.RECEIVE,
                TransportLayer.Packet(
                    command = TransportLayer.Command.ID_RESPONSE,
                    version = 0x10.toByte(),
                    sequenceBit = false,
                    reliabilityBit = false,
                    address = 0x01.toByte(),
                    nonce = Nonce(byteArrayListOfInts(0x02, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00)),
                    payload = byteArrayListOfInts(
                        0x59, 0x99, 0xD4, 0x01, 0x50, 0x55, 0x4D, 0x50, 0x5F, 0x31, 0x30, 0x32, 0x33, 0x30, 0x39, 0x34, 0x37
                    ),
                    machineAuthenticationCode = MachineAuthCode(byteArrayListOfInts(0x6E, 0xF4, 0x4D, 0xFE, 0x35, 0x6E, 0xFE, 0xB4))
                )
            ),
            PairingTestSequenceEntry(
                PacketDirection.SEND,
                TransportLayer.Packet(
                    command = TransportLayer.Command.REQUEST_REGULAR_CONNECTION,
                    version = 0x10.toByte(),
                    sequenceBit = false,
                    reliabilityBit = false,
                    address = 0x10.toByte(),
                    nonce = Nonce(byteArrayListOfInts(0x02, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00)),
                    payload = byteArrayListOfInts(),
                    machineAuthenticationCode = MachineAuthCode(byteArrayListOfInts(0xCF, 0xEE, 0x61, 0xF2, 0x83, 0xD3, 0xDC, 0x39))
                )
            ),
            PairingTestSequenceEntry(
                PacketDirection.RECEIVE,
                TransportLayer.Packet(
                    command = TransportLayer.Command.REGULAR_CONNECTION_REQUEST_ACCEPTED,
                    version = 0x10.toByte(),
                    sequenceBit = false,
                    reliabilityBit = false,
                    address = 0x01.toByte(),
                    nonce = Nonce(byteArrayListOfInts(0x03, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00)),
                    payload = byteArrayListOfInts(),
                    machineAuthenticationCode = MachineAuthCode(byteArrayListOfInts(0x40, 0x00, 0xB3, 0x41, 0x84, 0x55, 0x5F, 0x12))
                )
            ),
            // Application layer CTRL_CONNECT
            PairingTestSequenceEntry(
                PacketDirection.SEND,
                TransportLayer.Packet(
                    command = TransportLayer.Command.DATA,
                    version = 0x10.toByte(),
                    sequenceBit = false,
                    reliabilityBit = true,
                    address = 0x10.toByte(),
                    nonce = Nonce(byteArrayListOfInts(0x03, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00)),
                    payload = byteArrayListOfInts(0x10, 0x00, 0x55, 0x90, 0x39, 0x30, 0x00, 0x00),
                    machineAuthenticationCode = MachineAuthCode(byteArrayListOfInts(0xEF, 0xB9, 0x9E, 0xB6, 0x7B, 0x30, 0x7A, 0xCB))
                )
            ),
            // Application layer CTRL_CONNECT
            PairingTestSequenceEntry(
                PacketDirection.RECEIVE,
                TransportLayer.Packet(
                    command = TransportLayer.Command.DATA,
                    version = 0x10.toByte(),
                    sequenceBit = false,
                    reliabilityBit = true,
                    address = 0x01.toByte(),
                    nonce = Nonce(byteArrayListOfInts(0x05, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00)),
                    payload = byteArrayListOfInts(0x10, 0x00, 0x55, 0xA0, 0x00, 0x00),
                    machineAuthenticationCode = MachineAuthCode(byteArrayListOfInts(0xF4, 0x4D, 0xB8, 0xB3, 0xC1, 0x2E, 0xDE, 0x97))
                )
            ),
            // Response due to the last packet's reliability bit set to true
            PairingTestSequenceEntry(
                PacketDirection.SEND,
                TransportLayer.Packet(
                    command = TransportLayer.Command.ACK_RESPONSE,
                    version = 0x10.toByte(),
                    sequenceBit = false,
                    reliabilityBit = false,
                    address = 0x10.toByte(),
                    nonce = Nonce(byteArrayListOfInts(0x04, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00)),
                    payload = byteArrayListOfInts(),
                    machineAuthenticationCode = MachineAuthCode(byteArrayListOfInts(0x76, 0x01, 0xB6, 0xAB, 0x48, 0xDB, 0x4E, 0x87))
                )
            ),
            // Application layer CTRL_CONNECT
            PairingTestSequenceEntry(
                PacketDirection.SEND,
                TransportLayer.Packet(
                    command = TransportLayer.Command.DATA,
                    version = 0x10.toByte(),
                    sequenceBit = true,
                    reliabilityBit = true,
                    address = 0x10.toByte(),
                    nonce = Nonce(byteArrayListOfInts(0x05, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00)),
                    payload = byteArrayListOfInts(0x10, 0x00, 0x65, 0x90, 0xB7),
                    machineAuthenticationCode = MachineAuthCode(byteArrayListOfInts(0xEC, 0xA6, 0x4D, 0x59, 0x1F, 0xD3, 0xF4, 0xCD))
                )
            ),
            // Application layer CTRL_CONNECT_RESPONSE
            PairingTestSequenceEntry(
                PacketDirection.RECEIVE,
                TransportLayer.Packet(
                    command = TransportLayer.Command.DATA,
                    version = 0x10.toByte(),
                    sequenceBit = true,
                    reliabilityBit = true,
                    address = 0x01.toByte(),
                    nonce = Nonce(byteArrayListOfInts(0x07, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00)),
                    payload = byteArrayListOfInts(0x10, 0x00, 0x65, 0xA0, 0x00, 0x00, 0x01, 0x00),
                    machineAuthenticationCode = MachineAuthCode(byteArrayListOfInts(0x9D, 0xB3, 0x3F, 0x84, 0x87, 0x49, 0xE3, 0xAC))
                )
            ),
            // Response due to the last packet's reliability bit set to true
            PairingTestSequenceEntry(
                PacketDirection.SEND,
                TransportLayer.Packet(
                    command = TransportLayer.Command.ACK_RESPONSE,
                    version = 0x10.toByte(),
                    sequenceBit = true,
                    reliabilityBit = false,
                    address = 0x10.toByte(),
                    nonce = Nonce(byteArrayListOfInts(0x06, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00)),
                    payload = byteArrayListOfInts(),
                    machineAuthenticationCode = MachineAuthCode(byteArrayListOfInts(0x15, 0xA9, 0x9A, 0x64, 0x9C, 0x57, 0xD2, 0x72))
                )
            ),
            // Application layer CTRL_BIND
            PairingTestSequenceEntry(
                PacketDirection.SEND,
                TransportLayer.Packet(
                    command = TransportLayer.Command.DATA,
                    version = 0x10.toByte(),
                    sequenceBit = false,
                    reliabilityBit = true,
                    address = 0x10.toByte(),
                    nonce = Nonce(byteArrayListOfInts(0x07, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00)),
                    payload = byteArrayListOfInts(0x10, 0x00, 0x95, 0x90, 0x48),
                    machineAuthenticationCode = MachineAuthCode(byteArrayListOfInts(0x39, 0x8E, 0x57, 0xCC, 0xEE, 0x68, 0x41, 0xBB))
                )
            ),
            // Application layer CTRL_BIND_RESPONSE
            PairingTestSequenceEntry(
                PacketDirection.RECEIVE,
                TransportLayer.Packet(
                    command = TransportLayer.Command.DATA,
                    version = 0x10.toByte(),
                    sequenceBit = false,
                    reliabilityBit = true,
                    address = 0x01.toByte(),
                    nonce = Nonce(byteArrayListOfInts(0x09, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00)),
                    payload = byteArrayListOfInts(0x10, 0x00, 0x95, 0xA0, 0x00, 0x00, 0x48),
                    machineAuthenticationCode = MachineAuthCode(byteArrayListOfInts(0xF0, 0x49, 0xD4, 0x91, 0x01, 0x26, 0x33, 0xEF))
                )
            ),
            // Response due to the last packet's reliability bit set to true
            PairingTestSequenceEntry(
                PacketDirection.SEND,
                TransportLayer.Packet(
                    command = TransportLayer.Command.ACK_RESPONSE,
                    version = 0x10.toByte(),
                    sequenceBit = false,
                    reliabilityBit = false,
                    address = 0x10.toByte(),
                    nonce = Nonce(byteArrayListOfInts(0x08, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00)),
                    payload = byteArrayListOfInts(),
                    machineAuthenticationCode = MachineAuthCode(byteArrayListOfInts(0x38, 0x3D, 0x52, 0x56, 0x73, 0xBF, 0x59, 0xD8))
                )
            ),
            PairingTestSequenceEntry(
                PacketDirection.SEND,
                TransportLayer.Packet(
                    command = TransportLayer.Command.REQUEST_REGULAR_CONNECTION,
                    version = 0x10.toByte(),
                    sequenceBit = false,
                    reliabilityBit = false,
                    address = 0x10.toByte(),
                    nonce = Nonce(byteArrayListOfInts(0x09, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00)),
                    payload = byteArrayListOfInts(),
                    machineAuthenticationCode = MachineAuthCode(byteArrayListOfInts(0x1D, 0xD4, 0xD5, 0xC6, 0x03, 0x3E, 0x0A, 0xBE))
                )
            ),
            PairingTestSequenceEntry(
                PacketDirection.RECEIVE,
                TransportLayer.Packet(
                    command = TransportLayer.Command.REGULAR_CONNECTION_REQUEST_ACCEPTED,
                    version = 0x10.toByte(),
                    sequenceBit = false,
                    reliabilityBit = false,
                    address = 0x01.toByte(),
                    nonce = Nonce(byteArrayListOfInts(0x0A, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00)),
                    payload = byteArrayListOfInts(),
                    machineAuthenticationCode = MachineAuthCode(byteArrayListOfInts(0x34, 0xD2, 0x8B, 0x40, 0x27, 0x44, 0x82, 0x89))
                )
            ),
            // Application layer CTRL_DISCONNECT
            PairingTestSequenceEntry(
                PacketDirection.SEND,
                TransportLayer.Packet(
                    command = TransportLayer.Command.DATA,
                    version = 0x10.toByte(),
                    sequenceBit = false,
                    reliabilityBit = true,
                    address = 0x10.toByte(),
                    nonce = Nonce(byteArrayListOfInts(0x0A, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00)),
                    payload = byteArrayListOfInts(0x10, 0x00, 0x5A, 0x00, 0x03, 0x00),
                    machineAuthenticationCode = MachineAuthCode(byteArrayListOfInts(0x9D, 0xF4, 0x0F, 0x24, 0x44, 0xE3, 0x52, 0x03))
                )
            )
        )

        val testIO = PairingTestComboIO(expectedTestSequence)
        val testPumpStateStore = TestPumpStateStore()
        val testBluetoothDevice = TestBluetoothDevice(testIO)
        val pumpIO = PumpIO(testPumpStateStore, testBluetoothDevice, onNewDisplayFrame = {}, onPacketReceiverException = {})

        runBlockingWithWatchdog(12000) {
            pumpIO.performPairing(
                testBtFriendlyName,
                null
            ) { _, _ -> testPIN }
        }

        if (testIO.testErrorOccurred)
            fail("Failure in background coroutine")
    }
}
