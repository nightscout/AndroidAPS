package app.aaps.plugins.constraints.objectives

import app.aaps.core.interfaces.local.LocaleDependentSetting
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours

/**
 * What the iOS time check makes of an answer, or of none. The network is replaced by [FakeTransport];
 * [PosixSntpTransportTest] runs the real exchange.
 */
class IosSntpClientTest {

    /** Answers with what [answer] builds, and keeps what it was asked. */
    private class FakeTransport(
        private val answer: (request: ByteArray) -> SntpExchange?
    ) : SntpTransport {

        var calls = 0
        var host = ""
        var port = 0
        var timeoutMs = 0L
        var request = ByteArray(0)

        override suspend fun exchange(host: String, port: Int, timeoutMs: Long, buildRequest: (requestTime: Long) -> ByteArray): SntpExchange? {
            calls++
            this.host = host
            this.port = port
            this.timeoutMs = timeoutMs
            request = buildRequest(REQUEST_TIME)
            return answer(request)
        }
    }

    private val settings = object : LocaleDependentSetting {
        override val ntpServer = "time.example.org"
    }

    private fun client(transport: SntpTransport) = IosSntpClient(SilentLogger(), settings, transport)

    @Test
    fun `without a network nothing is sent`() = runBlocking<Unit> {
        val transport = FakeTransport { null }

        val result = client(transport).ntpTime(isConnected = false)

        assertFalse(result.success)
        assertFalse(result.networkConnected)
        assertEquals(0, transport.calls)
    }

    /** The objectives screen says "failed to retrieve time" for this, and "not connected" for the case above. */
    @Test
    fun `no answer is a failure with the network up`() = runBlocking<Unit> {
        val result = client(FakeTransport { null }).ntpTime(isConnected = true)

        assertFalse(result.success)
        assertTrue(result.networkConnected)
    }

    @Test
    fun `the request goes to the configured server on the NTP port`() = runBlocking<Unit> {
        val transport = FakeTransport { null }

        client(transport).ntpTime(isConnected = true)

        assertEquals("time.example.org", transport.host)
        assertEquals(123, transport.port)
        assertEquals(IosSntpClient.TIMEOUT_MS, transport.timeoutMs)
    }

    @Test
    fun `the request is a client packet stamped with the send time`() = runBlocking<Unit> {
        val transport = FakeTransport { null }

        client(transport).ntpTime(isConnected = true)

        assertEquals(SntpPacket.NTP_PACKET_SIZE, transport.request.size)
        // Version 3, mode 3 (client).
        assertEquals(0x1B.toByte(), transport.request[0])
        // The last byte is random, so the stamp can be off by less than a millisecond.
        val stamped = SntpPacket.readTimeStamp(transport.request, TRANSMIT_OFFSET)
        assertTrue(stamped in REQUEST_TIME - 1..REQUEST_TIME, "stamped $stamped")
    }

    /** The point of the whole check: a phone clock that is an hour behind is not believed. */
    @Test
    fun `the server time is used and not the phone clock`() = runBlocking<Unit> {
        val serverTime = REQUEST_TIME + 1.hours.inWholeMilliseconds
        val transport = FakeTransport { request ->
            val ticks = monotonicMillis()
            SntpExchange(serverAnswer(request, serverTime), REQUEST_TIME, ticks, ticks)
        }

        val result = client(transport).ntpTime(isConnected = true)

        assertTrue(result.success)
        assertTrue(result.networkConnected)
        // Moved on only by the few milliseconds the test itself took.
        assertTrue(result.time in serverTime - 2..serverTime + 2_000, "time ${result.time}, server $serverTime")
    }

    /**
     * A round trip of 200 ms, answered half way, from a server whose clock agrees with the phone. The
     * delay must not be mistaken for a clock difference.
     */
    @Test
    fun `the network delay is not taken for a clock difference`() = runBlocking<Unit> {
        val transport = FakeTransport { request ->
            val ticks = monotonicMillis()
            SntpExchange(serverAnswer(request, REQUEST_TIME + 100), REQUEST_TIME, ticks, ticks + 200)
        }

        val result = client(transport).ntpTime(isConnected = true)

        assertTrue(result.success)
        assertTrue(result.time in REQUEST_TIME - 2..REQUEST_TIME + 2_000, "time ${result.time}, request $REQUEST_TIME")
    }

    private companion object {

        /** 2026-11-19, a fixed time so the arithmetic can be checked exactly. */
        const val REQUEST_TIME = 1_795_124_000_000L
    }
}
