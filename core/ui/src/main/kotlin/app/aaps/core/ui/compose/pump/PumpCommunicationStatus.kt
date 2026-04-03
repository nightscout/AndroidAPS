package app.aaps.core.ui.compose.pump

import android.content.Context
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventPumpStatusChanged
import app.aaps.core.interfaces.rx.events.EventQueueChanged
import app.aaps.core.ui.compose.StatusLevel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * Shared communication status provider for all pump overview screens.
 *
 * Subscribes to [EventPumpStatusChanged] and [EventQueueChanged] and provides
 * a [refreshTrigger] flow that pump ViewModels should combine with their own
 * data flows. The [statusBanner] and [queueStatus] methods return the current
 * communication state for the shared [PumpOverviewScreen] card.
 */
class PumpCommunicationStatus(
    rxBus: RxBus,
    private val commandQueue: CommandQueue,
    private val context: Context,
    scope: CoroutineScope
) {

    private var pumpStatusText: String = ""
    private var pumpStatusLevel: StatusLevel = StatusLevel.UNSPECIFIED

    /** Emits whenever communication status or queue changes. */
    val refreshTrigger: MutableStateFlow<Long> = MutableStateFlow(0L)

    init {
        rxBus.toFlow(EventPumpStatusChanged::class.java)
            .onEach { event ->
                pumpStatusText = event.getStatus(context)
                pumpStatusLevel = StatusLevel.UNSPECIFIED
                refreshTrigger.value = System.currentTimeMillis()
            }
            .launchIn(scope)

        rxBus.toFlow(EventQueueChanged::class.java)
            .onEach { refreshTrigger.value = System.currentTimeMillis() }
            .launchIn(scope)
    }

    /** Builds the communication status banner (line 1 of the card). */
    fun statusBanner(): StatusBanner? {
        if (pumpStatusText.isEmpty()) return null
        return StatusBanner(text = pumpStatusText, level = pumpStatusLevel)
    }

    /** Returns the current command queue status text (line 2 of the card). */
    fun queueStatus(): String? =
        commandQueue.spannedStatus().toString().takeIf { it.isNotEmpty() }
}
