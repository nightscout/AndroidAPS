package app.aaps.pump.eopatch.vo

import app.aaps.pump.eopatch.AppConstant
import app.aaps.pump.eopatch.core.code.BolusType
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class BolusCurrentTest {

    private lateinit var bolusCurrent: BolusCurrent

    @BeforeEach
    fun setup() {
        bolusCurrent = BolusCurrent()
    }

    @Test
    fun `init should have empty bolus`() {
        assertThat(bolusCurrent.nowBolus.historyId).isEqualTo(0)
        assertThat(bolusCurrent.extBolus.historyId).isEqualTo(0)
    }

    @Test
    fun `startNowBolus should initialize now bolus`() {
        val historyId = 12345L
        val dose = 5.0f
        val start = System.currentTimeMillis()
        val end = start + 60000

        bolusCurrent.startNowBolus(historyId, dose, start, end)

        assertThat(bolusCurrent.nowBolus.historyId).isEqualTo(historyId)
        assertThat(bolusCurrent.nowBolus.remain).isWithin(0.001f).of(dose)
        assertThat(bolusCurrent.nowBolus.injected).isWithin(0.001f).of(0f)
        assertThat(bolusCurrent.nowBolus.startTimestamp).isEqualTo(start)
        assertThat(bolusCurrent.nowBolus.endTimestamp).isEqualTo(end)
        assertThat(bolusCurrent.nowBolus.endTimeSynced).isFalse()
    }

    @Test
    fun `startExtBolus should initialize extended bolus`() {
        val historyId = 54321L
        val dose = 3.5f
        val start = System.currentTimeMillis()
        val end = start + 120000
        val duration = 120000L

        bolusCurrent.startExtBolus(historyId, dose, start, end, duration)

        assertThat(bolusCurrent.extBolus.historyId).isEqualTo(historyId)
        assertThat(bolusCurrent.extBolus.remain).isWithin(0.001f).of(dose)
        assertThat(bolusCurrent.extBolus.duration).isEqualTo(duration)
    }

    @Test
    fun `totalDoseU should return sum of injected and remain`() {
        bolusCurrent.startNowBolus(1L, 5.0f, 0L, 0L)
        bolusCurrent.nowBolus.injected = 2.0f
        bolusCurrent.nowBolus.remain = 3.0f

        assertThat(bolusCurrent.nowBolus.totalDoseU).isWithin(0.001f).of(5.0f)
    }

    @Test
    fun `updateBolusFromPatch should update injected and remain`() {
        bolusCurrent.startNowBolus(1L, 5.0f, 0L, 0L)

        // 100 units * 0.05 = 5.0U injected, 20 units * 0.05 = 1.0U remain
        bolusCurrent.updateBolusFromPatch(BolusType.NOW, 100, 20)

        assertThat(bolusCurrent.nowBolus.injected).isWithin(0.001f).of(5.0f)
        assertThat(bolusCurrent.nowBolus.remain).isWithin(0.001f).of(1.0f)
    }

    @Test
    fun `clearBolus should reset bolus to default state`() {
        bolusCurrent.startNowBolus(123L, 5.0f, 1000L, 2000L)

        bolusCurrent.clearBolus(BolusType.NOW)

        assertThat(bolusCurrent.nowBolus.historyId).isEqualTo(0)
        assertThat(bolusCurrent.nowBolus.injected).isWithin(0.001f).of(0f)
        assertThat(bolusCurrent.nowBolus.remain).isWithin(0.001f).of(0f)
        assertThat(bolusCurrent.nowBolus.startTimestamp).isEqualTo(0)
        assertThat(bolusCurrent.nowBolus.endTimestamp).isEqualTo(0)
        assertThat(bolusCurrent.nowBolus.endTimeSynced).isFalse()
    }

    @Test
    fun `clearAll should reset both boluses`() {
        bolusCurrent.startNowBolus(1L, 5.0f, 0L, 0L)
        bolusCurrent.startExtBolus(2L, 3.0f, 0L, 0L, 60000L)

        bolusCurrent.clearAll()

        assertThat(bolusCurrent.nowBolus.historyId).isEqualTo(0)
        assertThat(bolusCurrent.extBolus.historyId).isEqualTo(0)
    }

    @Test
    fun `setEndTimeSynced should update sync status`() {
        bolusCurrent.startNowBolus(1L, 5.0f, 0L, 0L)

        bolusCurrent.setEndTimeSynced(BolusType.NOW, true)

        assertThat(bolusCurrent.nowBolus.endTimeSynced).isTrue()
    }

    @Test
    fun `historyId should return correct id for bolus type`() {
        bolusCurrent.startNowBolus(111L, 5.0f, 0L, 0L)
        bolusCurrent.startExtBolus(222L, 3.0f, 0L, 0L, 60000L)

        assertThat(bolusCurrent.historyId(BolusType.NOW)).isEqualTo(111L)
        assertThat(bolusCurrent.historyId(BolusType.EXT)).isEqualTo(222L)
    }

    @Test
    fun `injected should return correct amount for bolus type`() {
        bolusCurrent.startNowBolus(1L, 5.0f, 0L, 0L)
        bolusCurrent.nowBolus.injected = 2.5f

        assertThat(bolusCurrent.injected(BolusType.NOW)).isWithin(0.001f).of(2.5f)
    }

    @Test
    fun `remain should return correct amount for bolus type`() {
        bolusCurrent.startNowBolus(1L, 5.0f, 0L, 0L)
        bolusCurrent.nowBolus.remain = 2.5f

        assertThat(bolusCurrent.remain(BolusType.NOW)).isWithin(0.001f).of(2.5f)
    }

    @Test
    fun `startTimestamp should return correct timestamp`() {
        val timestamp = System.currentTimeMillis()
        bolusCurrent.startNowBolus(1L, 5.0f, timestamp, timestamp + 60000)

        assertThat(bolusCurrent.startTimestamp(BolusType.NOW)).isEqualTo(timestamp)
    }

    @Test
    fun `endTimestamp should return correct timestamp`() {
        val start = System.currentTimeMillis()
        val end = start + 60000
        bolusCurrent.startNowBolus(1L, 5.0f, start, end)

        assertThat(bolusCurrent.endTimestamp(BolusType.NOW)).isEqualTo(end)
    }

    @Test
    fun `endTimeSynced should return sync status`() {
        bolusCurrent.startNowBolus(1L, 5.0f, 0L, 0L)
        bolusCurrent.setEndTimeSynced(BolusType.NOW, true)

        assertThat(bolusCurrent.endTimeSynced(BolusType.NOW)).isTrue()
    }

    @Test
    fun `totalDoseU getter should return total dose`() {
        bolusCurrent.startNowBolus(1L, 5.0f, 0L, 0L)
        bolusCurrent.nowBolus.injected = 2.0f
        bolusCurrent.nowBolus.remain = 3.0f

        assertThat(bolusCurrent.totalDoseU(BolusType.NOW)).isWithin(0.001f).of(5.0f)
    }

    @Test
    fun `duration should return bolus duration`() {
        bolusCurrent.startExtBolus(1L, 3.0f, 0L, 0L, 120000L)

        assertThat(bolusCurrent.duration(BolusType.EXT)).isEqualTo(120000L)
    }

    @Test
    fun `Bolus updateTimeStamp should update timestamps`() {
        val bolus = BolusCurrent.Bolus()
        val start = 1000L
        val end = 2000L

        bolus.updateTimeStamp(start, end)

        assertThat(bolus.startTimestamp).isEqualTo(start)
        assertThat(bolus.endTimestamp).isEqualTo(end)
    }

    @Test
    fun `Bolus update should calculate insulin units correctly`() {
        val bolus = BolusCurrent.Bolus()

        // 100 units * 0.05 = 5.0U, 40 units * 0.05 = 2.0U
        bolus.update(100, 40)

        assertThat(bolus.injected).isWithin(0.001f).of(5.0f)
        assertThat(bolus.remain).isWithin(0.001f).of(2.0f)
    }

    @Test
    fun `Bolus equals should compare all fields`() {
        val bolus1 = BolusCurrent.Bolus()
        bolus1.startBolus(1L, 5.0f, 1000L, 2000L)

        val bolus2 = BolusCurrent.Bolus()
        bolus2.startBolus(1L, 5.0f, 1000L, 2000L)

        assertThat(bolus1).isEqualTo(bolus2)
    }

    @Test
    fun `Bolus equals should return false for different values`() {
        val bolus1 = BolusCurrent.Bolus()
        bolus1.startBolus(1L, 5.0f, 1000L, 2000L)

        val bolus2 = BolusCurrent.Bolus()
        bolus2.startBolus(2L, 5.0f, 1000L, 2000L) // Different history ID

        assertThat(bolus1).isNotEqualTo(bolus2)
    }

    @Test
    fun `Bolus hashCode should be consistent`() {
        val bolus = BolusCurrent.Bolus()
        bolus.startBolus(1L, 5.0f, 1000L, 2000L)

        val hash1 = bolus.hashCode()
        val hash2 = bolus.hashCode()

        assertThat(hash1).isEqualTo(hash2)
    }

    @Test
    fun `Bolus toString should show NONE when no history`() {
        val bolus = BolusCurrent.Bolus()

        assertThat(bolus.toString()).contains("NONE")
    }

    @Test
    fun `Bolus toString should show details when active`() {
        val bolus = BolusCurrent.Bolus()
        bolus.startBolus(123L, 5.0f, 1000L, 2000L)

        val str = bolus.toString()
        assertThat(str).contains("id=123")
        assertThat(str).contains("Bolus")
    }

    @Test
    fun `BolusCurrent equals should compare both boluses`() {
        val bc1 = BolusCurrent()
        bc1.startNowBolus(1L, 5.0f, 1000L, 2000L)

        val bc2 = BolusCurrent()
        bc2.startNowBolus(1L, 5.0f, 1000L, 2000L)

        assertThat(bc1).isEqualTo(bc2)
    }

    @Test
    fun `BolusCurrent hashCode should be consistent`() {
        val bc = BolusCurrent()
        bc.startNowBolus(1L, 5.0f, 1000L, 2000L)

        val hash1 = bc.hashCode()
        val hash2 = bc.hashCode()

        assertThat(hash1).isEqualTo(hash2)
    }

    @Test
    fun `toString should contain both boluses`() {
        val bc = BolusCurrent()
        bc.startNowBolus(1L, 5.0f, 1000L, 2000L)
        bc.startExtBolus(2L, 3.0f, 1000L, 2000L, 60000L)

        val str = bc.toString()
        assertThat(str).contains("BolusCurrent")
        assertThat(str).contains("nowBolus=")
        assertThat(str).contains("extBolus=")
    }

    @Test
    fun `observe should return observable`() {
        val observable = bolusCurrent.observe()

        assertThat(observable).isNotNull()
    }
}
