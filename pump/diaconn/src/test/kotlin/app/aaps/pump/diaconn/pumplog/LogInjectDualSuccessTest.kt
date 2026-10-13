package app.aaps.pump.diaconn.pumplog

import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class LogInjectDualSuccessTest : TestBase() {

    @Test
    fun parseValidLogData() {
        // Given - Dual bolus success: 25.5U normal + 10.0U square over 120 minutes
        // Format: timestamp(4) + typeAndKind(1) + injectNormAmount(2) + injectSquareAmount(2) + injectTime(1) + batteryRemain(1)
        // injectNormAmount: 2550 = 25.5U, injectSquareAmount: 1000 = 10.0U, injectTime: 120 minutes
        val hexData = "23C1AB6410F609E8037855"

        // When
        val log = LogInjectDualSuccess.parse(hexData)

        // Then
        assertThat(log).isNotNull()
        assertThat(log.data).isEqualTo(hexData)
        assertThat(log.kind).isEqualTo(LogInjectDualSuccess.LOG_KIND)
        assertThat(log.injectSquareAmount.toInt()).isEqualTo(1000) // 10.0U square
        assertThat(log.batteryRemain.toInt()).isEqualTo(85) // 85% battery
    }

    @Test
    fun parseLogDataWithShortDuration() {
        // Given - Dual bolus with 30 minute duration
        val hexData = "23C1AB6410F609E8031E55"

        // When
        val log = LogInjectDualSuccess.parse(hexData)

        // Then
        assertThat(log.getInjectTime()).isEqualTo(30) // 30 minutes
    }

    @Test
    fun parseLogDataWithLongDuration() {
        // Given - Dual bolus with 4 hour (240 minute) duration
        val hexData = "23C1AB6410F609E803F055"

        // When
        val log = LogInjectDualSuccess.parse(hexData)

        // Then
        assertThat(log.getInjectTime()).isEqualTo(240) // 240 minutes = 4 hours
    }

    @Test
    fun parseLogDataWithSmallDose() {
        // Given - Small dual bolus: 0.5U normal + 0.5U square
        val hexData = "23C1AB6410320032007855"

        // When
        val log = LogInjectDualSuccess.parse(hexData)

        // Then
        assertThat(log.injectSquareAmount.toInt()).isEqualTo(50) // 0.5U
    }

    @Test
    fun parseLogDataWithLargeDose() {
        // Given - Large dual bolus: 50.0U normal + 30.0U square
        val hexData = "23C1AB64101388B80B7855"

        // When
        val log = LogInjectDualSuccess.parse(hexData)

        // Then
        assertThat(log.injectSquareAmount.toInt()).isEqualTo(3000) // 30.0U
    }

    @Test
    fun parseLogDataWithLowBattery() {
        // Given - Dual bolus with low battery
        val hexData = "23C1AB6410F609E8037814"

        // When
        val log = LogInjectDualSuccess.parse(hexData)

        // Then
        assertThat(log.batteryRemain.toInt()).isEqualTo(20) // 20% battery
    }

    @Test
    fun getInjectTimeShouldReturnUnsignedValue() {
        // Given
        val hexData = "23C1AB6410F609E8037855"
        val log = LogInjectDualSuccess.parse(hexData)

        // When
        val injectTime = log.getInjectTime()

        // Then
        assertThat(injectTime).isEqualTo(120) // 120 minutes
    }

    @Test
    fun toStringContainsAllFields() {
        // Given
        val hexData = "23C1AB6410F609E8037855"
        val log = LogInjectDualSuccess.parse(hexData)

        // When
        val result = log.toString()

        // Then
        assertThat(result).contains("LOG_INJECT_DUAL_SUCCESS")
        assertThat(result).contains("LOG_KIND=${LogInjectDualSuccess.LOG_KIND.toInt()}")
        assertThat(result).contains("injectNormAmount=")
        assertThat(result).contains("injectSquareAmount=")
        assertThat(result).contains("injectTime=")
        assertThat(result).contains("batteryRemain=")
    }

    @Test
    fun logKindConstantIsCorrect() {
        // Then
        assertThat(LogInjectDualSuccess.LOG_KIND).isEqualTo(0x10.toByte())
    }
}
