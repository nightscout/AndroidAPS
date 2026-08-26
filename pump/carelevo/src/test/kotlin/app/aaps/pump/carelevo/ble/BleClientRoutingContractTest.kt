package app.aaps.pump.carelevo.ble

import app.aaps.pump.carelevo.ble.gatt.FakeGattConnection
import app.aaps.pump.carelevo.ble.gatt.GattConnState
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

/**
 * Executable specification for [BleClientImpl]'s **event router** — the paths a happy-path exchange never
 * reaches, and which exist precisely because a real pump does not cooperate:
 * - a frame the active waiter cannot consume (duplicate / already-settled) falling through to
 *   [BleClient.unsolicitedEvents] instead of vanishing inside a settled waiter,
 * - the **abandoned waiter** re-route: the requester unwound (cancel/timeout) after the router had already
 *   absorbed the response, so the frame is handed back and re-routed rather than silently swallowed — the
 *   real case being the ack of a bolus that DID start while our side gave up,
 * - non-notification events (connection-state, discovery, write-ack) staying inert.
 *
 * Companion to [BleClientContractTest] (single-response correlation) and [BleClientExtendedContractTest]
 * (multi/stream); same conventions — `runTest` virtual time, [FakeGattConnection], `runCurrent()` to flush
 * the router subscription.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class BleClientRoutingContractTest {

    private val writeUuid: UUID = UUID.fromString("00000001-0000-1000-8000-00805f9b34fb")
    private val notifyUuid: UUID = UUID.fromString("00000002-0000-1000-8000-00805f9b34fb")

    private lateinit var gatt: FakeGattConnection
    private var inlineRouterScope: CoroutineScope? = null

    @BeforeEach
    fun setUp() {
        gatt = FakeGattConnection()
    }

    @AfterEach
    fun tearDown() {
        // Not a child of the test job (see newInlineRouterClient), so stop it explicitly.
        inlineRouterScope?.cancel()
    }

    /** Router on the test scheduler — flushed with `runCurrent()`, as in the sibling contract tests. */
    private fun TestScope.newClient(): BleClient {
        val client = BleClientImpl(gatt, writeUuid, notifyUuid, backgroundScope)
        runCurrent()
        return client
    }

    /**
     * A client whose event router runs **unconfined**, so [FakeGattConnection.deliverNotification] routes
     * the frame INLINE, inside the `deliverNotification` call.
     *
     * That is what makes the abandon race reachable: the router completes the waiter's deferred, which only
     * *queues* the requester's continuation on the test dispatcher, so the test can cancel the requester in
     * that window. With the router on the same StandardTestDispatcher both would drain inside one
     * `runCurrent()` and the request would always win.
     */
    private fun TestScope.newInlineRouterClient(): BleClient {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        inlineRouterScope = scope
        // Unconfined: the router's collect subscribes eagerly during construction, so no runCurrent().
        return BleClientImpl(gatt, writeUuid, notifyUuid, scope)
    }

    // ===== Frames the active waiter cannot consume =====

    @Test
    fun `a duplicate response frame arriving before the requester resumes goes to unsolicitedEvents`() = runTest {
        val client = newClient()
        val unsolicited = mutableListOf<UnsolicitedMessage>()
        val collectorJob = launch { client.unsolicitedEvents.collect { unsolicited += it } }
        runCurrent()
        val cmd = fakeSingleCommand(0x24, 0x84.toByte())
        gatt.onNextWrite {
            gatt.deliverNotification(notifyUuid, byteArrayOf(0x84.toByte(), 0x01)) // the real response
            gatt.deliverNotification(notifyUuid, byteArrayOf(0x84.toByte(), 0x02)) // pump repeats it
        }

        // Both frames are routed before the request's continuation runs — the waiter is still registered
        // for the duplicate, and its deferred is already settled.
        val resp = client.request(cmd)
        runCurrent()

        assertThat(resp.raw[1]).isEqualTo(0x01.toByte()) // first frame wins the deferred
        assertThat(unsolicited).hasSize(1)
        assertThat(unsolicited.single().payload[1]).isEqualTo(0x02.toByte())
        collectorJob.cancel()
    }

    @Test
    fun `an empty notification payload is dropped and does not disturb routing`() = runTest {
        val client = newClient()
        val unsolicited = mutableListOf<UnsolicitedMessage>()
        val collectorJob = launch { client.unsolicitedEvents.collect { unsolicited += it } }
        runCurrent()

        gatt.deliverNotification(notifyUuid, byteArrayOf()) // no opcode byte to route on
        runCurrent()

        assertThat(unsolicited).isEmpty()
        // The router shrugged it off: a later request still correlates.
        val cmd = fakeSingleCommand(0x24, 0x84.toByte())
        gatt.onNextWrite { gatt.deliverNotification(notifyUuid, byteArrayOf(0x84.toByte(), 0x00)) }
        assertThat(client.request(cmd).raw[0]).isEqualTo(0x84.toByte())
        collectorJob.cancel()
    }

    // ===== Abandoned waiters (requester unwound without consuming the response) =====

    @Test
    fun `a single request abandoned after the router absorbed its response re-routes it to unsolicitedEvents`() = runTest {
        val client = newInlineRouterClient()
        val unsolicited = mutableListOf<UnsolicitedMessage>()
        val collectorJob = launch { client.unsolicitedEvents.collect { unsolicited += it } }
        runCurrent()
        val cmd = fakeSingleCommand(0x24, 0x84.toByte())
        val pending = launch { client.request(cmd) }
        runCurrent() // write out; waiter registered and awaiting

        // Routed INLINE → the deferred completes, but the requester's continuation is only queued...
        gatt.deliverNotification(notifyUuid, byteArrayOf(0x84.toByte(), 0x42))
        // ...and the requester is cancelled before it can run, so nobody ever consumes the response the
        // pump really sent.
        pending.cancel()
        runCurrent()

        // It must resurface as unsolicited rather than being silently swallowed.
        assertThat(unsolicited).hasSize(1)
        assertThat(unsolicited.single().opcode).isEqualTo(0x84.toByte())
        assertThat(unsolicited.single().payload[1]).isEqualTo(0x42.toByte())
        collectorJob.cancel()
    }

    @Test
    fun `a multi request abandoned after collecting its frames re-routes all of them to unsolicitedEvents`() = runTest {
        val client = newInlineRouterClient()
        val unsolicited = mutableListOf<UnsolicitedMessage>()
        val collectorJob = launch { client.unsolicitedEvents.collect { unsolicited += it } }
        runCurrent()
        val cmd = fakeMultiCommand(0x33, setOf(0x93.toByte(), 0x94.toByte()))
        val pending = launch { client.requestMultiple(cmd) }
        runCurrent()

        gatt.deliverNotification(notifyUuid, byteArrayOf(0x93.toByte(), 0x00, 0x11))
        gatt.deliverNotification(notifyUuid, byteArrayOf(0x94.toByte(), 0x00, 0x22)) // completes the waiter
        pending.cancel()
        runCurrent()

        // Every collected part comes back out — a complete round is never lost just because our side unwound.
        assertThat(unsolicited.map { it.opcode }).containsExactly(0x93.toByte(), 0x94.toByte())
        collectorJob.cancel()
    }

    // ===== Non-notification events =====

    @Test
    fun `a CONNECTED state change does not abort the pending request`() = runTest {
        val client = newClient()
        val cmd = fakeSingleCommand(0x24, 0x84.toByte())
        gatt.onNextWrite {
            // Only DISCONNECTED aborts the waiter; every other transition must be inert.
            gatt.deliverConnectionState(GattConnState.CONNECTED)
            gatt.deliverNotification(notifyUuid, byteArrayOf(0x84.toByte(), 0x00))
        }

        assertThat(client.request(cmd).raw[0]).isEqualTo(0x84.toByte())
    }

    @Test
    fun `service-discovery and write-ack events are ignored by the router`() = runTest {
        val client = newClient()
        val unsolicited = mutableListOf<UnsolicitedMessage>()
        val collectorJob = launch { client.unsolicitedEvents.collect { unsolicited += it } }
        runCurrent()

        gatt.discoverServices() // emits ServicesDiscovered on the same events flow
        runCurrent()

        val cmd = fakeSingleCommand(0x24, 0x84.toByte())
        gatt.onNextWrite { gatt.deliverNotification(notifyUuid, byteArrayOf(0x84.toByte(), 0x00)) }
        assertThat(client.request(cmd).raw[0]).isEqualTo(0x84.toByte())
        runCurrent()

        // Neither ServicesDiscovered nor the write's WriteAck is a protocol frame — they must not reach
        // the alarm/status consumers.
        assertThat(unsolicited).isEmpty()
        collectorJob.cancel()
    }

    // ===== Test fixtures =====

    private data class FakeSingleResponse(val raw: ByteArray) : BleResponse
    private data class FakeMultiResponse(val parts: Map<Byte, ByteArray>) : BleResponse

    private fun fakeSingleCommand(requestOpcode: Int, expected: Byte): BleCommand<FakeSingleResponse> =
        object : BleCommand<FakeSingleResponse> {
            override val requestOpcode: Byte = requestOpcode.toByte()
            override val expectedResponseOpcode: Byte = expected
            override fun encode(): ByteArray = byteArrayOf(this.requestOpcode)
            override fun decode(responsePayload: ByteArray) = FakeSingleResponse(responsePayload)
        }

    private fun fakeMultiCommand(requestOpcode: Int, expected: Set<Byte>): BleMultiCommand<FakeMultiResponse> =
        object : BleMultiCommand<FakeMultiResponse> {
            override val requestOpcode: Byte = requestOpcode.toByte()
            override val expectedResponseOpcodes: Set<Byte> = expected
            override fun encode(): ByteArray = byteArrayOf(this.requestOpcode)
            override fun decode(responses: Map<Byte, ByteArray>) = FakeMultiResponse(responses)
        }
}
