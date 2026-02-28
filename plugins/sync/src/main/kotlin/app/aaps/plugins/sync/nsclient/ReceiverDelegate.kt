package app.aaps.plugins.sync.nsclient

import app.aaps.core.interfaces.receivers.ReceiverStatusStore
import app.aaps.core.interfaces.receivers.ReceiverStatusStore.ChargingStatus
import app.aaps.core.interfaces.receivers.ReceiverStatusStore.NetworkStatus
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.StringKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.plugins.sync.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReceiverDelegate @Inject constructor(
    private val rh: ResourceHelper,
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

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

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
            processStateChange(if (!newChargingState) rh.gs(R.string.blocked_by_charging) else null)
        }
    }

    private fun onNetworkChange(ev: NetworkStatus) {
        val newNetworkState = calculateStatus(ev)
        if (newNetworkState != allowedNetworkState) {
            allowedNetworkState = newNetworkState
            val reason = if (!newNetworkState) {
                if (!ev.isAnyConnection) rh.gs(R.string.no_connectivity)
                else rh.gs(R.string.blocked_by_connectivity)
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
            ev.wifiConnected && preferences.get(BooleanKey.NsClientUseWifi) && preferences.get(StringKey.NsClientWifiSsids).isEmpty() ||
            ev.wifiConnected && preferences.get(BooleanKey.NsClientUseWifi) && preferences.get(StringKey.NsClientWifiSsids).split(";").contains(ev.ssid)
}
