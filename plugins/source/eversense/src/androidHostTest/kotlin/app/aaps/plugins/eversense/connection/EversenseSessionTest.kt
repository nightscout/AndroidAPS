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
import com.google.common.truth.Truth.assertThat
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
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

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
        val transmitterCloses = mutableListOf<String>()
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

        override fun closeLinkForTransmitter(reason: String) {
            transmitterCloses += reason
            closeLink(reason)
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
            assertThat(host.closes).containsExactly("notifications refused")
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

    /** A crypto whose encrypt and decrypt the test decides; the real one needs a session key. */
    private fun crypto(decrypted: List<ByteArray> = emptyList()): EversenseCrypto365Util = mock<EversenseCrypto365Util>().also {
        whenever(it.encrypt(any())).thenReturn(byteArrayOf(3, 14))
        if (decrypted.isNotEmpty()) whenever(it.decrypt(any())).thenReturn(decrypted.first(), *decrypted.drop(1).toTypedArray())
    }

    /** The E3 answer to [GetSignalStrengthRawPacket]: the raw value little endian at bytes 4 and 5. */
    private fun signalAnswer(raw: Int) =
        byteArrayOf(GetSignalStrengthRawPacket().spec.responseId, 0, 0, 0, raw.toByte(), (raw shr 8).toByte())

    // ----- one packet and its answer -----

    @Test fun anAnswerThatComesBeforeTheWriteReturnsIsKept(): Unit = sessionTest { setup ->
        with(setup) {
            receiveOnly()
            link.onWrite = { send(signalAnswer(903)) }
            val response = session.writePacket<GetSignalStrengthRawPacket.Response>(GetSignalStrengthRawPacket(), 100)
            assertThat(response.rawValue).isEqualTo(903)
            assertThat(response.signalStrength).isEqualTo(80)
            assertThat(host.closes).isEmpty()
        }
    }

    /** A late reply must not be taken for the answer to the next packet: the link is closed instead. */
    @Test fun aPacketWithNoAnswerClosesTheLink(): Unit = sessionTest { setup ->
        with(setup) {
            receiveOnly()
            val error = runCatching { session.writePacket<GetSignalStrengthRawPacket.Response>(GetSignalStrengthRawPacket(), 100) }.exceptionOrNull()
            assertThat(error).isInstanceOf(EversenseWriteException::class.java)
            assertThat(host.closes).containsExactly("no answer in 100 ms")
            // As the transmitter's close: on iOS the driver then waits for the transmitter, not on a timer
            assertThat(host.transmitterCloses).containsExactly("no answer in 100 ms")
            assertThat(currentTime).isEqualTo(100)
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
            assertThat(next.rawValue).isEqualTo(500)
        }
    }

    @Test fun aRefusedWriteFailsAtOnceAndKeepsTheLink(): Unit = sessionTest { setup ->
        with(setup) {
            receiveOnly()
            link.writeResult = BleResult.Failed(BleFailure(BleFailure.Kind.REFUSED, platformStatus = 133))
            val error = runCatching { session.writePacket<GetSignalStrengthRawPacket.Response>(GetSignalStrengthRawPacket(), 100) }.exceptionOrNull()
            assertThat(error).isInstanceOf(EversenseWriteException::class.java)
            assertThat(currentTime).isEqualTo(0)
            assertThat(host.closes).isEmpty()
        }
    }

    @Test fun aDeadLinkSendsNothing(): Unit = sessionTest { setup ->
        with(setup) {
            receiveOnly()
            link.close()
            val error = runCatching { session.writePacket<GetSignalStrengthRawPacket.Response>(GetSignalStrengthRawPacket(), 100) }.exceptionOrNull()
            assertThat(error).isInstanceOf(EversenseWriteException::class.java)
            assertThat(link.writes).isEmpty()
        }
    }

    @Test fun anE3ErrorFrameEndsTheExchangeAsRejected(): Unit = sessionTest { setup ->
        with(setup) {
            receiveOnly()
            link.onWrite = { frame -> send(e3Frame(EversenseE3Packets.ErrorResponseId, frame[0], 5)) }
            val error = runCatching { session.writePacket<GetSignalStrengthRawPacket.Response>(GetSignalStrengthRawPacket(), 100) }.exceptionOrNull()
            assertThat(error).isInstanceOf(EversenseWriteException::class.java)
            assertThat(error?.message).isEqualTo("Transmitter rejected the packet")
            assertThat(host.closes).isEmpty()
            assertThat(currentTime).isEqualTo(0)
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
            assertThat(response.rawValue).isEqualTo(903)
        }
    }

    /** SecureV2 rejects a command with ff, its type and an error code: rejected, and the signed-in link stays. */
    @Test fun aRejected365CommandEndsTheExchangeAndKeepsTheLink(): Unit = sessionTest(is365 = true) { setup ->
        with(setup) {
            session.cryptoUtil = crypto(listOf(byteArrayOf(0xff.toByte(), Eversense365Packets.WriteAppVersion, 5)))
            receiveOnly()
            link.onWrite = { send(byteArrayOf(1, 1, 1, 0x42, 1)) }
            val error = runCatching { session.writePacket<SetAppVersion365Packet.Response>(SetAppVersion365Packet(), 100) }.exceptionOrNull()
            assertThat(error?.message).isEqualTo("Transmitter rejected the packet")
            assertThat(host.closes).isEmpty()
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
            assertThat(error).isInstanceOf(EversenseWriteException::class.java)
            assertThat(host.closes).containsExactly("no answer in 100 ms")
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
            assertThat(link.writes).hasSize(1)
            linkScope.cancel()
            assertThat(result.await().exceptionOrNull()).isInstanceOf(EversenseWriteException::class.java)
            assertThat(currentTime).isLessThan(60_000)
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
            assertThat(queued.await().exceptionOrNull()).isInstanceOf(EversenseWriteException::class.java)
            assertThat(first.await().exceptionOrNull()).isInstanceOf(EversenseWriteException::class.java)
            assertThat(ran).isFalse()
            // New work for an ended link is refused, and the caller gets an answer, not a hang
            assertThat(runCatching { session.perform { 42 } }.exceptionOrNull()).isInstanceOf(EversenseWriteException::class.java)
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
            assertThat(events).containsExactly("start 1", "end 1", "start 2", "end 2", "start 3", "end 3").inOrder()
        }
    }

    /** A packet the transmitter refuses fails for the caller who sent it, not for the link: the next task runs. */
    @Test fun aFailedTaskFailsForItsCallerAndKeepsTheLink(): Unit = sessionTest { setup ->
        with(setup) {
            receiveOnly()
            link.onWrite = { frame -> send(e3Frame(EversenseE3Packets.ErrorResponseId, frame[0], 5)) }
            val error = runCatching { session.perform { writePacket<GetSignalStrengthRawPacket.Response>(GetSignalStrengthRawPacket(), 100) } }.exceptionOrNull()
            assertThat(error).isInstanceOf(EversenseWriteException::class.java)
            runCurrent()
            assertThat(linkScope.isActive).isTrue()
            link.onWrite = { send(signalAnswer(903)) }
            assertThat(session.perform { writePacket<GetSignalStrengthRawPacket.Response>(GetSignalStrengthRawPacket(), 100) }.rawValue).isEqualTo(903)
        }
    }

    /** Work nobody waits for (a sync, a keep alive) that fails is logged: it does not end the link. */
    @Test fun aFailedBackgroundTaskKeepsTheLink(): Unit = sessionTest { setup ->
        with(setup) {
            receiveOnly()
            session.launchTask { throw EversenseWriteException("refused") }
            runCurrent()
            assertThat(linkScope.isActive).isTrue()
            link.onWrite = { send(signalAnswer(903)) }
            assertThat(session.perform { writePacket<GetSignalStrengthRawPacket.Response>(GetSignalStrengthRawPacket(), 100) }.rawValue).isEqualTo(903)
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
            assertThat(link.writes).hasSize(1)
            assertThat(linkScope.isActive).isTrue()
            assertThat(host.closes).isEmpty()
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
            assertThat(linkScope.isActive).isTrue()
            assertThat(host.watcher.alarms.map { it.code }).containsExactly(EversenseAlarm.LOW_GLUCOSE)
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
            verify(crypto).disallowUseShortcut()
            assertThat(host.closes).containsExactly("decrypt failed")
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
            verify(crypto).decrypt(any())
            verify(crypto, never()).disallowUseShortcut()
            assertThat(host.closes).containsExactly("decrypt failed")
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
            assertThat(host.watcher.alarms.map { it.code }).containsExactly(EversenseAlarm.LOW_GLUCOSE)
            assertThat(host.closes).isEmpty()
        }
    }

    @Test fun anE3KeepAliveMarksTheLinkAliveAndReadsTheTransmitter(): Unit = sessionTest { setup ->
        with(setup) {
            receiveOnly()
            link.send(e3Frame(EversenseE3Packets.KeepAlivePush))
            runCurrent()
            assertThat(host.data).isEqualTo(1)
            // The clock first, so the reading has phone time
            assertThat(link.writes.first()[0]).isEqualTo(GetCurrentDatetimePacket().spec.requestId)
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
            assertThat(host.data).isEqualTo(1)
            assertThat(link.writes).isEmpty()

            link.send(byteArrayOf(1, 1, 1, 0x44, 2))
            runCurrent()
            assertThat(host.data).isEqualTo(2)
            assertThat(link.writes).hasSize(1)
        }
    }

    @Test fun dataOnAnotherCharacteristicIsIgnored(): Unit = sessionTest { setup ->
        with(setup) {
            receiveOnly()
            link.send(e3Frame(EversenseE3Packets.KeepAlivePush), characteristic = link.request)
            runCurrent()
            assertThat(host.data).isEqualTo(0)
            assertThat(link.writes).isEmpty()
        }
    }

    // ----- the sign-in -----

    @Test fun anE3ThatTakesTheBondingIsReadyAndSyncs(): Unit = sessionTest { setup ->
        with(setup) {
            link.actAsE3()
            session.start()
            runCurrent()
            assertThat(host.ready).isEqualTo(1)
            assertThat(host.watcher.ready).isEqualTo(1)
            assertThat(link.writes[0][0]).isEqualTo(SaveBondingInformationPacket().spec.requestId)
            // The full sync starts with a ping
            assertThat(link.writes[1][0]).isEqualTo(PingPacket().spec.requestId)
            assertThat(host.closes).isEmpty()
        }
    }

    /** Not ready, but the link stays: the driver's attempt deadline ends it. */
    @Test fun anE3ThatRefusesTheBondingIsNotReady(): Unit = sessionTest { setup ->
        with(setup) {
            link.actAsE3(acceptBonding = false)
            session.start()
            runCurrent()
            assertThat(host.ready).isEqualTo(0)
            assertThat(host.watcher.ready).isEqualTo(0)
            assertThat(link.writes).hasSize(1)
            assertThat(host.closes).isEmpty()
        }
    }

    /** After a reconnect the placement guide is still open: the transmitter left diagnostic mode with the link. */
    @Test fun thePlacementGuideIsResumedAfterAnE3Reconnect(): Unit = sessionTest { setup ->
        with(setup) {
            host.positioningMode = true
            link.actAsE3()
            session.start()
            runCurrent()
            assertThat(link.writes[1][0]).isEqualTo(EnterDiagnosticModePacket().spec.requestId)
        }
    }

    @Test fun refusedNotificationsCloseTheLinkAndSendNothing(): Unit = sessionTest { setup ->
        with(setup) {
            link.subscribeResult = BleResult.Failed(BleFailure(BleFailure.Kind.REFUSED))
            session.start()
            assertThat(host.closes).containsExactly("notifications refused")
            assertThat(link.writes).isEmpty()
        }
    }

    /** A 365 with no login stored (an E3 was chosen without one) cannot register: said as a refused login, nothing sent. */
    @Test fun a365WithoutALoginRecordsARefusedLoginAndSendsNothing(): Unit = sessionTest(is365 = true) { setup ->
        with(setup) {
            val crypto = crypto()
            whenever(crypto.canUseShortcut()).thenReturn(false)
            whenever(crypto.generateKeyPairIfNotExists()).thenReturn(true)
            session.cryptoUtil = crypto
            session.start()
            runCurrent()
            assertThat(store.getBoolean(StorageKeys.LOGIN_REFUSED, false)).isTrue()
            assertThat(host.closes).containsExactly("no login for the registration")
            assertThat(link.writes).isEmpty()
        }
    }

    /** The registration signs in at the Eversense server: offline nothing is sent, neither to the server nor over Bluetooth. */
    @Test fun a365RegistrationOfflineSendsNothing(): Unit = sessionTest(is365 = true) { setup ->
        with(setup) {
            val crypto = crypto()
            whenever(crypto.canUseShortcut()).thenReturn(false)
            whenever(crypto.generateKeyPairIfNotExists()).thenReturn(true)
            session.cryptoUtil = crypto
            host.username = "user"
            host.password = "secret"
            host.online = false
            session.start()
            runCurrent()
            assertThat(host.closes).containsExactly("no internet for the registration")
            assertThat(link.writes).isEmpty()
            assertThat(store.contains(StorageKeys.LOGIN_REFUSED)).isFalse()
        }
    }

    /** A registered 365 signs in with AuthStart over Bluetooth alone: no internet is needed or asked for. */
    @Test fun aRegistered365SignsInOverBluetoothWithoutInternet(): Unit = sessionTest(is365 = true) { setup ->
        with(setup) {
            val crypto = crypto()
            whenever(crypto.canUseShortcut()).thenReturn(true)
            whenever(crypto.generateKeyPairIfNotExists()).thenReturn(true)
            whenever(crypto.generateEphem()).thenReturn(byteArrayOf(1))
            whenever(crypto.getStartSecret(any())).thenReturn(byteArrayOf(1))
            // Stops the sign-in where the session key is made, so the test ends there
            doThrow(IllegalStateException("Authentication reached session-key derivation")).whenever(crypto).generateSessionKey(any())
            session.cryptoUtil = crypto
            host.online = false
            link.onWrite = {
                send(byteArrayOf(1, 1, 1, Eversense365Packets.AuthenticateResponseId, Eversense365Packets.AuthenticateStart) + ByteArray(64))
            }
            session.start()
            runCurrent()
            verify(crypto).generateSessionKey(any())
            assertThat(host.internetAsked).isEqualTo(0)
            assertThat(link.writes).hasSize(1)
            // The forced failure counts as a failed shortcut, and closes the link
            assertThat(host.failures).containsExactly(true)
            assertThat(host.closes).containsExactly("sign-in failed")
        }
    }
}
