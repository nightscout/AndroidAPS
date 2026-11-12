package app.aaps.wear.watchdirect

import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.TrendArrow
import app.aaps.core.interfaces.rx.weardata.EventData
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class WatchDirectReadingsTest {

    private val now = 1_700_000_000_000L

    private fun phone(timeStamp: Long, sgv: Double = 120.0, units: String = GlucoseUnit.MGDL.asText) =
        EventData.SingleBg(dataset = 0, timeStamp = timeStamp, sgv = sgv, high = 160.0, low = 75.0, glucoseUnits = units)

    private fun watch(timeStamp: Long, mgdl: Double = 118.0) =
        WatchDirectReadings.toSingleBg(WatchSensorReading(mgdl, timeStamp, 0.0), phone(now - 60 * 60_000L), emptyList())

    @Test
    fun `a watch reading is marked as from the sensor and takes the phone lines`() {
        val bg = watch(now)
        assertThat(bg.fromSensor).isTrue()
        assertThat(bg.dataset).isEqualTo(0)
        assertThat(bg.high).isEqualTo(160.0)
        assertThat(bg.low).isEqualTo(75.0)
        assertThat(bg.slopeArrow).isEqualTo(TrendArrow.FLAT.symbol)
    }

    @Test
    fun `without a phone reading the default lines are used`() {
        val bg = WatchDirectReadings.toSingleBg(WatchSensorReading(65.0, now, null), null, emptyList())
        assertThat(bg.low).isEqualTo(WatchDirectReadings.DEFAULT_LOW_MGDL)
        assertThat(bg.sgvLevel).isEqualTo(-1L)
        assertThat(bg.slopeArrow).isEqualTo(TrendArrow.NONE.symbol)
        assertThat(bg.delta).isEqualTo("--")
    }

    @Test
    fun `mmol is shown when the phone uses mmol`() {
        val bg = WatchDirectReadings.toSingleBg(WatchSensorReading(180.0, now, 0.0), phone(now - 60_000L, units = GlucoseUnit.MMOL.asText), emptyList())
        assertThat(bg.glucoseUnits).isEqualTo(GlucoseUnit.MMOL.asText)
        assertThat(bg.sgvString.replace(',', '.')).isEqualTo("10.0")
    }

    @Test
    fun `the delta comes from the reading five minutes before`() {
        val history = listOf(phone(now - 5 * 60_000L, sgv = 110.0), phone(now - 10 * 60_000L, sgv = 100.0))
        val bg = WatchDirectReadings.toSingleBg(WatchSensorReading(118.0, now, 1.5), phone(now - 5 * 60_000L), history)
        assertThat(bg.deltaMgdl).isEqualTo(8.0)
        assertThat(bg.delta).isEqualTo("+8")
    }

    @Test
    fun `the phone copy of the same reading wins`() {
        val fromWatch = watch(now)
        val fromPhone = phone(now + 10_000L)
        // The watch's reading arrives after the phone's: the phone's stays.
        assertThat(WatchDirectReadings.choose(fromPhone, fromWatch)).isSameInstanceAs(fromPhone)
        // The phone's arrives after the watch's: it replaces it.
        assertThat(WatchDirectReadings.choose(fromWatch, fromPhone)).isSameInstanceAs(fromPhone)
    }

    @Test
    fun `a newer watch reading shows while the phone is away and an old phone reading does not replace it`() {
        val oldPhone = phone(now - 20 * 60_000L)
        val fromWatch = watch(now)
        assertThat(WatchDirectReadings.choose(oldPhone, fromWatch)).isSameInstanceAs(fromWatch)
        assertThat(WatchDirectReadings.choose(fromWatch, oldPhone)).isSameInstanceAs(fromWatch)
        // A new phone reading always wins over the watch's.
        val newPhone = phone(now + 5 * 60_000L)
        assertThat(WatchDirectReadings.choose(fromWatch, newPhone)).isSameInstanceAs(newPhone)
    }

    @Test
    fun `the graph gets a watch reading only where it has none of that time`() {
        val graph = listOf(phone(now - 5 * 60_000L), phone(now))
        val merged = WatchDirectReadings.mergeIntoGraph(graph, listOf(watch(now + 20_000L), watch(now + 5 * 60_000L)))
        assertThat(merged.map { it.timeStamp }).containsExactly(now - 5 * 60_000L, now, now + 5 * 60_000L).inOrder()
        val old = WatchDirectReadings.mergeIntoGraph(listOf(phone(now - WatchDirectReadings.GRAPH_WINDOW_MS - 60_000L)), listOf(watch(now)))
        assertThat(old.map { it.timeStamp }).containsExactly(now)
    }

    @Test
    fun `the watch raises a low only while the phone is away`() {
        val urgent = WatchDirectReadings.lowLevel(50.0, 70.0)
        val low = WatchDirectReadings.lowLevel(65.0, 70.0)
        assertThat(urgent).isEqualTo(WatchDirectReadings.Low.URGENT_LOW)
        assertThat(low).isEqualTo(WatchDirectReadings.Low.LOW)
        assertThat(WatchDirectReadings.lowLevel(90.0, 70.0)).isNull()
        assertThat(WatchDirectReadings.shouldRaise(low, phoneSilent = false, lastRaised = null, lastRaisedAt = 0L, now = now)).isFalse()
        assertThat(WatchDirectReadings.shouldRaise(low, phoneSilent = true, lastRaised = null, lastRaisedAt = 0L, now = now)).isTrue()
        // A low that goes on is repeated after a while, a deeper one at once.
        assertThat(WatchDirectReadings.shouldRaise(low, true, WatchDirectReadings.Low.LOW, now - 60_000L, now)).isFalse()
        assertThat(WatchDirectReadings.shouldRaise(low, true, WatchDirectReadings.Low.LOW, now - WatchDirectReadings.LOW_REPEAT_MS, now)).isTrue()
        assertThat(WatchDirectReadings.shouldRaise(urgent, true, WatchDirectReadings.Low.LOW, now - 60_000L, now)).isTrue()
    }

    @Test
    fun `a value outside the sensor range is not shown`() {
        assertThat(WatchDirectReadings.isUsable(WatchSensorReading(39.0, now, null))).isFalse()
        assertThat(WatchDirectReadings.isUsable(WatchSensorReading(401.0, now, null))).isFalse()
        assertThat(WatchDirectReadings.isUsable(WatchSensorReading(100.0, now, null))).isTrue()
    }
}
