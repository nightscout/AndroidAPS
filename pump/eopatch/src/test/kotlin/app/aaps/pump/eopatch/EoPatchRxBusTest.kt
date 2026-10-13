package app.aaps.pump.eopatch

import com.google.common.truth.Truth.assertThat
import io.reactivex.rxjava3.observers.TestObserver
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class EoPatchRxBusTest {

    data class TestEvent1(val message: String)
    data class TestEvent2(val value: Int)
    data class TestEvent3(val flag: Boolean)

    @BeforeEach
    fun setUp() {
        // Note: EoPatchRxBus is a singleton with a PublishSubject that persists across tests
        // In a production environment, we might want to add a reset() method for testing
    }

    @Test
    fun `should publish and receive events`() {
        val testObserver = TestObserver<TestEvent1>()
        EoPatchRxBus.listen(TestEvent1::class.java).subscribe(testObserver)

        val event = TestEvent1("test message")
        EoPatchRxBus.publish(event)

        testObserver.assertValue(event)
        testObserver.assertNotComplete()
    }

    @Test
    fun `should filter events by type`() {
        val observer1 = TestObserver<TestEvent1>()
        val observer2 = TestObserver<TestEvent2>()

        EoPatchRxBus.listen(TestEvent1::class.java).subscribe(observer1)
        EoPatchRxBus.listen(TestEvent2::class.java).subscribe(observer2)

        val event1 = TestEvent1("test")
        val event2 = TestEvent2(42)

        EoPatchRxBus.publish(event1)
        EoPatchRxBus.publish(event2)

        observer1.assertValue(event1)
        observer1.assertValueCount(1)

        observer2.assertValue(event2)
        observer2.assertValueCount(1)
    }

    @Test
    fun `should support multiple subscribers for same event type`() {
        val observer1 = TestObserver<TestEvent1>()
        val observer2 = TestObserver<TestEvent1>()

        EoPatchRxBus.listen(TestEvent1::class.java).subscribe(observer1)
        EoPatchRxBus.listen(TestEvent1::class.java).subscribe(observer2)

        val event = TestEvent1("broadcast")
        EoPatchRxBus.publish(event)

        observer1.assertValue(event)
        observer2.assertValue(event)
    }

    @Test
    fun `should handle multiple events of same type`() {
        val testObserver = TestObserver<TestEvent1>()
        EoPatchRxBus.listen(TestEvent1::class.java).subscribe(testObserver)

        val event1 = TestEvent1("first")
        val event2 = TestEvent1("second")
        val event3 = TestEvent1("third")

        EoPatchRxBus.publish(event1)
        EoPatchRxBus.publish(event2)
        EoPatchRxBus.publish(event3)

        testObserver.assertValues(event1, event2, event3)
        testObserver.assertValueCount(3)
    }

    @Test
    fun `should not receive events after unsubscribe`() {
        val testObserver = TestObserver<TestEvent1>()
        EoPatchRxBus.listen(TestEvent1::class.java).subscribe(testObserver)

        val event1 = TestEvent1("before unsubscribe")
        EoPatchRxBus.publish(event1)

        testObserver.assertValue(event1)

        testObserver.dispose()

        val event2 = TestEvent1("after unsubscribe")
        EoPatchRxBus.publish(event2)

        // Should still only have one value
        testObserver.assertValueCount(1)
    }

    @Test
    fun `should only receive events published after subscription`() {
        val event1 = TestEvent1("before subscription")
        EoPatchRxBus.publish(event1)

        val testObserver = TestObserver<TestEvent1>()
        EoPatchRxBus.listen(TestEvent1::class.java).subscribe(testObserver)

        // Should not receive event1 since it was published before subscription
        testObserver.assertNoValues()

        val event2 = TestEvent1("after subscription")
        EoPatchRxBus.publish(event2)

        testObserver.assertValue(event2)
        testObserver.assertValueCount(1)
    }

    @Test
    fun `should handle different event types independently`() {
        val observer1 = TestObserver<TestEvent1>()
        val observer2 = TestObserver<TestEvent2>()
        val observer3 = TestObserver<TestEvent3>()

        EoPatchRxBus.listen(TestEvent1::class.java).subscribe(observer1)
        EoPatchRxBus.listen(TestEvent2::class.java).subscribe(observer2)
        EoPatchRxBus.listen(TestEvent3::class.java).subscribe(observer3)

        val event1 = TestEvent1("string event")
        val event2 = TestEvent2(100)
        val event3 = TestEvent3(true)

        EoPatchRxBus.publish(event1)
        EoPatchRxBus.publish(event2)
        EoPatchRxBus.publish(event3)

        observer1.assertValue(event1)
        observer1.assertValueCount(1)

        observer2.assertValue(event2)
        observer2.assertValueCount(1)

        observer3.assertValue(event3)
        observer3.assertValueCount(1)
    }

    @Test
    fun `should handle publishing Any type`() {
        val testObserver = TestObserver<String>()
        EoPatchRxBus.listen(String::class.java).subscribe(testObserver)

        val stringEvent = "plain string"
        EoPatchRxBus.publish(stringEvent)

        testObserver.assertValue(stringEvent)
    }

    @Test
    fun `listen should return Observable`() {
        val observable = EoPatchRxBus.listen(TestEvent1::class.java)

        assertThat(observable).isNotNull()
    }
}
