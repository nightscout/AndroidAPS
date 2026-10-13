package app.aaps.pump.diaconn.pumplog

import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class LogInjectSquareFailTest : TestBase() {

    @Test
    fun parseValidLogDataWithBlockageReason() {
        // Given - Square bolus failed due to blockage (reason=1)
        // Format: timestamp(4) + typeAndKind(1) + injectAmount(2) + injectTime(1) + reason(1) + batteryRemain(1)
        // injectAmount: 1000 = 10.0U, injectTime: 120 minutes
        val hexData = "23C1AB640EE803780155"

        // When
        val log = LogInjectSquareFail.parse(hexData)

        // Then
        assertThat(log).isNotNull()
        assertThat(log.data).isEqualTo(hexData)
        assertThat(log.kind).isEqualTo(LogInjectSquareFail.LOG_KIND)
        assertThat(log.injectAmount.toInt()).isEqualTo(1000) // 10.0U
        assertThat(log.reason.toInt()).isEqualTo(1) // Blockage
        assertThat(log.batteryRemain.toInt()).isEqualTo(85) // 85% battery
    }

    @Test
    fun parseLogDataWithLowBatteryReason() {
        // Given - Failed due to low battery (reason=2)
        val hexData = "23C1AB640EE803780255"

        // When
        val log = LogInjectSquareFail.parse(hexData)

        // Then
        assertThat(log.reason.toInt()).isEqualTo(2) // Low battery
    }

    @Test
    fun parseLogDataWithLowInsulinReason() {
        // Given - Failed due to low insulin (reason=3)
        val hexData = "23C1AB640EE803780355"

        // When
        val log = LogInjectSquareFail.parse(hexData)

        // Then
        assertThat(log.reason.toInt()).isEqualTo(3) // Low insulin
    }

    @Test
    fun parseLogDataWithUserStopReason() {
        // Given - Failed due to user stop (reason=4)
        val hexData = "23C1AB640EE803780455"

        // When
        val log = LogInjectSquareFail.parse(hexData)

        // Then
        assertThat(log.reason.toInt()).isEqualTo(4) // User stop
    }

    @Test
    fun parseLogDataWithSystemResetReason() {
        // Given - Failed due to system reset (reason=5)
        val hexData = "23C1AB640EE803780555"

        // When
        val log = LogInjectSquareFail.parse(hexData)

        // Then
        assertThat(log.reason.toInt()).isEqualTo(5) // System reset
    }

    @Test
    fun parseLogDataWithOtherReason() {
        // Given - Failed due to other reason (reason=6)
        val hexData = "23C1AB640EE803780655"

        // When
        val log = LogInjectSquareFail.parse(hexData)

        // Then
        assertThat(log.reason.toInt()).isEqualTo(6) // Other
    }

    @Test
    fun parseLogDataWithEmergencyStopReason() {
        // Given - Failed due to emergency stop (reason=7)
        val hexData = "23C1AB640EE803780755"

        // When
        val log = LogInjectSquareFail.parse(hexData)

        // Then
        assertThat(log.reason.toInt()).isEqualTo(7) // Emergency stop
    }

    @Test
    fun getInjectTimeShouldReturnUnsignedValue() {
        // Given
        val hexData = "23C1AB640EE803780155"
        val log = LogInjectSquareFail.parse(hexData)

        // When
        val injectTime = log.getInjectTime()

        // Then
        assertThat(injectTime).isEqualTo(120) // 120 minutes
    }

    @Test
    fun toStringContainsAllFields() {
        // Given
        val hexData = "23C1AB640EE803780155"
        val log = LogInjectSquareFail.parse(hexData)

        // When
        val result = log.toString()

        // Then
        assertThat(result).contains("LOG_INJECT_SQUARE_FAIL")
        assertThat(result).contains("LOG_KIND=${LogInjectSquareFail.LOG_KIND.toInt()}")
        assertThat(result).contains("injectAmount=")
        assertThat(result).contains("injectTime=")
        assertThat(result).contains("reason=")
        assertThat(result).contains("batteryRemain=")
    }

    @Test
    fun logKindConstantIsCorrect() {
        // Then
        assertThat(LogInjectSquareFail.LOG_KIND).isEqualTo(0x0E.toByte())
    }
}
