package app.aaps.plugins.sync.nsclientV3

import app.aaps.plugins.sync.SyncStrings
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.receivers.ReceiverStatusStore
import app.aaps.core.interfaces.receivers.ReceiverStatusStore.ChargingStatus
import app.aaps.core.interfaces.receivers.ReceiverStatusStore.NetworkStatus
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.StringKey
import app.aaps.core.keys.interfaces.Preferences
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import app.aaps.core.interfaces.concurrent.aapsIoDispatcher
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@SingleIn(AppScope::class)
class ReceiverDelegate @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val rh: TextResolver,
    private val preferences: Preferences,
    private val receiverStatusStore: ReceiverStatusStore
) {

    data class ConnectivityStatus(val blockingReason: String, val allowed: Boolean, val connected: Boolean)

    private var allowedChargingState: Boolean? = null
    private var allowedNetworkState: Boolean? = null

    private val _connectivityStatusFlow = MutableStateFlow(ConnectivityStatus("Status not available", allowed = false, connected = false))
    val connectivityStatusFlow: StateFlow<ConnectivityStatus> = _connectivityStatusFlow

    val allowed: Boolean get() = _connectivityStatusFlow.value.allowed
    val blockingReason: String get() = _connectivityStatusFlow.value.blockingReason

    private val scope = CoroutineScope(aapsIoDispatcher + SupervisorJob())

    init {
        val onNetworkPrefChange: suspend (Any) -> Unit = {
            receiverStatusStore.updateNetworkStatus()
            receiverStatusStore.networkStatusFlow.value?.let { onNetworkChange(it) }
        }
        preferences.observe(BooleanKey.NsClientUseWifi).drop(1).onEach(onNetworkPrefChange).launchIn(scope)
        preferences.observe(BooleanKey.NsClientUseCellular).drop(1).onEach(onNetworkPrefChange).launchIn(scope)
        preferences.observe(StringKey.NsClientWifiSsids).drop(1).onEach(onNetworkPrefChange).launchIn(scope)
        preferences.observe(BooleanKey.NsClientUseRoaming).drop(1).onEach(onNetworkPrefChange).launchIn(scope)
        val onChargingPrefChange: suspend (Any) -> Unit = {
            receiverStatusStore.chargingStatusFlow.value?.let { onChargingStateChange(it) }
        }
        preferences.observe(BooleanKey.NsClientUseOnCharging).drop(1).onEach(onChargingPrefChange).launchIn(scope)
        preferences.observe(BooleanKey.NsClientUseOnBattery).drop(1).onEach(onChargingPrefChange).launchIn(scope)
        receiverStatusStore.networkStatusFlow
            .filterNotNull()
            .onEach { ev -> onNetworkChange(ev) }
            .launchIn(scope)
        receiverStatusStore.chargingStatusFlow
            .filterNotNull()
            .onEach { ev -> onChargingStateChange(ev) }
            .launchIn(scope)
    }

    fun grabReceiversState() {
        receiverStatusStore.updateNetworkStatus()
    }

    private fun onChargingStateChange(ev: ChargingStatus) {
        val newChargingState = calculateStatus(ev)
        if (newChargingState != allowedChargingState) {
            allowedChargingState = newChargingState
            processStateChange(if (!newChargingState) rh.gs(SyncStrings.blocked_by_charging) else null)
        }
    }

    private fun onNetworkChange(ev: NetworkStatus) {
        val newNetworkState = calculateStatus(ev)
        if (newNetworkState != allowedNetworkState) {
            allowedNetworkState = newNetworkState
            val reason = if (!newNetworkState) {
                if (!ev.isAnyConnection) rh.gs(SyncStrings.no_connectivity)
                else rh.gs(SyncStrings.blocked_by_connectivity)
            } else null
            processStateChange(reason)
        }
    }

    private fun processStateChange(newBlockingReason: String?) {
        val newAllowed = allowedChargingState == true && allowedNetworkState == true
        val currentStatus = _connectivityStatusFlow.value
        if (newAllowed != currentStatus.allowed) {
            val reason = if (newAllowed) "Connected" else newBlockingReason ?: currentStatus.blockingReason
            _connectivityStatusFlow.value = ConnectivityStatus(reason, newAllowed, receiverStatusStore.isConnected)
        }
    }

    fun calculateStatus(ev: ChargingStatus): Boolean =
        !ev.isCharging && preferences.get(BooleanKey.NsClientUseOnBattery) ||
            ev.isCharging && preferences.get(BooleanKey.NsClientUseOnCharging)

    fun calculateStatus(ev: NetworkStatus): Boolean =
        ev.mobileConnected && preferences.get(BooleanKey.NsClientUseCellular) && !ev.roaming ||
            ev.mobileConnected && preferences.get(BooleanKey.NsClientUseCellular) && ev.roaming && preferences.get(BooleanKey.NsClientUseRoaming) ||
            wifiAllowed(ev)

    /**
     * Whether this wifi link may carry Nightscout traffic.
     *
     * The list of allowed network names is only a filter where a network name can be read. Off
     * Android none can be - iOS would need the Access WiFi Information entitlement, and a desktop
     * `NetworkInterface` has no name at all - so `contains(ev.ssid)` could never be true, and a user
     * who filled the field in lost Nightscout sync on every network at once, with nothing on screen
     * and nothing in the log to connect the two.
     *
     * So where the name cannot be read the filter is skipped rather than treated as a miss. That is
     * more permissive than the user asked for, and it is the better of the two errors here: syncing
     * on a network they meant to exclude is a smaller harm than a follower that silently stops. It is
     * logged at error, because a setting quietly not being applied should be findable.
     *
     * This is deliberately **not** keyed on the name being empty. On Android an empty name means the
     * read failed this time, usually a missing location permission, and the user's filter must still
     * be obeyed - see [NetworkStatus.ssidReadable].
     */
    private fun wifiAllowed(ev: NetworkStatus): Boolean {
        if (!ev.wifiConnected || !preferences.get(BooleanKey.NsClientUseWifi)) return false
        val allowed = preferences.get(StringKey.NsClientWifiSsids)
        if (allowed.isEmpty()) return true
        if (!ev.ssidReadable) {
            aapsLogger.error(LTag.NSCLIENT, "Cannot read the network name on this platform, so the allowed network list is ignored")
            return true
        }
        return allowed.split(";").contains(ev.ssid)
    }
}