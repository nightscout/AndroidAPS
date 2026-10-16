package app.aaps.wear.g7

import app.aaps.core.data.model.TrendArrow
import app.aaps.core.interfaces.rx.weardata.EventData
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.util.Locale

class G7WatchReadingsTest {

    private val minute = 60_000L
    private val now = 1_000_000_000L

    private fun phone(at: Long, sgv: Double = 120.0, units: String = "mg/dl", low: Double = 72.0, high: Double = 170.0) =
        EventData.SingleBg(dataset = 0, timeStamp = at, glucoseUnits = units, sgv = sgv, high = high, low = low)

    private fun sensor(at: Long, sgv: Double = 120.0) =
        EventData.SingleBg(dataset = 0, timeStamp = at, sgv = sgv, high = 180.0, low = 70.0, fromSensor = true)

    @Test
    fun buildsAReadingInThePhonesUnitsAndLines() {
        // Values follow the watch's locale; this one uses a dot.
        val saved = Locale.getDefault()
        Locale.setDefault(Locale.US)
        try {
            checkMmolReading()
        } finally {
            Locale.setDefault(saved)
        }
    }

    private fun checkMmolReading() {
        val bg = G7WatchReadings.toSingleBg(now, 90.0, TrendArrow.FORTY_FIVE_DOWN, phone(now - 5 * minute, units = "mmol", low = 72.0, high = 170.0), listOf(phone(now - 5 * minute, sgv = 100.0)))
        assertThat(bg.fromSensor).isTrue()
        assertThat(bg.glucoseUnits).isEqualTo("mmol")
        assertThat(bg.sgvString).isEqualTo("5.0")
        assertThat(bg.slopeArrow).isEqualTo(TrendArrow.FORTY_FIVE_DOWN.symbol)
        assertThat(bg.deltaMgdl).isEqualTo(-10.0)
        assertThat(bg.delta).isEqualTo("-0.6")
        assertThat(bg.low).isEqualTo(72.0)
        assertThat(bg.high).isEqualTo(170.0)
        assertThat(bg.sgvLevel).isEqualTo(0L)
    }

    @Test
    fun withoutAPhoneReadingUsesMgdlAndDefaultLines() {
        val bg = G7WatchReadings.toSingleBg(now, 65.0, TrendArrow.FLAT, null, emptyList())
        assertThat(bg.glucoseUnits).isEqualTo("mg/dl")
        assertThat(bg.sgvString).isEqualTo("65")
        assertThat(bg.delta).isEqualTo("--")
        assertThat(bg.sgvLevel).isEqualTo(-1L)
    }

    @Test
    fun phonesCopyOfTheSameReadingWins() {
        // The watch's copy arrives first, the phone's a few seconds later: the phone's replaces it.
        assertThat(G7WatchReadings.choose(sensor(now), phone(now + 2_000)).fromSensor).isFalse()
        // The phone's arrived first: the watch's copy of the same reading does not replace it.
        assertThat(G7WatchReadings.choose(phone(now), sensor(now + 2_000)).fromSensor).isFalse()
    }

    @Test
    fun newerSensorReadingWinsOverAnOldPhoneOne() {
        assertThat(G7WatchReadings.choose(phone(now - 10 * minute), sensor(now)).fromSensor).isTrue()
        // A late resend of old phone data does not hide the newer sensor reading.
        assertThat(G7WatchReadings.choose(sensor(now), phone(now - 10 * minute)).fromSensor).isTrue()
        // The phone back with a newer reading takes over again.
        assertThat(G7WatchReadings.choose(sensor(now), phone(now + 5 * minute)).fromSensor).isFalse()
    }

    @Test
    fun graphTakesOnlyReadingsItDoesNotHave() {
        val merged = G7WatchReadings.mergeIntoGraph(listOf(phone(now - 10 * minute), phone(now - 5 * minute)), listOf(sensor(now - 5 * minute + 1_000), sensor(now)))
        assertThat(merged.map { it.timeStamp }).containsExactly(now - 10 * minute, now - 5 * minute, now).inOrder()
        assertThat(merged.last().fromSensor).isTrue()
    }

    @Test
    fun graphDropsDayOldEntries() {
        val merged = G7WatchReadings.mergeIntoGraph(listOf(phone(now - G7WatchReadings.GRAPH_WINDOW_MS - minute)), listOf(sensor(now)))
        assertThat(merged.map { it.timeStamp }).containsExactly(now)
    }

    @Test
    fun lowLevels() {
        assertThat(G7WatchReadings.lowLevel(80.0, 72.0)).isNull()
        assertThat(G7WatchReadings.lowLevel(70.0, 72.0)).isEqualTo(G7WatchReadings.Low.LOW)
        assertThat(G7WatchReadings.lowLevel(54.0, 72.0)).isEqualTo(G7WatchReadings.Low.URGENT_LOW)
    }

    @Test
    fun watchAlarmsOnlyWhileThePhoneIsAway() {
        assertThat(G7WatchReadings.shouldRaise(G7WatchReadings.Low.LOW, phoneSilent = false, null, 0, now)).isFalse()
        assertThat(G7WatchReadings.shouldRaise(G7WatchReadings.Low.LOW, phoneSilent = true, null, 0, now)).isTrue()
        assertThat(G7WatchReadings.shouldRaise(null, phoneSilent = true, null, 0, now)).isFalse()
    }

    @Test
    fun aLowThatGoesOnIsRepeatedAndADeeperOneGoesOffAtOnce() {
        val low = G7WatchReadings.Low.LOW
        val urgent = G7WatchReadings.Low.URGENT_LOW
        assertThat(G7WatchReadings.shouldRaise(low, true, low, now - 5 * minute, now)).isFalse()
        assertThat(G7WatchReadings.shouldRaise(low, true, low, now - G7WatchReadings.LOW_REPEAT_MS, now)).isTrue()
        assertThat(G7WatchReadings.shouldRaise(urgent, true, low, now - minute, now)).isTrue()
        assertThat(G7WatchReadings.shouldRaise(urgent, true, urgent, now - G7WatchReadings.URGENT_LOW_REPEAT_MS, now)).isTrue()
        // Coming up from urgent to low is not a new alarm.
        assertThat(G7WatchReadings.shouldRaise(low, true, urgent, now - 5 * minute, now)).isFalse()
    }
}
