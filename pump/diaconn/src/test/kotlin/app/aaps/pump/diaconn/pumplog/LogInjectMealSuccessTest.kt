package app.aaps.pump.diaconn.pumplog

import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class LogInjectMealSuccessTest : TestBase() {

    @Test
    fun parseValidLogData() {
        // Given - meal bolus success log
        // Format: timestamp(4) + typeAndKind(1) + setAmount(2) + injectAmount(2) + injectTime(1) + time(1=breakfast) + batteryRemain(1)
        val hexData = "23C1AB64088E128E127C0155"

        // When
        val log = LogInjectMealSuccess.parse(hexData)

        // Then
        assertThat(log).isNotNull()
        assertThat(log.data).isEqualTo(hexData)
        assertThat(log.kind).isEqualTo(LogInjectMealSuccess.LOG_KIND)
        assertThat(log.injectAmount.toInt()).isEqualTo(4750) // 47.5U
        assertThat(log.getInjectTime()).isEqualTo(124) // 124 minutes
        assertThat(log.time.toInt()).isEqualTo(1) // Breakfast
        assertThat(log.batteryRemain.toInt()).isEqualTo(85)
    }

    @Test
    fun parseLogWithLunchTime() {
        // Given - lunch time (time=2)
        val hexData = "23C1AB6408E803B6033C0232"

        // When
        val log = LogInjectMealSuccess.parse(hexData)

        // Then
        assertThat(log.time.toInt()).isEqualTo(2) // Lunch
    }

    @Test
    fun parseLogWithDinnerTime() {
        // Given - dinner time (time=3)
        val hexData = "23C1AB6408E803B6033C0332"

        // When
        val log = LogInjectMealSuccess.parse(hexData)

        // Then
        assertThat(log.time.toInt()).isEqualTo(3) // Dinner
    }

    @Test
    fun toStringContainsAllFields() {
        // Given
        val hexData = "23C1AB64088E128E127C0155"
        val log = LogInjectMealSuccess.parse(hexData)

        // When
        val result = log.toString()

        // Then
        assertThat(result).contains("LOG_INJECT_MEAL_SUCCESS")
        assertThat(result).contains("LOG_KIND=${LogInjectMealSuccess.LOG_KIND.toInt()}")
        assertThat(result).contains("injectAmount=4750")
        assertThat(result).contains("injectTime=124")
        assertThat(result).contains("time=1")
        assertThat(result).contains("batteryRemain=85")
    }

    @Test
    fun logKindConstantIsCorrect() {
        // Then
        assertThat(LogInjectMealSuccess.LOG_KIND).isEqualTo(0x08.toByte())
    }
}
