package app.aaps.pump.insight.app_layer.history.history_events

import app.aaps.pump.insight.descriptors.AlertType
import app.aaps.pump.insight.descriptors.BolusType
import app.aaps.pump.insight.utils.ByteBuf
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * Covers [HistoryEvent.parseHeader] (BOC date/time + position, shared by all events) plus the
 * per-event [parse] byte decoding for a representative set of concrete history events. Pure ByteBuf.
 */
class HistoryEventsParseTest {

    private fun boc(v: Int): Byte = (((v / 10) shl 4) or (v % 10)).toByte()

    private fun header(year: Int = 2023, month: Int = 1, day: Int = 1, hour: Int = 0, min: Int = 0, sec: Int = 0, pos: Long = 0L) =
        ByteBuf(12).apply {
            putByte(boc(year / 100)); putByte(boc(year % 100))
            putByte(boc(month)); putByte(boc(day))
            putByte(0) // reserved
            putByte(boc(hour)); putByte(boc(min)); putByte(boc(sec))
            putUInt32LE(pos)
        }

    @Test
    fun parseHeader_decodesBocDateTimeAndPosition() {
        val e = HistoryEvent()
        e.parseHeader(header(2023, 11, 15, 14, 30, 45, 123456L))
        assertThat(e.eventYear).isEqualTo(2023)
        assertThat(e.eventMonth).isEqualTo(11)
        assertThat(e.eventDay).isEqualTo(15)
        assertThat(e.eventHour).isEqualTo(14)
        assertThat(e.eventMinute).isEqualTo(30)
        assertThat(e.eventSecond).isEqualTo(45)
        assertThat(e.eventPosition).isEqualTo(123456L)
    }

    @Test
    fun compareTo_ordersByPosition() {
        val a = HistoryEvent().apply { parseHeader(header(pos = 10L)) }
        val b = HistoryEvent().apply { parseHeader(header(pos = 20L)) }
        assertThat(a.compareTo(b)).isLessThan(0)
        assertThat(b.compareTo(a)).isGreaterThan(0)
        assertThat(a.compareTo(a)).isEqualTo(0)
    }

    @Test
    fun bolusDeliveredEvent_parsesFields() {
        val body = ByteBuf(20).apply {
            putUInt16LE(BolusType.STANDARD.id)
            putByte(0) // reserved
            putByte(boc(8)); putByte(boc(30)); putByte(boc(0)) // start h/m/s
            putUInt16Decimal(2.5)  // immediate
            putUInt16Decimal(1.0)  // extended
            putUInt16LE(60)        // duration
            putBytes(0x00.toByte(), 2) // reserved
            putUInt16LE(42)        // bolusID
        }
        val e = BolusDeliveredEvent().apply { parse(body) }
        assertThat(e.bolusType).isEqualTo(BolusType.STANDARD)
        assertThat(e.startHour).isEqualTo(8)
        assertThat(e.startMinute).isEqualTo(30)
        assertThat(e.immediateAmount).isEqualTo(2.5)
        assertThat(e.extendedAmount).isEqualTo(1.0)
        assertThat(e.duration).isEqualTo(60)
        assertThat(e.bolusID).isEqualTo(42)
    }

    @Test
    fun cannulaAndTubeFilledEvents_parseAmount() {
        val cannula = CannulaFilledEvent().apply { parse(ByteBuf(4).apply { putUInt16Decimal(0.5) }) }
        assertThat(cannula.amount).isEqualTo(0.5)
        val tube = TubeFilledEvent().apply { parse(ByteBuf(4).apply { putUInt16Decimal(3.0) }) }
        assertThat(tube.amount).isEqualTo(3.0)
    }

    @Test
    fun basalDeliveryChangedEvent_parsesOldAndNewRate() {
        val body = ByteBuf(10).apply {
            putUInt32LE(1500L) // 1.5 U/h (÷1000)
            putUInt32LE(2000L) // 2.0 U/h
        }
        val e = BasalDeliveryChangedEvent().apply { parse(body) }
        assertThat(e.oldBasalRate).isEqualTo(1.5)
        assertThat(e.newBasalRate).isEqualTo(2.0)
    }

    @Test
    fun occurrenceOfAlertEvent_resolvesAlertType() {
        val body = ByteBuf(6).apply {
            putUInt16LE(1)  // incId 1 -> REMINDER_01
            putUInt16LE(99) // alertID
        }
        val e = OccurrenceOfWarningEvent().apply { parse(body) }
        assertThat(e.alertType).isEqualTo(AlertType.REMINDER_01)
        assertThat(e.alertID).isEqualTo(99)
    }

    @Test
    fun dateTimeChangedEvent_parsesBeforeTimestamp() {
        val body = ByteBuf(12).apply {
            putByte(boc(20)); putByte(boc(22)) // 2022
            putByte(boc(6)); putByte(boc(1))   // month/day
            putByte(0)                         // reserved
            putByte(boc(12)); putByte(boc(5)); putByte(boc(30)) // h/m/s
        }
        val e = DateTimeChangedEvent().apply { parse(body) }
        assertThat(e.beforeYear).isEqualTo(2022)
        assertThat(e.beforeMonth).isEqualTo(6)
        assertThat(e.beforeDay).isEqualTo(1)
        assertThat(e.beforeHour).isEqualTo(12)
        assertThat(e.beforeMinute).isEqualTo(5)
        assertThat(e.beforeSecond).isEqualTo(30)
    }
}
