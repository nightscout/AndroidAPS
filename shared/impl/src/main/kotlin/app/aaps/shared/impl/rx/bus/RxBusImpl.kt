package app.aaps.shared.impl.rx.bus

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.Event
import app.aaps.core.interfaces.rx.events.EventUpdateOverviewCalcProgress
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.reflect.KClass

@Singleton
class RxBusImpl @Inject constructor(
    val aapsLogger: AAPSLogger
) : RxBus {

    private val flowPublisher = MutableSharedFlow<Event>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    override fun send(event: Event) {
        if (event !is EventUpdateOverviewCalcProgress)
            aapsLogger.debug(LTag.EVENTS, "Sending $event")
        flowPublisher.tryEmit(event)
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T : Event> toFlow(eventType: KClass<T>): Flow<T> =
        flowPublisher
            .filter { eventType.isInstance(it) }
            .map { it as T }
}
