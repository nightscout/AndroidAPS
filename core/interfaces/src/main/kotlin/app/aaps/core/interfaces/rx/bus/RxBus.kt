package app.aaps.core.interfaces.rx.bus

import app.aaps.core.interfaces.rx.events.Event
import io.reactivex.rxjava3.core.Observable

/**
 * Reactive event bus for decoupled communication between components.
 *
 * RxBus implements a publish-subscribe pattern using RxJava3. Components publish events
 * without knowledge of subscribers, and subscribers filter for event types they care about.
 *
 * ## Architecture Role
 * RxBus is the primary communication mechanism in AndroidAPS for:
 * - **UI updates**: Loop results trigger [EventLoopUpdateGui] -> Overview refreshes
 * - **Data changes**: New BG reading triggers [EventNewBgReading] -> Loop cycle starts
 * - **Status changes**: Pump events, preference changes, queue updates
 * - **Plugin coordination**: APS calculation finished -> Loop processes result
 *
 * ## Usage
 * ```kotlin
 * // Publishing an event
 * rxBus.send(EventLoopUpdateGui())
 *
 * // Subscribing (typically in onStart, disposed in onStop)
 * disposable += rxBus
 *     .toObservable(EventLoopUpdateGui::class.java)
 *     .observeOn(aapsSchedulers.main)
 *     .subscribe { updateUI() }
 * ```
 *
 * ## Thread Safety
 * Events are published on the caller's thread. Subscribers should use
 * `.observeOn(aapsSchedulers.main)` for UI updates or `.observeOn(aapsSchedulers.io)` for I/O.
 *
 * @see app.aaps.core.interfaces.rx.events.Event
 * @see app.aaps.core.interfaces.rx.AapsSchedulers
 */
interface RxBus {

    /**
     * Publish an event to all subscribers.
     *
     * @param event The event to broadcast. All subscribers matching the event's type will be notified.
     */
    fun send(event: Event)

    /**
     * Subscribe to events of a specific type.
     *
     * Uses RxJava's `ofType` operator to filter the event stream. The returned [Observable]
     * emits only events matching [eventType]. Subscribers must manage their own disposal
     * (typically via CompositeDisposable).
     *
     * @param eventType The class of events to listen for.
     * @return An [Observable] emitting events of the specified type.
     */
    fun <T : Any> toObservable(eventType: Class<T>): Observable<T>
}