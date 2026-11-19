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
    fun `a graph from the phone keeps the watch readings the phone does not have`() {
        // The phone was away from the sensor: its graph ends at "now", the watch has two newer readings.
        val fromPhone = arrayListOf(phone(now - 5 * 60_000L), phone(now))
        val onWatch = listOf(phone(now - 5 * 60_000L), phone(now), watch(now + 5 * 60_000L), watch(now + 10 * 60_000L))
        val kept = WatchDirectReadings.keepOwnReadings(fromPhone, onWatch)
        assertThat(kept.map { it.timeStamp }).containsExactly(now - 5 * 60_000L, now, now + 5 * 60_000L, now + 10 * 60_000L).inOrder()
        assertThat(kept.takeLast(2).all { it.fromSensor }).isTrue()
    }

    @Test
    fun `when the phone has the reading too its copy stays and the watch copy goes`() {
        // The phone is back and the sensor has filled in what it missed.
        val fromPhone = arrayListOf(phone(now), phone(now + 5 * 60_000L))
        val onWatch = listOf(phone(now), watch(now + 5 * 60_000L + 20_000L))
        val kept = WatchDirectReadings.keepOwnReadings(fromPhone, onWatch)
        assertThat(kept.map { it.timeStamp }).containsExactly(now, now + 5 * 60_000L).inOrder()
        assertThat(kept.none { it.fromSensor }).isTrue()
    }

    @Test
    fun `a graph from the phone is taken as it is when the watch has no reading of its own`() {
        val fromPhone = arrayListOf(phone(now), phone(now - 5 * 60_000L))
        assertThat(WatchDirectReadings.keepOwnReadings(fromPhone, listOf(phone(now - 10 * 60_000L)))).isSameInstanceAs(fromPhone)
    }

    @Test
    fun `phone values are old only while the watch shows its own reading and the phone is silent`() {
        val silent = now - WatchDirectReadings.PHONE_SILENT_MS - 1
        assertThat(WatchDirectReadings.phoneValuesAreOld(shownFromSensor = true, phoneReadingAt = silent, now = now)).isTrue()
        // The phone has a reading from a moment ago.
        assertThat(WatchDirectReadings.phoneValuesAreOld(shownFromSensor = true, phoneReadingAt = now - 60_000L, now = now)).isFalse()
        // The glucose on the face is the phone's: no direct to watch, or the phone is here.
        assertThat(WatchDirectReadings.phoneValuesAreOld(shownFromSensor = false, phoneReadingAt = silent, now = now)).isFalse()
        // Not known yet which reading the phone has (first run after the update).
        assertThat(WatchDirectReadings.phoneValuesAreOld(shownFromSensor = true, phoneReadingAt = 0L, now = now)).isFalse()
    }

    @Test
    fun `an old phone reading that is sent again does not make the phone look present`() {
        // The phone is at home without the sensor and syncs over the cloud: the same reading, again.
        val old = now - 40 * 60_000L
        val heard = WatchDirectReadings.newestPhoneReading(known = old, readingAt = old)
        assertThat(heard).isEqualTo(old)
        assertThat(now - heard > WatchDirectReadings.PHONE_SILENT_MS).isTrue()
        // A really new reading moves it, and an older one after that does not move it back.
        assertThat(WatchDirectReadings.newestPhoneReading(known = old, readingAt = now)).isEqualTo(now)
        assertThat(WatchDirectReadings.newestPhoneReading(known = now, readingAt = old)).isEqualTo(now)
    }

    @Test
    fun `the phone's signal loss is reworded only while the watch has a fresh reading of its own`() {
        val id = WatchDirectReadings.SIGNAL_LOSS_ALARM_ID
        val own = phone(now - 2 * 60_000L).copy(fromSensor = true)
        assertThat(WatchDirectReadings.phoneLostSignalWhileWatchReads(id, own, now)).isTrue()
        // Without direct to watch the value on the face is the phone's.
        assertThat(WatchDirectReadings.phoneLostSignalWhileWatchReads(id, phone(now - 2 * 60_000L), now)).isFalse()
        // The watch lost the sensor too: a real signal loss.
        val oldOwn = own.copy(timeStamp = now - WatchDirectReadings.OWN_READING_FRESH_MS - 1)
        assertThat(WatchDirectReadings.phoneLostSignalWhileWatchReads(id, oldOwn, now)).isFalse()
        // Every other alarm stays as the phone sent it.
        assertThat(WatchDirectReadings.phoneLostSignalWhileWatchReads("low", own, now)).isFalse()
    }

    @Test
    fun `a reading the watch already has adds nothing and a new one does`() {
        // The sensor sends its last hours again at every connection.
        val shown = watch(now)
        val graph = listOf(watch(now - 10 * 60_000L), watch(now - 5 * 60_000L), shown)
        // Known: an old reading that is in the graph, and the newest one sent again.
        assertThat(WatchDirectReadings.addsNothing(watch(now - 5 * 60_000L), shown, graph)).isTrue()
        assertThat(WatchDirectReadings.addsNothing(watch(now), shown, graph)).isTrue()
        // The phone's reading of the same time counts as having it.
        assertThat(WatchDirectReadings.addsNothing(watch(now - 5 * 60_000L), shown, listOf(phone(now - 5 * 60_000L + 20_000L), shown))).isTrue()
        // New for the graph: a reading from a gap.
        assertThat(WatchDirectReadings.addsNothing(watch(now - 15 * 60_000L), shown, graph)).isFalse()
        // New for the face: the next reading.
        assertThat(WatchDirectReadings.addsNothing(watch(now + 5 * 60_000L), shown, graph)).isFalse()
        // Newer than the face, though the phone already put it in the graph: the face must still change.
        assertThat(WatchDirectReadings.addsNothing(watch(now + 5 * 60_000L), shown, graph + phone(now + 5 * 60_000L))).isFalse()
    }

    @Test
    fun `old predictions are taken out and the history of boluses and basals stays`() {
        val treatments = EventData.TreatmentData(
            temps = arrayListOf(EventData.TreatmentData.TempBasal(now - 60 * 60_000L, 0.3, now - 30 * 60_000L, 0.3, 0.5)),
            basals = arrayListOf(EventData.TreatmentData.Basal(now - 60 * 60_000L, now, 0.35)),
            boluses = arrayListOf(EventData.TreatmentData.Treatment(now - 50 * 60_000L, 1.0, 0.0, isSMB = false, isValid = true)),
            predictions = arrayListOf(phone(now + 5 * 60_000L), phone(now + 10 * 60_000L)),
        )
        val shown = WatchDirectReadings.withoutOldPredictions(treatments)
        assertThat(shown.predictions).isEmpty()
        assertThat(shown.temps).isEqualTo(treatments.temps)
        assertThat(shown.basals).isEqualTo(treatments.basals)
        assertThat(shown.boluses).isEqualTo(treatments.boluses)
        // The stored data is not touched: the phone's next sync shows its predictions again.
        assertThat(treatments.predictions).hasSize(2)
    }

    @Test
    fun `old phone values are blanked and what does not go out of date stays`() {
        val status = EventData.Status(
            dataset = 0, externalStatus = "5m 0.35U/h 1.20U", iobSum = "1.20", iobDetail = "(0.9|0.3)", cob = "12g", currentBasal = "0.35U/h",
            battery = "80", rigBattery = "60%", openApsStatus = now - 20 * 60_000L, bgi = "-0.5", batteryLevel = 1,
            tempTarget = "5.5", tempTargetLevel = 0, reservoirString = "50U", reservoir = 50.0, reservoirLevel = 0, cobValue = 12.0
        )
        val blank = WatchDirectReadings.withoutOldPhoneValues(status)
        assertThat(blank.iobSum).isEqualTo("--")
        assertThat(blank.iobDetail).isEmpty()
        assertThat(blank.cob).isEqualTo("--g")
        assertThat(blank.currentBasal).isEqualTo("-.--U/h")
        assertThat(blank.bgi).isEqualTo("--")
        assertThat(blank.externalStatus).isEqualTo("--")
        assertThat(blank.cobValue).isEqualTo(-1.0)
        // Kept: the loop time (so its age keeps counting), the temp target, the reservoir, the batteries.
        assertThat(blank.openApsStatus).isEqualTo(status.openApsStatus)
        assertThat(blank.tempTarget).isEqualTo("5.5")
        assertThat(blank.reservoirString).isEqualTo("50U")
        assertThat(blank.battery).isEqualTo("80")
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
