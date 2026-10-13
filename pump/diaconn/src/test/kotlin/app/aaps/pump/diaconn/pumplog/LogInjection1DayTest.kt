package app.aaps.pump.diaconn.pumplog

import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class LogInjection1DayTest : TestBase() {

    @Test
    fun parseValidLogData() {
        // Given - Daily meal and extended bolus totals
        // Format: timestamp(4) + typeAndKind(1) + mealAmount(2) + extAmount(2) + batteryRemain(1)
        // mealAmount: 3000 = 30.0U, extAmount: 500 = 5.0U
        val hexData = "23C1AB642FB80BF40155"

        // When
        val log = LogInjection1Day.parse(hexData)

        // Then
        assertThat(log).isNotNull()
        assertThat(log.data).isEqualTo(hexData)
        assertThat(log.kind).isEqualTo(LogInjection1Day.LOG_KIND)
        assertThat(log.mealAmount.toInt()).isEqualTo(3000) // 30.0U meal bolus
        assertThat(log.extAmount.toInt()).isEqualTo(500) // 5.0U extended bolus
        assertThat(log.batteryRemain.toInt()).isEqualTo(85) // 85% battery
    }

    @Test
    fun parseLogDataWithNoExtendedBolus() {
        // Given - Only meal bolus, no extended
        val hexData = "23C1AB642FB80B000055"

        // When
        val log = LogInjection1Day.parse(hexData)

        // Then
        assertThat(log.mealAmount.toInt()).isEqualTo(3000) // 30.0U meal
        assertThat(log.extAmount.toInt()).isEqualTo(0) // No extended
    }

    @Test
    fun parseLogDataWithNoMealBolus() {
        // Given - Only extended bolus, no meal
        val hexData = "23C1AB642F0000F40155"

        // When
        val log = LogInjection1Day.parse(hexData)

        // Then
        assertThat(log.mealAmount.toInt()).isEqualTo(0) // No meal
        assertThat(log.extAmount.toInt()).isEqualTo(500) // 5.0U extended
    }

    @Test
    fun parseLogDataWithHighMealAmount() {
        // Given - High meal bolus (100.0U per day)
        val hexData = "23C1AB642F1027F40155"

        // When
        val log = LogInjection1Day.parse(hexData)

        // Then
        assertThat(log.mealAmount.toInt()).isEqualTo(10000) // 100.0U meal
    }

    @Test
    fun parseLogDataWithHighExtendedAmount() {
        // Given - High extended bolus (20.0U per day)
        val hexData = "23C1AB642FB80BD00755"

        // When
        val log = LogInjection1Day.parse(hexData)

        // Then
        assertThat(log.extAmount.toInt()).isEqualTo(2000) // 20.0U extended
    }

    @Test
    fun parseLogDataWithLowBattery() {
        // Given - Low battery
        val hexData = "23C1AB642FB80BF4011E"

        // When
        val log = LogInjection1Day.parse(hexData)

        // Then
        assertThat(log.batteryRemain.toInt()).isEqualTo(30) // 30% battery
    }

    @Test
    fun toStringContainsAllFields() {
        // Given
        val hexData = "23C1AB642FB80BF40155"
        val log = LogInjection1Day.parse(hexData)

        // When
        val result = log.toString()

        // Then
        assertThat(result).contains("LOG_INJECTION_1DAY")
        assertThat(result).contains("LOG_KIND=${LogInjection1Day.LOG_KIND.toInt()}")
        assertThat(result).contains("mealAmount=")
        assertThat(result).contains("extAmount=")
        assertThat(result).contains("batteryRemain=")
    }

    @Test
    fun logKindConstantIsCorrect() {
        // Then
        assertThat(LogInjection1Day.LOG_KIND).isEqualTo(0x2F.toByte())
    }
}
