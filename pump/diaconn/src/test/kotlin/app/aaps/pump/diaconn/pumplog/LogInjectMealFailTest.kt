package app.aaps.pump.diaconn.pumplog

import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class LogInjectMealFailTest : TestBase() {

    @Test
    fun parseValidLogDataWithBlockageReason() {
        // Given - Meal bolus failed at breakfast due to injection blockage
        // Format: timestamp(4) + typeAndKind(1) + setAmount(2) + injectAmount(2) + injectTime(1) + time(1) + reason(1)
        // setAmount: 4750 = 47.5U, injectAmount: 2000 = 20.0U, time: 1=breakfast, reason: 1=blockage
        val hexData = "23C1AB64098E12D0077C0101"

        // When
        val log = LogInjectMealFail.parse(hexData)

        // Then
        assertThat(log).isNotNull()
        assertThat(log.data).isEqualTo(hexData)
        assertThat(log.kind).isEqualTo(LogInjectMealFail.LOG_KIND)
        assertThat(log.injectAmount.toInt()).isEqualTo(2000) // 20.0U actually injected
        assertThat(log.time.toInt()).isEqualTo(1) // Breakfast
        assertThat(log.reason.toInt()).isEqualTo(1) // Blockage
    }

    @Test
    fun parseLogDataWithLowBatteryReason() {
        // Given - Failed due to low battery (reason=2)
        val hexData = "23C1AB64098E12D0077C0102"

        // When
        val log = LogInjectMealFail.parse(hexData)

        // Then
        assertThat(log.reason.toInt()).isEqualTo(2) // Low battery
    }

    @Test
    fun parseLogDataWithUserStopReason() {
        // Given - Failed due to user stop (reason=4)
        val hexData = "23C1AB64098E12D0077C0104"

        // When
        val log = LogInjectMealFail.parse(hexData)

        // Then
        assertThat(log.reason.toInt()).isEqualTo(4) // User stop
    }

    @Test
    fun parseLogDataWithLunchTime() {
        // Given - Failed at lunch (time=2)
        val hexData = "23C1AB64098E12D0077C0201"

        // When
        val log = LogInjectMealFail.parse(hexData)

        // Then
        assertThat(log.time.toInt()).isEqualTo(2) // Lunch
    }

    @Test
    fun parseLogDataWithDinnerTime() {
        // Given - Failed at dinner (time=3)
        val hexData = "23C1AB64098E12D0077C0301"

        // When
        val log = LogInjectMealFail.parse(hexData)

        // Then
        assertThat(log.time.toInt()).isEqualTo(3) // Dinner
    }

    @Test
    fun getInjectTimeShouldReturnUnsignedValue() {
        // Given
        val hexData = "23C1AB64098E12D0077C0101"
        val log = LogInjectMealFail.parse(hexData)

        // When
        val injectTime = log.getInjectTime()

        // Then
        assertThat(injectTime).isEqualTo(124) // 124 minutes = 2 hours 4 minutes
    }

    @Test
    fun toStringContainsAllFields() {
        // Given
        val hexData = "23C1AB64098E12D0077C0101"
        val log = LogInjectMealFail.parse(hexData)

        // When
        val result = log.toString()

        // Then
        assertThat(result).contains("LOG_INJECT_MEAL_FAIL")
        assertThat(result).contains("LOG_KIND=${LogInjectMealFail.LOG_KIND.toInt()}")
        assertThat(result).contains("setAmount=")
        assertThat(result).contains("injectAmount=")
        assertThat(result).contains("injectTime=")
        assertThat(result).contains("time=")
        assertThat(result).contains("reason=")
    }

    @Test
    fun logKindConstantIsCorrect() {
        // Then
        assertThat(LogInjectMealFail.LOG_KIND).isEqualTo(0x09.toByte())
    }
}
