package app.aaps.pump.eopatch.code

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class EventTypeTest {

    @Test
    fun `should have all expected event types`() {
        val eventTypes = EventType.entries

        assertThat(eventTypes).isNotEmpty()
        assertThat(eventTypes).contains(EventType.ACTIVATION_CLICKED)
        assertThat(eventTypes).contains(EventType.DEACTIVATION_CLICKED)
    }

    @Test
    fun `ACTIVATION_CLICKED should exist`() {
        assertThat(EventType.ACTIVATION_CLICKED).isNotNull()
    }

    @Test
    fun `DEACTIVATION_CLICKED should exist`() {
        assertThat(EventType.DEACTIVATION_CLICKED).isNotNull()
    }

    @Test
    fun `SUSPEND_CLICKED should exist`() {
        assertThat(EventType.SUSPEND_CLICKED).isNotNull()
    }

    @Test
    fun `RESUME_CLICKED should exist`() {
        assertThat(EventType.RESUME_CLICKED).isNotNull()
    }

    @Test
    fun `should support valueOf`() {
        assertThat(EventType.valueOf("ACTIVATION_CLICKED")).isEqualTo(EventType.ACTIVATION_CLICKED)
        assertThat(EventType.valueOf("DEACTIVATION_CLICKED")).isEqualTo(EventType.DEACTIVATION_CLICKED)
        assertThat(EventType.valueOf("SUSPEND_CLICKED")).isEqualTo(EventType.SUSPEND_CLICKED)
    }
}
