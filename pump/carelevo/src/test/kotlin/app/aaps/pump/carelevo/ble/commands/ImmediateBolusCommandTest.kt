package app.aaps.pump.carelevo.ble.commands

import app.aaps.pump.carelevo.ble.BleClientImpl
import app.aaps.pump.carelevo.ble.gatt.FakeGattConnection
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertFailsWith

/**
 * Tests for [ImmediateBolusCommand]. Safety-critical — wrong encoding = wrong dose.
 *
 * Coverage:
 * - Encode layout for typical + boundary volumes (2.50 U, 0.05 U, 0.33 U rounding)
 * - Response decoding with all six fields populated
 * - Input validation (actionId range, positive volume)
 * - Opcode mismatch and truncation rejection
 * - End-to-end round trip through [BleClientImpl] with correct actionId echo
 * - End-to-end **wrong** actionId echo → correlation rejects → request times out
 *   (proves the safety property: a stale BOLUS_RES never completes this request)
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class ImmediateBolusCommandTest {

    @Test
    fun `encode 2_50 U with actionId 42 produces opcode + id + 2 + 50`() {
        val cmd = ImmediateBolusCommand(actionId = 42, volume = 2.50)
        assertThat(cmd.encode()).isEqualTo(byteArrayOf(0x24, 0x2A, 0x02, 0x32))
    }

    @Test
    fun `encode 0_05 U with actionId 1 produces opcode + 1 + 0 + 5`() {
        val cmd = ImmediateBolusCommand(actionId = 1, volume = 0.05)
        assertThat(cmd.encode()).isEqualTo(byteArrayOf(0x24, 0x01, 0x00, 0x05))
    }

    @Test
    fun `encode rounds 0_333 U to 0_33 U HALF_UP`() {
        val cmd = ImmediateBolusCommand(actionId = 1, volume = 0.333)
        assertThat(cmd.encode()).isEqualTo(byteArrayOf(0x24, 0x01, 0x00, 0x21)) // 0x21 = 33
    }

    @Test
    fun `encode rounds 0_005 U to 0_01 U HALF_UP`() {
        val cmd = ImmediateBolusCommand(actionId = 1, volume = 0.005)
        assertThat(cmd.encode()).isEqualTo(byteArrayOf(0x24, 0x01, 0x00, 0x01))
    }

    @Test
    fun `encode actionId 200 becomes signed byte -56 preserving bit pattern`() {
        val cmd = ImmediateBolusCommand(actionId = 200, volume = 1.00)
        // 200 decimal = 0xC8 which is -56 as signed byte
        assertThat(cmd.encode()[1]).isEqualTo(0xC8.toByte())
    }

    @Test
    fun `constructor rejects actionId 0`() {
        assertFailsWith<IllegalArgumentException> {
            ImmediateBolusCommand(actionId = 0, volume = 1.0)
        }
    }

    @Test
    fun `constructor rejects actionId 256`() {
        assertFailsWith<IllegalArgumentException> {
            ImmediateBolusCommand(actionId = 256, volume = 1.0)
        }
    }

    @Test
    fun `constructor rejects zero volume`() {
        assertFailsWith<IllegalArgumentException> {
            ImmediateBolusCommand(actionId = 1, volume = 0.0)
        }
    }

    @Test
    fun `constructor rejects negative volume`() {
        assertFailsWith<IllegalArgumentException> {
            ImmediateBolusCommand(actionId = 1, volume = -0.1)
        }
    }

    @Test
    fun `decode extracts all fields from canonical response`() {
        val cmd = ImmediateBolusCommand(actionId = 42, volume = 2.50)
        // opcode, actionId=42, result=0, minutes=2, seconds=30, remains = 1*100 + 50 + 25/100 = 150.25 U
        val payload = byteArrayOf(
            0x84.toByte(),
            0x2A,
            0x00,
            0x02, 0x1E, // 2 min + 30 s = 150 s
            0x01, 0x32, 0x19 // 1, 50, 25
        )

        val response = cmd.decode(payload)

        assertThat(response.actionId).isEqualTo(42)
        assertThat(response.resultCode).isEqualTo(0)
        assertThat(response.expectedCompletionSeconds).isEqualTo(150)
        assertThat(response.remainingReservoirUnits).isEqualTo(150.25)
    }

    @Test
    fun `decode unsigned-byte values above 127`() {
        val cmd = ImmediateBolusCommand(actionId = 200, volume = 1.0)
        val payload = byteArrayOf(
            0x84.toByte(),
            0xC8.toByte(), // actionId 200
            0xFF.toByte(), // resultCode 255
            0x00, 0x00,
            0x00, 0x00, 0x00
        )

        val response = cmd.decode(payload)

        assertThat(response.actionId).isEqualTo(200)
        assertThat(response.resultCode).isEqualTo(255)
    }

    @Test
    fun `decode rejects wrong opcode`() {
        val cmd = ImmediateBolusCommand(actionId = 1, volume = 1.0)
        val wrong = byteArrayOf(0x85.toByte(), 0x01, 0, 0, 0, 0, 0, 0)

        assertFailsWith<IllegalArgumentException> { cmd.decode(wrong) }
    }

    @Test
    fun `decode rejects truncated payload`() {
        val cmd = ImmediateBolusCommand(actionId = 1, volume = 1.0)
        val short = byteArrayOf(0x84.toByte(), 0x01, 0x00)

        assertFailsWith<IllegalArgumentException> { cmd.decode(short) }
    }

    @Test
    fun `end to end - BleClient correlates response by actionId echo`() = runTest {
        val writeUuid = UUID.fromString("00000001-0000-1000-8000-00805f9b34fb")
        val notifyUuid = UUID.fromString("00000002-0000-1000-8000-00805f9b34fb")
        val gatt = FakeGattConnection()
        val client = BleClientImpl(gatt, writeUuid, notifyUuid, backgroundScope)
        runCurrent()

        gatt.onNextWrite { write ->
            assertThat(write.payload).isEqualTo(byteArrayOf(0x24, 0x2A, 0x02, 0x32))
            // Echo the correct actionId back.
            gatt.deliverNotification(
                notifyUuid,
                byteArrayOf(0x84.toByte(), 0x2A, 0x00, 0x02, 0x1E, 0x01, 0x32, 0x19)
            )
        }

        val response = client.request(ImmediateBolusCommand(actionId = 42, volume = 2.50))

        assertThat(response.actionId).isEqualTo(42)
        assertThat(response.resultCode).isEqualTo(0)
        assertThat(response.remainingReservoirUnits).isEqualTo(150.25)
    }

    @Test
    fun `end to end - wrong actionId echo is rejected and request times out`() = runTest {
        val writeUuid = UUID.fromString("00000001-0000-1000-8000-00805f9b34fb")
        val notifyUuid = UUID.fromString("00000002-0000-1000-8000-00805f9b34fb")
        val gatt = FakeGattConnection()
        val client = BleClientImpl(gatt, writeUuid, notifyUuid, backgroundScope)
        runCurrent()

        gatt.onNextWrite {
            // Response carries actionId 99, but the request sent 42.
            // BleClient must reject this as uncorrelated.
            gatt.deliverNotification(
                notifyUuid,
                byteArrayOf(0x84.toByte(), 0x63, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00)
            )
        }

        assertFailsWith<TimeoutCancellationException> {
            withTimeout(500) {
                client.request(ImmediateBolusCommand(actionId = 42, volume = 2.50))
            }
        }
    }
}
