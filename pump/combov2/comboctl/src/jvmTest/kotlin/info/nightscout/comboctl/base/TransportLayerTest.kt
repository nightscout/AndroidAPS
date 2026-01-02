package info.nightscout.comboctl.base

import app.aaps.shared.tests.TestBase
import info.nightscout.comboctl.base.testUtils.TestComboIO
import info.nightscout.comboctl.base.testUtils.TestPumpStateStore
import info.nightscout.comboctl.base.testUtils.WatchdogTimeoutException
import info.nightscout.comboctl.base.testUtils.coroutineScopeWithWatchdog
import info.nightscout.comboctl.base.testUtils.runBlockingWithWatchdog
import kotlinx.coroutines.Job
import kotlinx.datetime.UtcOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TransportLayerTest : TestBase() {

    @Test
    fun parsePacketData() {
        // Test the packet parser by parsing hardcoded packet data
        // and verifying the individual packet property values.

        val packetDataWithCRCPayload = byteArrayListOfInts(
            0x10, // version
            0x09, // request_pairing_connection command (sequence and data reliability bit set to 0)
            0x02, 0x00, // payload length
            0xF0, // address
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, // nonce
            0x99, 0x44, // payload
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00 // nullbyte MAC
        )

        // The actual parsing.
        val packet = packetDataWithCRCPayload.toTransportLayerPacket()

        // Check the individual properties.

        assertEquals(0x10, packet.version)
        assertFalse(packet.sequenceBit)
        assertFalse(packet.reliabilityBit)
        assertEquals(TransportLayer.Command.REQUEST_PAIRING_CONNECTION, packet.command)
        assertEquals(0xF0.toByte(), packet.address)
        assertEquals(Nonce.nullNonce(), packet.nonce)
        assertEquals(byteArrayListOfInts(0x99, 0x44), packet.payload)
        assertEquals(NullMachineAuthCode, packet.machineAuthenticationCode)
    }

    @Test
    fun createPacketData() {
        // Create packet, and check that it is correctly converted to a byte list.

        val packet = TransportLayer.Packet(
            command = TransportLayer.Command.REQUEST_PAIRING_CONNECTION,
            version = 0x42,
            sequenceBit = true,
            reliabilityBit = false,
            address = 0x45,
            nonce = Nonce(byteArrayListOfInts(0x0A, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x0B)),
            payload = byteArrayListOfInts(0x50, 0x60, 0x70),
            machineAuthenticationCode = MachineAuthCode(byteArrayListOfInts(0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08))
        )

        val byteList = packet.toByteList()

        val expectedPacketData = byteArrayListOfInts(
            0x42, // version
            0x80 or 0x09, // command 0x09 with sequence bit enabled
            0x03, 0x00, // payload length
            0x45, // address,
            0x0A, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x0B, // nonce
            0x50, 0x60, 0x70, // payload
            0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08 // MAC
        )

        assertEquals(byteList, expectedPacketData)
    }

    @Test
    fun verifyPacketDataIntegrityWithCRC() {
        // Create packet and verify that the CRC check detects data corruption.

        val packet = TransportLayer.Packet(
            command = TransportLayer.Command.REQUEST_PAIRING_CONNECTION,
            version = 0x42,
            sequenceBit = true,
            reliabilityBit = false,
            address = 0x45,
            nonce = Nonce(byteArrayListOfInts(0x0A, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x0B)),
            machineAuthenticationCode = MachineAuthCode(byteArrayListOfInts(0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00))
        )

        // Check that the computed CRC is correct.
        packet.computeCRC16Payload()
        val expectedCRCPayload = byteArrayListOfInts(0xE1, 0x7B)
        assertEquals(expectedCRCPayload, packet.payload)

        // The CRC should match, since it was just computed.
        assertTrue(packet.verifyCRC16Payload())

        // Simulate data corruption by altering the CRC itself.
        // This should produce a CRC mismatch, since the check
        // will recompute the CRC from the header data.
        packet.payload[0] = (packet.payload[0].toPosInt() xor 0xFF).toByte()
        assertFalse(packet.verifyCRC16Payload())
    }

    @Test
    fun verifyPacketDataIntegrityWithMAC() {
        // Create packet and verify that the MAC check detects data corruption.

        val key = ByteArray(CIPHER_KEY_SIZE).apply { fill('0'.code.toByte()) }
        val cipher = Cipher(key)

        val packet = TransportLayer.Packet(
            command = TransportLayer.Command.REQUEST_PAIRING_CONNECTION,
            version = 0x42,
            sequenceBit = true,
            reliabilityBit = false,
            address = 0x45,
            nonce = Nonce(byteArrayListOfInts(0x0A, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x0B)),
            payload = byteArrayListOfInts(0x00, 0x00)
        )

        // Check that the computed MAC is correct.
        packet.authenticate(cipher)
        val expectedMAC = MachineAuthCode(byteArrayListOfInts(0x00, 0xC5, 0x48, 0xB3, 0xA8, 0xE6, 0x97, 0x76))
        assertEquals(expectedMAC, packet.machineAuthenticationCode)

        // The MAC should match, since it was just computed.
        assertTrue(packet.verifyAuthentication(cipher))

        // Simulate data corruption by altering the payload.
        // This should produce a MAC mismatch.
        packet.payload[0] = 0xFF.toByte()
        assertFalse(packet.verifyAuthentication(cipher))
    }

    @Test
    fun checkBasicTransportLayerSequence() {
        // Run a basic sequence of IO operations and verify
        // that they produce the expected results. We use
        // the connection setup sequence, since this one does
        // not require an existing pump state.

        // The calls must be run from within a coroutine scope.
        // Starts a blocking scope with a watchdog that fails
        // the test if it does not finish within 5 seconds
        // (in case the tested code hangs).
        runBlockingWithWatchdog(12000) {
            val testPumpStateStore = TestPumpStateStore()
            val testComboIO = TestComboIO()
            val testBluetoothAddress = BluetoothAddress(byteArrayListOfInts(1, 2, 3, 4, 5, 6))
            val tpLayerIO = TransportLayer.IO(testPumpStateStore, testBluetoothAddress, testComboIO) {}

            // We'll simulate sending a REQUEST_PAIRING_CONNECTION packet and
            // receiving a PAIRING_CONNECTION_REQUEST_ACCEPTED packet.

            val pairingConnectionRequestAcceptedPacket = TransportLayer.Packet(
                command = TransportLayer.Command.PAIRING_CONNECTION_REQUEST_ACCEPTED,
                version = 0x10.toByte(),
                sequenceBit = false,
                reliabilityBit = false,
                address = 0x0f.toByte(),
                nonce = Nonce(byteArrayListOfInts(0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00)),
                payload = byteArrayListOfInts(0x00, 0xF0, 0x6D),
                machineAuthenticationCode = MachineAuthCode(byteArrayListOfInts(0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00))
            )

            // Set up transport layer IO and forward all packets to receive() calls.
            tpLayerIO.start(packetReceiverScope = this) { TransportLayer.IO.ReceiverBehavior.FORWARD_PACKET }

            // Send a REQUEST_PAIRING_CONNECTION and simulate Combo reaction
            // to it by feeding the simulated Combo response into the IO object.
            tpLayerIO.send(TransportLayer.createRequestPairingConnectionPacketInfo())
            testComboIO.feedIncomingData(pairingConnectionRequestAcceptedPacket.toByteList())
            // Receive the simulated response.
            val receivedPacket = tpLayerIO.receive()

            tpLayerIO.stop()

            // IO is done. We expect 1 packet to have been sent by the transport layer IO.
            // Also, we expect to have received the PAIRING_CONNECTION_REQUEST_ACCEPTED
            // packet. Check for this, and verify that the sent packet data and the
            // received packet data are correct.

            val expectedReqPairingConnectionPacket = TransportLayer.Packet(
                command = TransportLayer.Command.REQUEST_PAIRING_CONNECTION,
                version = 0x10.toByte(),
                sequenceBit = false,
                reliabilityBit = false,
                address = 0xf0.toByte(),
                nonce = Nonce(byteArrayListOfInts(0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00)),
                payload = byteArrayListOfInts(0xB2, 0x11),
                machineAuthenticationCode = MachineAuthCode(byteArrayListOfInts(0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00))
            )

            assertEquals(1, testComboIO.sentPacketData.size)
            assertEquals(expectedReqPairingConnectionPacket.toByteList(), testComboIO.sentPacketData[0])

            assertEquals(pairingConnectionRequestAcceptedPacket.toByteList(), receivedPacket.toByteList())
        }
    }

    @Test
    fun checkPacketReceiverExceptionHandling() {
        // Test how exceptions in TransportLayer.IO are handled.
        // We expect that the coroutine inside IO is stopped by
        // an exception thrown inside.
        // Subsequent send and receive call attempts need to throw
        // a PacketReceiverException which in turn contains the
        // exception that caused the failure.

        runBlockingWithWatchdog(12000) {
            val testPumpStateStore = TestPumpStateStore()
            val testComboIO = TestComboIO()
            val testBluetoothAddress = BluetoothAddress(byteArrayListOfInts(1, 2, 3, 4, 5, 6))
            var expectedError: Throwable? = null
            val waitingForExceptionJob = Job()
            val tpLayerIO = TransportLayer.IO(testPumpStateStore, testBluetoothAddress, testComboIO) { exception ->
                expectedError = exception
                waitingForExceptionJob.complete()
            }

            // Initialize pump state for the ERROR_RESPONSE packet, since
            // that one is authenticated via its MAC and this pump state.

            val testDecryptedCPKey =
                byteArrayListOfInts(0x5a, 0x25, 0x0b, 0x75, 0xa9, 0x02, 0x21, 0xfa, 0xab, 0xbd, 0x36, 0x4d, 0x5c, 0xb8, 0x37, 0xd7)
            val testDecryptedPCKey =
                byteArrayListOfInts(0x2a, 0xb0, 0xf2, 0x67, 0xc2, 0x7d, 0xcf, 0xaa, 0x32, 0xb2, 0x48, 0x94, 0xe1, 0x6d, 0xe9, 0x5c)
            val testAddress = 0x10.toByte()

            testPumpStateStore.createPumpState(
                testBluetoothAddress,
                InvariantPumpData(
                    clientPumpCipher = Cipher(testDecryptedCPKey.toByteArray()),
                    pumpClientCipher = Cipher(testDecryptedPCKey.toByteArray()),
                    keyResponseAddress = testAddress,
                    pumpID = "testPump"
                ),
                UtcOffset.ZERO, CurrentTbrState.NoTbrOngoing
            )

            val errorResponsePacket = TransportLayer.Packet(
                command = TransportLayer.Command.ERROR_RESPONSE,
                version = 0x10.toByte(),
                sequenceBit = false,
                reliabilityBit = false,
                address = 0x01.toByte(),
                nonce = Nonce(byteArrayListOfInts(0x02, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00)),
                payload = byteArrayListOfInts(0x0F)
            )
            errorResponsePacket.authenticate(Cipher(testDecryptedPCKey.toByteArray()))

            // Start IO, and "receive" the error response packet (which
            // normally would be sent by the Combo to the client) by feeding
            // it into the test IO object. Since this packet contains an
            // error report by the simulated Combo, an exception is thrown
            // in the packet receiver coroutine in the IO class.
            tpLayerIO.start(packetReceiverScope = this) { TransportLayer.IO.ReceiverBehavior.FORWARD_PACKET }
            testComboIO.feedIncomingData(errorResponsePacket.toByteList())

            // Wait until an exception is thrown in the packet receiver
            // and we get notified about it.
            waitingForExceptionJob.join()
            println(
                "Exception thrown by in packet receiver (this exception was expected by the test): $expectedError"
            )
            assertNotNull(expectedError)
            assertIs<TransportLayer.ErrorResponseException>(expectedError!!.cause)

            // At this point, the packet receiver is not running anymore
            // due to the exception. Attempts at sending and receiving
            // must fail and throw the exception that caused the failure
            // in the packet receiver. This allows for propagating the
            // error in a POSIX-esque style, where return codes inform
            // about a failure that previously happened.

            val exceptionThrownBySendCall = assertFailsWith<TransportLayer.PacketReceiverException> {
                // The actual packet does not matter here. We just
                // use createRequestPairingConnectionPacketInfo() to
                // be able to use send(). Might as well use any
                // other create*PacketInfo function.
                tpLayerIO.send(TransportLayer.createRequestPairingConnectionPacketInfo())
            }
            println(
                "Exception thrown by send() call (this exception was expected by the test): $exceptionThrownBySendCall"
            )
            assertIs<TransportLayer.ErrorResponseException>(exceptionThrownBySendCall.cause)

            val exceptionThrownByReceiveCall = assertFailsWith<TransportLayer.PacketReceiverException> {
                tpLayerIO.receive()
            }
            println(
                "Exception thrown by receive() call (this exception was expected by the test): $exceptionThrownByReceiveCall"
            )
            assertIs<TransportLayer.ErrorResponseException>(exceptionThrownByReceiveCall.cause)

            tpLayerIO.stop()
        }
    }

    @Test
    fun checkCustomIncomingPacketFiltering() {
        // Test the custom incoming packet processing feature and
        // its ability to drop packets. We simulate 3 incoming packets,
        // one of which is a DATA packet. This one we want to drop
        // before it ever reaches a receive() call. Consequently, we
        // expect only 2 packets to ever reach receive(), while a third
        // attempt at receiving should cause that third call to be
        // suspended indefinitely. Also, we expect our tpLayerIO.start()
        // callback to see all 3 packets. We count the number of DATA
        // packets observed to confirm that the expected single DATA
        // paket is in fact received by the TransportLayer IO.

        runBlockingWithWatchdog(12000) {
            // Setup.

            val testPumpStateStore = TestPumpStateStore()
            val testBluetoothAddress = BluetoothAddress(byteArrayListOfInts(1, 2, 3, 4, 5, 6))
            val testComboIO = TestComboIO()

            val tpLayerIO = TransportLayer.IO(testPumpStateStore, testBluetoothAddress, testComboIO) {}

            val testDecryptedCPKey =
                byteArrayListOfInts(0x5a, 0x25, 0x0b, 0x75, 0xa9, 0x02, 0x21, 0xfa, 0xab, 0xbd, 0x36, 0x4d, 0x5c, 0xb8, 0x37, 0xd7)
            val testDecryptedPCKey =
                byteArrayListOfInts(0x2a, 0xb0, 0xf2, 0x67, 0xc2, 0x7d, 0xcf, 0xaa, 0x32, 0xb2, 0x48, 0x94, 0xe1, 0x6d, 0xe9, 0x5c)
            val testAddress = 0x10.toByte()

            testPumpStateStore.createPumpState(
                testBluetoothAddress,
                InvariantPumpData(
                    clientPumpCipher = Cipher(testDecryptedCPKey.toByteArray()),
                    pumpClientCipher = Cipher(testDecryptedPCKey.toByteArray()),
                    keyResponseAddress = testAddress,
                    pumpID = "testPump"
                ),
                UtcOffset.ZERO, CurrentTbrState.NoTbrOngoing
            )

            // The packets that our simulated Combo transmits to our client.
            val customDataPackets = listOf(
                TransportLayer.Packet(
                    command = TransportLayer.Command.REGULAR_CONNECTION_REQUEST_ACCEPTED,
                    version = 0x10.toByte(),
                    sequenceBit = false,
                    reliabilityBit = false,
                    address = 0x01.toByte(),
                    nonce = Nonce(byteArrayListOfInts(0x02, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00)),
                    payload = byteArrayListOfInts(0, 1, 2, 3)
                ),
                TransportLayer.Packet(
                    command = TransportLayer.Command.DATA,
                    version = 0x10.toByte(),
                    sequenceBit = false,
                    reliabilityBit = false,
                    address = 0x01.toByte(),
                    nonce = Nonce(byteArrayListOfInts(0x03, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00)),
                    payload = byteArrayListOfInts(1, 2, 3)
                ),
                TransportLayer.Packet(
                    command = TransportLayer.Command.REGULAR_CONNECTION_REQUEST_ACCEPTED,
                    version = 0x10.toByte(),
                    sequenceBit = false,
                    reliabilityBit = false,
                    address = 0x01.toByte(),
                    nonce = Nonce(byteArrayListOfInts(0x04, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00)),
                    payload = byteArrayListOfInts(0, 1, 2, 3)
                )
            )
            customDataPackets.forEach {
                it.authenticate(Cipher(testDecryptedPCKey.toByteArray()))
            }

            var numReceivedDataPackets = 0
            tpLayerIO.start(packetReceiverScope = this) { tpLayerPacket ->
                if (tpLayerPacket.command == TransportLayer.Command.DATA) {
                    numReceivedDataPackets++
                    TransportLayer.IO.ReceiverBehavior.DROP_PACKET
                } else
                    TransportLayer.IO.ReceiverBehavior.FORWARD_PACKET
            }

            customDataPackets.forEach {
                testComboIO.feedIncomingData(it.toByteList())
            }

            // Check that we received 2 non-DATA packets.
            for (i in 0 until 2) {
                val tpLayerPacket = tpLayerIO.receive()
                assertNotEquals(TransportLayer.Command.DATA, tpLayerPacket.command)
            }

            // An attempt at receiving another packet should never
            // finish, since any packet other than the 2 non-DATA
            // ones must have been filtered out.
            assertFailsWith<WatchdogTimeoutException> {
                coroutineScopeWithWatchdog(1000) {
                    tpLayerIO.receive()
                }
            }

            tpLayerIO.stop()

            assertEquals(1, numReceivedDataPackets)
        }
    }
}
