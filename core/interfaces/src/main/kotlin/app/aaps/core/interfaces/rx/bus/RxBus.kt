package app.aaps.core.interfaces.rx.bus

import app.aaps.core.interfaces.rx.events.Event
import io.reactivex.rxjava3.core.Observable
import kotlinx.coroutines.flow.Flow

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
     * Subscribes to events of a specific type via RxJava Observable.
     *
     * @param eventType The class of the event to listen for.
     * @return An [Observable] that emits events of the specified type.
     */
    fun <T : Any> toObservable(eventType: Class<T>): Observable<T>

    /**
     * Subscribes to events of a specific type via coroutines Flow.
     * Use this in Compose/coroutine code instead of [toObservable].
     *
     * @param eventType The class of the event to listen for.
     * @return A [Flow] that emits events of the specified type.
     */
    fun <T : Event> toFlow(eventType: Class<T>): Flow<T>
}