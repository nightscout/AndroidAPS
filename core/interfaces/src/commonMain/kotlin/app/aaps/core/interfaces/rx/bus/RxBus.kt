package app.aaps.core.interfaces.rx.bus

import app.aaps.core.interfaces.rx.events.Event
import kotlinx.coroutines.flow.Flow
import kotlin.reflect.KClass

/**
 * A simple event bus for communication between different parts of the application.
 */
interface RxBus {

    /**
     * Sends an event to the bus.
     *
     * @param event The event to send.
     */
    fun send(event: Event)

    /**
     * Subscribes to events of a specific type.
     *
     * The bus has no replay, so a collector only sees what is sent after it starts. Collect with
     * `app.aaps.core.interfaces.rx.collectResilient` and `CoroutineStart.UNDISPATCHED` when the
     * subscription is made from a constructor or `onStart`, so nothing sent right after it is lost.
     *
     * @param eventType The class of the event to listen for.
     * @return A [Flow] that emits events of the specified type.
     */
    fun <T : Event> toFlow(eventType: KClass<T>): Flow<T>
}
