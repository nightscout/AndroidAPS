package app.aaps.pump.diaconn.pumplog

import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class LogChangeInjectorSuccessTest : TestBase() {

    @Test
    fun parseValidLogData() {
        // Given - Injector change success with priming
        // Format: timestamp(4) + typeAndKind(1) + primeAmount(2) + remainAmount(2) + batteryRemain(1)
        // primeAmount: 500 = 5.0U, remainAmount: 15000 = 150.0U
        val hexData = "23C1AB641AF401983A55"

        // When
        val log = LogChangeInjectorSuccess.parse(hexData)

        // Then
        assertThat(log).isNotNull()
        assertThat(log.data).isEqualTo(hexData)
        assertThat(log.kind).isEqualTo(LogChangeInjectorSuccess.LOG_KIND)
        assertThat(log.primeAmount.toInt()).isEqualTo(500) // 5.0U for priming
        assertThat(log.remainAmount.toUShort().toInt()).isEqualTo(15000) // 150.0U remaining
        assertThat(log.batteryRemain.toInt()).isEqualTo(85) // 85% battery
    }

    @Test
    fun parseLogDataWithSmallPrimeAmount() {
        // Given - Small prime amount (1.0U)
        val hexData = "23C1AB641A6400983A55"

        // When
        val log = LogChangeInjectorSuccess.parse(hexData)

        // Then
        assertThat(log.primeAmount.toInt()).isEqualTo(100) // 1.0U
    }

    @Test
    fun parseLogDataWithLargePrimeAmount() {
        // Given - Large prime amount (10.0U)
        val hexData = "23C1AB641AE803983A55"

        // When
        val log = LogChangeInjectorSuccess.parse(hexData)

        // Then
        assertThat(log.primeAmount.toInt()).isEqualTo(1000) // 10.0U
    }

    @Test
    fun parseLogDataWithLowRemaining() {
        // Given - Low insulin remaining (20.0U)
        val hexData = "23C1AB641AF401D00755"

        // When
        val log = LogChangeInjectorSuccess.parse(hexData)

        // Then
        assertThat(log.remainAmount.toUShort().toInt()).isEqualTo(2000) // 20.0U remaining
    }

    @Test
    fun parseLogDataWithFullReservoir() {
        // Given - Full reservoir (300.0U)
        val hexData = "23C1AB641AF401307555"

        // When
        val log = LogChangeInjectorSuccess.parse(hexData)

        // Then
        assertThat(log.remainAmount.toUShort().toInt()).isEqualTo(30000) // 300.0U remaining
    }

    @Test
    fun parseLogDataWithLowBattery() {
        // Given - Low battery level
        val hexData = "23C1AB641AF401983A14"

        // When
        val log = LogChangeInjectorSuccess.parse(hexData)

        // Then
        assertThat(log.batteryRemain.toInt()).isEqualTo(20) // 20% battery
    }

    @Test
    fun toStringContainsAllFields() {
        // Given
        val hexData = "23C1AB641AF401983A55"
        val log = LogChangeInjectorSuccess.parse(hexData)

        // When
        val result = log.toString()

        // Then
        assertThat(result).contains("LOG_CHANGE_INJECTOR_SUCCESS")
        assertThat(result).contains("LOG_KIND=${LogChangeInjectorSuccess.LOG_KIND.toInt()}")
        assertThat(result).contains("primeAmount=")
        assertThat(result).contains("remainAmount=")
        assertThat(result).contains("batteryRemain=")
    }

    @Test
    fun logKindConstantIsCorrect() {
        // Then
        assertThat(LogChangeInjectorSuccess.LOG_KIND).isEqualTo(0x1A.toByte())
    }
}
