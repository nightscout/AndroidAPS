package app.aaps.desktop.shell.platform

import app.aaps.core.interfaces.receivers.ReceiverStatusStore
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.NetworkInterface

/**
 * Network and power status on desktop.
 *
 * ## Network is real
 *
 * [updateNetworkStatus] walks the machine's own interfaces and reports whether any of them is up,
 * not a loopback and has an address. That is genuinely what "is there a connection" means here, and
 * it is what `ReceiverDelegate` gates Nightscout sync on.
 *
 * Everything is reported as a **wifi** connection. Desktop has no mobile data concept, and
 * `ReceiverDelegate` reads `mobileConnected` to decide whether the user's "sync over cellular" and
 * "sync while roaming" preferences apply. Saying wifi keeps those preferences out of the decision,
 * which is the truthful answer for a machine on a fixed line - and it avoids the trap the Apple side
 * documents, where an always-false roaming flag silently defeats the roaming preference.
 *
 * ## Power is not, and says so
 *
 * A JVM cannot read battery state without a per-OS call, and a desktop is usually on mains anyway.
 * [isCharging] is true and [batteryLevel] is 100, which are the values that make power-based rules
 * behave as "there is no power problem" rather than as "the battery is flat". A laptop actually
 * running low will not be noticed - worth knowing before any feature is gated on battery here.
 */
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
class DesktopReceiverStatusStore @Inject constructor() : ReceiverStatusStore {

    private val _networkStatusFlow = MutableStateFlow<ReceiverStatusStore.NetworkStatus?>(null)
    override val networkStatusFlow: StateFlow<ReceiverStatusStore.NetworkStatus?> = _networkStatusFlow.asStateFlow()

    private val _chargingStatusFlow = MutableStateFlow<ReceiverStatusStore.ChargingStatus?>(
        ReceiverStatusStore.ChargingStatus(isCharging = true, batteryLevel = 100)
    )
    override val chargingStatusFlow: StateFlow<ReceiverStatusStore.ChargingStatus?> = _chargingStatusFlow.asStateFlow()

    init {
        updateNetworkStatus()
    }

    override fun setNetworkStatus(event: ReceiverStatusStore.NetworkStatus) {
        _networkStatusFlow.value = event
    }

    override fun setChargingStatus(event: ReceiverStatusStore.ChargingStatus) {
        _chargingStatusFlow.value = event
    }

    override fun updateNetworkStatus() {
        val up = runCatching {
            NetworkInterface.networkInterfaces().anyMatch { it.isUp && !it.isLoopback && it.inetAddresses().findAny().isPresent }
        }.getOrDefault(false)
        // ssidReadable = false: a `NetworkInterface` has no network name, so the "only sync on these
        // networks" list can never match here. See NetworkStatus.ssidReadable.
        setNetworkStatus(ReceiverStatusStore.NetworkStatus(wifiConnected = up, ssidReadable = false))
    }

    override val isWifiConnected: Boolean get() = _networkStatusFlow.value?.wifiConnected == true
    override val isKnownNetworkStatus: Boolean get() = _networkStatusFlow.value != null
    override val isConnected: Boolean get() = _networkStatusFlow.value?.isAnyConnection == true

    override val isCharging: Boolean get() = _chargingStatusFlow.value?.isCharging == true
    override val batteryLevel: Int get() = _chargingStatusFlow.value?.batteryLevel ?: 100
}
