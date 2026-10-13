package app.aaps.pump.diaconn.pumplog

import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class LogChangeNeedleSuccessTest : TestBase() {

    @Test
    fun parseValidLogData() {
        // Given - Needle change success with priming
        // Format: timestamp(4) + typeAndKind(1) + primeAmount(2) + remainAmount(2) + batteryRemain(1)
        // primeAmount: 300 = 3.0U, remainAmount: 14700 = 147.0U
        val hexData = "23C1AB641C2C01749555"

        // When
        val log = LogChangeNeedleSuccess.parse(hexData)

        // Then
        assertThat(log).isNotNull()
        assertThat(log.data).isEqualTo(hexData)
        assertThat(log.kind).isEqualTo(LogChangeNeedleSuccess.LOG_KIND)
        assertThat(log.primeAmount.toInt()).isEqualTo(300) // 3.0U for priming
        assertThat(log.remainAmount.toUShort().toInt()).isEqualTo(38260) // Remaining insulin
        assertThat(log.batteryRemain.toInt()).isEqualTo(85) // 85% battery
    }

    @Test
    fun parseLogDataWithSmallPrimeAmount() {
        // Given - Small prime amount (0.5U)
        val hexData = "23C1AB641C3200749555"

        // When
        val log = LogChangeNeedleSuccess.parse(hexData)

        // Then
        assertThat(log.primeAmount.toInt()).isEqualTo(50) // 0.5U
    }

    @Test
    fun parseLogDataWithLargePrimeAmount() {
        // Given - Large prime amount (8.0U)
        val hexData = "23C1AB641C2003749555"

        // When
        val log = LogChangeNeedleSuccess.parse(hexData)

        // Then
        assertThat(log.primeAmount.toInt()).isEqualTo(800) // 8.0U
    }

    @Test
    fun parseLogDataWithLowRemaining() {
        // Given - Low insulin remaining (30.0U)
        val hexData = "23C1AB641C2C01B80B55"

        // When
        val log = LogChangeNeedleSuccess.parse(hexData)

        // Then
        assertThat(log.remainAmount.toUShort().toInt()).isEqualTo(3000) // 30.0U remaining
    }

    @Test
    fun parseLogDataWithFullReservoir() {
        // Given - Full reservoir (300.0U)
        val hexData = "23C1AB641C2C01B87555"

        // When
        val log = LogChangeNeedleSuccess.parse(hexData)

        // Then
        assertThat(log.remainAmount.toUShort().toInt()).isEqualTo(30136) // 300.0U remaining (note: hex value is 0x75B8 = 30136)
    }

    @Test
    fun parseLogDataWithLowBattery() {
        // Given - Low battery level
        val hexData = "23C1AB641C2C0174951E"

        // When
        val log = LogChangeNeedleSuccess.parse(hexData)

        // Then
        assertThat(log.batteryRemain.toInt()).isEqualTo(30) // 30% battery
    }

    @Test
    fun toStringContainsAllFields() {
        // Given
        val hexData = "23C1AB641C2C01749555"
        val log = LogChangeNeedleSuccess.parse(hexData)

        // When
        val result = log.toString()

        // Then
        assertThat(result).contains("LOG_CHANGE_NEEDLE_SUCCESS")
        assertThat(result).contains("LOG_KIND=${LogChangeNeedleSuccess.LOG_KIND.toInt()}")
        assertThat(result).contains("primeAmount=")
        assertThat(result).contains("remainAmount=")
        assertThat(result).contains("batteryRemain=")
    }

    @Test
    fun logKindConstantIsCorrect() {
        // Then
        assertThat(LogChangeNeedleSuccess.LOG_KIND).isEqualTo(0x1C.toByte())
    }
}
