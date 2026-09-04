package app.aaps.plugins.sync.nsclientV3.ws

import app.aaps.core.keys.StringKey
import app.aaps.plugins.sync.nsclientV3.NSClientV3Plugin
import app.aaps.plugins.sync.nsclientV3.compose.NSClientRepositoryImpl
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * What the app says to a Nightscout socket when it connects, and what it does when one drops.
 *
 * This was written twice - `NSClientV3Service` with `org.json`, [SocketNsConnection] with kotlinx -
 * and tested neither time. The frame handlers next door were pinned by two characterization suites
 * before being merged; these four were merged with nothing watching, so the tests come after rather
 * than before, and they say what the merged version must keep doing.
 *
 * The collection list gets the most attention here. It is what the app asks Nightscout to push, and
 * a name present on one platform and missing on another is not a visible failure - it is a
 * collection that silently never arrives, which is how `settings` came to be dead on iOS.
 */
class NsConnectHandlerTest : TestBaseWithProfile() {

    /** Captures what was emitted, and hands back whatever the test wants Nightscout to have said. */
    private class FakeSocket(private val reply: String, override val id: String? = "socket-1") : NsSocket {

        var emittedEvent: String? = null
        var emittedPayload: String? = null

        override fun on(event: String, listener: (payload: String) -> Unit) = Unit
        override fun connect() = Unit
        override fun close() = Unit
        override fun emitAlarmAck(level: Int, group: String, silenceForMillis: Long) = Unit

        override fun emitWithAck(event: String, payload: String, ack: (response: String) -> Unit) {
            emittedEvent = event
            emittedPayload = payload
            ack(reply)
        }
    }

    @Mock lateinit var nsClientV3Plugin: NSClientV3Plugin

    private lateinit var sut: NsConnectHandler

    @BeforeEach
    fun setUp() {
        sut = NsConnectHandler(aapsLogger, preferences, NSClientRepositoryImpl(rxBus, aapsLogger)) { nsClientV3Plugin }
        whenever(preferences.get(StringKey.NsClientAccessToken)).thenReturn("token")
        // The handler reports the plugin's status to the repository after every outcome.
        whenever(nsClientV3Plugin.status).thenReturn("status")
    }

    private fun success(message: String = "ok") =
        """{"success":true,"message":"$message","collections":"entries"}"""

    // ---- storage ----------------------------------------------------------------------------

    /**
     * The alignment this class exists for: every collection the app needs, in one list. A name
     * missing here is a collection Nightscout never pushes, and nothing reports that.
     */
    @Test
    fun `the storage subscribe asks for every collection`() {
        val socket = FakeSocket(success())

        sut.onConnectStorage(socket) {}

        assertThat(socket.emittedEvent).isEqualTo("subscribe")
        listOf("devicestatus", "entries", "profile", "treatments", "foods", "settings").forEach {
            assertThat(socket.emittedPayload).contains(it)
        }
    }

    @Test
    fun `the storage subscribe carries the access token`() {
        val socket = FakeSocket(success())

        sut.onConnectStorage(socket) {}

        assertThat(socket.emittedPayload).contains("token")
    }

    /**
     * A reconnect has to catch up: nothing arrived while the socket was down, so the next round must
     * be a full load rather than a push-driven one.
     */
    @Test
    fun `a successful subscribe reports connected and asks for a catch-up load`() {
        var connected: Boolean? = null

        sut.onConnectStorage(FakeSocket(success())) { connected = it }

        assertThat(connected).isTrue()
        verify(nsClientV3Plugin).initialLoadFinished = false
        verify(nsClientV3Plugin).executeLoop("WS_CONNECT")
    }

    /** Nightscout saying no is an answer, not a crash - and it must not look like a connection. */
    @Test
    fun `a refused subscribe reports not connected and loads nothing`() {
        var connected: Boolean? = null

        sut.onConnectStorage(FakeSocket("""{"success":false}""")) { connected = it }

        assertThat(connected).isFalse()
        verify(nsClientV3Plugin, never()).executeLoop(any())
    }

    /** Unparseable is treated as refused rather than thrown: the socket is not to be trusted. */
    @Test
    fun `an unreadable reply is treated as refused`() {
        var connected: Boolean? = null

        sut.onConnectStorage(FakeSocket("not json at all")) { connected = it }

        assertThat(connected).isFalse()
    }

    /** Called from a socket callback, so a socket that vanished in between must not take the app down. */
    @Test
    fun `a missing storage socket is survived`() {
        var called = false

        sut.onConnectStorage(null) { called = true }

        assertThat(called).isFalse()
    }

    // ---- alarms -----------------------------------------------------------------------------

    @Test
    fun `the alarm subscribe carries the token and nothing else`() {
        val socket = FakeSocket(success())

        sut.onConnectAlarms(socket)

        assertThat(socket.emittedEvent).isEqualTo("subscribe")
        assertThat(socket.emittedPayload).contains("token")
        assertThat(socket.emittedPayload).doesNotContain("collections")
    }

    @Test
    fun `a missing alarm socket is survived`() {
        sut.onConnectAlarms(null)
    }

    // ---- disconnects ------------------------------------------------------------------------

    /** Whatever arrived while the socket was up may now be behind, so the next load starts over. */
    @Test
    fun `a storage disconnect reports it and forces a fresh load`() {
        var disconnected = false

        sut.onDisconnectStorage("transport close") { disconnected = true }

        assertThat(disconnected).isTrue()
        verify(nsClientV3Plugin).initialLoadFinished = false
    }

    /**
     * Android logged this and the other platforms did not, so an alarm socket dropping was invisible
     * on iOS and the desktop. Nothing else depends on it - being able to read it is the point.
     */
    @Test
    fun `an alarm disconnect is survived and does not touch the load state`() {
        sut.onDisconnectAlarm("ping timeout")

        verify(nsClientV3Plugin, never()).executeLoop(any())
    }
}
