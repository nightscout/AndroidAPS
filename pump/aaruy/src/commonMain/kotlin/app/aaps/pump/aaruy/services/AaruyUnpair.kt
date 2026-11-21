package app.aaps.pump.aaruy.services

import app.aaps.core.interfaces.concurrent.aapsIoDispatcher
import app.aaps.pump.aaruy.AaruyStrings
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.notifications.NotificationId
import app.aaps.core.interfaces.notifications.NotificationLevel
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.pump.aaruy.events.EventAaruyDeviceChange
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * The user unpairs the pump (acceptance D9). The pump is told first, through the queue, which opens the link. When the
 * queue could not reach it, the pump is removed from AAPS anyway, and the user is told to take it off: a pump that is only
 * out of range still gives its basal. Either way AAPS counts nothing more from it: its temp basal ends with the removal.
 */
@SingleIn(AppScope::class)
@Inject
class AaruyUnpair(
    private val commandQueue: CommandQueue,
    private val aaruyBle: AaruyBLE,
    private val notificationManager: NotificationManager,
    private val rh: TextResolver,
    private val rxBus: RxBus,
    private val aapsLogger: AAPSLogger
) {

    /** Not the scope of a screen: leaving the screen must not stop the removal. */
    private val scope = CoroutineScope(aapsIoDispatcher + SupervisorJob())

    fun start() {
        scope.launch { unpair() }
    }

    /** @return whether the pump was told. Removed from AAPS in both cases. */
    suspend fun unpair(): Boolean {
        val told = commandQueue.customCommand(AaruyUnpairCommand()).success
        if (!told) {
            aapsLogger.warn(LTag.PUMP, "The pump was not reached. It is removed from AAPS anyway")
            aaruyBle.unbindDevice()
            notificationManager.post(NotificationId.PUMP_WARNING, rh.gs(AaruyStrings.aaruy_unpaired_not_reached), level = NotificationLevel.IMPORTANT)
        }
        // Ends what AAPS counts from this pump (PumpSync.connectNewPump) and its alerts. A told pump drops the link, which
        // sends this too; the second one finds nothing to end.
        rxBus.send(EventAaruyDeviceChange())
        return told
    }
}
