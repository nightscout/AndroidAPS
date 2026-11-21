package app.aaps.plugins.eversense.connection

import app.aaps.core.interfaces.pump.ble.BleFailure
import app.aaps.core.interfaces.pump.ble.BleResult
import app.aaps.core.interfaces.pump.ble.DeviceId
import app.aaps.plugins.eversense.FakeBleLink
import app.aaps.plugins.eversense.MemoryStore
import app.aaps.plugins.eversense.RecordingWatcher
import app.aaps.plugins.eversense.actAsE3
import app.aaps.plugins.eversense.callbacks.EversenseWatcher
import app.aaps.plugins.eversense.e3Frame
import app.aaps.plugins.eversense.enums.EversenseAlarm
import app.aaps.plugins.eversense.enums.EversenseSecurityType
import app.aaps.plugins.eversense.exceptions.EversenseWriteException
import app.aaps.plugins.eversense.packets.e3.EnterDiagnosticModePacket
import app.aaps.plugins.eversense.packets.e3.EversenseE3Packets
import app.aaps.plugins.eversense.packets.e3.GetCurrentDatetimePacket
import app.aaps.plugins.eversense.packets.e3.GetSignalStrengthRawPacket
import app.aaps.plugins.eversense.packets.e3.PingPacket
import app.aaps.plugins.eversense.packets.e3.SaveBondingInformationPacket
import app.aaps.plugins.eversense.packets.e365.Eversense365Packets
import app.aaps.plugins.eversense.packets.e365.Ping365Packet
import app.aaps.plugins.eversense.packets.e365.SetAppVersion365Packet
import app.aaps.plugins.eversense.packets.e365.utils.toUnixArray
import app.aaps.plugins.eversense.protocol.PacketTime
import app.aaps.plugins.eversense.util.EversenseCrypto365Util
import app.aaps.plugins.eversense.util.StorageKeys
import app.aaps.shared.tests.AAPSLoggerTest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * One connection to a transmitter: the frames it receives, the packets it sends and the sign-in. The test plays the
 * transmitter on a [FakeBleLink] and the driver as a [FakeHost].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EversenseSessionTest {

    /** The driver's side, as the session sees it. */
    private class FakeHost : EversenseSession.Host {

        override var username = ""
        override var password = ""
        override var positioningMode = false
        var online = true
        var internetAsked = 0
        val watcher = RecordingWatcher()
        val closes = mutableListOf<String>()
        val failures = mutableListOf<Boolean>()
        var successes = 0
        var ready = 0
        var data = 0

        override fun hasInternet(): Boolean {
            internetAsked++
            return online
        }

        override fun watchers(): List<EversenseWatcher> = listOf(watcher)
        override fun authenticationSucceeded() {
            successes++
        }

        override fun authenticationFailed(shortcut: Boolean) {
            failures += shortcut
        }

        override fun ready() {
            ready++
        }

        override fun dataReceived() {
            data++
        }

        override fun closeLink(reason: String) {
            closes += reason
        }
    }

    private class Setup(val link: FakeBleLink, val session: EversenseSession, val host: FakeHost, val store: MemoryStore, val linkScope: CoroutineScope) {

        /**
         * Starts receiving without the sign-in: the subscription is refused, so the sign-in does not run. The close it
         * asks for is forgotten, and the subscription works again for a later start.
         */
        fun TestScope.receiveOnly() {
            link.subscribeResult = BleResult.Failed(BleFailure(BleFailure.Kind.REFUSED))
            backgroundScope.async { session.start() }
            runCurrent()
            assertEquals(listOf("notifications refused"), host.closes)
            host.closes.clear()
            link.subscribeResult = BleResult.Ok(Unit)
        }
    }

    private fun sessionTest(is365: Boolean = false, body: suspend TestScope.(Setup) -> Unit) = runTest {
        val link = FakeBleLink(DeviceId("00:11:22:33:44:55"), is365)
        // The link's own scope, as the driver gives one to each link; it ends with the test
        val linkScope = CoroutineScope(backgroundScope.coroutineContext + Job(backgroundScope.coroutineContext.job))
        val host = FakeHost()
        val store = MemoryStore()
        val security = if (is365) EversenseSecurityType.SecureV2 else EversenseSecurityType.None
        val session = EversenseSession(link, link.request, link.response, security, FakeBleLink.MTU - 3, store, AAPSLoggerTest(), linkScope, host)
        body(Setup(link, session, host, store, linkScope))
    }

    /**
     * A crypto whose answers the test decides; the real one needs a session key. Decrypt gives [decrypted] in turn, and
     * the last one again after that. The sign-in steps answer what the test set, and count their calls.
     */
    private class FakeCrypto(decrypted: List<ByteArray> = emptyList()) : EversenseCrypto365Util(MemoryStore(), AAPSLoggerTest()) {

        private val answers = ArrayDeque(decrypted)
        private var last: ByteArray? = null
        var shortcut = false
        var keyPairMade = false
        var ephem: ByteArray? = null
        var startSecret = byteArrayOf()
        var sessionKeyFailure: Exception? = null
        var decrypts = 0
        var shortcutsDisallowed = 0
        var sessionKeys = 0

        override fun encrypt(data: ByteArray): ByteArray = byteArrayOf(3, 14)
        override fun decrypt(response: ByteArray): ByteArray {
            decrypts++
            last = answers.removeFirstOrNull() ?: last
            return last ?: error("no decrypt answer prepared")
        }

        override fun canUseShortcut(): Boolean = shortcut
        override fun disallowUseShortcut() {
            shortcutsDisallowed++
        }

        override fun generateKeyPairIfNotExists(): Boolean = keyPairMade
        override fun generateEphem(): ByteArray? = ephem
        override fun getStartSecret(signature: ByteArray): ByteArray = startSecret
        override fun generateSessionKey(encodedPublicKey: ByteArray) {
            sessionKeys++
            sessionKeyFailure?.let { throw it }
        }
    }

    private fun crypto(decrypted: List<ByteArray> = emptyList()) = FakeCrypto(decrypted)

    /** The E3 answer to [GetSignalStrengthRawPacket]: the raw value little endian at bytes 4 and 5. */
    private fun signalAnswer(raw: Int) =
        byteArrayOf(GetSignalStrengthRawPacket().spec.responseId, 0, 0, 0, raw.toByte(), (raw shr 8).toByte())

    // ----- one packet and its answer -----

    @Test fun anAnswerThatComesBeforeTheWriteReturnsIsKept(): Unit = sessionTest { setup ->
        with(setup) {
            receiveOnly()
            link.onWrite = { send(signalAnswer(903)) }
            val response = session.writePacket<GetSignalStrengthRawPacket.Response>(GetSignalStrengthRawPacket(), 100)
            assertEquals(903, response.rawValue)
            assertEquals(80, response.signalStrength)
            assertEquals(emptyList(), host.closes)
        }
    }

    /** A late reply must not be taken for the answer to the next packet: the link is closed instead. */
    @Test fun aPacketWithNoAnswerClosesTheLink(): Unit = sessionTest { setup ->
        with(setup) {
            receiveOnly()
            val error = runCatching { session.writePacket<GetSignalStrengthRawPacket.Response>(GetSignalStrengthRawPacket(), 100) }.exceptionOrNull()
            assertIs<EversenseWriteException>(error)
            assertEquals(listOf("no answer in 100 ms"), host.closes)
            assertEquals(100, currentTime)
        }
    }

    @Test fun aLateAnswerWithNoPacketWaitingIsIgnored(): Unit = sessionTest { setup ->
        with(setup) {
            receiveOnly()
            runCatching { session.writePacket<GetSignalStrengthRawPacket.Response>(GetSignalStrengthRawPacket(), 100) }
            link.send(signalAnswer(903))
            runCurrent()
            link.onWrite = { send(signalAnswer(500)) }
            val next = session.writePacket<GetSignalStrengthRawPacket.Response>(GetSignalStrengthRawPacket(), 100)
            assertEquals(500, next.rawValue)
        }
    }

    @Test fun aRefusedWriteFailsAtOnceAndKeepsTheLink(): Unit = sessionTest { setup ->
        with(setup) {
            receiveOnly()
            link.writeResult = BleResult.Failed(BleFailure(BleFailure.Kind.REFUSED, platformStatus = 133))
            val error = runCatching { session.writePacket<GetSignalStrengthRawPacket.Response>(GetSignalStrengthRawPacket(), 100) }.exceptionOrNull()
            assertIs<EversenseWriteException>(error)
            assertEquals(0, currentTime)
            assertEquals(emptyList(), host.closes)
        }
    }

    @Test fun aDeadLinkSendsNothing(): Unit = sessionTest { setup ->
        with(setup) {
            receiveOnly()
            link.close()
            val error = runCatching { session.writePacket<GetSignalStrengthRawPacket.Response>(GetSignalStrengthRawPacket(), 100) }.exceptionOrNull()
            assertIs<EversenseWriteException>(error)
            assertEquals(emptyList(), link.writes)
        }
    }

    @Test fun anE3ErrorFrameEndsTheExchangeAsRejected(): Unit = sessionTest { setup ->
        with(setup) {
            receiveOnly()
            link.onWrite = { frame -> send(e3Frame(EversenseE3Packets.ErrorResponseId, frame[0], 5)) }
            val error = runCatching { session.writePacket<GetSignalStrengthRawPacket.Response>(GetSignalStrengthRawPacket(), 100) }.exceptionOrNull()
            assertIs<EversenseWriteException>(error)
            assertEquals("Transmitter rejected the packet", error?.message)
            assertEquals(emptyList(), host.closes)
            assertEquals(0, currentTime)
        }
    }

    @Test fun anE3AnswerWithAnotherResponseIdIsIgnored(): Unit = sessionTest { setup ->
        with(setup) {
            receiveOnly()
            link.onWrite = {
                send(e3Frame(EversenseE3Packets.SaveBLEBondingInformationResponseId))
                send(signalAnswer(903))
            }
            val response = session.writePacket<GetSignalStrengthRawPacket.Response>(GetSignalStrengthRawPacket(), 100)
            assertEquals(903, response.rawValue)
        }
    }

    /** SecureV2 rejects a command with ff, its type and an error code: rejected, and the signed-in link stays. */
    @Test fun aRejected365CommandEndsTheExchangeAndKeepsTheLink(): Unit = sessionTest(is365 = true) { setup ->
        with(setup) {
            session.cryptoUtil = crypto(listOf(byteArrayOf(0xff.toByte(), Eversense365Packets.WriteAppVersion, 5)))
            receiveOnly()
            link.onWrite = { send(byteArrayOf(1, 1, 1, 0x42, 1)) }
            val error = runCatching { session.writePacket<SetAppVersion365Packet.Response>(SetAppVersion365Packet(), 100) }.exceptionOrNull()
            assertEquals("Transmitter rejected the packet", error?.message)
            assertEquals(emptyList(), host.closes)
        }
    }

    /** A 365 answer must have the response id and the type of the packet; anything else does not end the exchange. */
    @Test fun a365AnswerOfAnotherIdOrTypeIsIgnored(): Unit = sessionTest(is365 = true) { setup ->
        with(setup) {
            session.cryptoUtil = crypto(
                listOf(
                    byteArrayOf(Eversense365Packets.ReadResponseId, (Eversense365Packets.ReadPing + 1).toByte()),
                    byteArrayOf(Eversense365Packets.WriteResponseId, Eversense365Packets.ReadPing)
                )
            )
            receiveOnly()
            link.onWrite = {
                send(byteArrayOf(1, 1, 1, 0x42, 1))
                send(byteArrayOf(1, 1, 1, 0x42, 2))
            }
            val error = runCatching { session.writePacket<Ping365Packet.Response>(Ping365Packet(), 100) }.exceptionOrNull()
            assertIs<EversenseWriteException>(error)
            assertEquals(listOf("no answer in 100 ms"), host.closes)
        }
    }

    // ----- the work of the link -----

    @Test fun theEndOfTheLinkReleasesAPacketThatWaitsForItsAnswer(): Unit = sessionTest { setup ->
        with(setup) {
            receiveOnly()
            val result = async {
                runCatching { session.perform { writePacket<GetSignalStrengthRawPacket.Response>(GetSignalStrengthRawPacket(), 60_000) } }
            }
            runCurrent()
            assertEquals(1, link.writes.size)
            linkScope.cancel()
            assertIs<EversenseWriteException>(result.await().exceptionOrNull())
            assertTrue(currentTime < 60_000)
        }
    }

    @Test fun workQueuedOnAnEndedLinkDoesNotRun(): Unit = sessionTest { setup ->
        with(setup) {
            val release = CompletableDeferred<Unit>()
            val first = async { runCatching { session.perform { release.await() } } }
            runCurrent()
            var ran = false
            val queued = async { runCatching { session.perform { ran = true } } }
            runCurrent()
            linkScope.cancel()
            assertIs<EversenseWriteException>(queued.await().exceptionOrNull())
            assertIs<EversenseWriteException>(first.await().exceptionOrNull())
            assertFalse(ran)
            // New work for an ended link is refused, and the caller gets an answer, not a hang
            assertIs<EversenseWriteException>(runCatching { session.perform { 42 } }.exceptionOrNull())
            release.complete(Unit)
        }
    }

    @Test fun tasksRunOneAtATimeInTheOrderTheyCame(): Unit = sessionTest { setup ->
        with(setup) {
            val events = mutableListOf<String>()
            val tasks = (1..3).map { number ->
                async {
                    session.perform {
                        events += "start $number"
                        delay(10)
                        events += "end $number"
                    }
                }
            }
            tasks.forEach { it.await() }
            assertEquals(listOf("start 1", "end 1", "start 2", "end 2", "start 3", "end 3"), events)
        }
    }

    /** A packet the transmitter refuses fails for the caller who sent it, not for the link: the next task runs. */
    @Test fun aFailedTaskFailsForItsCallerAndKeepsTheLink(): Unit = sessionTest { setup ->
        with(setup) {
            receiveOnly()
            link.onWrite = { frame -> send(e3Frame(EversenseE3Packets.ErrorResponseId, frame[0], 5)) }
            val error = runCatching { session.perform { writePacket<GetSignalStrengthRawPacket.Response>(GetSignalStrengthRawPacket(), 100) } }.exceptionOrNull()
            assertIs<EversenseWriteException>(error)
            runCurrent()
            assertTrue(linkScope.isActive)
            link.onWrite = { send(signalAnswer(903)) }
            assertEquals(903, session.perform { writePacket<GetSignalStrengthRawPacket.Response>(GetSignalStrengthRawPacket(), 100) }.rawValue)
        }
    }

    /** Work nobody waits for (a sync, a keep alive) that fails is logged: it does not end the link. */
    @Test fun aFailedBackgroundTaskKeepsTheLink(): Unit = sessionTest { setup ->
        with(setup) {
            receiveOnly()
            session.launchTask { throw EversenseWriteException("refused") }
            runCurrent()
            assertTrue(linkScope.isActive)
            link.onWrite = { send(signalAnswer(903)) }
            assertEquals(903, session.perform { writePacket<GetSignalStrengthRawPacket.Response>(GetSignalStrengthRawPacket(), 100) }.rawValue)
        }
    }

    /** The glucose read after a 365 keep alive is refused: logged, and the link goes on. */
    @Test fun a365KeepAliveWhoseReadIsRefusedKeepsTheLink(): Unit = sessionTest(is365 = true) { setup ->
        with(setup) {
            val keepAlive = byteArrayOf(Eversense365Packets.NotificationResponseId, Eversense365Packets.NotificationKeepAlive) + ByteArray(9) +
                PacketTime.nowMillis().toUnixArray()
            session.cryptoUtil = crypto(listOf(keepAlive, byteArrayOf(EversenseE3Packets.ErrorResponseId, 1, 2)))
            receiveOnly()
            link.onWrite = { send(byteArrayOf(1, 1, 1, 0x44, 2)) }
            link.send(byteArrayOf(1, 1, 1, 0x44, 1))
            runCurrent()
            assertEquals(1, link.writes.size)
            assertTrue(linkScope.isActive)
            assertEquals(emptyList(), host.closes)
        }
    }

    /** A notification that cannot be read is dropped: the link keeps receiving. */
    @Test fun aNotificationThatCannotBeReadIsDropped(): Unit = sessionTest(is365 = true) { setup ->
        with(setup) {
            val shortKeepAlive = byteArrayOf(Eversense365Packets.NotificationResponseId, Eversense365Packets.NotificationKeepAlive, 1, 2)
            val alarm = byteArrayOf(Eversense365Packets.NotificationResponseId, Eversense365Packets.NotificationAlarmWithData, EversenseAlarm.LOW_GLUCOSE.code.toByte(), 0) +
                ByteArray(8)
            session.cryptoUtil = crypto(listOf(shortKeepAlive, alarm))
            receiveOnly()
            link.send(byteArrayOf(1, 1, 1, 0x44, 1))
            link.send(byteArrayOf(1, 1, 1, 0x44, 2))
            runCurrent()
            assertTrue(linkScope.isActive)
            assertEquals(listOf(EversenseAlarm.LOW_GLUCOSE), host.watcher.alarms.map { it.code })
        }
    }

    // ----- what the transmitter sends by itself -----

    /** A decrypt failure means the session key no longer matches: with internet the next connect signs in fully. */
    @Test fun aDecryptFailureWithInternetDisallowsTheShortcutAndCloses(): Unit = sessionTest(is365 = true) { setup ->
        with(setup) {
            val crypto = crypto(listOf(byteArrayOf()))
            session.cryptoUtil = crypto
            host.online = true
            receiveOnly()
            link.send(byteArrayOf(1, 1, 1, 0x42, 1))
            runCurrent()
            assertEquals(1, crypto.shortcutsDisallowed)
            assertEquals(listOf("decrypt failed"), host.closes)
        }
    }

    /** Offline a full sign-in cannot run: the registered identity is kept for a new local handshake. */
    @Test fun aDecryptFailureOfflineKeepsTheShortcutAndCloses(): Unit = sessionTest(is365 = true) { setup ->
        with(setup) {
            val crypto = crypto(listOf(byteArrayOf()))
            session.cryptoUtil = crypto
            host.online = false
            receiveOnly()
            link.send(byteArrayOf(1, 1, 1, 0x42, 1))
            runCurrent()
            assertEquals(1, crypto.decrypts)
            assertEquals(0, crypto.shortcutsDisallowed)
            assertEquals(listOf("decrypt failed"), host.closes)
        }
    }

    @Test fun pushAlarmsReachTheWatchersAndUnknownCodesDoNot(): Unit = sessionTest(is365 = true) { setup ->
        with(setup) {
            fun alarm(code: Int) = byteArrayOf(Eversense365Packets.NotificationResponseId, Eversense365Packets.NotificationAlarmWithData, code.toByte(), 0) + ByteArray(8)
            session.cryptoUtil = crypto(listOf(alarm(EversenseAlarm.LOW_GLUCOSE.code), alarm(254), alarm(68)))
            receiveOnly()
            link.send(byteArrayOf(1, 1, 1, 0x44, 1))
            link.send(byteArrayOf(1, 1, 1, 0x44, 2))
            link.send(byteArrayOf(1, 1, 1, 0x44, 3))
            runCurrent()
            assertEquals(listOf(EversenseAlarm.LOW_GLUCOSE), host.watcher.alarms.map { it.code })
            assertEquals(emptyList(), host.closes)
        }
    }

    @Test fun anE3KeepAliveMarksTheLinkAliveAndReadsTheTransmitter(): Unit = sessionTest { setup ->
        with(setup) {
            receiveOnly()
            link.send(e3Frame(EversenseE3Packets.KeepAlivePush))
            runCurrent()
            assertEquals(1, host.data)
            // The clock first, so the reading has phone time
            assertEquals(GetCurrentDatetimePacket().spec.requestId, link.writes.first()[0])
        }
    }

    /** A 365 keep alive with a reading of the last 4.5 minutes reads it; an older one only says the link is alive. */
    @Test fun a365KeepAliveReadsTheGlucoseOnlyWhenItsReadingIsRecent(): Unit = sessionTest(is365 = true) { setup ->
        with(setup) {
            fun keepAlive(readingAt: Long) =
                byteArrayOf(Eversense365Packets.NotificationResponseId, Eversense365Packets.NotificationKeepAlive) + ByteArray(9) + readingAt.toUnixArray()
            val now = PacketTime.nowMillis()
            session.cryptoUtil = crypto(listOf(keepAlive(now - 10 * 60_000L), keepAlive(now)))
            receiveOnly()
            link.send(byteArrayOf(1, 1, 1, 0x44, 1))
            runCurrent()
            assertEquals(1, host.data)
            assertEquals(emptyList(), link.writes)

            link.send(byteArrayOf(1, 1, 1, 0x44, 2))
            runCurrent()
            assertEquals(2, host.data)
            assertEquals(1, link.writes.size)
        }
    }

    @Test fun dataOnAnotherCharacteristicIsIgnored(): Unit = sessionTest { setup ->
        with(setup) {
            receiveOnly()
            link.send(e3Frame(EversenseE3Packets.KeepAlivePush), characteristic = link.request)
            runCurrent()
            assertEquals(0, host.data)
            assertEquals(emptyList(), link.writes)
        }
    }

    // ----- the sign-in -----

    @Test fun anE3ThatTakesTheBondingIsReadyAndSyncs(): Unit = sessionTest { setup ->
        with(setup) {
            link.actAsE3()
            session.start()
            runCurrent()
            assertEquals(1, host.ready)
            assertEquals(1, host.watcher.ready)
            assertEquals(SaveBondingInformationPacket().spec.requestId, link.writes[0][0])
            // The full sync starts with a ping
            assertEquals(PingPacket().spec.requestId, link.writes[1][0])
            assertEquals(emptyList(), host.closes)
        }
    }

    /** Not ready, but the link stays: the driver's attempt deadline ends it. */
    @Test fun anE3ThatRefusesTheBondingIsNotReady(): Unit = sessionTest { setup ->
        with(setup) {
            link.actAsE3(acceptBonding = false)
            session.start()
            runCurrent()
            assertEquals(0, host.ready)
            assertEquals(0, host.watcher.ready)
            assertEquals(1, link.writes.size)
            assertEquals(emptyList(), host.closes)
        }
    }

    /** After a reconnect the placement guide is still open: the transmitter left diagnostic mode with the link. */
    @Test fun thePlacementGuideIsResumedAfterAnE3Reconnect(): Unit = sessionTest { setup ->
        with(setup) {
            host.positioningMode = true
            link.actAsE3()
            session.start()
            runCurrent()
            assertEquals(EnterDiagnosticModePacket().spec.requestId, link.writes[1][0])
        }
    }

    @Test fun refusedNotificationsCloseTheLinkAndSendNothing(): Unit = sessionTest { setup ->
        with(setup) {
            link.subscribeResult = BleResult.Failed(BleFailure(BleFailure.Kind.REFUSED))
            session.start()
            assertEquals(listOf("notifications refused"), host.closes)
            assertEquals(emptyList(), link.writes)
        }
    }

    /** A 365 with no login stored (an E3 was chosen without one) cannot register: said as a refused login, nothing sent. */
    @Test fun a365WithoutALoginRecordsARefusedLoginAndSendsNothing(): Unit = sessionTest(is365 = true) { setup ->
        with(setup) {
            val crypto = crypto().apply {
                shortcut = false
                keyPairMade = true
            }
            session.cryptoUtil = crypto
            session.start()
            runCurrent()
            assertTrue(store.getBoolean(StorageKeys.LOGIN_REFUSED, false))
            assertEquals(listOf("no login for the registration"), host.closes)
            assertEquals(emptyList(), link.writes)
        }
    }

    /** The registration signs in at the Eversense server: offline nothing is sent, neither to the server nor over Bluetooth. */
    @Test fun a365RegistrationOfflineSendsNothing(): Unit = sessionTest(is365 = true) { setup ->
        with(setup) {
            val crypto = crypto().apply {
                shortcut = false
                keyPairMade = true
            }
            session.cryptoUtil = crypto
            host.username = "user"
            host.password = "secret"
            host.online = false
            session.start()
            runCurrent()
            assertEquals(listOf("no internet for the registration"), host.closes)
            assertEquals(emptyList(), link.writes)
            assertFalse(store.contains(StorageKeys.LOGIN_REFUSED))
        }
    }

    /** A registered 365 signs in with AuthStart over Bluetooth alone: no internet is needed or asked for. */
    @Test fun aRegistered365SignsInOverBluetoothWithoutInternet(): Unit = sessionTest(is365 = true) { setup ->
        with(setup) {
            val crypto = crypto().apply {
                shortcut = true
                keyPairMade = true
                ephem = byteArrayOf(1)
                startSecret = byteArrayOf(1)
                // Stops the sign-in where the session key is made, so the test ends there
                sessionKeyFailure = IllegalStateException("Authentication reached session-key derivation")
            }
            session.cryptoUtil = crypto
            host.online = false
            link.onWrite = {
                send(byteArrayOf(1, 1, 1, Eversense365Packets.AuthenticateResponseId, Eversense365Packets.AuthenticateStart) + ByteArray(64))
            }
            session.start()
            runCurrent()
            assertEquals(1, crypto.sessionKeys)
            assertEquals(0, host.internetAsked)
            assertEquals(1, link.writes.size)
            // The forced failure counts as a failed shortcut, and closes the link
            assertEquals(listOf(true), host.failures)
            assertEquals(listOf("sign-in failed"), host.closes)
        }
    }
}
