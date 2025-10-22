package app.aaps.implementation.stats

import androidx.collection.LongSparseArray
import app.aaps.core.data.model.GV
import app.aaps.core.data.model.SourceSensor
import app.aaps.core.data.model.TrendArrow
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.MidnightTime
import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever

class TirCalculatorImplTest : TestBase() {

    @Mock lateinit var rh: ResourceHelper
    @Mock lateinit var profileUtil: ProfileUtil
    @Mock lateinit var dateUtil: DateUtil
    @Mock lateinit var persistenceLayer: PersistenceLayer

    private lateinit var tirCalculator: TirCalculatorImpl

    private val now = 1000000000L
    private val midnight = MidnightTime.calc(now)
    private val lowMgdl = 70.0
    private val highMgdl = 180.0
    
    companion object {
        private const val MMOL_TO_MGDL = 18.0182  // Conversion factor from mmol/L to mg/dL
        private const val ERROR_THRESHOLD_MGDL = 39.0  // Error threshold in mg/dL
    }

    @BeforeEach
    fun setup() {
        tirCalculator = TirCalculatorImpl(rh, profileUtil, dateUtil, persistenceLayer)
        whenever(dateUtil.now()).thenReturn(now)
    }

    // ===== calculate() method tests =====

    @Test
    fun `calculate throws exception when lowMgdl is below 39`() {
        assertThrows<RuntimeException> {
            tirCalculator.calculate(7, 38.0, highMgdl)
        }
    }

    @Test
    fun `calculate throws exception when lowMgdl is greater than highMgdl`() {
        assertThrows<RuntimeException> {
            tirCalculator.calculate(7, 200.0, 180.0)
        }
    }

    @Test
    fun `calculate returns empty array when no bg readings`() {
        whenever(persistenceLayer.getBgReadingsDataFromTimeToTime(any(), any(), any()))
            .thenReturn(emptyList())

        val result = tirCalculator.calculate(7, lowMgdl, highMgdl)

        assertThat(result.size()).isEqualTo(0)
    }

    @Test
    fun `calculate groups readings by day correctly`() {
        val day1 = midnight
        val day2 = midnight - T.days(1).msecs()
        val day3 = midnight - T.days(2).msecs()

        val readings = listOf(
            createGV(day1, 100.0),
            createGV(day1 + T.hours(1).msecs(), 120.0),
            createGV(day2, 90.0),
            createGV(day2 + T.hours(2).msecs(), 150.0),
            createGV(day3, 80.0)
        )

        whenever(persistenceLayer.getBgReadingsDataFromTimeToTime(any(), any(), any()))
            .thenReturn(readings)

        val result = tirCalculator.calculate(7, lowMgdl, highMgdl)

        assertThat(result.size()).isEqualTo(3)
        assertThat(result[MidnightTime.calc(day1)]).isNotNull()
        assertThat(result[MidnightTime.calc(day2)]).isNotNull()
        assertThat(result[MidnightTime.calc(day3)]).isNotNull()
    }

    @Test
    fun `calculate categorizes glucose values correctly into ranges`() {
        val readings = listOf(
            createGV(midnight, 30.0),      // error
            createGV(midnight + T.mins(5).msecs(), 50.0),   // below
            createGV(midnight + T.mins(10).msecs(), 100.0), // in range
            createGV(midnight + T.mins(15).msecs(), 200.0)  // above
        )

        whenever(persistenceLayer.getBgReadingsDataFromTimeToTime(any(), any(), any()))
            .thenReturn(readings)

        val result = tirCalculator.calculate(7, lowMgdl, highMgdl)

        assertThat(result.size()).isEqualTo(1)
        val tir = result[MidnightTime.calc(midnight)]
        assertThat(tir).isNotNull()
        assertThat(tir?.error).isEqualTo(1)
        assertThat(tir?.below).isEqualTo(1)
        assertThat(tir?.inRange).isEqualTo(1)
        assertThat(tir?.above).isEqualTo(1)
        assertThat(tir?.count).isEqualTo(3) // error doesn't count
    }

