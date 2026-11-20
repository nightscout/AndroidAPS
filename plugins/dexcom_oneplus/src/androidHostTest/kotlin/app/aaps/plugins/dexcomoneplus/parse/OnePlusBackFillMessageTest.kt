package app.aaps.plugins.dexcomoneplus.parse

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class OnePlusBackFillMessageTest {

    @Test
    fun backFillTx_layout() {
        val packet = OnePlusBackFillTx.build(100, 400)
        assertThat(packet).hasLength(9)
        assertThat(packet[0]).isEqualTo(OnePlusBackFillTx.OPCODE)
        val buf = ByteBuffer.wrap(packet).order(ByteOrder.LITTLE_ENDIAN)
        buf.get()
        assertThat(buf.int).isEqualTo(100)
        assertThat(buf.int).isEqualTo(400)
    }

    @Test
    fun backFillStream_decodesG7Record() {
        val currentDex = 10_000
        val nowMs = 1_700_000_000_000L
        val dexTime = 9_700 // 300s ago
        val record = ByteBuffer.allocate(9).order(ByteOrder.LITTLE_ENDIAN)
        record.putInt(dexTime)
        record.putShort(120) // glucose
        record.put(0x06) // Ok
        record.put(0x00) // extra
        record.put(0x00) // trend
        val stream = OnePlusBackFillStream()
        stream.pushG7(record.array())
        val samples = stream.decode(currentDexTimeSeconds = currentDex, nowMs = nowMs)
        assertThat(samples).hasSize(1)
        assertThat(samples[0].mgdl).isEqualTo(120.0)
        assertThat(samples[0].timestampMs).isEqualTo(nowMs - 300_000L)
    }

    @Test
    fun backFillStream_skipsWarmingUp() {
        val record = ByteBuffer.allocate(9).order(ByteOrder.LITTLE_ENDIAN)
        record.putInt(100)
        record.putShort(90)
        record.put(0x02) // WarmingUp
        record.put(0x00)
        record.put(0x00)
        val stream = OnePlusBackFillStream()
        stream.pushG7(record.array())
        assertThat(stream.decode(currentDexTimeSeconds = 200, nowMs = 1_000_000L)).isEmpty()
    }

    @Test
    fun `a display-only backfill record is skipped`() {
        val record = ByteBuffer.allocate(9).order(ByteOrder.LITTLE_ENDIAN)
        record.putInt(9_700)
        record.putShort(120)
        record.put(0x06) // Ok
        record.put(OnePlusGlucoseParser.DISPLAY_ONLY_FLAG.toByte())
        record.put(0x00)
        val stream = OnePlusBackFillStream()
        stream.pushG7(record.array())
        assertThat(stream.decode(currentDexTimeSeconds = 10_000, nowMs = 1_700_000_000_000L)).isEmpty()
    }

    @Test
    fun backFillControlRx_ack() {
        assertThat(OnePlusBackFillControlRx.isAck(byteArrayOf(0x59, 0x00))).isTrue()
        assertThat(OnePlusBackFillControlRx.isAck(byteArrayOf(0x4e))).isFalse()
    }
}
