package app.aaps.pump.equil.events

import app.aaps.shared.tests.TestBase
import org.junit.jupiter.api.Test

class EventEquilUnPairChangedTest : TestBase() {

    @Test
    fun `event should be instantiable`() {
        val event = EventEquilUnPairChanged()
        assert(event is EventEquilUnPairChanged)
    }

    @Test
    fun `event should extend Event base class`() {
        val event = EventEquilUnPairChanged()
        assert(event is app.aaps.core.interfaces.rx.events.Event)
    }

    @Test
    fun `multiple instances should be creatable`() {
        val event1 = EventEquilUnPairChanged()
        val event2 = EventEquilUnPairChanged()
        val event3 = EventEquilUnPairChanged()

        assert(event1 is EventEquilUnPairChanged)
        assert(event2 is EventEquilUnPairChanged)
        assert(event3 is EventEquilUnPairChanged)
    }

    @Test
    fun `event should have no-arg constructor`() {
        // If this compiles and runs, the no-arg constructor exists
        val event = EventEquilUnPairChanged()
        assert(event is EventEquilUnPairChanged)
    }
}
