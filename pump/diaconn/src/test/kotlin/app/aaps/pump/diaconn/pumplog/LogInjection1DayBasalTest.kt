package app.aaps.pump.diaconn.pumplog

import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class LogInjection1DayBasalTest : TestBase() {

    @Test
    fun parseValidLogData() {
        // Given - Daily basal total
        // Format: timestamp(4) + typeAndKind(1) + amount(2) + batteryRemain(1)
        // amount: 2400 = 24.0U per day
        val hexData = "23C1AB642E600955"

        // When
        val log = LogInjection1DayBasal.parse(hexData)

        // Then
        assertThat(log).isNotNull()
        assertThat(log.data).isEqualTo(hexData)
        assertThat(log.kind).isEqualTo(LogInjection1DayBasal.LOG_KIND)
        assertThat(log.amount.toInt()).isEqualTo(2400) // 24.0U per day
        assertThat(log.batteryRemain.toInt()).isEqualTo(85) // 85% battery
    }

    @Test
    fun parseLogDataWithLowDailyBasal() {
        // Given - Low daily basal (5.0U per day)
        val hexData = "23C1AB642EF40155"

        // When
        val log = LogInjection1DayBasal.parse(hexData)

        // Then
        assertThat(log.amount.toInt()).isEqualTo(500) // 5.0U per day
    }

    @Test
    fun parseLogDataWithHighDailyBasal() {
        // Given - High daily basal (60.0U per day)
        val hexData = "23C1AB642E701755"

        // When
        val log = LogInjection1DayBasal.parse(hexData)

        // Then
        assertThat(log.amount.toInt()).isEqualTo(6000) // 60.0U per day
    }

    @Test
    fun parseLogDataWithModerateDailyBasal() {
        // Given - Moderate daily basal (36.0U per day, typical 1.5U/h)
        val hexData = "23C1AB642E100E55"

        // When
        val log = LogInjection1DayBasal.parse(hexData)

        // Then
        assertThat(log.amount.toInt()).isEqualTo(3600) // 36.0U per day
    }

    @Test
    fun parseLogDataWithLowBattery() {
        // Given - Low battery
        val hexData = "23C1AB642E60091E"

        // When
        val log = LogInjection1DayBasal.parse(hexData)

        // Then
        assertThat(log.batteryRemain.toInt()).isEqualTo(30) // 30% battery
    }

    @Test
    fun toStringContainsAllFields() {
        // Given
        val hexData = "23C1AB642E600955"
        val log = LogInjection1DayBasal.parse(hexData)

        // When
        val result = log.toString()

        // Then
        assertThat(result).contains("LOG_INJECTION_1DAY_BASAL")
        assertThat(result).contains("LOG_KIND=${LogInjection1DayBasal.LOG_KIND.toInt()}")
        assertThat(result).contains("amount=")
        assertThat(result).contains("batteryRemain=")
    }

    @Test
    fun logKindConstantIsCorrect() {
        // Then
        assertThat(LogInjection1DayBasal.LOG_KIND).isEqualTo(0x2E.toByte())
    }
}
