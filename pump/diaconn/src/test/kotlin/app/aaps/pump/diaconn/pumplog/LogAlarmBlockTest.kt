package app.aaps.pump.diaconn.pumplog

import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class LogAlarmBlockTest : TestBase() {

    @Test
    fun parseValidLogDataWithInfoLevel() {
        // Given - Injection blocked alarm with INFO level
        // Format: timestamp(4) + typeAndKind(1) + alarmLevel(1) + ack(1) + amount(2) + reason(1) + batteryRemain(1)
        // alarmLevel: 1=INFO, ack: 1=OCCUR, amount: 500 = 5.0U, reason: 1=BASE
        val hexData = "23C1AB64290101F401011E"

        // When
        val log = LogAlarmBlock.parse(hexData)

        // Then
        assertThat(log).isNotNull()
        assertThat(log.data).isEqualTo(hexData)
        assertThat(log.kind).isEqualTo(LogAlarmBlock.LOG_KIND)
        assertThat(log.amount.toInt()).isEqualTo(500) // 5.0U
        assertThat(log.reason.toInt()).isEqualTo(1) // BASE
        assertThat(log.batteryRemain.toInt()).isEqualTo(30) // 30% battery
    }

    @Test
    fun parseLogDataWithWarningLevel() {
        // Given - WARNING level alarm (alarmLevel=2)
        val hexData = "23C1AB64290201F401011E"

        // When
        val log = LogAlarmBlock.parse(hexData)

        // Then - alarmLevel is parsed and stored
        assertThat(log).isNotNull()
    }

    @Test
    fun parseLogDataWithMajorLevel() {
        // Given - MAJOR level alarm (alarmLevel=3)
        val hexData = "23C1AB64290301F401011E"

        // When
        val log = LogAlarmBlock.parse(hexData)

        // Then
        assertThat(log).isNotNull()
    }

    @Test
    fun parseLogDataWithCriticalLevel() {
        // Given - CRITICAL level alarm (alarmLevel=4)
        val hexData = "23C1AB64290401F401011E"

        // When
        val log = LogAlarmBlock.parse(hexData)

        // Then
        assertThat(log).isNotNull()
    }

    @Test
    fun parseLogDataWithMealReason() {
        // Given - Blocked during meal bolus (reason=2)
        val hexData = "23C1AB64290101F401021E"

        // When
        val log = LogAlarmBlock.parse(hexData)

        // Then
        assertThat(log.reason.toInt()).isEqualTo(2) // Meal
    }

    @Test
    fun parseLogDataWithSnackReason() {
        // Given - Blocked during snack bolus (reason=3)
        val hexData = "23C1AB64290101F401031E"

        // When
        val log = LogAlarmBlock.parse(hexData)

        // Then
        assertThat(log.reason.toInt()).isEqualTo(3) // Snack
    }

    @Test
    fun parseLogDataWithSquareReason() {
        // Given - Blocked during square bolus (reason=4)
        val hexData = "23C1AB64290101F401041E"

        // When
        val log = LogAlarmBlock.parse(hexData)

        // Then
        assertThat(log.reason.toInt()).isEqualTo(4) // Square
    }

    @Test
    fun parseLogDataWithDualReason() {
        // Given - Blocked during dual bolus (reason=5)
        val hexData = "23C1AB64290101F401051E"

        // When
        val log = LogAlarmBlock.parse(hexData)

        // Then
        assertThat(log.reason.toInt()).isEqualTo(5) // Dual
    }

    @Test
    fun parseLogDataWithTubeChangeReason() {
        // Given - Blocked during tube change (reason=6)
        val hexData = "23C1AB64290101F401061E"

        // When
        val log = LogAlarmBlock.parse(hexData)

        // Then
        assertThat(log.reason.toInt()).isEqualTo(6) // Tube change
    }

    @Test
    fun parseLogDataWithInsulinChangeReason() {
        // Given - Blocked during insulin change (reason=8)
        val hexData = "23C1AB64290101F401081E"

        // When
        val log = LogAlarmBlock.parse(hexData)

        // Then
        assertThat(log.reason.toInt()).isEqualTo(8) // Insulin change
    }

    @Test
    fun toStringContainsAllFields() {
        // Given
        val hexData = "23C1AB64290101F401011E"
        val log = LogAlarmBlock.parse(hexData)

        // When
        val result = log.toString()

        // Then
        assertThat(result).contains("LOG_ALARM_BLOCK")
        assertThat(result).contains("LOG_KIND=${LogAlarmBlock.LOG_KIND.toInt()}")
        assertThat(result).contains("alarmLevel=")
        assertThat(result).contains("ack=")
        assertThat(result).contains("amount=")
        assertThat(result).contains("reason=")
        assertThat(result).contains("batteryRemain=")
    }

    @Test
    fun logKindConstantIsCorrect() {
        // Then
        assertThat(LogAlarmBlock.LOG_KIND).isEqualTo(0x29.toByte())
    }
}
