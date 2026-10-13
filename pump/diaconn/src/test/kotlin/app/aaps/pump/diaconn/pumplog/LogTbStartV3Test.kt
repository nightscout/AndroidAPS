package app.aaps.pump.diaconn.pumplog

import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class LogTbStartV3Test : TestBase() {

    @Test
    fun parseValidLogDataWithPercentage() {
        // Given - TB start at 120%
        // Format: timestamp(4) + typeAndKind(1) + tbTime(1) + tbInjectRateRatio(2) + tbDttm(4)
        // tbInjectRateRatio: 50120 = 50000 + 120 (120%)
        val hexData = "23C1AB64120678C3AABBCCDD"

        // When
        val log = LogTbStartV3.parse(hexData)

        // Then
        assertThat(log).isNotNull()
        assertThat(log.data).isEqualTo(hexData)
        assertThat(log.kind).isEqualTo(LogTbStartV3.LOG_KIND)
        assertThat(log.tbTime.toInt()).isEqualTo(6) // 6 * 15min = 90 minutes
        assertThat(log.getTbInjectRateRatio()).isEqualTo(50040) // Percentage rate
    }

    @Test
    fun parseLogDataWithAbsoluteRate() {
        // Given - TB start at absolute 2.5 U/h = 1250 (1000 + 250)
        val hexData = "23C1AB641208E204AABBCCDD"

        // When
        val log = LogTbStartV3.parse(hexData)

        // Then
        assertThat(log.tbTime.toInt()).isEqualTo(8) // 8 * 15min = 2 hours
        assertThat(log.getTbInjectRateRatio()).isLessThan(50000) // Absolute rate
    }

    @Test
    fun parseShortDurationTempBasal() {
        // Given - 30 minute TB (tbTime=2, 2*15=30)
        val hexData = "23C1AB64120278C3AABBCCDD"

        // When
        val log = LogTbStartV3.parse(hexData)

        // Then
        assertThat(log.tbTime.toInt()).isEqualTo(2) // 30 minutes
    }

    @Test
    fun parseLongDurationTempBasal() {
        // Given - 24 hour TB (tbTime=96, 96*15=1440 minutes)
        val hexData = "23C1AB64126078C3AABBCCDD"

        // When
        val log = LogTbStartV3.parse(hexData)

        // Then
        assertThat(log.tbTime.toInt()).isEqualTo(96) // 24 hours
    }

    @Test
    fun toStringContainsAllFields() {
        // Given
        val hexData = "23C1AB64120678C3AABBCCDD"
        val log = LogTbStartV3.parse(hexData)

        // When
        val result = log.toString()

        // Then
        assertThat(result).contains("LOG_TB_START_V3")
        assertThat(result).contains("LOG_KIND=${LogTbStartV3.LOG_KIND.toInt()}")
        assertThat(result).contains("tbTime=6")
        assertThat(result).contains("tbInjectRateRatio=")
    }

    @Test
    fun logKindConstantIsCorrect() {
        // Then
        assertThat(LogTbStartV3.LOG_KIND).isEqualTo(0x12.toByte())
    }
}
