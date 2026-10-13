package app.aaps.pump.equil.events

import app.aaps.shared.tests.TestBase
import org.junit.jupiter.api.Test

class EventEquilDataChangedTest : TestBase() {

    @Test
    fun `event should be instantiable`() {
        val event = EventEquilDataChanged()
        assert(event is EventEquilDataChanged)
    }

    @Test
    fun `event should extend Event base class`() {
        val event = EventEquilDataChanged()
        assert(event is app.aaps.core.interfaces.rx.events.Event)
    }

    @Test
    fun `multiple instances should be creatable`() {
        val event1 = EventEquilDataChanged()
        val event2 = EventEquilDataChanged()
        val event3 = EventEquilDataChanged()

        assert(event1 is EventEquilDataChanged)
        assert(event2 is EventEquilDataChanged)
        assert(event3 is EventEquilDataChanged)
    }

    @Test
    fun `event should have no-arg constructor`() {
        // If this compiles and runs, the no-arg constructor exists
        val event = EventEquilDataChanged()
        assert(event is EventEquilDataChanged)
    }
}
