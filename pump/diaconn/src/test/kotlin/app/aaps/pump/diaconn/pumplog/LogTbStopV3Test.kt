package app.aaps.pump.diaconn.pumplog

import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class LogTbStopV3Test : TestBase() {

    @Test
    fun parseValidLogDataWithComplete() {
        // Given - TB stop completed normally (reason=0)
        // Format: timestamp(4) + typeAndKind(1) + tbInjectRateRatio(2) + reason(1) + tbDttm(4)
        // tbInjectRateRatio: 50120 = 50000 + 120 (120%)
        val hexData = "23C1AB641378C300AABBCCDD"

        // When
        val log = LogTbStopV3.parse(hexData)

        // Then
        assertThat(log).isNotNull()
        assertThat(log.data).isEqualTo(hexData)
        assertThat(log.kind).isEqualTo(LogTbStopV3.LOG_KIND)
        assertThat(log.getTbInjectRateRatio()).isEqualTo(50040) // TB rate/ratio
        assertThat(log.reason.toInt()).isEqualTo(0) // Completed
    }

    @Test
    fun parseLogDataWithUserStop() {
        // Given - TB stopped by user (reason=4)
        val hexData = "23C1AB641278C304AABBCCDD"

        // When
        val log = LogTbStopV3.parse(hexData)

        // Then
        assertThat(log.reason.toInt()).isEqualTo(4) // User stop
    }

    @Test
    fun parseLogDataWithOtherReason() {
        // Given - TB stopped for other reason (reason=6)
        val hexData = "23C1AB641278C306AABBCCDD"

        // When
        val log = LogTbStopV3.parse(hexData)

        // Then
        assertThat(log.reason.toInt()).isEqualTo(6) // Other
    }

    @Test
    fun parseLogDataWithEmergencyStop() {
        // Given - TB stopped by emergency (reason=7)
        val hexData = "23C1AB641278C307AABBCCDD"

        // When
        val log = LogTbStopV3.parse(hexData)

        // Then
        assertThat(log.reason.toInt()).isEqualTo(7) // Emergency stop
    }

    @Test
    fun parseLogDataWithPercentageRate() {
        // Given - TB with percentage rate (150% = 50150)
        val hexData = "23C1AB641296C300AABBCCDD"

        // When
        val log = LogTbStopV3.parse(hexData)

        // Then
        assertThat(log.getTbInjectRateRatio()).isGreaterThan(50000) // Percentage rate
    }

    @Test
    fun parseLogDataWithAbsoluteRate() {
        // Given - TB with absolute rate (2.5 U/h = 1250)
        val hexData = "23C1AB6412E20400AABBCCDD"

        // When
        val log = LogTbStopV3.parse(hexData)

        // Then
        assertThat(log.getTbInjectRateRatio()).isLessThan(50000) // Absolute rate
    }

    @Test
    fun getTbInjectRateRatioShouldReturnUnsignedValue() {
        // Given
        val hexData = "23C1AB641378C300AABBCCDD"
        val log = LogTbStopV3.parse(hexData)

        // When
        val ratio = log.getTbInjectRateRatio()

        // Then
        assertThat(ratio).isGreaterThan(0)
    }

    @Test
    fun toStringContainsAllFields() {
        // Given
        val hexData = "23C1AB641378C300AABBCCDD"
        val log = LogTbStopV3.parse(hexData)

        // When
        val result = log.toString()

        // Then
        assertThat(result).contains("LOG_TB_STOP_V3")
        assertThat(result).contains("LOG_KIND=${LogTbStopV3.LOG_KIND.toInt()}")
        assertThat(result).contains("tbInjectRateRatio=")
        assertThat(result).contains("reason=")
    }

    @Test
    fun logKindConstantIsCorrect() {
        // Then
        assertThat(LogTbStopV3.LOG_KIND).isEqualTo(0x13.toByte())
    }
}
