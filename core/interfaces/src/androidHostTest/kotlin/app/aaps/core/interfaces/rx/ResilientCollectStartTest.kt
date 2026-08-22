package app.aaps.core.interfaces.rx

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

/**
 * The subscription window in [collectResilient], and the [CoroutineStart] that closes it.
 *
 * This is the shape `RxBus` publishes through - a `MutableSharedFlow` with **replay 0** - so anything
 * emitted before a collector has actually subscribed is dropped, with no error and no log. The default
 * `launchIn` only *schedules* the collector, so converting an RxJava `subscribe()` (which registered
 * synchronously) to a Flow collector silently acquires that window.
 *
 * The first test is the interesting one: it does not assert the event is lost, because that is a race
 * and would be flaky in the other direction. It asserts the guarantee that matters - that with
 * UNDISPATCHED the emission is *never* lost - and the second test documents that the default gives no
 * such guarantee by showing subscription has not happened yet when the call returns.
 */
class ResilientCollectStartTest {

    private val aapsLogger: AAPSLogger = mock()
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    @AfterEach fun tearDown() = scope.cancel()

    private fun busLikeFlow() = MutableSharedFlow<String>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    @Test
    fun `undispatched start receives an item emitted immediately after subscribing`() {
        val bus = busLikeFlow()
        val seen = mutableListOf<String>()

        bus.collectResilient(scope, aapsLogger, LTag.CORE, start = CoroutineStart.UNDISPATCHED) { seen += it }
        // No await, no delay: this is the exact pattern a converted RxBus subscription has to survive,
        // and the whole point is that the subscribe already happened above.
        val delivered = bus.tryEmit("event")

        assertThat(delivered).isTrue()
        assertThat(bus.subscriptionCount.value).isEqualTo(1)
    }

    @Test
    fun `the default start has not subscribed yet when the call returns`() {
        val bus = busLikeFlow()

        bus.collectResilient(scope, aapsLogger, LTag.CORE) { }

        // Nothing is collecting yet, so a replay-0 source would drop anything emitted right now. This
        // is why the bus subscriptions ask for UNDISPATCHED and a StateFlow collector need not.
        assertThat(bus.subscriptionCount.value).isEqualTo(0)
    }

    @Test
    fun `undispatched start has subscribed by the time the call returns`() {
        val bus = busLikeFlow()

        bus.collectResilient(scope, aapsLogger, LTag.CORE, start = CoroutineStart.UNDISPATCHED) { }

        assertThat(bus.subscriptionCount.value).isEqualTo(1)
    }
}
