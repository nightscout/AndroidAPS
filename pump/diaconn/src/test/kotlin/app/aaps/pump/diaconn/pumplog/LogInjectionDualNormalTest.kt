package app.aaps.pump.diaconn.pumplog

import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class LogInjectionDualNormalTest : TestBase() {

    @Test
    fun parseValidLogData() {
        // Given - Dual normal portion completion
        // Format: timestamp(4) + typeAndKind(1) + setAmount(2) + injectAmount(2) + injectTime(1) + batteryRemain(1)
        // setAmount: 500 = 5.0U, injectAmount: 500 = 5.0U, injectTime: 5 minutes
        val hexData = "23C1AB6435F401F4010555"

        // When
        val log = LogInjectionDualNormal.parse(hexData)

        // Then
        assertThat(log).isNotNull()
        assertThat(log.data).isEqualTo(hexData)
        assertThat(log.kind).isEqualTo(LogInjectionDualNormal.LOG_KIND)
        assertThat(log.injectAmount.toInt()).isEqualTo(500) // 5.0U injected
        assertThat(log.getInjectTime()).isEqualTo(5) // 5 minutes
        assertThat(log.batteryRemain.toInt()).isEqualTo(85) // 85% battery
    }

    @Test
    fun parseLogDataWithSmallDose() {
        // Given - Small normal portion (1.0U)
        val hexData = "23C1AB6435640064000555"

        // When
        val log = LogInjectionDualNormal.parse(hexData)

        // Then
        assertThat(log.injectAmount.toInt()).isEqualTo(100) // 1.0U
    }

    @Test
    fun parseLogDataWithLargeDose() {
        // Given - Large normal portion (30.0U)
        val hexData = "23C1AB6435B80BB80B0555"

        // When
        val log = LogInjectionDualNormal.parse(hexData)

        // Then
        assertThat(log.injectAmount.toInt()).isEqualTo(3000) // 30.0U
    }

    @Test
    fun parseLogDataWithShortDuration() {
        // Given - Short injection duration (1 minute)
        val hexData = "23C1AB6435F401F4010155"

        // When
        val log = LogInjectionDualNormal.parse(hexData)

        // Then
        assertThat(log.getInjectTime()).isEqualTo(1) // 1 minute
    }

    @Test
    fun parseLogDataWithLongDuration() {
        // Given - Long injection duration (30 minutes)
        val hexData = "23C1AB6435F401F4011E55"

        // When
        val log = LogInjectionDualNormal.parse(hexData)

        // Then
        assertThat(log.getInjectTime()).isEqualTo(30) // 30 minutes
    }

    @Test
    fun parseLogDataWithPartialInjection() {
        // Given - Partial injection (set 10.0U, injected 7.5U)
        val hexData = "23C1AB6435E803EE020555"

        // When
        val log = LogInjectionDualNormal.parse(hexData)

        // Then
        assertThat(log.injectAmount.toInt()).isEqualTo(750) // 7.5U actually injected
    }

    @Test
    fun getInjectTimeShouldReturnUnsignedValue() {
        // Given
        val hexData = "23C1AB6435F401F401F055"
        val log = LogInjectionDualNormal.parse(hexData)

        // When
        val injectTime = log.getInjectTime()

        // Then
        assertThat(injectTime).isEqualTo(240) // 240 minutes
    }

    @Test
    fun toStringContainsAllFields() {
        // Given
        val hexData = "23C1AB6435F401F4010555"
        val log = LogInjectionDualNormal.parse(hexData)

        // When
        val result = log.toString()

        // Then
        assertThat(result).contains("LOG_INJECTION_DUAL_NORMAL")
        assertThat(result).contains("LOG_KIND=${LogInjectionDualNormal.LOG_KIND.toInt()}")
        assertThat(result).contains("setAmount=")
        assertThat(result).contains("injectAmount=")
        assertThat(result).contains("injectTime=")
        assertThat(result).contains("batteryRemain=")
    }

    @Test
    fun logKindConstantIsCorrect() {
        // Then
        assertThat(LogInjectionDualNormal.LOG_KIND).isEqualTo(0x35.toByte())
    }
}
