package info.nightscout.comboctl.base

import app.aaps.shared.tests.TestBase
import info.nightscout.comboctl.base.testUtils.TestBluetoothDevice
import info.nightscout.comboctl.base.testUtils.TestComboIO
import info.nightscout.comboctl.base.testUtils.TestPumpStateStore
import info.nightscout.comboctl.base.testUtils.TestRefPacketItem
import info.nightscout.comboctl.base.testUtils.checkTestPacketSequence
import info.nightscout.comboctl.base.testUtils.produceTpLayerPacket
import info.nightscout.comboctl.base.testUtils.runBlockingWithWatchdog
import kotlinx.coroutines.delay
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.UtcOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PumpIOTest : TestBase() {
    // Common test code.
    class TestStates(setupInvariantPumpData: Boolean) {

        var testPumpStateStore: TestPumpStateStore
        val testBluetoothDevice: TestBluetoothDevice
        var testIO: TestComboIO
        var pumpIO: PumpIO

        init {
            Logger.threshold = LogLevel.VERBOSE

            // Set up the invariant pump data to be able to test regular connections.

            testPumpStateStore = TestPumpStateStore()

            testIO = TestComboIO()
            testIO.respondToRTKeypressWithConfirmation = true

            testBluetoothDevice = TestBluetoothDevice(testIO)

            if (setupInvariantPumpData) {
                val invariantPumpData = InvariantPumpData(
                    keyResponseAddress = 0x10,
                    clientPumpCipher = Cipher(
                        byteArrayOfInts(
                            0x5a, 0x25, 0x0b, 0x75, 0xa9, 0x02, 0x21, 0xfa,
                            0xab, 0xbd, 0x36, 0x4d, 0x5c, 0xb8, 0x37, 0xd7
                        )
                    ),
                    pumpClientCipher = Cipher(
                        byteArrayOfInts(
                            0x2a, 0xb0, 0xf2, 0x67, 0xc2, 0x7d, 0xcf, 0xaa,
                            0x32, 0xb2, 0x48, 0x94, 0xe1, 0x6d, 0xe9, 0x5c
                        )
                    ),
                    pumpID = "testPump"
                )
                testPumpStateStore.createPumpState(testBluetoothDevice.address, invariantPumpData, UtcOffset.ZERO, CurrentTbrState.NoTbrOngoing)
                testIO.pumpClientCipher = invariantPumpData.pumpClientCipher
            }

            pumpIO = PumpIO(testPumpStateStore, testBluetoothDevice, onNewDisplayFrame = {}, onPacketReceiverException = {})
        }

        // Tests that a long button press is handled correctly.
        // We expect an initial RT_BUTTON_STATUS packet with its
        // buttonStatusChanged flag set to true, followed by
        // a series of similar packet with the buttonStatusChanged
        // flag set to false, and finished by an RT_BUTTON_STATUS
        // packet whose button code is NO_BUTTON.
        fun checkLongRTButtonPressPacketSequence(appLayerButton: ApplicationLayer.RTButton) {
            assertTrue(
                testIO.sentPacketData.size >= 3,
                "Expected at least 3 items in sentPacketData list, got ${testIO.sentPacketData.size}"
            )

            checkRTButtonStatusPacketData(
                testIO.sentPacketData.first(),
                appLayerButton,
                true
            )
            testIO.sentPacketData.removeAt(0)

            checkDisconnectPacketData(testIO.sentPacketData.last())
            testIO.sentPacketData.removeAt(testIO.sentPacketData.size - 1)

            checkRTButtonStatusPacketData(
                testIO.sentPacketData.last(),
                ApplicationLayer.RTButton.NO_BUTTON,
                true
            )
            testIO.sentPacketData.removeAt(testIO.sentPacketData.size - 1)

            for (packetData in testIO.sentPacketData) {
                checkRTButtonStatusPacketData(
                    packetData,
                    appLayerButton,
                    false
                )
            }
        }

        // Feeds initial connection setup packets into the test IO
        // that would normally be sent by the Combo during connection
        // setup. In that setup, the Combo is instructed to switch to
        // the RT mode, so this also feeds a CTRL_ACTIVATE_SERVICE_RESPONSE
        // packet into the IO.
        suspend fun feedInitialPackets() {
            val invariantPumpData = testPumpStateStore.getInvariantPumpData(testBluetoothDevice.address)

            testIO.feedIncomingData(
                produceTpLayerPacket(
                    TransportLayer.OutgoingPacketInfo(
                        command = TransportLayer.Command.REGULAR_CONNECTION_REQUEST_ACCEPTED
                    ),
                    invariantPumpData.pumpClientCipher
                ).toByteList()
            )

            testIO.feedIncomingData(
                produceTpLayerPacket(
                    ApplicationLayer.Packet(
                        command = ApplicationLayer.Command.CTRL_CONNECT_RESPONSE
                    ).toTransportLayerPacketInfo(),
                    invariantPumpData.pumpClientCipher
                ).toByteList()
            )

            testIO.feedIncomingData(
                produceTpLayerPacket(
                    ApplicationLayer.Packet(
                        command = ApplicationLayer.Command.CTRL_ACTIVATE_SERVICE_RESPONSE,
                        payload = byteArrayListOfInts(1, 2, 3, 4, 5)
                    ).toTransportLayerPacketInfo(),
                    invariantPumpData.pumpClientCipher
                ).toByteList()
            )
        }

        // This removes initial connection setup packets that are
        // normally sent to the Combo. Outgoing packets are recorded
        // in the testIO.sentPacketData list. In the tests here, we
        // are not interested in these initial packets. This function
        // gets rid of them.
        fun checkAndRemoveInitialSentPackets() {
            val expectedInitialPacketSequence = listOf(
                TestRefPacketItem.TransportLayerPacketItem(
                    TransportLayer.createRequestRegularConnectionPacketInfo()
                ),
                TestRefPacketItem.ApplicationLayerPacketItem(
                    ApplicationLayer.createCTRLConnectPacket()
                ),
                TestRefPacketItem.ApplicationLayerPacketItem(
                    ApplicationLayer.createCTRLActivateServicePacket(ApplicationLayer.ServiceID.RT_MODE)
                )
            )

            checkTestPacketSequence(expectedInitialPacketSequence, testIO.sentPacketData)
            for (i in expectedInitialPacketSequence.indices)
                testIO.sentPacketData.removeAt(0)
        }

        fun checkRTButtonStatusPacketData(
            packetData: List<Byte>,
            rtButton: ApplicationLayer.RTButton,
            buttonStatusChangedFlag: Boolean
        ) {
            val appLayerPacket = ApplicationLayer.Packet(packetData.toTransportLayerPacket())
            assertEquals(ApplicationLayer.Command.RT_BUTTON_STATUS, appLayerPacket.command, "Application layer packet command mismatch")
            assertEquals(rtButton.id.toByte(), appLayerPacket.payload[2], "RT_BUTTON_STATUS button byte mismatch")
            assertEquals((if (buttonStatusChangedFlag) 0xB7 else 0x48).toByte(), appLayerPacket.payload[3], "RT_BUTTON_STATUS status flag mismatch")
        }

        fun checkDisconnectPacketData(packetData: List<Byte>) {
            val appLayerPacket = ApplicationLayer.Packet(packetData.toTransportLayerPacket())
            assertEquals(ApplicationLayer.Command.CTRL_DISCONNECT, appLayerPacket.command, "Application layer packet command mismatch")
        }
    }

    @Test
    fun checkShortButtonPress() {
        // Check that a short button press is handled correctly.
        // Short button presses are performed by sending two RT_BUTTON_STATUS
        // packets. The first one contains the actual button code, the second
        // one contains a NO_BUTTON code. We send two short button presses.
        // This amounts to 2 pairs of RT_BUTTON_STATUS packets plus the
        // final CTRL_DISCONNECT packets, for a total of 5 packets.

        runBlockingWithWatchdog(12000) {
            val testStates = TestStates(true)
            val pumpIO = testStates.pumpIO
            val testIO = testStates.testIO

            testStates.feedInitialPackets()

            pumpIO.connect(runHeartbeat = false)

            pumpIO.sendShortRTButtonPress(ApplicationLayer.RTButton.UP)
            delay(200L)

            pumpIO.sendShortRTButtonPress(ApplicationLayer.RTButton.UP)
            delay(200L)

            pumpIO.disconnect()

            testStates.checkAndRemoveInitialSentPackets()

            // 4 RT packets from the sendShortRTButtonPress() calls
            // above plus the final CTRL_DISCONNECT packet -> 5 packets.
            assertEquals(5, testIO.sentPacketData.size)

            // The two RT_BUTTON_STATUS packets (first one with button
            // code UP, second one with button code NO_BUTTON) that
            // were sent by the first sendShortRTButtonPress() call.

            testStates.checkRTButtonStatusPacketData(
                testIO.sentPacketData[0],
                ApplicationLayer.RTButton.UP,
                true
            )
            testStates.checkRTButtonStatusPacketData(
                testIO.sentPacketData[1],
                ApplicationLayer.RTButton.NO_BUTTON,
                true
            )

            // The two RT_BUTTON_STATUS packets (first one with button
            // code UP, second one with button code NO_BUTTON) that
            // were sent by the second sendShortRTButtonPress() call.

            testStates.checkRTButtonStatusPacketData(
                testIO.sentPacketData[2],
                ApplicationLayer.RTButton.UP,
                true
            )
            testStates.checkRTButtonStatusPacketData(
                testIO.sentPacketData[3],
                ApplicationLayer.RTButton.NO_BUTTON,
                true
            )

            // The final CTRL_DISCONNECT packet.
            testStates.checkDisconnectPacketData(testIO.sentPacketData[4])
        }
    }

    @Test
    fun checkUpDownLongRTButtonPress() {
        // Basic long press test. After connecting to the simulated Combo,
        // the UP button is long-pressed. Then, the client reconnects to the
        // Combo, and the same is done with the DOWN button. This tests that
        // no states remain from a previous connection, and also of course
        // tests that long-presses are handled correctly.
        // The connection is established with the RT Keep-alive loop disabled
        // to avoid having to deal with RT_KEEP_ALIVE packets in the
        // testIO.sentPacketData list.

        runBlockingWithWatchdog(12000) {
            val testStates = TestStates(true)
            val testIO = testStates.testIO
            val pumpIO = testStates.pumpIO

            // First, test long UP button press.

            testStates.feedInitialPackets()

            pumpIO.connect(runHeartbeat = false)

            var counter = 0
            pumpIO.startLongRTButtonPress(ApplicationLayer.RTButton.UP) {
                // Return true the first time, false the second time.
                // This way, we inform the function that it should
                // send a button status to the Combo once (= when
                // we return true).
                counter++
                counter <= 1
            }
            pumpIO.waitForLongRTButtonPressToFinish()

            pumpIO.disconnect()

            testStates.checkAndRemoveInitialSentPackets()
            testStates.checkLongRTButtonPressPacketSequence(ApplicationLayer.RTButton.UP)

            // Next, test long DOWN button press. Use stopLongRTButtonPress()
            // instead of waitForLongRTButtonPressToFinish() here to also
            // test that function. Waiting for a while and calling it should
            // amount to the same behavior as calling waitForLongRTButtonPressToFinish().

            testIO.resetSentPacketData()
            testIO.resetIncomingPacketDataChannel()

            testStates.feedInitialPackets()

            pumpIO.connect(runHeartbeat = false)

            pumpIO.startLongRTButtonPress(ApplicationLayer.RTButton.DOWN)
            delay(500L)
            pumpIO.stopLongRTButtonPress()
            delay(500L)

            pumpIO.disconnect()

            testStates.checkAndRemoveInitialSentPackets()
            testStates.checkLongRTButtonPressPacketSequence(ApplicationLayer.RTButton.DOWN)
        }
    }

    @Test
    fun checkDoubleLongButtonPress() {
        // Check what happens if the user issues redundant startLongRTButtonPress()
        // calls. The second call here should be ignored.

        runBlockingWithWatchdog(12000) {
            val testStates = TestStates(true)
            val pumpIO = testStates.pumpIO

            testStates.feedInitialPackets()

            pumpIO.connect(runHeartbeat = false)

            var counter = 0
            pumpIO.startLongRTButtonPress(ApplicationLayer.RTButton.UP) {
                // Return true the first time, false the second time.
                // This way, we inform the function that it should
                // send a button status to the Combo once (= when
                // we return true).
                counter++
                counter <= 1
            }
            pumpIO.startLongRTButtonPress(ApplicationLayer.RTButton.UP)

            pumpIO.waitForLongRTButtonPressToFinish()

            pumpIO.disconnect()

            testStates.checkAndRemoveInitialSentPackets()
            testStates.checkLongRTButtonPressPacketSequence(ApplicationLayer.RTButton.UP)
        }
    }

    @Test
    fun checkDoubleLongButtonRelease() {
        // Check what happens if the user issues redundant waitForLongRTButtonPressToFinish()
        // calls. The second call here should be ignored.

        runBlockingWithWatchdog(12000) {
            val testStates = TestStates(true)
            val pumpIO = testStates.pumpIO

            testStates.feedInitialPackets()

            pumpIO.connect(runHeartbeat = false)

            var counter = 0
            pumpIO.startLongRTButtonPress(ApplicationLayer.RTButton.UP) {
                // Return true the first time, false the second time.
                // This way, we inform the function that it should
                // send a button status to the Combo once (= when
                // we return true).
                counter++
                counter <= 1
            }

            pumpIO.waitForLongRTButtonPressToFinish()
            pumpIO.waitForLongRTButtonPressToFinish()

            pumpIO.disconnect()

            testStates.checkAndRemoveInitialSentPackets()
            testStates.checkLongRTButtonPressPacketSequence(ApplicationLayer.RTButton.UP)
        }
    }

    @Test
    fun checkRTSequenceNumberAssignment() {
        // Check that PumpIO fills in correctly  the RT sequence
        // in outgoing RT packets. We use sendShortRTButtonPress()
        // for this purpose, since each call produces 2 RT packets.
        // We look at the transmitted RT packets and check if their
        // RT sequence numbers are monotonically increasing, which
        // is the correct behavior.

        runBlockingWithWatchdog(12000) {
            val testStates = TestStates(true)
            val pumpIO = testStates.pumpIO
            val testIO = testStates.testIO

            testStates.feedInitialPackets()

            pumpIO.connect(runHeartbeat = false)

            pumpIO.sendShortRTButtonPress(ApplicationLayer.RTButton.UP)
            delay(200L)
            pumpIO.sendShortRTButtonPress(ApplicationLayer.RTButton.UP)
            delay(200L)
            pumpIO.sendShortRTButtonPress(ApplicationLayer.RTButton.UP)
            delay(200L)

            pumpIO.disconnect()

            testStates.checkAndRemoveInitialSentPackets()

            // 6 RT packets from the sendShortRTButtonPress() calls
            // above plus the final CTRL_DISCONNECT packet -> 7 packets.
            assertEquals(7, testIO.sentPacketData.size)

            // The 3 sendShortRTButtonPress() calls each sent two
            // packets, so we look at the first six packets here.
            // The last one is the CTRL_DISCONNECT packet, which
            // we verify below. The first 6 packets are RT packets,
            // and their sequence numbers must be monotonically
            // increasing, as explained above.
            for (index in 0 until 6) {
                val appLayerPacket = ApplicationLayer.Packet(testIO.sentPacketData[index].toTransportLayerPacket())
                val rtSequenceNumber = (appLayerPacket.payload[0].toPosInt() shl 0) or (appLayerPacket.payload[1].toPosInt() shl 8)
                assertEquals(index, rtSequenceNumber)
            }

            testStates.checkDisconnectPacketData(testIO.sentPacketData[6])
        }
    }

    @Test
    fun cmdCMDReadErrorWarningStatus() {
        runBlockingWithWatchdog(12000) {
            // Check that a simulated CMD error/warning status retrieval is performed successfully.
            // Feed in raw data bytes into the test IO. These raw bytes are packets that contain
            // error/warning status data. Check that these packets are correctly parsed and that
            // the retrieved status is correct.

            val testStates = TestStates(setupInvariantPumpData = false)
            val pumpIO = testStates.pumpIO
            val testIO = testStates.testIO

            // Need to set up custom keys since the test data was
            // created with those instead of the default test keys.
            val invariantPumpData = InvariantPumpData(
                keyResponseAddress = 0x10,
                clientPumpCipher = Cipher(
                    byteArrayOfInts(
                        0x12, 0xe2, 0x4a, 0xb6, 0x67, 0x50, 0xe5, 0xb4,
                        0xc4, 0xea, 0x10, 0xa7, 0x55, 0x11, 0x61, 0xd4
                    )
                ),
                pumpClientCipher = Cipher(
                    byteArrayOfInts(
                        0x8e, 0x0d, 0x35, 0xe3, 0x7c, 0xd7, 0x20, 0x55,
                        0x57, 0x2b, 0x05, 0x50, 0x34, 0x43, 0xc9, 0x8d
                    )
                ),
                pumpID = "testPump"
            )
            testStates.testPumpStateStore.createPumpState(
                testStates.testBluetoothDevice.address, invariantPumpData, UtcOffset.ZERO, CurrentTbrState.NoTbrOngoing
            )
            testIO.pumpClientCipher = invariantPumpData.pumpClientCipher

            testStates.feedInitialPackets()

            pumpIO.connect(
                initialMode = PumpIO.Mode.COMMAND,
                runHeartbeat = false
            )

            val errorWarningStatusData = byteArrayListOfInts(
                0x10, 0x23, 0x08, 0x00, 0x01, 0x39, 0x01, 0x00, 0x00, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x10, 0xb7, 0xa5, 0xaa,
                0x00, 0x00, 0x48, 0xb7, 0xa0, 0xea, 0x70, 0xc3, 0xd4, 0x42, 0x61, 0xd7
            )
            testIO.feedIncomingData(errorWarningStatusData)

            val errorWarningStatus = pumpIO.readCMDErrorWarningStatus()

            pumpIO.disconnect()

            assertEquals(
                ApplicationLayer.CMDErrorWarningStatus(errorOccurred = false, warningOccurred = true),
                errorWarningStatus
            )
        }
    }

    @Test
    fun checkCMDHistoryDeltaRetrieval() {
        runBlockingWithWatchdog(12000) {
            // Check that a simulated CMD history delta retrieval is performed successfully.
            // Feed in raw data bytes into the test IO. These raw bytes are packets that
            // contain history data with a series of events inside. Check that these packets
            // are correctly parsed and that the retrieved history is correct.

            val testStates = TestStates(setupInvariantPumpData = false)
            val pumpIO = testStates.pumpIO
            val testIO = testStates.testIO

            // Need to set up custom keys since the test data was
            // created with those instead of the default test keys.
            val invariantPumpData = InvariantPumpData(
                keyResponseAddress = 0x10,
                clientPumpCipher = Cipher(
                    byteArrayOfInts(
                        0x75, 0xb8, 0x88, 0xa8, 0xe7, 0x68, 0xc9, 0x25,
                        0x66, 0xc9, 0x3c, 0x4b, 0xd8, 0x09, 0x27, 0xd8
                    )
                ),
                pumpClientCipher = Cipher(
                    byteArrayOfInts(
                        0xb8, 0x75, 0x8c, 0x54, 0x88, 0x71, 0x78, 0xed,
                        0xad, 0xb7, 0xb7, 0xc1, 0x48, 0x37, 0xf3, 0x07
                    )
                ),
                pumpID = "testPump"
            )
            testStates.testPumpStateStore.createPumpState(
                testStates.testBluetoothDevice.address, invariantPumpData, UtcOffset.ZERO, CurrentTbrState.NoTbrOngoing
            )
            testIO.pumpClientCipher = invariantPumpData.pumpClientCipher

            val historyBlockPacketData = listOf(
                // CMD_READ_HISTORY_BLOCK_RESPONSE
                byteArrayListOfInts(
                    0x10, 0xa3, 0x65, 0x00, 0x01, 0x08, 0x05, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                    0x10, 0xb7, 0x96, 0xa9, 0x00, 0x00, 0x10, 0x00, 0x48, 0xb7, 0x05, 0xaa, 0x0d, 0x93, 0x54, 0x0f, 0x00, 0x00,
                    0x00, 0x04, 0x00, 0x6b, 0xf3, 0x09, 0x3b, 0x01, 0x00, 0x92, 0x4c, 0xb1, 0x0d, 0x93, 0x54, 0x0f, 0x00, 0x00,
                    0x00, 0x05, 0x00, 0xa1, 0x25, 0x0b, 0x3b, 0x01, 0x00, 0xe4, 0x75, 0x46, 0x0e, 0x93, 0x54, 0x1d, 0x00, 0x00,
                    0x00, 0x06, 0x00, 0xb7, 0xda, 0x0d, 0x3b, 0x01, 0x00, 0x7e, 0x3e, 0x54, 0x0e, 0x93, 0x54, 0x1d, 0x00, 0x00,
                    0x00, 0x07, 0x00, 0x73, 0x49, 0x0f, 0x3b, 0x01, 0x00, 0x08, 0x07, 0x77, 0x0e, 0x93, 0x54, 0x05, 0x00, 0x00,
                    0x00, 0x04, 0x00, 0x2f, 0xd8, 0x11, 0x3b, 0x01, 0x00, 0xeb, 0x6a, 0x81, 0xf5, 0x6c, 0x43, 0xf0, 0x88, 0x15, 0x3b
                ),
                // CMD_CONFIRM_HISTORY_BLOCK_RESPONSE
                byteArrayListOfInts(
                    0x10, 0x23, 0x06, 0x00, 0x01, 0x0a, 0x05, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                    0x00, 0x00, 0x10, 0xb7, 0x99, 0xa9, 0x00, 0x00, 0x8f, 0xec, 0xfa, 0xa7, 0xf5, 0x0d, 0x01, 0x6c
                ),
                // CMD_READ_HISTORY_BLOCK_RESPONSE
                byteArrayListOfInts(
                    0x10, 0xa3, 0x65, 0x00, 0x01, 0x0c, 0x05, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                    0x10, 0xb7, 0x96, 0xa9, 0x00, 0x00, 0x0b, 0x00, 0x48, 0xb7, 0x05, 0x79, 0x0e, 0x93, 0x54, 0x05, 0x00, 0x00,
                    0x00, 0x05, 0x00, 0x0c, 0x40, 0x13, 0x3b, 0x01, 0x00, 0x9d, 0x53, 0xad, 0x0e, 0x93, 0x54, 0x12, 0x00, 0x00,
                    0x00, 0x06, 0x00, 0x46, 0xa5, 0x15, 0x3b, 0x01, 0x00, 0x07, 0x18, 0xb6, 0x0e, 0x93, 0x54, 0x12, 0x00, 0x00,
                    0x00, 0x07, 0x00, 0x8c, 0x73, 0x17, 0x3b, 0x01, 0x00, 0x71, 0x21, 0x13, 0x10, 0x93, 0x54, 0xb1, 0x00, 0x0f,
                    0x00, 0x08, 0x00, 0xbb, 0x78, 0x1a, 0x3b, 0x01, 0x00, 0xfe, 0xaa, 0xd2, 0x13, 0x93, 0x54, 0xb1, 0x00, 0x0f,
                    0x00, 0x09, 0x00, 0xce, 0x68, 0x1c, 0x3b, 0x01, 0x00, 0x64, 0xe1, 0x2c, 0xc8, 0x37, 0xb3, 0xe5, 0xb7, 0x7c, 0xc4
                ),
                // CMD_CONFIRM_HISTORY_BLOCK_RESPONSE
                byteArrayListOfInts(
                    0x10, 0x23, 0x06, 0x00, 0x01, 0x0e, 0x05, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                    0x00, 0x00, 0x10, 0xb7, 0x99, 0xa9, 0x00, 0x00, 0xe5, 0xab, 0x11, 0x6d, 0xfc, 0x60, 0xfb, 0xee
                ),
                // CMD_READ_HISTORY_BLOCK_RESPONSE
                byteArrayListOfInts(
                    0x10, 0xa3, 0x65, 0x00, 0x01, 0x10, 0x05, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                    0x10, 0xb7, 0x96, 0xa9, 0x00, 0x00, 0x06, 0x00, 0x48, 0xb7, 0x05, 0x5f, 0x15, 0x93, 0x54, 0xc1, 0x94, 0xe0,
                    0x01, 0x0a, 0x00, 0x76, 0x3b, 0x1e, 0x3b, 0x01, 0x00, 0x12, 0xd8, 0xc8, 0x1c, 0x93, 0x54, 0xc1, 0x94, 0xe0,
                    0x01, 0x0b, 0x00, 0xc8, 0xa4, 0x20, 0x3b, 0x01, 0x00, 0xa2, 0x3a, 0x59, 0x20, 0x93, 0x54, 0x40, 0x30, 0x93,
                    0x54, 0x18, 0x00, 0xbb, 0x0c, 0x23, 0x3b, 0x01, 0x00, 0x6f, 0x1f, 0x40, 0x30, 0x93, 0x54, 0x00, 0x00, 0x00,
                    0x00, 0x19, 0x00, 0x2b, 0x80, 0x24, 0x3b, 0x01, 0x00, 0x4e, 0x48, 0x85, 0x30, 0x93, 0x54, 0x14, 0x00, 0x00,
                    0x00, 0x04, 0x00, 0xe8, 0x98, 0x2b, 0x3b, 0x01, 0x00, 0xb7, 0xfa, 0x0e, 0x32, 0x37, 0x19, 0xb6, 0x59, 0x5a, 0xb1
                ),
                // CMD_CONFIRM_HISTORY_BLOCK_RESPONSE
                byteArrayListOfInts(
                    0x10, 0x23, 0x06, 0x00, 0x01, 0x12, 0x05, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                    0x00, 0x00, 0x10, 0xb7, 0x99, 0xa9, 0x00, 0x00, 0xae, 0xaa, 0xa7, 0x3a, 0xbc, 0x82, 0x8c, 0x15
                ),
                // CMD_READ_HISTORY_BLOCK_RESPONSE
                byteArrayListOfInts(
                    0x10, 0xa3, 0x1d, 0x00, 0x01, 0x14, 0x05, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                    0x10, 0xb7, 0x96, 0xa9, 0x00, 0x00, 0x01, 0x00, 0xb7, 0xb7, 0x01, 0x8f, 0x30, 0x93, 0x54, 0x14, 0x00, 0x00,
                    0x00, 0x05, 0x00, 0x57, 0xb0, 0x2d, 0x3b, 0x01, 0x00, 0x2d, 0xb1, 0x29, 0x32, 0xde, 0x3c, 0xa0, 0x80, 0x33, 0xd3
                ),
                // CMD_CONFIRM_HISTORY_BLOCK_RESPONSE
                byteArrayListOfInts(
                    0x10, 0x23, 0x06, 0x00, 0x01, 0x16, 0x05, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                    0x00, 0x00, 0x10, 0xb7, 0x99, 0xa9, 0x00, 0x00, 0x15, 0x63, 0xa5, 0x60, 0x3d, 0x75, 0xff, 0xfc
                )
            )

            val expectedHistoryDeltaEvents = listOf(
                ApplicationLayer.CMDHistoryEvent(
                    LocalDateTime(year = 2021, month = 2, day = 9, hour = 16, minute = 54, second = 42),
                    80649,
                    ApplicationLayer.CMDHistoryEventDetail.QuickBolusRequested(15)
                ),
                ApplicationLayer.CMDHistoryEvent(
                    LocalDateTime(year = 2021, month = 2, day = 9, hour = 16, minute = 54, second = 49),
                    80651,
                    ApplicationLayer.CMDHistoryEventDetail.QuickBolusInfused(15)
                ),

                ApplicationLayer.CMDHistoryEvent(
                    LocalDateTime(year = 2021, month = 2, day = 9, hour = 16, minute = 57, second = 6),
                    80653,
                    ApplicationLayer.CMDHistoryEventDetail.StandardBolusRequested(29, true)
                ),
                ApplicationLayer.CMDHistoryEvent(
                    LocalDateTime(year = 2021, month = 2, day = 9, hour = 16, minute = 57, second = 20),
                    80655,
                    ApplicationLayer.CMDHistoryEventDetail.StandardBolusInfused(29, true)
                ),

                ApplicationLayer.CMDHistoryEvent(
                    LocalDateTime(year = 2021, month = 2, day = 9, hour = 16, minute = 57, second = 55),
                    80657,
                    ApplicationLayer.CMDHistoryEventDetail.QuickBolusRequested(5)
                ),
                ApplicationLayer.CMDHistoryEvent(
                    LocalDateTime(year = 2021, month = 2, day = 9, hour = 16, minute = 57, second = 57),
                    80659,
                    ApplicationLayer.CMDHistoryEventDetail.QuickBolusInfused(5)
                ),

                ApplicationLayer.CMDHistoryEvent(
                    LocalDateTime(year = 2021, month = 2, day = 9, hour = 16, minute = 58, second = 45),
                    80661,
                    ApplicationLayer.CMDHistoryEventDetail.StandardBolusRequested(18, true)
                ),
                ApplicationLayer.CMDHistoryEvent(
                    LocalDateTime(year = 2021, month = 2, day = 9, hour = 16, minute = 58, second = 54),
                    80663,
                    ApplicationLayer.CMDHistoryEventDetail.StandardBolusInfused(18, true)
                ),

                ApplicationLayer.CMDHistoryEvent(
                    LocalDateTime(year = 2021, month = 2, day = 9, hour = 17, minute = 0, second = 19),
                    80666,
                    ApplicationLayer.CMDHistoryEventDetail.ExtendedBolusStarted(177, 15, true)
                ),
                ApplicationLayer.CMDHistoryEvent(
                    LocalDateTime(year = 2021, month = 2, day = 9, hour = 17, minute = 15, second = 18),
                    80668,
                    ApplicationLayer.CMDHistoryEventDetail.ExtendedBolusEnded(177, 15, true)
                ),

                ApplicationLayer.CMDHistoryEvent(
                    LocalDateTime(year = 2021, month = 2, day = 9, hour = 17, minute = 21, second = 31),
                    80670,
                    ApplicationLayer.CMDHistoryEventDetail.MultiwaveBolusStarted(193, 37, 30, true)
                ),
                ApplicationLayer.CMDHistoryEvent(
                    LocalDateTime(year = 2021, month = 2, day = 9, hour = 17, minute = 51, second = 8),
                    80672,
                    ApplicationLayer.CMDHistoryEventDetail.MultiwaveBolusEnded(193, 37, 30, true)
                ),

                ApplicationLayer.CMDHistoryEvent(
                    LocalDateTime(year = 2021, month = 2, day = 9, hour = 18, minute = 1, second = 25),
                    80675,
                    ApplicationLayer.CMDHistoryEventDetail.NewDateTimeSet(
                        LocalDateTime(year = 2021, month = 2, day = 9, hour = 19, minute = 1, second = 0)
                    )
                ),

                ApplicationLayer.CMDHistoryEvent(
                    LocalDateTime(year = 2021, month = 2, day = 9, hour = 19, minute = 2, second = 5),
                    80683,
                    ApplicationLayer.CMDHistoryEventDetail.QuickBolusRequested(20)
                ),
                ApplicationLayer.CMDHistoryEvent(
                    LocalDateTime(year = 2021, month = 2, day = 9, hour = 19, minute = 2, second = 15),
                    80685,
                    ApplicationLayer.CMDHistoryEventDetail.QuickBolusInfused(20)
                )
            )

            testStates.feedInitialPackets()

            pumpIO.connect(
                initialMode = PumpIO.Mode.COMMAND,
                runHeartbeat = false
            )

            historyBlockPacketData.forEach { testIO.feedIncomingData(it) }

            val historyDelta = pumpIO.getCMDHistoryDelta(100)

            pumpIO.disconnect()

            assertEquals(expectedHistoryDeltaEvents.size, historyDelta.size)
            for (events in expectedHistoryDeltaEvents.zip(historyDelta))
                assertEquals(events.first, events.second)
        }
    }
}
