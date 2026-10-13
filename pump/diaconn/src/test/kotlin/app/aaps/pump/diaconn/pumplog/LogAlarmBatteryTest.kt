package app.aaps.pump.diaconn.pumplog

import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class LogAlarmBatteryTest : TestBase() {

    @Test
    fun parseValidLogData() {
        // Given - battery alarm log
        // Format: timestamp(4) + typeAndKind(1) + alarmLevel(1) + ack(1) + batteryRemain(1)
        // alarmLevel: 1=INFO, 2=WARNING, 3=MAJOR, 4=CRITICAL
        // ack: 1=OCCUR, 2=STOP
        val hexData = "23C1AB642802011E"

        // When
        val log = LogAlarmBattery.parse(hexData)

        // Then
        assertThat(log).isNotNull()
        assertThat(log.data).isEqualTo(hexData)
        assertThat(log.kind).isEqualTo(LogAlarmBattery.LOG_KIND)
        assertThat(log.batteryRemain.toInt()).isEqualTo(30) // 30%
    }

    @Test
    fun parseWarningLevel() {
        // Given - warning level battery alarm
        val hexData = "23C1AB6428020120"

        // When
        val log = LogAlarmBattery.parse(hexData)

        // Then
        assertThat(log.batteryRemain.toInt()).isAtLeast(0)
        assertThat(log.batteryRemain.toInt()).isAtMost(100)
    }

    @Test
    fun parseCriticalLevel() {
        // Given - critical level battery alarm (5% remaining)
        val hexData = "23C1AB6428040105"

        // When
        val log = LogAlarmBattery.parse(hexData)

        // Then
        assertThat(log.batteryRemain.toInt()).isEqualTo(5)
    }

    @Test
    fun toStringContainsAllFields() {
        // Given
        val hexData = "23C1AB642802011E"
        val log = LogAlarmBattery.parse(hexData)

        // When
        val result = log.toString()

        // Then
        assertThat(result).contains("LOG_ALARM_BATTERY")
        assertThat(result).contains("LOG_KIND=${LogAlarmBattery.LOG_KIND.toInt()}")
        assertThat(result).contains("batteryRemain=30")
    }

    @Test
    fun logKindConstantIsCorrect() {
        // Then
        assertThat(LogAlarmBattery.LOG_KIND).isEqualTo(0x28.toByte())
    }
}
