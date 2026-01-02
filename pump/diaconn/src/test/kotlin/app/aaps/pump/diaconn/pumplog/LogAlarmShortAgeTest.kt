package app.aaps.pump.diaconn.pumplog

import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class LogAlarmShortAgeTest : TestBase() {

    @Test
    fun parseValidLogDataWithInfoLevel() {
        // Given - Insulin shortage alarm with INFO level
        // Format: timestamp(4) + typeAndKind(1) + alarmLevel(1) + ack(1) + remain(1) + batteryRemain(1)
        // alarmLevel: 1=INFO, ack: 1=OCCUR, remain: 50U
        val hexData = "23C1AB642A0101321E"

        // When
        val log = LogAlarmShortAge.parse(hexData)

        // Then
        assertThat(log).isNotNull()
        assertThat(log.data).isEqualTo(hexData)
        assertThat(log.kind).isEqualTo(LogAlarmShortAge.LOG_KIND)
        assertThat(log.remain.toInt()).isEqualTo(50) // 50U insulin remaining
        assertThat(log.batteryRemain.toInt()).isEqualTo(30) // 30% battery
    }

    @Test
    fun parseLogDataWithWarningLevel() {
        // Given - WARNING level alarm (alarmLevel=2)
        val hexData = "23C1AB642A0201321E"

        // When
        val log = LogAlarmShortAge.parse(hexData)

        // Then
        assertThat(log).isNotNull()
    }

    @Test
    fun parseLogDataWithMajorLevel() {
        // Given - MAJOR level alarm (alarmLevel=3)
        val hexData = "23C1AB642A0301321E"

        // When
        val log = LogAlarmShortAge.parse(hexData)

        // Then
        assertThat(log).isNotNull()
    }

    @Test
    fun parseLogDataWithCriticalLevel() {
        // Given - CRITICAL level alarm (alarmLevel=4)
        val hexData = "23C1AB642A0401321E"

        // When
        val log = LogAlarmShortAge.parse(hexData)

        // Then
        assertThat(log).isNotNull()
    }

    @Test
    fun parseLogDataWithLowRemaining() {
        // Given - Very low insulin remaining (10U)
        val hexData = "23C1AB642A01010A1E"

        // When
        val log = LogAlarmShortAge.parse(hexData)

        // Then
        assertThat(log.remain.toInt()).isEqualTo(10) // 10U remaining
    }

    @Test
    fun parseLogDataWithHighRemaining() {
        // Given - High insulin remaining (100U)
        val hexData = "23C1AB642A0101641E"

        // When
        val log = LogAlarmShortAge.parse(hexData)

        // Then
        assertThat(log.remain.toInt()).isEqualTo(100) // 100U remaining
    }

    @Test
    fun parseLogDataWithStopAck() {
        // Given - Alarm stopped (ack=2)
        val hexData = "23C1AB642A0102321E"

        // When
        val log = LogAlarmShortAge.parse(hexData)

        // Then
        assertThat(log).isNotNull()
    }

    @Test
    fun toStringContainsAllFields() {
        // Given
        val hexData = "23C1AB642A0101321E"
        val log = LogAlarmShortAge.parse(hexData)

        // When
        val result = log.toString()

        // Then
        assertThat(result).contains("LOG_ALARM_SHORTAGE")
        assertThat(result).contains("LOG_KIND=${LogAlarmShortAge.LOG_KIND.toInt()}")
        assertThat(result).contains("alarmLevel=")
        assertThat(result).contains("ack=")
        assertThat(result).contains("remain=")
        assertThat(result).contains("batteryRemain=")
    }

    @Test
    fun logKindConstantIsCorrect() {
        // Then
        assertThat(LogAlarmShortAge.LOG_KIND).isEqualTo(0x2A.toByte())
    }
}
