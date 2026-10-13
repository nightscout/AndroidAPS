package app.aaps.pump.diaconn.pumplog

import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class LogInjectNormalFailTest : TestBase() {

    @Test
    fun parseValidLogDataWithBlockageReason() {
        // Given - Normal bolus failed due to blockage (reason=1)
        // Format: timestamp(4) + typeAndKind(1) + setAmount(2) + injectAmount(2) + injectTime(1) + reason(1) + batteryRemain(1)
        // setAmount: 5000 = 50.0U, injectAmount: 2500 = 25.0U actually injected
        val hexData = "23C1AB640B8813C4097C0155"

        // When
        val log = LogInjectNormalFail.parse(hexData)

        // Then
        assertThat(log).isNotNull()
        assertThat(log.data).isEqualTo(hexData)
        assertThat(log.kind).isEqualTo(LogInjectNormalFail.LOG_KIND)
        assertThat(log.injectAmount.toInt()).isEqualTo(2500) // 25.0U actually injected
        assertThat(log.reason.toInt()).isEqualTo(1) // Blockage
        assertThat(log.batteryRemain.toInt()).isEqualTo(85) // 85% battery
    }

    @Test
    fun parseLogDataWithLowBatteryReason() {
        // Given - Failed due to low battery (reason=2)
        val hexData = "23C1AB640B8813C4097C0255"

        // When
        val log = LogInjectNormalFail.parse(hexData)

        // Then
        assertThat(log.reason.toInt()).isEqualTo(2) // Low battery
    }

    @Test
    fun parseLogDataWithLowInsulinReason() {
        // Given - Failed due to low insulin (reason=3)
        val hexData = "23C1AB640B8813C4097C0355"

        // When
        val log = LogInjectNormalFail.parse(hexData)

        // Then
        assertThat(log.reason.toInt()).isEqualTo(3) // Low insulin
    }

    @Test
    fun parseLogDataWithUserStopReason() {
        // Given - Failed due to user stop (reason=4)
        val hexData = "23C1AB640B8813C4097C0455"

        // When
        val log = LogInjectNormalFail.parse(hexData)

        // Then
        assertThat(log.reason.toInt()).isEqualTo(4) // User stop
    }

    @Test
    fun parseLogDataWithSystemResetReason() {
        // Given - Failed due to system reset (reason=5)
        val hexData = "23C1AB640B8813C4097C0555"

        // When
        val log = LogInjectNormalFail.parse(hexData)

        // Then
        assertThat(log.reason.toInt()).isEqualTo(5) // System reset
    }

    @Test
    fun parseLogDataWithOtherReason() {
        // Given - Failed due to other reason (reason=6)
        val hexData = "23C1AB640B8813C4097C0655"

        // When
        val log = LogInjectNormalFail.parse(hexData)

        // Then
        assertThat(log.reason.toInt()).isEqualTo(6) // Other
    }

    @Test
    fun parseLogDataWithEmergencyStopReason() {
        // Given - Failed due to emergency stop (reason=7)
        val hexData = "23C1AB640B8813C4097C0755"

        // When
        val log = LogInjectNormalFail.parse(hexData)

        // Then
        assertThat(log.reason.toInt()).isEqualTo(7) // Emergency stop
    }

    @Test
    fun getInjectTimeShouldReturnUnsignedValue() {
        // Given
        val hexData = "23C1AB640B8813C4097C0155"
        val log = LogInjectNormalFail.parse(hexData)

        // When
        val injectTime = log.getInjectTime()

        // Then
        assertThat(injectTime).isEqualTo(124) // 124 minutes = 2 hours 4 minutes
    }

    @Test
    fun toStringContainsAllFields() {
        // Given
        val hexData = "23C1AB640B8813C4097C0155"
        val log = LogInjectNormalFail.parse(hexData)

        // When
        val result = log.toString()

        // Then
        assertThat(result).contains("LOG_INJECT_NORMAL_FAIL")
        assertThat(result).contains("LOG_KIND=${LogInjectNormalFail.LOG_KIND.toInt()}")
        assertThat(result).contains("setAmount=")
        assertThat(result).contains("injectAmount=")
        assertThat(result).contains("injectTime=")
        assertThat(result).contains("reason=")
        assertThat(result).contains("batteryRemain=")
    }

    @Test
    fun logKindConstantIsCorrect() {
        // Then
        assertThat(LogInjectNormalFail.LOG_KIND).isEqualTo(0x0B.toByte())
    }
}
