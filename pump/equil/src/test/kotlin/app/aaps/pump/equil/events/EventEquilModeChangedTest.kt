package app.aaps.pump.equil.events

import app.aaps.shared.tests.TestBase
import org.junit.jupiter.api.Test

class EventEquilModeChangedTest : TestBase() {

    @Test
    fun `event should be instantiable`() {
        val event = EventEquilModeChanged()
        assert(event is EventEquilModeChanged)
    }

    @Test
    fun `event should extend Event base class`() {
        val event = EventEquilModeChanged()
        assert(event is app.aaps.core.interfaces.rx.events.Event)
    }

    @Test
    fun `multiple instances should be creatable`() {
        val event1 = EventEquilModeChanged()
        val event2 = EventEquilModeChanged()
        val event3 = EventEquilModeChanged()

        assert(event1 is EventEquilModeChanged)
        assert(event2 is EventEquilModeChanged)
        assert(event3 is EventEquilModeChanged)
    }

    @Test
    fun `event should have no-arg constructor`() {
        // If this compiles and runs, the no-arg constructor exists
        val event = EventEquilModeChanged()
        assert(event is EventEquilModeChanged)
    }
}
