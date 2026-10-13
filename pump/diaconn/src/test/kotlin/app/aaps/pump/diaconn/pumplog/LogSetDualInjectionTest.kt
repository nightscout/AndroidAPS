package app.aaps.pump.diaconn.pumplog

import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class LogSetDualInjectionTest : TestBase() {

    @Test
    fun parseValidLogData() {
        // Given - Dual bolus setting/start
        // Format: timestamp(4) + typeAndKind(1) + setNormAmount(2) + setSquareAmount(2) + injectTime(1) + batteryRemain(1)
        // setNormAmount: 500 = 5.0U, setSquareAmount: 1000 = 10.0U, injectTime: 12 (120 minutes)
        val hexData = "23C1AB640FF401E8030C55"

        // When
        val log = LogSetDualInjection.parse(hexData)

        // Then
        assertThat(log).isNotNull()
        assertThat(log.data).isEqualTo(hexData)
        assertThat(log.kind).isEqualTo(LogSetDualInjection.LOG_KIND)
        assertThat(log.setSquareAmount.toInt()).isEqualTo(1000) // 10.0U square
        assertThat(log.getInjectTime()).isEqualTo(12) // 12 * 10 min = 120 minutes
        assertThat(log.batteryRemain.toInt()).isEqualTo(85) // 85% battery
    }

    @Test
    fun parseLogDataWithShortDuration() {
        // Given - Short duration (1 * 10min = 10 minutes)
        val hexData = "23C1AB640FF401E8030155"

        // When
        val log = LogSetDualInjection.parse(hexData)

        // Then
        assertThat(log.getInjectTime()).isEqualTo(1) // 10 minutes
    }

    @Test
    fun parseLogDataWithLongDuration() {
        // Given - Long duration (30 * 10min = 300 minutes = 5 hours)
        val hexData = "23C1AB640FF401E8031E55"

        // When
        val log = LogSetDualInjection.parse(hexData)

        // Then
        assertThat(log.getInjectTime()).isEqualTo(30) // 300 minutes
    }

    @Test
    fun parseLogDataWithSmallDose() {
        // Given - Small dual bolus (0.5U + 1.0U)
        val hexData = "23C1AB640F320064000655"

        // When
        val log = LogSetDualInjection.parse(hexData)

        // Then
        assertThat(log.setSquareAmount.toInt()).isEqualTo(100) // 1.0U square
    }

    @Test
    fun parseLogDataWithLargeDose() {
        // Given - Large dual bolus (30.0U + 20.0U)
        val hexData = "23C1AB640FB80BD0071855"

        // When
        val log = LogSetDualInjection.parse(hexData)

        // Then
        assertThat(log.setSquareAmount.toInt()).isEqualTo(2000) // 20.0U square
    }

    @Test
    fun parseLogDataWithMidDuration() {
        // Given - Mid duration (18 * 10min = 180 minutes = 3 hours)
        val hexData = "23C1AB640FF401E8031255"

        // When
        val log = LogSetDualInjection.parse(hexData)

        // Then
        assertThat(log.getInjectTime()).isEqualTo(18) // 180 minutes
    }

    @Test
    fun getInjectTimeShouldReturnUnsignedValue() {
        // Given
        val hexData = "23C1AB640FF401E8030C55"
        val log = LogSetDualInjection.parse(hexData)

        // When
        val injectTime = log.getInjectTime()

        // Then
        assertThat(injectTime).isEqualTo(12) // 120 minutes
    }

    @Test
    fun toStringContainsAllFields() {
        // Given
        val hexData = "23C1AB640FF401E8030C55"
        val log = LogSetDualInjection.parse(hexData)

        // When
        val result = log.toString()

        // Then
        assertThat(result).contains("LOG_SET_DUAL_INJECTION")
        assertThat(result).contains("LOG_KIND=${LogSetDualInjection.LOG_KIND.toInt()}")
        assertThat(result).contains("setNormAmount=")
        assertThat(result).contains("setSquareAmount=")
        assertThat(result).contains("injectTime=")
        assertThat(result).contains("batteryRemain=")
    }

    @Test
    fun logKindConstantIsCorrect() {
        // Then
        assertThat(LogSetDualInjection.LOG_KIND).isEqualTo(0x0F.toByte())
    }
}
