package app.aaps.pump.diaconn.pumplog

import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class LogSetSquareInjectionTest : TestBase() {

    @Test
    fun parseValidLogData() {
        // Given - Square bolus setting/start
        // Format: timestamp(4) + typeAndKind(1) + setAmount(2) + injectTime(1) + batteryRemain(1)
        // setAmount: 1000 = 10.0U, injectTime: 12 (120 minutes)
        val hexData = "23C1AB640CE8030C55"

        // When
        val log = LogSetSquareInjection.parse(hexData)

        // Then
        assertThat(log).isNotNull()
        assertThat(log.data).isEqualTo(hexData)
        assertThat(log.kind).isEqualTo(LogSetSquareInjection.LOG_KIND)
        assertThat(log.setAmount.toInt()).isEqualTo(1000) // 10.0U
        assertThat(log.getInjectTime()).isEqualTo(12) // 12 * 10 min = 120 minutes
        assertThat(log.batteryRemain.toInt()).isEqualTo(85) // 85% battery
    }

    @Test
    fun parseLogDataWithShortDuration() {
        // Given - Short duration (1 * 10min = 10 minutes)
        val hexData = "23C1AB640CE8030155"

        // When
        val log = LogSetSquareInjection.parse(hexData)

        // Then
        assertThat(log.getInjectTime()).isEqualTo(1) // 10 minutes
    }

    @Test
    fun parseLogDataWithLongDuration() {
        // Given - Long duration (30 * 10min = 300 minutes = 5 hours)
        val hexData = "23C1AB640CE8031E55"

        // When
        val log = LogSetSquareInjection.parse(hexData)

        // Then
        assertThat(log.getInjectTime()).isEqualTo(30) // 300 minutes
    }

    @Test
    fun parseLogDataWithSmallDose() {
        // Given - Small square bolus (0.5U)
        val hexData = "23C1AB640C32000C55"

        // When
        val log = LogSetSquareInjection.parse(hexData)

        // Then
        assertThat(log.setAmount.toInt()).isEqualTo(50) // 0.5U
    }

    @Test
    fun parseLogDataWithLargeDose() {
        // Given - Large square bolus (50.0U)
        val hexData = "23C1AB640C88130C55"

        // When
        val log = LogSetSquareInjection.parse(hexData)

        // Then
        assertThat(log.setAmount.toInt()).isEqualTo(5000) // 50.0U
    }

    @Test
    fun parseLogDataWithMidDuration() {
        // Given - Mid duration (18 * 10min = 180 minutes = 3 hours)
        val hexData = "23C1AB640CE8031255"

        // When
        val log = LogSetSquareInjection.parse(hexData)

        // Then
        assertThat(log.getInjectTime()).isEqualTo(18) // 180 minutes
    }

    @Test
    fun parseLogDataWithLowBattery() {
        // Given - Low battery
        val hexData = "23C1AB640CE8030C1E"

        // When
        val log = LogSetSquareInjection.parse(hexData)

        // Then
        assertThat(log.batteryRemain.toInt()).isEqualTo(30) // 30% battery
    }

    @Test
    fun getInjectTimeShouldReturnUnsignedValue() {
        // Given
        val hexData = "23C1AB640CE8030C55"
        val log = LogSetSquareInjection.parse(hexData)

        // When
        val injectTime = log.getInjectTime()

        // Then
        assertThat(injectTime).isEqualTo(12) // 120 minutes
    }

    @Test
    fun toStringContainsAllFields() {
        // Given
        val hexData = "23C1AB640CE8030C55"
        val log = LogSetSquareInjection.parse(hexData)

        // When
        val result = log.toString()

        // Then
        assertThat(result).contains("LOG_SET_SQUARE_INJECTION")
        assertThat(result).contains("LOG_KIND=${LogSetSquareInjection.LOG_KIND.toInt()}")
        assertThat(result).contains("setAmount=")
        assertThat(result).contains("injectTime=")
        assertThat(result).contains("batteryRemain=")
    }

    @Test
    fun logKindConstantIsCorrect() {
        // Then
        assertThat(LogSetSquareInjection.LOG_KIND).isEqualTo(0x0C.toByte())
    }
}
