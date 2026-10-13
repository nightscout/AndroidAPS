package app.aaps.pump.diaconn.pumplog

import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class LogInjection1HourBasalTest : TestBase() {

    @Test
    fun parseValidLogData() {
        // Given - 1 hour basal injection record
        // Format: timestamp(4) + typeAndKind(1) + tbBeforeAmount(2) + tbAfterAmount(2) + batteryRemain(1) + remainTotalAmount(2)
        // tbBeforeAmount: 100 = 1.0U, tbAfterAmount: 120 = 1.2U (with TB adjustment)
        val hexData = "23C1AB642C6400780055983A"

        // When
        val log = LogInjection1HourBasal.parse(hexData)

        // Then
        assertThat(log).isNotNull()
        assertThat(log.data).isEqualTo(hexData)
        assertThat(log.kind).isEqualTo(LogInjection1HourBasal.LOG_KIND)
        assertThat(log.beforeAmount.toInt()).isEqualTo(100) // 1.0U before TB
        assertThat(log.afterAmount.toInt()).isEqualTo(120) // 1.2U after TB
        assertThat(log.batteryRemain.toInt()).isEqualTo(85) // 85% battery
    }

    @Test
    fun parseLogDataWithNoTempBasal() {
        // Given - Basal with no TB adjustment (same before/after)
        val hexData = "23C1AB642C6400640055983A"

        // When
        val log = LogInjection1HourBasal.parse(hexData)

        // Then
        assertThat(log.beforeAmount.toInt()).isEqualTo(100) // 1.0U
        assertThat(log.afterAmount.toInt()).isEqualTo(100) // 1.0U (no change)
    }

    @Test
    fun parseLogDataWithHighBasalRate() {
        // Given - High basal rate (5.0U per hour)
        val hexData = "23C1AB642CF401F40155983A"

        // When
        val log = LogInjection1HourBasal.parse(hexData)

        // Then
        assertThat(log.beforeAmount.toInt()).isEqualTo(500) // 5.0U
        assertThat(log.afterAmount.toInt()).isEqualTo(500) // 5.0U
    }

    @Test
    fun parseLogDataWithLowBasalRate() {
        // Given - Low basal rate (0.1U per hour)
        val hexData = "23C1AB642C0A000A0055983A"

        // When
        val log = LogInjection1HourBasal.parse(hexData)

        // Then
        assertThat(log.beforeAmount.toInt()).isEqualTo(10) // 0.1U
        assertThat(log.afterAmount.toInt()).isEqualTo(10) // 0.1U
    }

    @Test
    fun parseLogDataWithTempBasalIncrease() {
        // Given - TB increased basal (1.0U -> 2.0U, 200% TB)
        val hexData = "23C1AB642C6400C80055983A"

        // When
        val log = LogInjection1HourBasal.parse(hexData)

        // Then
        assertThat(log.beforeAmount.toInt()).isEqualTo(100) // 1.0U before
        assertThat(log.afterAmount.toInt()).isEqualTo(200) // 2.0U after (doubled)
    }

    @Test
    fun parseLogDataWithTempBasalDecrease() {
        // Given - TB decreased basal (1.0U -> 0.5U, 50% TB)
        val hexData = "23C1AB642C6400320055983A"

        // When
        val log = LogInjection1HourBasal.parse(hexData)

        // Then
        assertThat(log.beforeAmount.toInt()).isEqualTo(100) // 1.0U before
        assertThat(log.afterAmount.toInt()).isEqualTo(50) // 0.5U after (halved)
    }

    @Test
    fun toStringContainsAllFields() {
        // Given
        val hexData = "23C1AB642C6400780055983A"
        val log = LogInjection1HourBasal.parse(hexData)

        // When
        val result = log.toString()

        // Then
        assertThat(result).contains("LOG_INJECTION_1HOUR_BASAL")
        assertThat(result).contains("LOG_KIND=${LogInjection1HourBasal.LOG_KIND.toInt()}")
        assertThat(result).contains("tbBeforeAmount=")
        assertThat(result).contains("tbAfterAmount=")
        assertThat(result).contains("batteryRemain=")
        assertThat(result).contains("remainTotalAmount=")
    }

    @Test
    fun logKindConstantIsCorrect() {
        // Then
        assertThat(LogInjection1HourBasal.LOG_KIND).isEqualTo(0x2C.toByte())
    }
}