    @Test
    fun `calculate handles edge cases at thresholds`() {
        val readings = listOf(
            createGV(midnight, ERROR_THRESHOLD_MGDL),      // exactly at error boundary - should be below
            createGV(midnight + T.mins(5).msecs(), 70.0),   // exactly at low threshold - in range
            createGV(midnight + T.mins(10).msecs(), 180.0), // exactly at high threshold - in range
            createGV(midnight + T.mins(15).msecs(), 180.1)  // just above high - above
        )

        whenever(persistenceLayer.getBgReadingsDataFromTimeToTime(any(), any(), any()))
            .thenReturn(readings)

        val result = tirCalculator.calculate(7, lowMgdl, highMgdl)

        val tir = result[MidnightTime.calc(midnight)]
        assertThat(tir?.below).isEqualTo(1)  // ERROR_THRESHOLD_MGDL
        assertThat(tir?.inRange).isEqualTo(2) // 70.0 and 180.0
        assertThat(tir?.above).isEqualTo(1)   // 180.1
        assertThat(tir?.error).isEqualTo(0)
    }

    @Test
    fun `calculate sets correct thresholds in TIR objects`() {
        val readings = listOf(createGV(midnight, 100.0))

        whenever(persistenceLayer.getBgReadingsDataFromTimeToTime(any(), any(), any()))
            .thenReturn(readings)

        val result = tirCalculator.calculate(7, lowMgdl, highMgdl)

        val tir = result[MidnightTime.calc(midnight)]
        assertThat(tir?.lowThreshold).isEqualTo(lowMgdl)
        assertThat(tir?.highThreshold).isEqualTo(highMgdl)
    }

    @Test
    fun `calculate handles multiple readings on same day`() {
        val readings = mutableListOf<GV>()
        // Create 288 readings for one day (every 5 minutes)
        for (i in 0 until 288) {
            readings.add(createGV(midnight + T.mins(i * 5L).msecs(), 100.0))
        }

        whenever(persistenceLayer.getBgReadingsDataFromTimeToTime(any(), any(), any()))
            .thenReturn(readings)

        val result = tirCalculator.calculate(7, lowMgdl, highMgdl)

        val tir = result[MidnightTime.calc(midnight)]
        assertThat(tir?.count).isEqualTo(288)
        assertThat(tir?.inRange).isEqualTo(288)
    }

    // ===== calculateToday() method tests =====

    @Test
    fun `calculateToday throws exception when lowMgdl is below 39`() {
        assertThrows<RuntimeException> {
            tirCalculator.calculateToday(38.0, highMgdl)
        }
    }

    @Test
    fun `calculateToday throws exception when lowMgdl is greater than highMgdl`() {
        assertThrows<RuntimeException> {
            tirCalculator.calculateToday(200.0, 180.0)
        }
    }

    @Test
    fun `calculateToday returns TIR for current day only`() {
        val currentMidnight = MidnightTime.calc(now)
        
        val readings = listOf(
            createGV(currentMidnight + T.hours(6).msecs(), 100.0),
            createGV(currentMidnight + T.hours(12).msecs(), 120.0),
            createGV(currentMidnight - T.days(1).msecs(), 90.0) // previous day - should not be included
        )

        whenever(persistenceLayer.getBgReadingsDataFromTimeToTime(any(), any(), any()))
            .thenAnswer { invocation ->
                val start = invocation.arguments[0] as Long
                val end = invocation.arguments[1] as Long
                readings.filter { it.timestamp in start..end }
            }

        val result = tirCalculator.calculateToday(lowMgdl, highMgdl)

        assertThat(result.count).isEqualTo(2)
        assertThat(result.inRange).isEqualTo(2)
    }

    @Test
    fun `calculateToday with no readings returns empty TIR`() {
        whenever(persistenceLayer.getBgReadingsDataFromTimeToTime(any(), any(), any()))
            .thenReturn(emptyList())

        val result = tirCalculator.calculateToday(lowMgdl, highMgdl)

        assertThat(result.count).isEqualTo(0)
        assertThat(result.below).isEqualTo(0)
        assertThat(result.inRange).isEqualTo(0)
        assertThat(result.above).isEqualTo(0)
        assertThat(result.error).isEqualTo(0)
    }

