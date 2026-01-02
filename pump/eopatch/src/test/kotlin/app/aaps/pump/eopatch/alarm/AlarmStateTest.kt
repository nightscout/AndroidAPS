package app.aaps.pump.eopatch.alarm

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class AlarmStateTest {

    @Test
    fun `should have exactly three states`() {
        assertThat(AlarmState.entries).hasSize(3)
    }

    @Test
    fun `should contain all expected states`() {
        assertThat(AlarmState.entries).containsExactly(
            AlarmState.REGISTER,
            AlarmState.FIRED,
            AlarmState.HANDLE
        )
    }

    @Test
    fun `REGISTER should be first state`() {
        assertThat(AlarmState.entries[0]).isEqualTo(AlarmState.REGISTER)
        assertThat(AlarmState.REGISTER.ordinal).isEqualTo(0)
    }

    @Test
    fun `FIRED should be second state`() {
        assertThat(AlarmState.entries[1]).isEqualTo(AlarmState.FIRED)
        assertThat(AlarmState.FIRED.ordinal).isEqualTo(1)
    }

    @Test
    fun `HANDLE should be third state`() {
        assertThat(AlarmState.entries[2]).isEqualTo(AlarmState.HANDLE)
        assertThat(AlarmState.HANDLE.ordinal).isEqualTo(2)
    }

    @Test
    fun `all states should be distinct`() {
        assertThat(AlarmState.REGISTER).isNotEqualTo(AlarmState.FIRED)
        assertThat(AlarmState.FIRED).isNotEqualTo(AlarmState.HANDLE)
        assertThat(AlarmState.REGISTER).isNotEqualTo(AlarmState.HANDLE)
    }

    @Test
    fun `should support valueOf`() {
        assertThat(AlarmState.valueOf("REGISTER")).isEqualTo(AlarmState.REGISTER)
        assertThat(AlarmState.valueOf("FIRED")).isEqualTo(AlarmState.FIRED)
        assertThat(AlarmState.valueOf("HANDLE")).isEqualTo(AlarmState.HANDLE)
    }

    @Test
    fun `states should follow logical lifecycle order`() {
        // REGISTER comes before FIRED
        assertThat(AlarmState.REGISTER.ordinal).isLessThan(AlarmState.FIRED.ordinal)

        // FIRED comes before HANDLE
        assertThat(AlarmState.FIRED.ordinal).isLessThan(AlarmState.HANDLE.ordinal)
    }

    @Test
    fun `ordinal values should be sequential`() {
        assertThat(AlarmState.REGISTER.ordinal).isEqualTo(0)
        assertThat(AlarmState.FIRED.ordinal).isEqualTo(1)
        assertThat(AlarmState.HANDLE.ordinal).isEqualTo(2)
    }
}
