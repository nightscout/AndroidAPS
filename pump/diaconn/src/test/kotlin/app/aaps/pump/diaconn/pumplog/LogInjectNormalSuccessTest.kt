package app.aaps.pump.diaconn.pumplog

import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class LogInjectNormalSuccessTest : TestBase() {

    @Test
    fun parseValidLogData() {
        // Given - valid hex string representing a normal injection success log
        // Format: timestamp(4 bytes) + typeAndKind(1) + setAmount(2) + injectAmount(2) + injectTime(1) + batteryRemain(1)
        // timestamp: 0x64ABC123 (example)
        // typeAndKind: 0x0A (LOG_KIND)
        // setAmount: 4750 (47.5 units) = 0x128E in little endian: 8E 12
        // injectAmount: 4750 (47.5 units) = 0x128E in little endian: 8E 12
        // injectTime: 124 (124 minutes) = 0x7C
        // batteryRemain: 85 (85%) = 0x55
        val hexData = "23C1AB640A8E128E127C55"

        // When
        val log = LogInjectNormalSuccess.parse(hexData)

        // Then
        assertThat(log).isNotNull()
        assertThat(log.data).isEqualTo(hexData)
        assertThat(log.kind).isEqualTo(LogInjectNormalSuccess.LOG_KIND)
        assertThat(log.injectAmount.toInt()).isEqualTo(4750)
        assertThat(log.getInjectTime()).isEqualTo(124)
        assertThat(log.batteryRemain.toInt()).isEqualTo(85)
    }

    @Test
    fun parseLogWithDifferentValues() {
        // Given - different bolus values
        // setAmount: 1000 (10.0 units) = 0x03E8 in little endian: E8 03
        // injectAmount: 950 (9.5 units) = 0x03B6 in little endian: B6 03
        // injectTime: 60 (60 minutes) = 0x3C
        // batteryRemain: 50 (50%) = 0x32
        val hexData = "23C1AB640AE803B6033C32"

        // When
        val log = LogInjectNormalSuccess.parse(hexData)

        // Then
        assertThat(log.injectAmount.toInt()).isEqualTo(950)
        assertThat(log.getInjectTime()).isEqualTo(60)
        assertThat(log.batteryRemain.toInt()).isEqualTo(50)
    }

    @Test
    fun toStringContainsAllFields() {
        // Given
        val hexData = "23C1AB640A8E128E127C55"
        val log = LogInjectNormalSuccess.parse(hexData)

        // When
        val result = log.toString()

        // Then
        assertThat(result).contains("LOG_INJECT_NORMAL_SUCCESS")
        assertThat(result).contains("LOG_KIND=${LogInjectNormalSuccess.LOG_KIND.toInt()}")
        assertThat(result).contains("data='$hexData'")
        assertThat(result).contains("injectAmount=4750")
        assertThat(result).contains("injectTime=124")
        assertThat(result).contains("batteryRemain=85")
    }

    @Test
    fun logKindConstantIsCorrect() {
        // Then
        assertThat(LogInjectNormalSuccess.LOG_KIND).isEqualTo(0x0A.toByte())
    }

    @Test
    fun parseExtractsTypeAndKindCorrectly() {
        // Given - typeAndKind byte with type in upper 2 bits and kind in lower 6 bits
        // Example: type=1 (01), kind=10 (001010) -> 01001010 = 0x4A
        val hexDataWithType = "23C1AB644A8E128E127C55"

        // When
        val log = LogInjectNormalSuccess.parse(hexDataWithType)

        // Then
        assertThat(log.type.toInt()).isEqualTo(1) // Upper 2 bits
        assertThat(log.kind.toInt()).isEqualTo(10) // Lower 6 bits
    }

    @Test
    fun injectTimeHandlesUnsignedByte() {
        // Given - inject time > 127 (tests unsigned byte handling)
        // injectTime: 200 = 0xC8
        val hexData = "23C1AB640A8E128E12C855"

        // When
        val log = LogInjectNormalSuccess.parse(hexData)

        // Then
        assertThat(log.getInjectTime()).isEqualTo(200)
    }
}