    // ===== calculateRange() method tests =====

    @Test
    fun `calculateRange throws exception when lowMgdl is below 39`() {
        assertThrows<RuntimeException> {
            tirCalculator.calculateRange(midnight, now, 38.0, highMgdl)
        }
    }

    @Test
    fun `calculateRange throws exception when lowMgdl is greater than highMgdl`() {
        assertThrows<RuntimeException> {
            tirCalculator.calculateRange(midnight, now, 200.0, 180.0)
        }
    }

    @Test
    fun `calculateRange returns TIR for specified time range`() {
        val start = midnight
        val end = midnight + T.hours(6).msecs()
        
        val readings = listOf(
            createGV(start + T.hours(1).msecs(), 100.0),
            createGV(start + T.hours(2).msecs(), 120.0),
            createGV(start + T.hours(3).msecs(), 150.0),
            createGV(end + T.hours(1).msecs(), 90.0) // outside range - should not be included
        )

        whenever(persistenceLayer.getBgReadingsDataFromTimeToTime(start, end, true))
            .thenReturn(readings.filter { it.timestamp in start..end })

        val result = tirCalculator.calculateRange(start, end, lowMgdl, highMgdl)

        assertThat(result.count).isEqualTo(3)
        assertThat(result.inRange).isEqualTo(3)
        assertThat(result.date).isEqualTo(start)
    }

    @Test
    fun `calculateRange categorizes all glucose ranges correctly`() {
        val start = midnight
        val end = midnight + T.hours(2).msecs()
        
        val readings = listOf(
            createGV(start, 20.0),         // error
            createGV(start + T.mins(10).msecs(), 38.0),   // error
            createGV(start + T.mins(20).msecs(), ERROR_THRESHOLD_MGDL),   // below
            createGV(start + T.mins(30).msecs(), 60.0),   // below
            createGV(start + T.mins(40).msecs(), 70.0),   // in range
            createGV(start + T.mins(50).msecs(), 100.0),  // in range
            createGV(start + T.mins(60).msecs(), 180.0),  // in range
            createGV(start + T.mins(70).msecs(), 181.0),  // above
            createGV(start + T.mins(80).msecs(), 250.0)   // above
        )

        whenever(persistenceLayer.getBgReadingsDataFromTimeToTime(start, end, true))
            .thenReturn(readings)

        val result = tirCalculator.calculateRange(start, end, lowMgdl, highMgdl)

        assertThat(result.error).isEqualTo(2)  // 20.0, 38.0
        assertThat(result.below).isEqualTo(2)  // ERROR_THRESHOLD_MGDL, 60.0
        assertThat(result.inRange).isEqualTo(3) // 70.0, 100.0, 180.0
        assertThat(result.above).isEqualTo(2)   // 181.0, 250.0
        assertThat(result.count).isEqualTo(7)   // excludes errors
    }

    @Test
    fun `calculateRange with custom thresholds`() {
        val customLow = 80.0
        val customHigh = 140.0
        val start = midnight
        val end = midnight + T.hours(1).msecs()
        
        val readings = listOf(
            createGV(start, 70.0),   // below (< 80)
            createGV(start + T.mins(10).msecs(), 90.0),   // in range
            createGV(start + T.mins(20).msecs(), 130.0),  // in range
            createGV(start + T.mins(30).msecs(), 150.0)   // above (> 140)
        )

        whenever(persistenceLayer.getBgReadingsDataFromTimeToTime(start, end, true))
            .thenReturn(readings)

        val result = tirCalculator.calculateRange(start, end, customLow, customHigh)

        assertThat(result.below).isEqualTo(1)
        assertThat(result.inRange).isEqualTo(2)
        assertThat(result.above).isEqualTo(1)
        assertThat(result.lowThreshold).isEqualTo(customLow)
        assertThat(result.highThreshold).isEqualTo(customHigh)
    }

