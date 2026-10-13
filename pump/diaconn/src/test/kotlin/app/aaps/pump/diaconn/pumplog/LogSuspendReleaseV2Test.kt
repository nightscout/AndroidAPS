package app.aaps.pump.diaconn.pumplog

import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class LogSuspendReleaseV2Test : TestBase() {

    @Test
    fun parseValidLogDataWithBasePattern() {
        // Given - Suspend release with Base pattern (patternType=1)
        // Format: timestamp(4) + typeAndKind(1) + batteryRemain(1) + patternType(1)
        val hexData = "23C1AB64045501"

        // When
        val log = LogSuspendReleaseV2.parse(hexData)

        // Then
        assertThat(log).isNotNull()
        assertThat(log.data).isEqualTo(hexData)
        assertThat(log.kind).isEqualTo(LogSuspendReleaseV2.LOG_KIND)
        assertThat(log.batteryRemain.toInt()).isEqualTo(85) // 85% battery
        assertThat(log.getBasalPattern()).isEqualTo("Base")
    }

    @Test
    fun parseLogDataWithLife1Pattern() {
        // Given - Life1 pattern (patternType=2)
        val hexData = "23C1AB64045502"

        // When
        val log = LogSuspendReleaseV2.parse(hexData)

        // Then
        assertThat(log.getBasalPattern()).isEqualTo("Life1")
    }

    @Test
    fun parseLogDataWithLife2Pattern() {
        // Given - Life2 pattern (patternType=3)
        val hexData = "23C1AB64045503"

        // When
        val log = LogSuspendReleaseV2.parse(hexData)

        // Then
        assertThat(log.getBasalPattern()).isEqualTo("Life2")
    }

    @Test
    fun parseLogDataWithLife3Pattern() {
        // Given - Life3 pattern (patternType=4)
        val hexData = "23C1AB64045504"

        // When
        val log = LogSuspendReleaseV2.parse(hexData)

        // Then
        assertThat(log.getBasalPattern()).isEqualTo("Life3")
    }

    @Test
    fun parseLogDataWithDr1Pattern() {
        // Given - Dr1 pattern (patternType=5)
        val hexData = "23C1AB64045505"

        // When
        val log = LogSuspendReleaseV2.parse(hexData)

        // Then
        assertThat(log.getBasalPattern()).isEqualTo("Dr1")
    }

    @Test
    fun parseLogDataWithDr2Pattern() {
        // Given - Dr2 pattern (patternType=6)
        val hexData = "23C1AB64045506"

        // When
        val log = LogSuspendReleaseV2.parse(hexData)

        // Then
        assertThat(log.getBasalPattern()).isEqualTo("Dr2")
    }

    @Test
    fun parseLogDataWithUnknownPattern() {
        // Given - Unknown pattern (patternType=9)
        val hexData = "23C1AB64045509"

        // When
        val log = LogSuspendReleaseV2.parse(hexData)

        // Then
        assertThat(log.getBasalPattern()).isEqualTo("No Pattern")
    }

    @Test
    fun parseLogDataWithLowBattery() {
        // Given - Low battery
        val hexData = "23C1AB64041E01"

        // When
        val log = LogSuspendReleaseV2.parse(hexData)

        // Then
        assertThat(log.batteryRemain.toInt()).isEqualTo(30) // 30% battery
    }

    @Test
    fun toStringContainsAllFields() {
        // Given
        val hexData = "23C1AB64045501"
        val log = LogSuspendReleaseV2.parse(hexData)

        // When
        val result = log.toString()

        // Then
        assertThat(result).contains("LOG_SUSPEND_RELEASE_V2")
        assertThat(result).contains("LOG_KIND=${LogSuspendReleaseV2.LOG_KIND.toInt()}")
        assertThat(result).contains("batteryRemain=")
        assertThat(result).contains("patternType=")
    }

    @Test
    fun logKindConstantIsCorrect() {
        // Then
        assertThat(LogSuspendReleaseV2.LOG_KIND).isEqualTo(0x04.toByte())
    }
}
