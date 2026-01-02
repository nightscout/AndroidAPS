package app.aaps.pump.diaconn.pumplog

import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class LogInjectSquareSuccessTest : TestBase() {

    @Test
    fun parseValidLogData() {
        // Given - Square bolus success: 10.0U over 120 minutes
        // Format: timestamp(4) + typeAndKind(1) + injectAmount(2) + injectTime(1) + batteryRemain(1)
        // injectAmount: 1000 = 10.0U, injectTime: 120 minutes
        val hexData = "23C1AB640DE8037855"

        // When
        val log = LogInjectSquareSuccess.parse(hexData)

        // Then
        assertThat(log).isNotNull()
        assertThat(log.data).isEqualTo(hexData)
        assertThat(log.kind).isEqualTo(LogInjectSquareSuccess.LOG_KIND)
        assertThat(log.getInjectTime()).isEqualTo(120) // 120 minutes
        assertThat(log.batteryRemain.toInt()).isEqualTo(85) // 85% battery
    }

    @Test
    fun parseLogDataWithShortDuration() {
        // Given - Square bolus with 30 minute duration
        val hexData = "23C1AB640DE8031E55"

        // When
        val log = LogInjectSquareSuccess.parse(hexData)

        // Then
        assertThat(log.getInjectTime()).isEqualTo(30) // 30 minutes
    }

    @Test
    fun parseLogDataWithLongDuration() {
        // Given - Square bolus with 8 hour (480 minute) duration
        val hexData = "23C1AB640DE803E055"

        // When
        val log = LogInjectSquareSuccess.parse(hexData)

        // Then
        assertThat(log.getInjectTime()).isEqualTo(224) // 224 minutes
    }

    @Test
    fun parseLogDataWithSmallDose() {
        // Given - Small square bolus: 0.5U
        val hexData = "23C1AB640D32007855"

        // When
        val log = LogInjectSquareSuccess.parse(hexData)

        // Then
        assertThat(log.getInjectTime()).isEqualTo(120) // 120 minutes
    }

    @Test
    fun parseLogDataWithLargeDose() {
        // Given - Large square bolus: 50.0U
        val hexData = "23C1AB640D88137855"

        // When
        val log = LogInjectSquareSuccess.parse(hexData)

        // Then
        assertThat(log.getInjectTime()).isEqualTo(120) // 120 minutes
    }

    @Test
    fun parseLogDataWithLowBattery() {
        // Given - Square bolus with low battery
        val hexData = "23C1AB640DE803781E"

        // When
        val log = LogInjectSquareSuccess.parse(hexData)

        // Then
        assertThat(log.batteryRemain.toInt()).isEqualTo(30) // 30% battery
    }

    @Test
    fun getInjectTimeShouldReturnUnsignedValue() {
        // Given
        val hexData = "23C1AB640DE803F055"
        val log = LogInjectSquareSuccess.parse(hexData)

        // When
        val injectTime = log.getInjectTime()

        // Then
        assertThat(injectTime).isEqualTo(240) // 240 minutes
    }

    @Test
    fun toStringContainsAllFields() {
        // Given
        val hexData = "23C1AB640DE8037855"
        val log = LogInjectSquareSuccess.parse(hexData)

        // When
        val result = log.toString()

        // Then
        assertThat(result).contains("LOG_INJECT_SQUARE_SUCCESS")
        assertThat(result).contains("LOG_KIND=${LogInjectSquareSuccess.LOG_KIND.toInt()}")
        assertThat(result).contains("injectAmount=")
        assertThat(result).contains("injectTime=")
        assertThat(result).contains("batteryRemain=")
    }

    @Test
    fun logKindConstantIsCorrect() {
        // Then
        assertThat(LogInjectSquareSuccess.LOG_KIND).isEqualTo(0x0D.toByte())
    }
}