    @Test
    fun `calculateRange returns empty TIR when no readings in range`() {
        val start = midnight
        val end = midnight + T.hours(1).msecs()

        whenever(persistenceLayer.getBgReadingsDataFromTimeToTime(start, end, true))
            .thenReturn(emptyList())

        val result = tirCalculator.calculateRange(start, end, lowMgdl, highMgdl)

        assertThat(result.count).isEqualTo(0)
        assertThat(result.below).isEqualTo(0)
        assertThat(result.inRange).isEqualTo(0)
        assertThat(result.above).isEqualTo(0)
    }

    // ===== Integration and realistic scenario tests =====

    @Test
    fun `realistic 24-hour scenario with typical glucose variations`() {
        val readings = mutableListOf<GV>()
        
        // Simulate realistic glucose pattern over 24 hours
        // Night: stable in range
        for (i in 0..8) {
            readings.add(createGV(midnight + T.hours(i.toLong()).msecs(), 95.0 + (i * 2)))
        }
        
        // Morning spike
        readings.add(createGV(midnight + T.hours(9).msecs(), 190.0))  // above
        readings.add(createGV(midnight + T.hours(10).msecs(), 185.0)) // above
        
        // Back in range
        for (i in 11..14) {
            readings.add(createGV(midnight + T.hours(i.toLong()).msecs(), 120.0))
        }
        
        // Low episode
        readings.add(createGV(midnight + T.hours(15).msecs(), 65.0))  // below
        readings.add(createGV(midnight + T.hours(16).msecs(), 60.0))  // below
        
        // Recovery to range
        for (i in 17..23) {
            readings.add(createGV(midnight + T.hours(i.toLong()).msecs(), 110.0))
        }

        whenever(persistenceLayer.getBgReadingsDataFromTimeToTime(any(), any(), any()))
            .thenReturn(readings)

        val result = tirCalculator.calculate(1, lowMgdl, highMgdl)

        val tir = result[MidnightTime.calc(midnight)]
        assertThat(tir).isNotNull()
        assertThat(tir?.count).isEqualTo(24)
        assertThat(tir?.below).isEqualTo(2)   // 2 low readings
        assertThat(tir?.inRange).isEqualTo(20) // 20 in-range readings
        assertThat(tir?.above).isEqualTo(2)   // 2 high readings
        assertThat(tir?.error).isEqualTo(0)
    }

    @Test
    fun `multiple days with different glucose patterns`() {
        val day1 = midnight
        val day2 = midnight - T.days(1).msecs()
        
        val readings = listOf(
            // Day 1: all in range
            createGV(day1, 100.0),
            createGV(day1 + T.hours(6).msecs(), 110.0),
            createGV(day1 + T.hours(12).msecs(), 120.0),
            createGV(day1 + T.hours(18).msecs(), 130.0),
            
            // Day 2: mixed
            createGV(day2, 50.0),  // below
            createGV(day2 + T.hours(6).msecs(), 100.0),  // in range
            createGV(day2 + T.hours(12).msecs(), 200.0), // above
            createGV(day2 + T.hours(18).msecs(), 30.0)   // error
        )

        whenever(persistenceLayer.getBgReadingsDataFromTimeToTime(any(), any(), any()))
            .thenReturn(readings)

        val result = tirCalculator.calculate(2, lowMgdl, highMgdl)

        val tir1 = result[MidnightTime.calc(day1)]
        assertThat(tir1?.count).isEqualTo(4)
        assertThat(tir1?.inRange).isEqualTo(4)
        assertThat(tir1?.below).isEqualTo(0)
        assertThat(tir1?.above).isEqualTo(0)
        
        val tir2 = result[MidnightTime.calc(day2)]
        assertThat(tir2?.count).isEqualTo(3)  // error doesn't count
        assertThat(tir2?.below).isEqualTo(1)
        assertThat(tir2?.inRange).isEqualTo(1)
        assertThat(tir2?.above).isEqualTo(1)
        assertThat(tir2?.error).isEqualTo(1)
    }

