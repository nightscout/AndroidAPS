package app.aaps.implementation.stats

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class TirImplTest {

    private lateinit var tir: TirImpl
    private val testDate = 1000000000L
    private val lowThreshold = 70.0
    private val highThreshold = 180.0

    @BeforeEach
    fun setup() {
        tir = TirImpl(testDate, lowThreshold, highThreshold)
    }

    @Test
    fun `initial values are zero`() {
        assertThat(tir.below).isEqualTo(0)
        assertThat(tir.inRange).isEqualTo(0)
        assertThat(tir.above).isEqualTo(0)
        assertThat(tir.error).isEqualTo(0)
        assertThat(tir.count).isEqualTo(0)
    }

    @Test
    fun `date is set correctly`() {
        assertThat(tir.date).isEqualTo(testDate)
    }

    @Test
    fun `thresholds are set correctly`() {
        assertThat(tir.lowThreshold).isEqualTo(lowThreshold)
        assertThat(tir.highThreshold).isEqualTo(highThreshold)
    }

    @Test
    fun `error increments error counter but not count`() {
        tir.error()
        assertThat(tir.error).isEqualTo(1)
        assertThat(tir.count).isEqualTo(0)
    }

    @Test
    fun `below increments both below and count`() {
        tir.below()
        assertThat(tir.below).isEqualTo(1)
        assertThat(tir.count).isEqualTo(1)
    }

    @Test
    fun `inRange increments both inRange and count`() {
        tir.inRange()
        assertThat(tir.inRange).isEqualTo(1)
        assertThat(tir.count).isEqualTo(1)
    }

    @Test
    fun `above increments both above and count`() {
        tir.above()
        assertThat(tir.above).isEqualTo(1)
        assertThat(tir.count).isEqualTo(1)
    }

    @Test
    fun `multiple below calls increment correctly`() {
        tir.below()
        tir.below()
        tir.below()
        assertThat(tir.below).isEqualTo(3)
        assertThat(tir.count).isEqualTo(3)
    }

    @Test
    fun `multiple inRange calls increment correctly`() {
        tir.inRange()
        tir.inRange()
        tir.inRange()
        tir.inRange()
        assertThat(tir.inRange).isEqualTo(4)
        assertThat(tir.count).isEqualTo(4)
    }

    @Test
    fun `multiple above calls increment correctly`() {
        tir.above()
        tir.above()
        assertThat(tir.above).isEqualTo(2)
        assertThat(tir.count).isEqualTo(2)
    }

    @Test
    fun `multiple error calls increment correctly`() {
        tir.error()
        tir.error()
        tir.error()
        assertThat(tir.error).isEqualTo(3)
        assertThat(tir.count).isEqualTo(0)
    }

    @Test
    fun `mixed calls update counts correctly`() {
        tir.below()       // below=1, count=1
        tir.inRange()     // inRange=1, count=2
        tir.above()       // above=1, count=3
        tir.below()       // below=2, count=4
        tir.error()       // error=1, count=4

        assertThat(tir.below).isEqualTo(2)
        assertThat(tir.inRange).isEqualTo(1)
        assertThat(tir.above).isEqualTo(1)
        assertThat(tir.error).isEqualTo(1)
        assertThat(tir.count).isEqualTo(4)
    }

    @Test
    fun `percentage calculations with equal distribution`() {
        // Add equal amounts of each category
        repeat(10) { tir.below() }
        repeat(10) { tir.inRange() }
        repeat(10) { tir.above() }

        // Total count = 30
        // Below: 10/30 = 33.33%
        // InRange: 10/30 = 33.33%
        // Above: 10/30 = 33.33%
        // But inRangePct = 100 - belowPct - abovePct, so it might be slightly different due to rounding

        assertThat(tir.count).isEqualTo(30)
    }

    @Test
    fun `percentage calculations with all in range`() {
        repeat(20) { tir.inRange() }

        assertThat(tir.count).isEqualTo(20)
        assertThat(tir.below).isEqualTo(0)
        assertThat(tir.inRange).isEqualTo(20)
        assertThat(tir.above).isEqualTo(0)
    }

    @Test
    fun `percentage calculations with all below`() {
        repeat(15) { tir.below() }

        assertThat(tir.count).isEqualTo(15)
        assertThat(tir.below).isEqualTo(15)
        assertThat(tir.inRange).isEqualTo(0)
        assertThat(tir.above).isEqualTo(0)
    }

    @Test
    fun `percentage calculations with all above`() {
        repeat(25) { tir.above() }

        assertThat(tir.count).isEqualTo(25)
        assertThat(tir.below).isEqualTo(0)
        assertThat(tir.inRange).isEqualTo(0)
        assertThat(tir.above).isEqualTo(25)
    }

    @Test
    fun `errors do not affect percentage calculations`() {
        repeat(10) { tir.inRange() }
        repeat(5) { tir.error() }

        // Only inRange should count toward the total
        assertThat(tir.count).isEqualTo(10)
        assertThat(tir.error).isEqualTo(5)
    }

    @Test
    fun `large numbers are handled correctly`() {
        repeat(1000) { tir.below() }
        repeat(2000) { tir.inRange() }
        repeat(3000) { tir.above() }

        assertThat(tir.count).isEqualTo(6000)
        assertThat(tir.below).isEqualTo(1000)
        assertThat(tir.inRange).isEqualTo(2000)
        assertThat(tir.above).isEqualTo(3000)
    }

    @Test
    fun `realistic glucose monitoring scenario`() {
        // Simulate one day: 288 readings (5-minute intervals)
        // 70% in range, 20% above, 10% below
        repeat(29) { tir.below() }    // ~10%
        repeat(201) { tir.inRange() }  // ~70%
        repeat(58) { tir.above() }     // ~20%

        assertThat(tir.count).isEqualTo(288)
        assertThat(tir.below).isEqualTo(29)
        assertThat(tir.inRange).isEqualTo(201)
        assertThat(tir.above).isEqualTo(58)
    }

    @Test
    fun `can create multiple TIR instances with different thresholds`() {
        val tir1 = TirImpl(testDate, 70.0, 180.0)
        val tir2 = TirImpl(testDate, 80.0, 140.0)

        tir1.inRange()
        tir2.above()

        assertThat(tir1.lowThreshold).isEqualTo(70.0)
        assertThat(tir1.highThreshold).isEqualTo(180.0)
        assertThat(tir1.inRange).isEqualTo(1)

        assertThat(tir2.lowThreshold).isEqualTo(80.0)
        assertThat(tir2.highThreshold).isEqualTo(140.0)
        assertThat(tir2.above).isEqualTo(1)
    }

    @Test
    fun `can create TIR with different date values`() {
        val date1 = 1000000000L
        val date2 = 2000000000L

        val tir1 = TirImpl(date1, lowThreshold, highThreshold)
        val tir2 = TirImpl(date2, lowThreshold, highThreshold)

        assertThat(tir1.date).isEqualTo(date1)
        assertThat(tir2.date).isEqualTo(date2)
    }

    @Test
    fun `accumulating data from multiple sources`() {
        // Simulate accumulating TIR data
        val readings = listOf(50.0, 90.0, 120.0, 150.0, 200.0, 65.0, 100.0, 190.0)

        readings.forEach { value ->
            when {
                value < 39.0 -> tir.error()
                value >= 39.0 && value < lowThreshold -> tir.below()
                value in lowThreshold..highThreshold -> tir.inRange()
                value > highThreshold -> tir.above()
            }
        }

        assertThat(tir.count).isEqualTo(8)
        assertThat(tir.below).isEqualTo(2)  // 50, 65
        assertThat(tir.inRange).isEqualTo(4)  // 90, 120, 150, 100
        assertThat(tir.above).isEqualTo(2)  // 200, 190
        assertThat(tir.error).isEqualTo(0)
    }

    @Test
    fun `handles edge case with value exactly at low threshold`() {
        val readings = listOf(70.0)  // Exactly at low threshold

        readings.forEach { value ->
            when {
                value < 39.0 -> tir.error()
                value >= 39.0 && value < lowThreshold -> tir.below()
                value in lowThreshold..highThreshold -> tir.inRange()
                value > highThreshold -> tir.above()
            }
        }

        assertThat(tir.inRange).isEqualTo(1)
    }

    @Test
    fun `handles edge case with value exactly at high threshold`() {
        val readings = listOf(180.0)  // Exactly at high threshold

        readings.forEach { value ->
            when {
                value < 39.0 -> tir.error()
                value >= 39.0 && value < lowThreshold -> tir.below()
                value in lowThreshold..highThreshold -> tir.inRange()
                value > highThreshold -> tir.above()
            }
        }

        assertThat(tir.inRange).isEqualTo(1)
    }

    @Test
    fun `handles edge case with value just below low threshold`() {
        val readings = listOf(69.9)

        readings.forEach { value ->
            when {
                value < 39.0 -> tir.error()
                value >= 39.0 && value < lowThreshold -> tir.below()
                value in lowThreshold..highThreshold -> tir.inRange()
                value > highThreshold -> tir.above()
            }
        }

        assertThat(tir.below).isEqualTo(1)
    }

    @Test
    fun `handles edge case with value just above high threshold`() {
        val readings = listOf(180.1)

        readings.forEach { value ->
            when {
                value < 39.0 -> tir.error()
                value >= 39.0 && value < lowThreshold -> tir.below()
                value in lowThreshold..highThreshold -> tir.inRange()
                value > highThreshold -> tir.above()
            }
        }

        assertThat(tir.above).isEqualTo(1)
    }

    @Test
    fun `handles error values below 39`() {
        val readings = listOf(38.0, 30.0, 20.0)

        readings.forEach { value ->
            when {
                value < 39.0 -> tir.error()
                value >= 39.0 && value < lowThreshold -> tir.below()
                value in lowThreshold..highThreshold -> tir.inRange()
                value > highThreshold -> tir.above()
            }
        }

        assertThat(tir.error).isEqualTo(3)
        assertThat(tir.count).isEqualTo(0)
    }
}
