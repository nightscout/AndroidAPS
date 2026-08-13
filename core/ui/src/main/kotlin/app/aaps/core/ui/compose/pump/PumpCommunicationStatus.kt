package app.aaps.core.ui.compose.pump

import androidx.compose.ui.text.AnnotatedString
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventPumpStatusChanged
import app.aaps.core.interfaces.rx.events.EventQueueChanged
import app.aaps.core.ui.compose.StatusLevel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * Shared communication status provider for all pump overview screens.
 *
 * Subscribes to [EventPumpStatusChanged] and [EventQueueChanged] and exposes
 * [statusBannerFlow] / [queueStatusFlow] for direct Compose collection, and
 * [refreshTrigger] for pump ViewModels that combine it with their own data flows.
 */
class PumpCommunicationStatus(
    rxBus: RxBus,
    private val commandQueue: CommandQueue,
    private val rh: ResourceHelper,
    scope: CoroutineScope
) {

    private val _statusBanner = MutableStateFlow<StatusBanner?>(null)
    val statusBannerFlow: StateFlow<StatusBanner?> = _statusBanner.asStateFlow()

    private val _queueStatus = MutableStateFlow<AnnotatedString?>(null)
    val queueStatusFlow: StateFlow<AnnotatedString?> = _queueStatus.asStateFlow()

    /** Emits whenever communication status or queue changes. */
    val refreshTrigger: MutableStateFlow<Long> = MutableStateFlow(0L)

    init {
        rxBus.toFlow(EventPumpStatusChanged::class.java)
            .onEach { event ->
                val text = rh.gs(event.getStatus())
                _statusBanner.value = if (text.isEmpty()) null else StatusBanner(text = text, level = StatusLevel.UNSPECIFIED)
                refreshTrigger.value = System.currentTimeMillis()
            }
            .launchIn(scope)

        rxBus.toFlow(EventQueueChanged::class.java)
            .onEach {
                _queueStatus.value = commandQueue.statusAsAnnotated().takeIf { it.isNotEmpty() }
                refreshTrigger.value = System.currentTimeMillis()
            }
            .launchIn(scope)
    }

    /** Returns the current communication status banner. */
    fun statusBanner(): StatusBanner? = _statusBanner.value

    /** Returns the current command queue status text. */
    fun queueStatus(): AnnotatedString? = _queueStatus.value
}