    @Test
    fun `stress test with large number of readings`() {
        val readings = mutableListOf<GV>()
        
        // Generate 2016 readings (7 days * 288 readings per day)
        for (day in 0..6) {
            val dayStart = midnight - T.days(day.toLong()).msecs()
            for (reading in 0..287) {
                val value = when {
                    reading < 50 -> 65.0  // below
                    reading < 250 -> 100.0 // in range
                    else -> 190.0 // above
                }
                readings.add(createGV(dayStart + T.mins(reading * 5L).msecs(), value))
            }
        }

        whenever(persistenceLayer.getBgReadingsDataFromTimeToTime(any(), any(), any()))
            .thenReturn(readings)

        val result = tirCalculator.calculate(7, lowMgdl, highMgdl)

        assertThat(result.size()).isEqualTo(7)
        
        // Verify each day
        for (day in 0..6) {
            val dayMidnight = MidnightTime.calc(midnight - T.days(day.toLong()).msecs())
            val tir = result[dayMidnight]
            assertThat(tir?.count).isEqualTo(288)
            assertThat(tir?.below).isEqualTo(50)
            assertThat(tir?.inRange).isEqualTo(200)
            assertThat(tir?.above).isEqualTo(38)
        }
    }

    @Test
    fun `verify time range boundaries are inclusive`() {
        val start = midnight
        val end = midnight + T.hours(1).msecs()
        
        val readings = listOf(
            createGV(start - 1, 100.0),     // just before start
            createGV(start, 110.0),         // exactly at start
            createGV(start + T.mins(30).msecs(), 120.0), // middle
            createGV(end, 130.0),           // exactly at end
            createGV(end + 1, 140.0)        // just after end
        )

        whenever(persistenceLayer.getBgReadingsDataFromTimeToTime(start, end, true))
            .thenReturn(readings.filter { it.timestamp >= start && it.timestamp <= end })

        val result = tirCalculator.calculateRange(start, end, lowMgdl, highMgdl)

        // Should include start and end timestamps
        assertThat(result.count).isEqualTo(3) // start, middle, end
    }

    @Test
    fun `calculate handles NaN and Infinity values`() {
        val readings = listOf(
            createGV(midnight, Double.NaN),                     // NaN should be ignored
            createGV(midnight + T.mins(5).msecs(), Double.POSITIVE_INFINITY), // Infinity treated as above
            createGV(midnight + T.mins(10).msecs(), 100.0)      // in range
        )

        whenever(persistenceLayer.getBgReadingsDataFromTimeToTime(any(), any(), any()))
            .thenReturn(readings)

        val result = tirCalculator.calculate(7, lowMgdl, highMgdl)

        val tir = result[MidnightTime.calc(midnight)]
        assertThat(tir?.error).isEqualTo(0)
        assertThat(tir?.below).isEqualTo(0)
        assertThat(tir?.inRange).isEqualTo(1)
        assertThat(tir?.above).isEqualTo(1) // Infinity
        assertThat(tir?.count).isEqualTo(2)
    }

    @Test
    fun `calculate handles negative timestamps and raw null`() {
        val negativeTs = -1_000_000L
        val readings = listOf(
            // raw = null should not affect classification
            GV(timestamp = negativeTs, value = 100.0, noise = null, raw = null, trendArrow = TrendArrow.FLAT, sourceSensor = SourceSensor.UNKNOWN),
            createGV(midnight, 90.0)
        )

        whenever(persistenceLayer.getBgReadingsDataFromTimeToTime(any(), any(), any()))
            .thenReturn(readings)

        val result = tirCalculator.calculate(7, lowMgdl, highMgdl)

        // Both readings should be present and counted
        val tirNeg = result[MidnightTime.calc(negativeTs)]
        val tirMid = result[MidnightTime.calc(midnight)]
        assertThat(tirNeg?.count).isEqualTo(1)
        assertThat(tirNeg?.inRange).isEqualTo(1)
        assertThat(tirMid?.count).isEqualTo(1)
    }

    @Test
    fun `calculate counts duplicate timestamps separately`() {
        val ts = midnight + T.hours(2).msecs()
        val readings = listOf(
            createGV(ts, 100.0),
            createGV(ts, 200.0)
        )

        whenever(persistenceLayer.getBgReadingsDataFromTimeToTime(any(), any(), any()))
            .thenReturn(readings)

        val result = tirCalculator.calculate(7, lowMgdl, highMgdl)

        val tir = result[MidnightTime.calc(midnight)]
        assertThat(tir?.count).isEqualTo(2)
        assertThat(tir?.inRange).isEqualTo(1)
        assertThat(tir?.above).isEqualTo(1)
    }

