package app.aaps.pump.danar.comm

import app.aaps.core.data.time.T
import app.aaps.pump.dana.database.DanaHistoryRecord
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.verify
import java.util.GregorianCalendar

class MsgHistoryBolusTest : DanaRTestBase() {

    private fun stored(packet: ByteArray): DanaHistoryRecord {
        MsgHistoryBolus(injector).handleMessage(packet)
        val record = argumentCaptor<DanaHistoryRecord>()
        verify(danaHistoryRecordDao).createOrUpdate(record.capture())
        return record.firstValue
    }

    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

    /** As a real Korean DanaR sent it (2026-11-10): 0.30 U at 2026-11-10 14:37:09, type 'B'. */
    @Test
    fun aKoreanBolusRecordHasSecondsAndALetter() {
        val record = stored(bytes(0x7e, 0x7e, 0x0d, 0xf2, 0x31, 0x01, 0x01, 0x1a, 0x0b, 0x0a, 0x0e, 0x25, 0x09, 0x42, 0x00, 0x1e, 0xf2, 0x61, 0x2e, 0x2e))

        assertThat(record.timestamp).isEqualTo(GregorianCalendar(2026, 10, 10, 14, 37, 9).timeInMillis)
        assertThat(record.bolusType).isEqualTo("S")
        assertThat(record.value).isWithin(0.001).of(0.30)
        assertThat(record.duration).isEqualTo(0L)
    }

    /** An export DanaR: the minutes of the duration, then the type and the hours. An extended bolus of 1 h 30 min. */
    @Test
    fun anExportBolusRecordHasTheDuration() {
        val record = stored(bytes(0x7e, 0x7e, 0x0d, 0xf2, 0x31, 0x01, 0x01, 0x1a, 0x0b, 0x0a, 0x0e, 0x25, 0x1e, 0xc1, 0x00, 0x64, 0x00, 0x00, 0x2e, 0x2e))

        assertThat(record.timestamp).isEqualTo(GregorianCalendar(2026, 10, 10, 14, 37).timeInMillis)
        assertThat(record.bolusType).isEqualTo("E")
        assertThat(record.value).isWithin(0.001).of(1.00)
        assertThat(record.duration).isEqualTo(T.mins(90).msecs())
    }
}
