package app.aaps.pump.carelevo.ble

import app.aaps.pump.carelevo.ble.gatt.FakeGattConnection
import app.aaps.pump.carelevo.ble.gatt.GattConnState
import app.aaps.pump.carelevo.ble.gatt.GattWriteException
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.take
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
 * Executable specification for [BleClient].
 *
 * Uses `runTest` virtual time throughout — no real-time waits, no `Thread.sleep`,
 * no flakiness. `withTimeout(...)` advances virtual time deterministically.
 *
 * The client is created inside each test so it can be bound to the `TestScope`
 * `backgroundScope` — its event collector runs on the test's scheduler and stops
 * automatically at the end of the test.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class BleClientContractTest {

    private val writeUuid: UUID = UUID.fromString("00000001-0000-1000-8000-00805f9b34fb")
    private val notifyUuid: UUID = UUID.fromString("00000002-0000-1000-8000-00805f9b34fb")

    private lateinit var gatt: FakeGattConnection

    @BeforeEach
    fun setUp() {
        gatt = FakeGattConnection()
    }

    /** Construct the client and flush pending coroutines so the event collector is subscribed. */
    private fun TestScope.newClient(): BleClient {
        val client = BleClientImpl(gatt, writeUuid, notifyUuid, backgroundScope)
        runCurrent()
        return client
    }

    @Test
    fun `01 opcode match happy path returns parsed response`() = runTest {
        val client = newClient()
        val cmd = fakeCommand(
            requestOpcode = 0x24,
            expectedResponseOpcode = 0x84.toByte(),
            body = byteArrayOf(0x01, 0x02)
        )
        gatt.onNextWrite {
            gatt.deliverNotification(
                notifyUuid,
                byteArrayOf(0x84.toByte(), 0xFF.toByte(), 0x10)
            )
        }

        val resp = client.request(cmd)

        assertThat(resp.raw.contentEquals(byteArrayOf(0x84.toByte(), 0xFF.toByte(), 0x10))).isTrue()
        assertThat(gatt.recordedWrites).hasSize(1)
        assertThat(gatt.recordedWrites.single().uuid).isEqualTo(writeUuid)
        assertThat(gatt.recordedWrites.single().payload[0]).isEqualTo(0x24.toByte())
    }

    @Test
    fun `02 mismatched response opcode is ignored waiter keeps waiting`() = runTest {
        val client = newClient()
        val cmd = fakeCommand(
            requestOpcode = 0x24,
            expectedResponseOpcode = 0x84.toByte(),
            body = byteArrayOf(0x01)
        )
        gatt.onNextWrite {
            // Wrong opcode — BleClient must not complete the deferred with this.
            gatt.deliverNotification(notifyUuid, byteArrayOf(0x85.toByte(), 0x00))
        }

        assertFailsWith<TimeoutCancellationException> {
            withTimeout(500) { client.request(cmd) }
        }
    }

    @Test
    fun `03 bolus actionId mismatch is ignored`() = runTest {
        val client = newClient()
        // Command expects actionId=3 echoed back at byte 1 of the response.
        val cmd = fakeCommand(
            requestOpcode = 0x24,
            expectedResponseOpcode = 0x84.toByte(),
            body = byteArrayOf(0x03),
            correlationByte = 0x03
        )
        gatt.onNextWrite {
            // Correct opcode but wrong actionId — must be rejected.
            gatt.deliverNotification(notifyUuid, byteArrayOf(0x84.toByte(), 0x07, 0x00))
        }

        assertFailsWith<TimeoutCancellationException> {
            withTimeout(500) { client.request(cmd) }
        }
    }

    @Test
    fun `04 unsolicited alarm during request goes to unsolicitedEvents not the waiter`() = runTest {
        val client = newClient()
        val cmd = fakeCommand(0x24, 0x84.toByte(), byteArrayOf(0x01))
        val unsolicited = mutableListOf<UnsolicitedMessage>()
        val collectorJob = launch {
            client.unsolicitedEvents.collect { unsolicited += it }
        }
        runCurrent() // ensure collector is attached before we emit

        gatt.onNextWrite {
            // Alarm (unsolicited) arrives during the request...
            gatt.deliverNotification(notifyUuid, byteArrayOf(0xA1.toByte(), 0x11))
            // ...then the real response.
            gatt.deliverNotification(notifyUuid, byteArrayOf(0x84.toByte(), 0x00))
        }

        val resp = client.request(cmd)
        runCurrent()

        assertThat(resp.raw[0]).isEqualTo(0x84.toByte())
        assertThat(unsolicited).hasSize(1)
        assertThat(unsolicited.single().opcode).isEqualTo(0xA1.toByte())
        collectorJob.cancel()
    }

    @Test
    fun `05 concurrent requests are serialized in call order`() = runTest {
        val client = newClient()
        val cmd1 = fakeCommand(
            0x24, 0x84.toByte(), byteArrayOf(0x01), correlationByte = 0x01
        )
        val cmd2 = fakeCommand(
            0x24, 0x84.toByte(), byteArrayOf(0x02), correlationByte = 0x02
        )

        // Each write echoes back the actionId it received.
        val echoResponse: suspend (FakeGattConnection.Write) -> Unit = { w ->
            gatt.deliverNotification(notifyUuid, byteArrayOf(0x84.toByte(), w.payload[1], 0x00))
        }
        gatt.onNextWrite(echoResponse)
        gatt.onNextWrite(echoResponse)

        val a = async { client.request(cmd1) }
        val b = async { client.request(cmd2) }

        a.await()
        b.await()

        assertThat(gatt.recordedWrites).hasSize(2)
        assertThat(gatt.recordedWrites[0].payload[1]).isEqualTo(0x01.toByte())
        assertThat(gatt.recordedWrites[1].payload[1]).isEqualTo(0x02.toByte())
    }

    @Test
    fun `06 timeout leaves client in a clean state for the next request`() = runTest {
        val client = newClient()
        val cmd1 = fakeCommand(0x24, 0x84.toByte(), byteArrayOf(0x01))
        // No scripted response — pump stays silent.

        assertFailsWith<TimeoutCancellationException> {
            withTimeout(500) { client.request(cmd1) }
        }

        // Immediately issue another request; if BleClient leaked a deferred it would stay blocked.
        val cmd2 = fakeCommand(0x25, 0x85.toByte(), byteArrayOf(0x01))
        gatt.onNextWrite {
            gatt.deliverNotification(notifyUuid, byteArrayOf(0x85.toByte(), 0x00))
        }

        val resp = client.request(cmd2)
        assertThat(resp.raw[0]).isEqualTo(0x85.toByte())
    }

    @Test
    fun `07 BLE write failure surfaces as GattWriteException`() = runTest {
        val client = newClient()
        val cmd = fakeCommand(0x24, 0x84.toByte(), byteArrayOf(0x01))
        gatt.scriptNextWriteFailure("stack rejected")

        assertFailsWith<GattWriteException> {
            client.request(cmd)
        }
    }

    @Test
    fun `08 disconnect mid-request completes the waiter exceptionally`() = runTest {
        val client = newClient()
        val cmd = fakeCommand(0x24, 0x84.toByte(), byteArrayOf(0x01))
        gatt.onNextWrite {
            gatt.deliverConnectionState(GattConnState.DISCONNECTED)
            // No response ever delivered; disconnection must abort the request.
        }

        assertFailsWith<BleDisconnectedException> {
            withTimeout(1000) { client.request(cmd) }
        }
    }

    @Test
    fun `09 alarm when no request pending appears in unsolicitedEvents`() = runTest {
        val client = newClient()
        val collected = async {
            client.unsolicitedEvents.take(2).toList()
        }
        runCurrent() // attach collector before emitting

        gatt.deliverNotification(notifyUuid, byteArrayOf(0xA1.toByte(), 0x22))
        gatt.deliverNotification(notifyUuid, byteArrayOf(0xA2.toByte(), 0x33))

        val events = collected.await()
        assertThat(events.map { it.opcode }).containsExactly(0xA1.toByte(), 0xA2.toByte()).inOrder()
    }

    @Test
    fun `10 response delivered synchronously during writeCharacteristic is not lost`() = runTest {
        val client = newClient()
        // The exact scenario the current PublishSubject+blockingFirst design fails:
        // peripheral answers BEFORE the write call returns. BleClient must register
        // its waiter BEFORE invoking the GATT write, so this can never race.
        val cmd = fakeCommand(0x24, 0x84.toByte(), byteArrayOf(0x01))
        gatt.onNextWrite {
            // Synchronously — still inside writeCharacteristic — deliver the response.
            gatt.deliverNotification(notifyUuid, byteArrayOf(0x84.toByte(), 0x00))
        }

        val resp = client.request(cmd)
        assertThat(resp.raw[0]).isEqualTo(0x84.toByte())
    }

    @Test
    fun `11 notification after disconnect goes to unsolicitedEvents not dropped`() = runTest {
        // Regression for a stale-waiter window: after DISCONNECTED completes the
        // deferred exceptionally, [BleClientImpl] must clear [waiter] so a
        // late-arriving notification falls through to unsolicitedEvents instead of
        // hitting an already-completed deferred and being silently dropped.
        val client = newClient()
        val cmd = fakeCommand(0x24, 0x84.toByte(), byteArrayOf(0x01))
        val unsolicited = mutableListOf<UnsolicitedMessage>()
        val collectorJob = launch {
            client.unsolicitedEvents.collect { unsolicited += it }
        }
        runCurrent()

        gatt.onNextWrite {
            gatt.deliverConnectionState(GattConnState.DISCONNECTED)
            // Stale notification — its opcode matches the (now-aborted) waiter.
            gatt.deliverNotification(notifyUuid, byteArrayOf(0x84.toByte(), 0x00))
        }

        assertFailsWith<BleDisconnectedException> {
            withTimeout(1000) { client.request(cmd) }
        }
        runCurrent()

        assertThat(unsolicited).hasSize(1)
        assertThat(unsolicited.single().opcode).isEqualTo(0x84.toByte())
        collectorJob.cancel()
    }

    // ===== Test fixtures =====

    private data class FakeResponse(val raw: ByteArray) : BleResponse

    private class FakeCommandImpl(
        override val requestOpcode: Byte,
        override val expectedResponseOpcode: Byte,
        private val body: ByteArray,
        override val correlationByte: Byte?
    ) : BleCommand<FakeResponse> {

        override fun encode(): ByteArray = byteArrayOf(requestOpcode) + body
        override fun decode(responsePayload: ByteArray): FakeResponse = FakeResponse(responsePayload)
    }

    private fun fakeCommand(
        requestOpcode: Int,
        expectedResponseOpcode: Byte,
        body: ByteArray,
        correlationByte: Byte? = null
    ): BleCommand<FakeResponse> =
        FakeCommandImpl(
            requestOpcode = requestOpcode.toByte(),
            expectedResponseOpcode = expectedResponseOpcode,
            body = body,
            correlationByte = correlationByte
        )
}