    @Test
    fun `calculate handles data holes without filling readings`() {
        val readings = listOf(
            createGV(midnight, 100.0),
            createGV(midnight + T.hours(12).msecs(), 110.0) // large gap, should be counted but not filled
        )

        whenever(persistenceLayer.getBgReadingsDataFromTimeToTime(any(), any(), any()))
            .thenReturn(readings)

        val result = tirCalculator.calculate(7, lowMgdl, highMgdl)

        val tir = result[MidnightTime.calc(midnight)]
        assertThat(tir?.count).isEqualTo(2)
        assertThat(tir?.inRange).isEqualTo(2)
    }

    // ===== mmol/L unit tests (using conversion to mg/dL) =====

    @Test
    fun `calculate with mmol L values converted to mg dL`() {
        // mmol/L to mg/dL conversion: multiply by ~MMOL_TO_MGDL
        // Low: 3.9 mmol/L = ~70 mg/dL, High: 10.0 mmol/L = ~180 mg/dL
        val lowMmol = 3.9
        val highMmol = 10.0
        val lowMgdl = lowMmol * MMOL_TO_MGDL  // ~70.27
        val highMgdl = highMmol * MMOL_TO_MGDL  // ~180.18
        
        val readings = listOf(
            createGV(midnight, 2.8 * MMOL_TO_MGDL),    // 50 mg/dL - below
            createGV(midnight + T.mins(5).msecs(), 5.5 * MMOL_TO_MGDL),    // 99 mg/dL - in range
            createGV(midnight + T.mins(10).msecs(), 11.0 * MMOL_TO_MGDL)   // 198 mg/dL - above
        )

        whenever(persistenceLayer.getBgReadingsDataFromTimeToTime(any(), any(), any()))
            .thenReturn(readings)

        val result = tirCalculator.calculate(7, lowMgdl, highMgdl)

        val tir = result[MidnightTime.calc(midnight)]
        assertThat(tir?.below).isEqualTo(1)   // 2.8 mmol/L
        assertThat(tir?.inRange).isEqualTo(1) // 5.5 mmol/L
        assertThat(tir?.above).isEqualTo(1)   // 11.0 mmol/L
    }

    @Test
    fun `mmol L error threshold is approximately 2_2 mmol L`() {
        // Error threshold: < 39 mg/dL = ~2.16 mmol/L
        val errorThresholdMmol = ERROR_THRESHOLD_MGDL / MMOL_TO_MGDL  // ~2.164 mmol/L
        
        val readings = listOf(
            createGV(midnight, 2.0 * MMOL_TO_MGDL),      // ~36 mg/dL - error
            createGV(midnight + T.mins(5).msecs(), 2.2 * MMOL_TO_MGDL),   // ~39.6 mg/dL - below
            createGV(midnight + T.mins(10).msecs(), 2.15 * MMOL_TO_MGDL)  // ~38.7 mg/dL - error (< 39)
        )

        whenever(persistenceLayer.getBgReadingsDataFromTimeToTime(any(), any(), any()))
            .thenReturn(readings)

        val result = tirCalculator.calculate(7, lowMgdl, highMgdl)

        val tir = result[MidnightTime.calc(midnight)]
        assertThat(tir?.error).isEqualTo(2)   // 2.0 and 2.15 mmol/L (both < 39 mg/dL)
        assertThat(tir?.below).isEqualTo(1)   // 2.2 mmol/L
        assertThat(tir?.count).isEqualTo(1)   // errors don't count
    }

