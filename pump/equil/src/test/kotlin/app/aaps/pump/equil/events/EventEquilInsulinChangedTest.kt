package app.aaps.pump.equil.events

import app.aaps.shared.tests.TestBase
import org.junit.jupiter.api.Test

class EventEquilInsulinChangedTest : TestBase() {

    @Test
    fun `event should be instantiable`() {
        val event = EventEquilInsulinChanged()
        assert(event is EventEquilInsulinChanged)
    }

    @Test
    fun `event should extend Event base class`() {
        val event = EventEquilInsulinChanged()
        assert(event is app.aaps.core.interfaces.rx.events.Event)
    }

    @Test
    fun `multiple instances should be creatable`() {
        val event1 = EventEquilInsulinChanged()
        val event2 = EventEquilInsulinChanged()
        val event3 = EventEquilInsulinChanged()

        assert(event1 is EventEquilInsulinChanged)
        assert(event2 is EventEquilInsulinChanged)
        assert(event3 is EventEquilInsulinChanged)
    }

    @Test
    fun `event should have no-arg constructor`() {
        // If this compiles and runs, the no-arg constructor exists
        val event = EventEquilInsulinChanged()
        assert(event is EventEquilInsulinChanged)
    }
}
