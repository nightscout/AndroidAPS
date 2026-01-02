package app.aaps.pump.eopatch.code

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class AlarmCategoryTest {

    @Test
    fun `should have exactly three categories`() {
        assertThat(AlarmCategory.entries).hasSize(3)
    }

    @Test
    fun `should contain all expected categories`() {
        assertThat(AlarmCategory.entries).containsExactly(
            AlarmCategory.NONE,
            AlarmCategory.ALARM,
            AlarmCategory.ALERT
        )
    }

    @Test
    fun `NONE should be first category`() {
        assertThat(AlarmCategory.entries[0]).isEqualTo(AlarmCategory.NONE)
    }

    @Test
    fun `ALARM should be second category`() {
        assertThat(AlarmCategory.entries[1]).isEqualTo(AlarmCategory.ALARM)
    }

    @Test
    fun `ALERT should be third category`() {
        assertThat(AlarmCategory.entries[2]).isEqualTo(AlarmCategory.ALERT)
    }

    @Test
    fun `all categories should be distinct`() {
        assertThat(AlarmCategory.NONE).isNotEqualTo(AlarmCategory.ALARM)
        assertThat(AlarmCategory.ALARM).isNotEqualTo(AlarmCategory.ALERT)
        assertThat(AlarmCategory.NONE).isNotEqualTo(AlarmCategory.ALERT)
    }

    @Test
    fun `should support valueOf`() {
        assertThat(AlarmCategory.valueOf("NONE")).isEqualTo(AlarmCategory.NONE)
        assertThat(AlarmCategory.valueOf("ALARM")).isEqualTo(AlarmCategory.ALARM)
        assertThat(AlarmCategory.valueOf("ALERT")).isEqualTo(AlarmCategory.ALERT)
    }

    @Test
    fun `ordinal values should be sequential`() {
        assertThat(AlarmCategory.NONE.ordinal).isEqualTo(0)
        assertThat(AlarmCategory.ALARM.ordinal).isEqualTo(1)
        assertThat(AlarmCategory.ALERT.ordinal).isEqualTo(2)
    }
}