    @Test
    fun `mmol L boundary values at thresholds`() {
        // Test exact boundaries in mmol/L
        // 3.9 mmol/L = 70.27 mg/dL (in range)
        // 10.0 mmol/L = 180.18 mg/dL (in range)
        val lowMgdl = 3.9 * MMOL_TO_MGDL   // ~70.27
        val highMgdl = 10.0 * MMOL_TO_MGDL  // ~180.18
        
        val readings = listOf(
            createGV(midnight, 3.85 * MMOL_TO_MGDL),  // Just below low - below
            createGV(midnight + T.mins(5).msecs(), 3.9 * MMOL_TO_MGDL),   // Exactly at low - in range
            createGV(midnight + T.mins(10).msecs(), 10.0 * MMOL_TO_MGDL), // Exactly at high - in range
            createGV(midnight + T.mins(15).msecs(), 10.1 * MMOL_TO_MGDL)  // Just above high - above
        )

        whenever(persistenceLayer.getBgReadingsDataFromTimeToTime(any(), any(), any()))
            .thenReturn(readings)

        val result = tirCalculator.calculate(7, lowMgdl, highMgdl)

        val tir = result[MidnightTime.calc(midnight)]
        assertThat(tir?.below).isEqualTo(1)   // 3.85 mmol/L
        assertThat(tir?.inRange).isEqualTo(2) // 3.9 and 10.0 mmol/L
        assertThat(tir?.above).isEqualTo(1)   // 10.1 mmol/L
    }

    @Test
    fun `realistic mmol L scenario with typical glucose variations`() {
        // Realistic European/international scenario using mmol/L values
        val lowMgdl = 3.9 * MMOL_TO_MGDL   // 3.9 mmol/L
        val highMgdl = 10.0 * MMOL_TO_MGDL  // 10.0 mmol/L
        
        val readings = mutableListOf<GV>()
        
        // Night: stable around 5.5 mmol/L (99 mg/dL)
        for (i in 0..6) {
            readings.add(createGV(midnight + T.hours(i.toLong()).msecs(), 5.5 * MMOL_TO_MGDL))
        }
        
        // Morning spike to 11.0 mmol/L (198 mg/dL)
        readings.add(createGV(midnight + T.hours(7).msecs(), 11.0 * MMOL_TO_MGDL))
        readings.add(createGV(midnight + T.hours(8).msecs(), 10.5 * MMOL_TO_MGDL))
        
        // Back to range 6.0 mmol/L (108 mg/dL)
        for (i in 9..13) {
            readings.add(createGV(midnight + T.hours(i.toLong()).msecs(), 6.0 * MMOL_TO_MGDL))
        }
        
        // Low episode 3.5 mmol/L (63 mg/dL)
        readings.add(createGV(midnight + T.hours(14).msecs(), 3.5 * MMOL_TO_MGDL))
        readings.add(createGV(midnight + T.hours(15).msecs(), 3.3 * MMOL_TO_MGDL))
        
        // Recovery 5.0 mmol/L (90 mg/dL)
        for (i in 16..23) {
            readings.add(createGV(midnight + T.hours(i.toLong()).msecs(), 5.0 * MMOL_TO_MGDL))
        }

        whenever(persistenceLayer.getBgReadingsDataFromTimeToTime(any(), any(), any()))
            .thenReturn(readings)

        val result = tirCalculator.calculate(1, lowMgdl, highMgdl)

        val tir = result[MidnightTime.calc(midnight)]
        assertThat(tir?.count).isEqualTo(24)
        assertThat(tir?.below).isEqualTo(2)   // 2 low readings
        assertThat(tir?.inRange).isEqualTo(20) // 20 in-range readings
        assertThat(tir?.above).isEqualTo(2)   // 2 high readings
    }

    @Test
    fun `calculateRange with mmol L custom thresholds`() {
        // Using tighter control range: 4.0-8.0 mmol/L
        val customLowMgdl = 4.0 * MMOL_TO_MGDL   // 72 mg/dL
        val customHighMgdl = 8.0 * MMOL_TO_MGDL  // 144 mg/dL
        
        val start = midnight
        val end = midnight + T.hours(6).msecs()
        
        val readings = listOf(
            createGV(start, 3.8 * MMOL_TO_MGDL),               // 68 mg/dL - below
            createGV(start + T.hours(1).msecs(), 5.0 * MMOL_TO_MGDL),   // 90 mg/dL - in range
            createGV(start + T.hours(2).msecs(), 7.0 * MMOL_TO_MGDL),   // 126 mg/dL - in range
            createGV(start + T.hours(3).msecs(), 9.0 * MMOL_TO_MGDL),   // 162 mg/dL - above
            createGV(start + T.hours(4).msecs(), 6.0 * MMOL_TO_MGDL),   // 108 mg/dL - in range
            createGV(start + T.hours(5).msecs(), 10.0 * MMOL_TO_MGDL)   // 180 mg/dL - above
        )

        whenever(persistenceLayer.getBgReadingsDataFromTimeToTime(start, end, true))
            .thenReturn(readings)

        val result = tirCalculator.calculateRange(start, end, customLowMgdl, customHighMgdl)

        assertThat(result.below).isEqualTo(1)   // 3.8 mmol/L
        assertThat(result.inRange).isEqualTo(3) // 5.0, 7.0, 6.0 mmol/L
        assertThat(result.above).isEqualTo(2)   // 9.0, 10.0 mmol/L
        assertThat(result.count).isEqualTo(6)
    }

