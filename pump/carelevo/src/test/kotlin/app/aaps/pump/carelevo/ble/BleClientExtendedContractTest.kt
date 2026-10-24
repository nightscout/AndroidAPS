package app.aaps.pump.carelevo.ble

import app.aaps.pump.carelevo.ble.gatt.FakeGattConnection
import app.aaps.pump.carelevo.ble.gatt.GattConnState
import app.aaps.pump.carelevo.ble.gatt.GattWriteException
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertFailsWith

/**
 * Executable specification for the extended [BleClient] surface:
 * [BleClient.requestMultiple] (multi-response, e.g. Patch Info `0x33`→`0x93`+`0x94`) and
 * [BleClient.requestStream] (streaming/progress, e.g. Safety Check `0x12`→`0x72`…).
 *
 * Same conventions as [BleClientContractTest]: `runTest` virtual time, `backgroundScope`
 * for the client's event collector, `runCurrent()` to flush the subscription.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class BleClientExtendedContractTest {

    private val writeUuid: UUID = UUID.fromString("00000001-0000-1000-8000-00805f9b34fb")
    private val notifyUuid: UUID = UUID.fromString("00000002-0000-1000-8000-00805f9b34fb")

    private lateinit var gatt: FakeGattConnection

    @BeforeEach
    fun setUp() {
        gatt = FakeGattConnection()
    }

    private fun TestScope.newClient(): BleClient {
        val client = BleClientImpl(gatt, writeUuid, notifyUuid, backgroundScope)
        runCurrent()
        return client
    }

    // ===== Multi-response (requestMultiple) =====

    @Test
    fun `multi collects both opcodes and decodes from the map`() = runTest {
        val client = newClient()
        val cmd = fakeMultiCommand(0x33, setOf(0x93.toByte(), 0x94.toByte()))
        gatt.onNextWrite {
            gatt.deliverNotification(notifyUuid, byteArrayOf(0x93.toByte(), 0x00, 0x11))
            gatt.deliverNotification(notifyUuid, byteArrayOf(0x94.toByte(), 0x00, 0x22))
        }

        val resp = client.requestMultiple(cmd)

        assertThat(resp.parts.keys).containsExactly(0x93.toByte(), 0x94.toByte())
        assertThat(resp.parts[0x93.toByte()]!![2]).isEqualTo(0x11.toByte())
        assertThat(resp.parts[0x94.toByte()]!![2]).isEqualTo(0x22.toByte())
        assertThat(gatt.recordedWrites.single().payload[0]).isEqualTo(0x33.toByte())
    }

    @Test
    fun `multi completes regardless of arrival order`() = runTest {
        val client = newClient()
        val cmd = fakeMultiCommand(0x33, setOf(0x93.toByte(), 0x94.toByte()))
        gatt.onNextWrite {
            // RPT2 first, then RPT1 — order must not matter.
            gatt.deliverNotification(notifyUuid, byteArrayOf(0x94.toByte(), 0x00, 0x22))
            gatt.deliverNotification(notifyUuid, byteArrayOf(0x93.toByte(), 0x00, 0x11))
        }

        val resp = client.requestMultiple(cmd)

        assertThat(resp.parts.keys).containsExactly(0x93.toByte(), 0x94.toByte())
    }

    @Test
    fun `multi waits for all opcodes - partial set times out`() = runTest {
        val client = newClient()
        val cmd = fakeMultiCommand(0x33, setOf(0x93.toByte(), 0x94.toByte()))
        gatt.onNextWrite {
            // Only RPT1 arrives; RPT2 never does.
            gatt.deliverNotification(notifyUuid, byteArrayOf(0x93.toByte(), 0x00, 0x11))
        }

        assertFailsWith<TimeoutCancellationException> {
            withTimeout(500) { client.requestMultiple(cmd) }
        }
    }

    @Test
    fun `multi first notification per opcode wins - duplicate goes unsolicited`() = runTest {
        val client = newClient()
        val cmd = fakeMultiCommand(0x33, setOf(0x93.toByte(), 0x94.toByte()))
        val unsolicited = mutableListOf<UnsolicitedMessage>()
        val collectorJob = launch { client.unsolicitedEvents.collect { unsolicited += it } }
        runCurrent()

        gatt.onNextWrite {
            gatt.deliverNotification(notifyUuid, byteArrayOf(0x93.toByte(), 0x00, 0x11)) // first RPT1 (wins)
            gatt.deliverNotification(notifyUuid, byteArrayOf(0x93.toByte(), 0x00, 0x99.toByte())) // duplicate RPT1
            gatt.deliverNotification(notifyUuid, byteArrayOf(0x94.toByte(), 0x00, 0x22)) // RPT2 completes it
        }

        val resp = client.requestMultiple(cmd)
        runCurrent()

        assertThat(resp.parts[0x93.toByte()]!![2]).isEqualTo(0x11.toByte()) // first, not 0x99
        assertThat(unsolicited).hasSize(1)
        assertThat(unsolicited.single().opcode).isEqualTo(0x93.toByte())
        collectorJob.cancel()
    }

    @Test
    fun `multi routes an unsolicited alarm during collection to unsolicitedEvents`() = runTest {
        val client = newClient()
        val cmd = fakeMultiCommand(0x33, setOf(0x93.toByte(), 0x94.toByte()))
        val unsolicited = mutableListOf<UnsolicitedMessage>()
        val collectorJob = launch { client.unsolicitedEvents.collect { unsolicited += it } }
        runCurrent()

        gatt.onNextWrite {
            gatt.deliverNotification(notifyUuid, byteArrayOf(0x93.toByte(), 0x00, 0x11))
            gatt.deliverNotification(notifyUuid, byteArrayOf(0xA1.toByte(), 0x55)) // alarm mid-collection
            gatt.deliverNotification(notifyUuid, byteArrayOf(0x94.toByte(), 0x00, 0x22))
        }

        val resp = client.requestMultiple(cmd)
        runCurrent()

        assertThat(resp.parts.keys).containsExactly(0x93.toByte(), 0x94.toByte())
        assertThat(unsolicited.single().opcode).isEqualTo(0xA1.toByte())
        collectorJob.cancel()
    }

    @Test
    fun `multi disconnect mid-collection aborts with BleDisconnectedException`() = runTest {
        val client = newClient()
        val cmd = fakeMultiCommand(0x33, setOf(0x93.toByte(), 0x94.toByte()))
        gatt.onNextWrite {
            gatt.deliverNotification(notifyUuid, byteArrayOf(0x93.toByte(), 0x00, 0x11))
            gatt.deliverConnectionState(GattConnState.DISCONNECTED) // drop before RPT2
        }

        assertFailsWith<BleDisconnectedException> {
            withTimeout(1000) { client.requestMultiple(cmd) }
        }
    }

    @Test
    fun `multi leaves a clean state for the next request`() = runTest {
        val client = newClient()
        val cmd1 = fakeMultiCommand(0x33, setOf(0x93.toByte(), 0x94.toByte()))
        // Only one part arrives → times out.
        gatt.onNextWrite { gatt.deliverNotification(notifyUuid, byteArrayOf(0x93.toByte(), 0x00)) }
        assertFailsWith<TimeoutCancellationException> {
            withTimeout(500) { client.requestMultiple(cmd1) }
        }

        // A subsequent single request must work — no leaked waiter.
        val cmd2 = fakeSingleCommand(0x24, 0x84.toByte())
        gatt.onNextWrite { gatt.deliverNotification(notifyUuid, byteArrayOf(0x84.toByte(), 0x00)) }
        val resp = client.request(cmd2)
        assertThat(resp.raw[0]).isEqualTo(0x84.toByte())
    }

    // ===== Streaming (requestStream) =====

    @Test
    fun `stream emits progress then terminal and completes`() = runTest {
        val client = newClient()
        val cmd = fakeStreamCommand(0x12, 0x72.toByte())
        gatt.onNextWrite {
            gatt.deliverNotification(notifyUuid, byteArrayOf(0x72.toByte(), PROGRESS)) // progress
            gatt.deliverNotification(notifyUuid, byteArrayOf(0x72.toByte(), TERMINAL)) // SUCCESS
        }

        val results = client.requestStream(cmd).toList()

        assertThat(results).hasSize(2)
        assertThat(results.map { it.terminal }).containsExactly(false, true).inOrder()
    }

    @Test
    fun `stream emits multiple progress events before terminal`() = runTest {
        val client = newClient()
        val cmd = fakeStreamCommand(0x12, 0x72.toByte())
        gatt.onNextWrite {
            gatt.deliverNotification(notifyUuid, byteArrayOf(0x72.toByte(), PROGRESS))
            gatt.deliverNotification(notifyUuid, byteArrayOf(0x72.toByte(), PROGRESS))
            gatt.deliverNotification(notifyUuid, byteArrayOf(0x72.toByte(), TERMINAL))
        }

        val results = client.requestStream(cmd).toList()

        assertThat(results.map { it.terminal }).containsExactly(false, false, true).inOrder()
    }

    @Test
    fun `stream ignores a non-matching notification - routed to unsolicitedEvents`() = runTest {
        val client = newClient()
        val cmd = fakeStreamCommand(0x12, 0x72.toByte())
        val unsolicited = mutableListOf<UnsolicitedMessage>()
        val collectorJob = launch { client.unsolicitedEvents.collect { unsolicited += it } }
        runCurrent()

        gatt.onNextWrite {
            gatt.deliverNotification(notifyUuid, byteArrayOf(0x72.toByte(), PROGRESS))
            gatt.deliverNotification(notifyUuid, byteArrayOf(0xA1.toByte(), 0x55)) // alarm during stream
            gatt.deliverNotification(notifyUuid, byteArrayOf(0x72.toByte(), TERMINAL))
        }

        val results = client.requestStream(cmd).toList()
        runCurrent()

        assertThat(results.map { it.terminal }).containsExactly(false, true).inOrder()
        assertThat(unsolicited.single().opcode).isEqualTo(0xA1.toByte())
        collectorJob.cancel()
    }

    @Test
    fun `stream disconnect mid-stream throws BleDisconnectedException`() = runTest {
        val client = newClient()
        val cmd = fakeStreamCommand(0x12, 0x72.toByte())
        gatt.onNextWrite {
            gatt.deliverNotification(notifyUuid, byteArrayOf(0x72.toByte(), PROGRESS))
            gatt.deliverConnectionState(GattConnState.DISCONNECTED) // drop before terminal
        }

        assertFailsWith<BleDisconnectedException> {
            withTimeout(1000) { client.requestStream(cmd).toList() }
        }
    }

    @Test
    fun `stream without terminal times out`() = runTest {
        val client = newClient()
        val cmd = fakeStreamCommand(0x12, 0x72.toByte())
        gatt.onNextWrite {
            gatt.deliverNotification(notifyUuid, byteArrayOf(0x72.toByte(), PROGRESS)) // no terminal ever
        }

        assertFailsWith<TimeoutCancellationException> {
            withTimeout(500) { client.requestStream(cmd).toList() }
        }
    }

    @Test
    fun `stream holds the request slot until it terminates - a concurrent request waits`() = runTest {
        val client = newClient()
        val stream = fakeStreamCommand(0x12, 0x72.toByte())
        val single = fakeSingleCommand(0x24, 0x84.toByte())

        // The stream write delivers only progress until we release it below.
        gatt.onNextWrite {
            gatt.deliverNotification(notifyUuid, byteArrayOf(0x72.toByte(), PROGRESS))
        }
        // The single request's write (runs only after the stream frees the mutex).
        gatt.onNextWrite {
            gatt.deliverNotification(notifyUuid, byteArrayOf(0x84.toByte(), 0x00))
        }

        val streamResults = mutableListOf<FakeStreamResponse>()
        val streamJob = async { client.requestStream(stream).toList().also { streamResults += it } }
        val singleJob = async { client.request(single) }
        runCurrent()

        // While the stream is open (only progress so far) the single request cannot have run.
        assertThat(gatt.recordedWrites).hasSize(1)
        assertThat(singleJob.isCompleted).isFalse()

        // Deliver the terminal → stream completes, mutex frees, single request proceeds.
        gatt.deliverNotification(notifyUuid, byteArrayOf(0x72.toByte(), TERMINAL))
        streamJob.await()
        val resp = singleJob.await()

        assertThat(streamResults.map { it.terminal }).containsExactly(false, true).inOrder()
        assertThat(resp.raw[0]).isEqualTo(0x84.toByte())
        assertThat(gatt.recordedWrites).hasSize(2)
    }

    @Test
    fun `stream isTerminal throw ends the stream and does not brick the client`() = runTest {
        val client = newClient()
        val cmd = object : BleStreamCommand<FakeStreamResponse> {
            override val requestOpcode: Byte = 0x12
            override val expectedResponseOpcode: Byte = 0x72.toByte()
            override fun encode(): ByteArray = byteArrayOf(requestOpcode)
            override fun decode(responsePayload: ByteArray) = FakeStreamResponse(responsePayload, terminal = false)
            override fun isTerminal(response: FakeStreamResponse): Boolean = error("boom in isTerminal")
        }
        gatt.onNextWrite { gatt.deliverNotification(notifyUuid, byteArrayOf(0x72.toByte(), PROGRESS)) }

        // The stream fails (not hangs) with the thrown cause...
        assertFailsWith<IllegalStateException> {
            withTimeout(1000) { client.requestStream(cmd).toList() }
        }
        // ...and — critically — the sole event collector survived: a later request works.
        val single = fakeSingleCommand(0x24, 0x84.toByte())
        gatt.onNextWrite { gatt.deliverNotification(notifyUuid, byteArrayOf(0x84.toByte(), 0x00)) }
        assertThat(client.request(single).raw[0]).isEqualTo(0x84.toByte())
    }

    @Test
    fun `stream decode failure ends the stream with the error and does not leak to unsolicited`() = runTest {
        val client = newClient()
        val unsolicited = mutableListOf<UnsolicitedMessage>()
        val collectorJob = launch { client.unsolicitedEvents.collect { unsolicited += it } }
        runCurrent()
        val cmd = object : BleStreamCommand<FakeStreamResponse> {
            override val requestOpcode: Byte = 0x12
            override val expectedResponseOpcode: Byte = 0x72.toByte()
            override fun encode(): ByteArray = byteArrayOf(requestOpcode)
            override fun decode(responsePayload: ByteArray): FakeStreamResponse {
                if (responsePayload.size > 1 && responsePayload[1] == POISON) error("boom in decode")
                return FakeStreamResponse(responsePayload, terminal = responsePayload.size > 1 && responsePayload[1] == TERMINAL)
            }

            override fun isTerminal(response: FakeStreamResponse): Boolean = response.terminal
        }
        gatt.onNextWrite {
            gatt.deliverNotification(notifyUuid, byteArrayOf(0x72.toByte(), PROGRESS))
            gatt.deliverNotification(notifyUuid, byteArrayOf(0x72.toByte(), POISON)) // decode throws on this
        }

        assertFailsWith<IllegalStateException> {
            withTimeout(1000) { client.requestStream(cmd).toList() }
        }
        runCurrent()

        assertThat(unsolicited).isEmpty() // poison frame consumed by the stream, not leaked
        val single = fakeSingleCommand(0x24, 0x84.toByte())
        gatt.onNextWrite { gatt.deliverNotification(notifyUuid, byteArrayOf(0x84.toByte(), 0x00)) }
        assertThat(client.request(single).raw[0]).isEqualTo(0x84.toByte())
        collectorJob.cancel()
    }

    @Test
    fun `stream cancelled mid-flight frees the request slot for the next request`() = runTest {
        val client = newClient()
        val cmd = fakeStreamCommand(0x12, 0x72.toByte())
        gatt.onNextWrite { gatt.deliverNotification(notifyUuid, byteArrayOf(0x72.toByte(), PROGRESS)) }

        // Collect the (still-open, no terminal) stream in a job, then cancel it mid-flight.
        val streamJob = launch { client.requestStream(cmd).collect { } }
        runCurrent()
        streamJob.cancel()
        streamJob.join()

        // The long-held request mutex must have been freed on cancellation.
        val single = fakeSingleCommand(0x24, 0x84.toByte())
        gatt.onNextWrite { gatt.deliverNotification(notifyUuid, byteArrayOf(0x84.toByte(), 0x00)) }
        assertThat(client.request(single).raw[0]).isEqualTo(0x84.toByte())
    }

    @Test
    fun `stream is cold - no write until collected`() = runTest {
        val client = newClient()
        val cmd = fakeStreamCommand(0x12, 0x72.toByte())

        val flow = client.requestStream(cmd)
        runCurrent()
        assertThat(gatt.recordedWrites).isEmpty() // not collected → no write, no mutex taken

        gatt.onNextWrite { gatt.deliverNotification(notifyUuid, byteArrayOf(0x72.toByte(), TERMINAL)) }
        flow.toList()
        assertThat(gatt.recordedWrites).hasSize(1)
    }

    @Test
    fun `stream write failure surfaces GattWriteException and leaves clean state`() = runTest {
        val client = newClient()
        val cmd = fakeStreamCommand(0x12, 0x72.toByte())
        gatt.scriptNextWriteFailure("stack rejected")

        assertFailsWith<GattWriteException> { client.requestStream(cmd).toList() }

        val single = fakeSingleCommand(0x24, 0x84.toByte())
        gatt.onNextWrite { gatt.deliverNotification(notifyUuid, byteArrayOf(0x84.toByte(), 0x00)) }
        assertThat(client.request(single).raw[0]).isEqualTo(0x84.toByte())
    }

    @Test
    fun `multi with empty expectedResponseOpcodes fails fast`() = runTest {
        val client = newClient()

        assertFailsWith<IllegalArgumentException> {
            client.requestMultiple(fakeMultiCommand(0x33, emptySet()))
        }

        // The guard fires before the write, releasing the mutex with no leaked waiter.
        val single = fakeSingleCommand(0x24, 0x84.toByte())
        gatt.onNextWrite { gatt.deliverNotification(notifyUuid, byteArrayOf(0x84.toByte(), 0x00)) }
        assertThat(client.request(single).raw[0]).isEqualTo(0x84.toByte())
    }

    @Test
    fun `multi with a single-opcode set completes on one notification`() = runTest {
        val client = newClient()
        val cmd = fakeMultiCommand(0x33, setOf(0x93.toByte()))
        gatt.onNextWrite { gatt.deliverNotification(notifyUuid, byteArrayOf(0x93.toByte(), 0x00, 0x11)) }

        val resp = client.requestMultiple(cmd)

        assertThat(resp.parts.keys).containsExactly(0x93.toByte())
    }

    @Test
    fun `multi write failure surfaces GattWriteException and leaves clean state`() = runTest {
        val client = newClient()
        val cmd = fakeMultiCommand(0x33, setOf(0x93.toByte(), 0x94.toByte()))
        gatt.scriptNextWriteFailure("stack rejected")

        assertFailsWith<GattWriteException> { client.requestMultiple(cmd) }

        val single = fakeSingleCommand(0x24, 0x84.toByte())
        gatt.onNextWrite { gatt.deliverNotification(notifyUuid, byteArrayOf(0x84.toByte(), 0x00)) }
        assertThat(client.request(single).raw[0]).isEqualTo(0x84.toByte())
    }

    // ===== Test fixtures =====

    private data class FakeSingleResponse(val raw: ByteArray) : BleResponse
    private data class FakeMultiResponse(val parts: Map<Byte, ByteArray>) : BleResponse
    private data class FakeStreamResponse(val raw: ByteArray, val terminal: Boolean) : BleResponse

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

    private fun fakeStreamCommand(requestOpcode: Int, expected: Byte): BleStreamCommand<FakeStreamResponse> =
        object : BleStreamCommand<FakeStreamResponse> {
            override val requestOpcode: Byte = requestOpcode.toByte()
            override val expectedResponseOpcode: Byte = expected
            override fun encode(): ByteArray = byteArrayOf(this.requestOpcode)
            override fun decode(responsePayload: ByteArray) =
                FakeStreamResponse(responsePayload, terminal = responsePayload.size > 1 && responsePayload[1] == TERMINAL)

            override fun isTerminal(response: FakeStreamResponse): Boolean = response.terminal
        }

    private companion object {

        const val PROGRESS: Byte = 0x04 // REP_REQUEST — a progress notification
        const val TERMINAL: Byte = 0x00 // SUCCESS — the terminal notification
        const val POISON: Byte = 0x7F // a frame whose decode() throws, for the decode-failure test
    }
}
