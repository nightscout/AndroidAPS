package app.aaps.pump.diaconn.pumplog

import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class LogResetSysV3Test : TestBase() {

    @Test
    fun parseValidLogDataWithFactoryReset() {
        // Given - System reset after factory reset (reason=1)
        // Format: timestamp(4) + typeAndKind(1) + batteryRemain(1) + reason(1) + rcon1(2) + rcon2(2)
        val hexData = "23C1AB6401550112345678"

        // When
        val log = LogResetSysV3.parse(hexData)

        // Then
        assertThat(log).isNotNull()
        assertThat(log.data).isEqualTo(hexData)
        assertThat(log.kind).isEqualTo(LogResetSysV3.LOG_KIND)
        assertThat(log.batteryRemain.toInt()).isEqualTo(85) // 85% battery
        assertThat(log.reason.toInt()).isEqualTo(1) // Factory reset
    }

    @Test
    fun parseLogDataWithEmergencyStopRelease() {
        // Given - Reset after emergency stop release (reason=2)
        val hexData = "23C1AB6401550212345678"

        // When
        val log = LogResetSysV3.parse(hexData)

        // Then
        assertThat(log.reason.toInt()).isEqualTo(2) // Emergency stop release
    }

    @Test
    fun parseLogDataWithBatteryChange() {
        // Given - Reset after user battery change (reason=3)
        val hexData = "23C1AB6401550312345678"

        // When
        val log = LogResetSysV3.parse(hexData)

        // Then
        assertThat(log.reason.toInt()).isEqualTo(3) // Battery change
    }

    @Test
    fun parseLogDataWithCalibration() {
        // Given - Reset after calibration (reason=4)
        val hexData = "23C1AB6401550412345678"

        // When
        val log = LogResetSysV3.parse(hexData)

        // Then
        assertThat(log.reason.toInt()).isEqualTo(4) // Calibration
    }

    @Test
    fun parseLogDataWithUnexpectedReset() {
        // Given - Unexpected system reset (reason=9)
        val hexData = "23C1AB6401550912345678"

        // When
        val log = LogResetSysV3.parse(hexData)

        // Then
        assertThat(log.reason.toInt()).isEqualTo(9) // Unexpected reset
    }

    @Test
    fun parseLogDataWithLowBattery() {
        // Given - Low battery
        val hexData = "23C1AB64011E0112345678"

        // When
        val log = LogResetSysV3.parse(hexData)

        // Then
        assertThat(log.batteryRemain.toInt()).isEqualTo(30) // 30% battery
    }

    @Test
    fun parseLogDataWithDifferentRconValues() {
        // Given - Different RCON values
        val hexData = "23C1AB64015501AABBCCDD"

        // When
        val log = LogResetSysV3.parse(hexData)

        // Then
        assertThat(log).isNotNull()
    }

    @Test
    fun toStringContainsAllFields() {
        // Given
        val hexData = "23C1AB6401550112345678"
        val log = LogResetSysV3.parse(hexData)

        // When
        val result = log.toString()

        // Then
        assertThat(result).contains("LOG_RESET_SYS_V3")
        assertThat(result).contains("LOG_KIND=${LogResetSysV3.LOG_KIND.toInt()}")
        assertThat(result).contains("batteryRemain=")
        assertThat(result).contains("reason=")
        assertThat(result).contains("rcon1=")
        assertThat(result).contains("rcon2=")
    }

    @Test
    fun logKindConstantIsCorrect() {
        // Then
        assertThat(LogResetSysV3.LOG_KIND).isEqualTo(0x01.toByte())
    }
}
