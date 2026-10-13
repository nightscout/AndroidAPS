package app.aaps.pump.diaconn.pumplog

import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class LogChangeTubeSuccessTest : TestBase() {

    @Test
    fun parseValidLogData() {
        // Given - Tube change success with priming
        // Format: timestamp(4) + typeAndKind(1) + primeAmount(2) + remainAmount(2) + batteryRemain(1)
        // primeAmount: 1500 = 15.0U, remainAmount: 13500 = 135.0U
        val hexData = "23C1AB6418DC05BC3455"

        // When
        val log = LogChangeTubeSuccess.parse(hexData)

        // Then
        assertThat(log).isNotNull()
        assertThat(log.data).isEqualTo(hexData)
        assertThat(log.kind).isEqualTo(LogChangeTubeSuccess.LOG_KIND)
        assertThat(log.primeAmount.toInt()).isEqualTo(1500) // 15.0U for priming
        assertThat(log.remainAmount.toUShort().toInt()).isEqualTo(13500) // 135.0U remaining
        assertThat(log.batteryRemain.toInt()).isEqualTo(85) // 85% battery
    }

    @Test
    fun parseLogDataWithSmallPrimeAmount() {
        // Given - Small prime amount (5.0U)
        val hexData = "23C1AB6418F401BC3455"

        // When
        val log = LogChangeTubeSuccess.parse(hexData)

        // Then
        assertThat(log.primeAmount.toInt()).isEqualTo(500) // 5.0U
    }

    @Test
    fun parseLogDataWithLargePrimeAmount() {
        // Given - Large prime amount (25.0U)
        val hexData = "23C1AB6418C409BC3455"

        // When
        val log = LogChangeTubeSuccess.parse(hexData)

        // Then
        assertThat(log.primeAmount.toInt()).isEqualTo(2500) // 25.0U
    }

    @Test
    fun parseLogDataWithLowRemaining() {
        // Given - Low insulin remaining (50.0U)
        val hexData = "23C1AB6418DC05881355"

        // When
        val log = LogChangeTubeSuccess.parse(hexData)

        // Then
        assertThat(log.remainAmount.toUShort().toInt()).isEqualTo(5000) // 50.0U remaining
    }

    @Test
    fun parseLogDataWithFullReservoir() {
        // Given - Full reservoir (285.0U after 15U prime)
        val hexData = "23C1AB6418DC05546F55"

        // When
        val log = LogChangeTubeSuccess.parse(hexData)

        // Then
        assertThat(log.remainAmount.toUShort().toInt()).isEqualTo(28500) // 285.0U remaining
    }

    @Test
    fun parseLogDataWithLowBattery() {
        // Given - Low battery level
        val hexData = "23C1AB6418DC05BC341E"

        // When
        val log = LogChangeTubeSuccess.parse(hexData)

        // Then
        assertThat(log.batteryRemain.toInt()).isEqualTo(30) // 30% battery
    }

    @Test
    fun toStringContainsAllFields() {
        // Given
        val hexData = "23C1AB6418DC05BC3455"
        val log = LogChangeTubeSuccess.parse(hexData)

        // When
        val result = log.toString()

        // Then
        assertThat(result).contains("LOG_CHANGE_TUBE_SUCCESS")
        assertThat(result).contains("LOG_KIND=${LogChangeTubeSuccess.LOG_KIND.toInt()}")
        assertThat(result).contains("primeAmount=")
        assertThat(result).contains("remainAmount=")
        assertThat(result).contains("batteryRemain=")
    }

    @Test
    fun logKindConstantIsCorrect() {
        // Then
        assertThat(LogChangeTubeSuccess.LOG_KIND).isEqualTo(0x18.toByte())
    }
}