    @Test
    fun `mmol L values in tight diabetes range 3_9 to 7_8 mmol L`() {
        // Tight diabetes control range (common in Europe)
        val lowMgdl = 3.9 * MMOL_TO_MGDL   // 70 mg/dL
        val highMgdl = 7.8 * MMOL_TO_MGDL  // 140 mg/dL
        
        val readings = listOf(
            createGV(midnight, 3.5 * MMOL_TO_MGDL),   // 63 mg/dL - below
            createGV(midnight + T.mins(10).msecs(), 5.0 * MMOL_TO_MGDL),   // 90 mg/dL - in range
            createGV(midnight + T.mins(20).msecs(), 6.5 * MMOL_TO_MGDL),   // 117 mg/dL - in range
            createGV(midnight + T.mins(30).msecs(), 7.5 * MMOL_TO_MGDL),   // 135 mg/dL - in range
            createGV(midnight + T.mins(40).msecs(), 8.0 * MMOL_TO_MGDL),   // 144 mg/dL - above
            createGV(midnight + T.mins(50).msecs(), 12.0 * MMOL_TO_MGDL)   // 216 mg/dL - above
        )

        whenever(persistenceLayer.getBgReadingsDataFromTimeToTime(any(), any(), any()))
            .thenReturn(readings)

        val result = tirCalculator.calculate(7, lowMgdl, highMgdl)

        val tir = result[MidnightTime.calc(midnight)]
        assertThat(tir?.below).isEqualTo(1)   // 3.5 mmol/L
        assertThat(tir?.inRange).isEqualTo(3) // 5.0, 6.5, 7.5 mmol/L
        assertThat(tir?.above).isEqualTo(2)   // 8.0, 12.0 mmol/L
        assertThat(tir?.count).isEqualTo(6)
    }

    @Test
    fun `verify mmol L conversion factor accuracy`() {
        // Verify the conversion factor is correct
        val conversionFactor = MMOL_TO_MGDL
        
        // Test known conversions
        // 5.0 mmol/L = 90.09 mg/dL (in range with 70-180)
        // 9.0 mmol/L = 162.16 mg/dL (in range with 70-180)
        val value5mmol = 5.0 * conversionFactor
        val value9mmol = 9.0 * conversionFactor
        
        assertThat(value5mmol).isWithin(0.1).of(90.09)
        assertThat(value9mmol).isWithin(0.1).of(162.16)
        
        // Use these in actual calculation
        val readings = listOf(
            createGV(midnight, value5mmol),
            createGV(midnight + T.mins(5).msecs(), value9mmol)
        )

        whenever(persistenceLayer.getBgReadingsDataFromTimeToTime(any(), any(), any()))
            .thenReturn(readings)

        val result = tirCalculator.calculate(7, lowMgdl, highMgdl)

        val tir = result[MidnightTime.calc(midnight)]
        assertThat(tir?.count).isEqualTo(2)
        assertThat(tir?.inRange).isEqualTo(2) // Both should be in range with standard thresholds (70-180 mg/dL)
    }

    // ===== Helper method =====

    private fun createGV(timestamp: Long, value: Double): GV =
        GV(
            timestamp = timestamp,
            value = value,
            noise = null,
            raw = value,
            trendArrow = TrendArrow.FLAT,
            sourceSensor = SourceSensor.UNKNOWN
        )
}
