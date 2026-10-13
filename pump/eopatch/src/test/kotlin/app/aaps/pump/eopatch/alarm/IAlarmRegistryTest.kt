package app.aaps.pump.eopatch.alarm

import app.aaps.pump.eopatch.core.code.PatchAeCode
import com.google.common.truth.Truth.assertThat
import io.reactivex.rxjava3.core.Maybe
import org.junit.jupiter.api.Test
import org.mockito.Mockito.anyBoolean
import org.mockito.Mockito.anyLong
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

class IAlarmRegistryTest {

    @Test
    fun `interface should define add method with AlarmCode`() {
        val registry = mock(IAlarmRegistry::class.java)
        val alarmCode = AlarmCode.A002
        val triggerAfter = 1000L

        `when`(registry.add(alarmCode, triggerAfter, false))
            .thenReturn(Maybe.just(alarmCode))

        val result = registry.add(alarmCode, triggerAfter, false)

        verify(registry).add(alarmCode, triggerAfter, false)
        assertThat(result.blockingGet()).isEqualTo(alarmCode)
    }

    @Test
    fun `interface should define add method with PatchAeCode set`() {
        val registry = mock(IAlarmRegistry::class.java)
        val aeCodes = setOf(mock(PatchAeCode::class.java))

        registry.add(aeCodes)

        verify(registry).add(aeCodes)
    }

    @Test
    fun `interface should define remove method`() {
        val registry = mock(IAlarmRegistry::class.java)
        val alarmCode = AlarmCode.B003

        `when`(registry.remove(alarmCode)).thenReturn(Maybe.just(alarmCode))

        val result = registry.remove(alarmCode)

        verify(registry).remove(alarmCode)
        assertThat(result.blockingGet()).isEqualTo(alarmCode)
    }

    @Test
    fun `add method should have default parameter for isFirst`() {
        val registry = mock(IAlarmRegistry::class.java)
        val alarmCode = AlarmCode.A003

        // The interface defines isFirst with a default value of false
        // This test verifies the interface signature is correct
        `when`(registry.add(alarmCode, 0L)).thenReturn(Maybe.just(alarmCode))

        registry.add(alarmCode, 0L)

        verify(registry).add(alarmCode, 0L)
    }
}
