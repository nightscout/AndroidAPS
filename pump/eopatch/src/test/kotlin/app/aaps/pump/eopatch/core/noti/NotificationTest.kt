package app.aaps.pump.eopatch.core.noti

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.pump.eopatch.core.code.BolusType
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import java.nio.ByteBuffer

class NotificationTest {

    private val aapsLogger = mock(AAPSLogger::class.java)

    private fun createInfoBytes(): ByteArray {
        val buf = ByteBuffer.allocate(32)
        // positions 0-1: skipped in InfoNotification
        buf.put(0.toByte()) // skip
        buf.put(0.toByte()) // skip
        // NOW bolus: act=1, in=100 (0x0064), target=200 (0x00C8)
        buf.put(1.toByte())           // act[NOW]
        buf.putShort(100.toShort())   // in[NOW]
        buf.putShort(200.toShort())   // target[NOW]
        // EXT bolus: act=2, in=50 (0x0032), target=150 (0x0096)
        buf.put(2.toByte())           // act[EXT]
        buf.putShort(50.toShort())    // in[EXT]
        buf.putShort(150.toShort())   // target[EXT]
        // pad to position 26 for BaseNotification counters
        while (buf.position() < 26) buf.put(0.toByte())
        // SB_CNT, EB_CNT, Basal_CNT
        buf.putShort(10.toShort())   // SB_CNT
        buf.putShort(20.toShort())   // EB_CNT
        buf.putShort(30.toShort())   // Basal_CNT
        return buf.array()
    }

    @Test
    fun `InfoNotification should parse counters from BaseNotification`() {
        val bytes = createInfoBytes()
        val noti = InfoNotification(bytes, aapsLogger)
        assertThat(noti.sB_CNT).isEqualTo(10)
        assertThat(noti.eB_CNT).isEqualTo(20)
        assertThat(noti.basal_CNT).isEqualTo(30)
        assertThat(noti.totalInjected).isEqualTo(60)
    }

    @Test
    fun `InfoNotification getInjected should return correct values`() {
        val bytes = createInfoBytes()
        val noti = InfoNotification(bytes, aapsLogger)
        assertThat(noti.getInjected(BolusType.NOW)).isEqualTo(100)
        assertThat(noti.getInjected(BolusType.EXT)).isEqualTo(50)
        assertThat(noti.getInjected(BolusType.COMBO)).isEqualTo(150) // 100 + 50
    }

    @Test
    fun `InfoNotification getRemain should return target minus injected`() {
        val bytes = createInfoBytes()
        val noti = InfoNotification(bytes, aapsLogger)
        assertThat(noti.getRemain(BolusType.NOW)).isEqualTo(100) // 200 - 100
        assertThat(noti.getRemain(BolusType.EXT)).isEqualTo(100) // 150 - 50
        assertThat(noti.getRemain(BolusType.COMBO)).isEqualTo(200) // (200+150) - (100+50)
    }

    @Test
    fun `InfoNotification isBolusRegAct should detect active bolus`() {
        val bytes = createInfoBytes()
        val noti = InfoNotification(bytes, aapsLogger)
        assertThat(noti.isBolusRegAct).isTrue()
        assertThat(noti.isBolusRegAct(BolusType.NOW)).isTrue()
        assertThat(noti.isBolusRegAct(BolusType.EXT)).isTrue()
    }

    @Test
    fun `InfoNotification isBolusDone should detect finished bolus`() {
        val bytes = createInfoBytes()
        val noti = InfoNotification(bytes, aapsLogger)
        // NOW act=1 (running), EXT act=2 (finished)
        assertThat(noti.isBolusDone(BolusType.NOW)).isFalse()
        assertThat(noti.isBolusDone(BolusType.EXT)).isTrue()
        assertThat(noti.isBolusDone()).isTrue() // any finished
    }

    @Test
    fun `AlarmNotification should parse curIndex and lastFinishedIndex`() {
        val bytes = ByteArray(102)
        // Set position 100-101 to curIndex = 5
        bytes[100] = 0
        bytes[101] = 5
        // Set counters at position 26
        ByteBuffer.wrap(bytes, 26, 6).apply {
            putShort(1)
            putShort(2)
            putShort(3)
        }
        val alarm = AlarmNotification(bytes, aapsLogger)
        assertThat(alarm.curIndex).isEqualTo(5)
        assertThat(alarm.lastFinishedIndex).isEqualTo(4)
    }

    @Test
    fun `AlarmNotification curIndex 0 should give lastFinished -1`() {
        val bytes = ByteArray(102)
        ByteBuffer.wrap(bytes, 26, 6).apply {
            putShort(0)
            putShort(0)
            putShort(0)
        }
        val alarm = AlarmNotification(bytes, aapsLogger)
        assertThat(alarm.lastFinishedIndex).isEqualTo(-1)
    }
}
