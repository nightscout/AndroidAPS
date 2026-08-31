package app.aaps.implementation.receivers

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.receivers.ReceiverStatusStore
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.Network.nw_interface_type_cellular
import platform.Network.nw_interface_type_wifi
import platform.Network.nw_interface_type_wired
import platform.Network.nw_path_get_status
import platform.Network.nw_path_is_expensive
import platform.Network.nw_path_monitor_create
import platform.Network.nw_path_monitor_set_queue
import platform.Network.nw_path_monitor_set_update_handler
import platform.Network.nw_path_monitor_start
import platform.Network.nw_path_status_satisfied
import platform.Network.nw_path_uses_interface_type
import platform.UIKit.UIDevice
import platform.UIKit.UIDeviceBatteryState
import platform.darwin.dispatch_get_main_queue

/**
 * Network and battery state on iOS, from `nw_path_monitor` and `UIDevice`.
 *
 * Android learns both from broadcast receivers. iOS has no broadcasts, so the network half is a
 * path monitor that calls back when the route changes, and the battery half is read on demand from
 * `UIDevice` after asking it to start monitoring.
 *
 * ## Two fields iOS cannot answer, and what that costs
 *
 * - **`roaming` is always false.** iOS exposes no roaming state at all - not through
 *   `CTTelephonyNetworkInfo`, not anywhere public. This is **not harmless**:
 *   `ReceiverDelegate` gates Nightscout sync on it, so a user who turned off "sync while roaming"
 *   still syncs over cellular abroad and may be charged for it. Recorded in
 *   `_docs/ios_blockers.md`; false is the least surprising of two wrong answers, because true would
 *   stop cellular sync working at all for everyone.
 * - **`ssid` is always empty.** Reading it needs the Access WiFi Information entitlement plus
 *   location permission. Until that is arranged, a Wi-Fi SSID automation trigger can be configured
 *   on iOS and will never match - the same shape as the Bluetooth trigger.
 *
 * Everything else is real: connectivity, which interface carries it, whether the link is expensive,
 * and the battery.
 */
@OptIn(ExperimentalForeignApi::class)
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class IosReceiverStatusStore @Inject constructor(
    private val aapsLogger: AAPSLogger
) : ReceiverStatusStore {

    private val _networkStatusFlow = MutableStateFlow<ReceiverStatusStore.NetworkStatus?>(null)
    override val networkStatusFlow: StateFlow<ReceiverStatusStore.NetworkStatus?> = _networkStatusFlow.asStateFlow()

    private val _chargingStatusFlow = MutableStateFlow<ReceiverStatusStore.ChargingStatus?>(null)
    override val chargingStatusFlow: StateFlow<ReceiverStatusStore.ChargingStatus?> = _chargingStatusFlow.asStateFlow()

    /**
     * Started on first use rather than in the constructor.
     *
     * Building the graph must not start a network monitor or switch on battery polling; nothing
     * needs either until something actually asks about connectivity.
     */
    private val monitor by lazy {
        nw_path_monitor_create()?.also { created ->
            nw_path_monitor_set_update_handler(created) { path ->
                val status = ReceiverStatusStore.NetworkStatus(
                    mobileConnected = nw_path_uses_interface_type(path, nw_interface_type_cellular),
                    wifiConnected = nw_path_uses_interface_type(path, nw_interface_type_wifi) ||
                        nw_path_uses_interface_type(path, nw_interface_type_wired),
                    // No public API for either. See the class docs - roaming in particular has a cost.
                    vpnConnected = false,
                    ssid = "",
                    roaming = false,
                    metered = nw_path_is_expensive(path)
                )
                val reachable = nw_path_get_status(path) == nw_path_status_satisfied
                // A path that exists but is not satisfied is not a connection, whatever interfaces
                // it names, so nothing is reported as connected in that case.
                setNetworkStatus(if (reachable) status else ReceiverStatusStore.NetworkStatus())
            }
            nw_path_monitor_set_queue(created, dispatch_get_main_queue())
            nw_path_monitor_start(created)
            aapsLogger.debug(LTag.CORE, "Network path monitor started")
        }
    }

    override fun setNetworkStatus(event: ReceiverStatusStore.NetworkStatus) {
        _networkStatusFlow.value = event
    }

    override fun updateNetworkStatus() {
        // Touching the monitor starts it; after that the handler keeps the flow current on its own.
        monitor
    }

    override val isKnownNetworkStatus: Boolean get() = _networkStatusFlow.value != null
    override val isWifiConnected: Boolean get() = _networkStatusFlow.value?.wifiConnected == true
    override val isConnected: Boolean get() = _networkStatusFlow.value?.isAnyConnection == true

    override fun setChargingStatus(event: ReceiverStatusStore.ChargingStatus) {
        _chargingStatusFlow.value = event
    }

    override val isCharging: Boolean get() = readBattery().isCharging
    override val batteryLevel: Int get() = readBattery().batteryLevel

    /**
     * Reads the battery, switching monitoring on the first time.
     *
     * `batteryLevel` is -1 while monitoring is off, and iOS reports it as 0..1 rather than a
     * percentage. A device that still answers -1 is reported as 100 rather than 0, because a false
     * empty battery would raise an alarm that is not real.
     */
    private fun readBattery(): ReceiverStatusStore.ChargingStatus {
        val device = UIDevice.currentDevice
        if (!device.batteryMonitoringEnabled) device.batteryMonitoringEnabled = true
        val raw = device.batteryLevel
        val level = if (raw < 0f) 100 else (raw * 100).toInt()
        val charging = device.batteryState == UIDeviceBatteryState.UIDeviceBatteryStateCharging ||
            device.batteryState == UIDeviceBatteryState.UIDeviceBatteryStateFull
        val status = ReceiverStatusStore.ChargingStatus(isCharging = charging, batteryLevel = level)
        _chargingStatusFlow.value = status
        return status
    }
}
