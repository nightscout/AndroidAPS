package app.aaps.pump.omnipod.eros.manager

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.notifications.NotificationId
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.pump.omnipod.eros.driver.manager.ErosPodStateManager
import app.aaps.pump.omnipod.eros.event.EventOmnipodErosActiveAlertsChanged
import app.aaps.pump.omnipod.eros.event.EventOmnipodErosFaultEventChanged
import app.aaps.pump.omnipod.eros.event.EventOmnipodErosTbrChanged
import app.aaps.pump.omnipod.eros.event.EventOmnipodErosUncertainTbrRecovered
import app.aaps.pump.omnipod.eros.keys.ErosStringNonPreferenceKey
import javax.inject.Inject
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.SingleIn

/**
 * Was Java.
 *
 * javax `@Singleton`, not Metro's `@SingleIn`: Dagger owns this and hands it to Metro through
 * `PumpLeaves`. This is the pod state - two copies would mean the pump writing to one object while the
 * screens read another.
 */
@SingleIn(AppScope::class)
class AapsErosPodStateManager @Inject constructor(
    aapsLogger: AAPSLogger,
    private val preferences: Preferences,
    private val rxBus: RxBus,
    private val notificationManager: NotificationManager
) : ErosPodStateManager(aapsLogger) {

    override fun readPodState(): String = preferences.get(ErosStringNonPreferenceKey.PodState)

    override fun storePodState(podState: String) {
        preferences.put(ErosStringNonPreferenceKey.PodState, podState)
    }

    override fun onUncertainTbrRecovered() {
        rxBus.send(EventOmnipodErosUncertainTbrRecovered())
    }

    override fun onTbrChanged() {
        rxBus.send(EventOmnipodErosTbrChanged())
    }

    override fun onActiveAlertsChanged() {
        rxBus.send(EventOmnipodErosActiveAlertsChanged())
    }

    override fun onFaultEventChanged() {
        rxBus.send(EventOmnipodErosFaultEventChanged())
    }

    override fun onUpdatedFromResponse() {
        notificationManager.dismiss(NotificationId.OMNIPOD_STARTUP_STATUS_REFRESH_FAILED)
    }
}
