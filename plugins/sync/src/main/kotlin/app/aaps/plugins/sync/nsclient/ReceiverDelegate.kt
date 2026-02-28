package app.aaps.plugins.sync.nsclient

import app.aaps.core.interfaces.receivers.ReceiverStatusStore
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventAppInitialized
import app.aaps.core.interfaces.rx.events.EventChargingState
import app.aaps.core.interfaces.rx.events.EventNetworkChange
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.StringKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.plugins.sync.R
import app.aaps.plugins.sync.nsShared.events.EventConnectivityOptionChanged
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReceiverDelegate @Inject constructor(
    private val rxBus: RxBus,
    private val rh: ResourceHelper,
    private val preferences: Preferences,
    private val receiverStatusStore: ReceiverStatusStore
) {

    private var allowedChargingState: Boolean? = null
    private var allowedNetworkState: Boolean? = null
    var allowed: Boolean = false
    var blockingReason = "Status not available"

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    init {
        val onNetworkPrefChange: suspend (Any) -> Unit = {
            receiverStatusStore.updateNetworkStatus()
            receiverStatusStore.lastNetworkEvent?.let { onNetworkChange(it) }
        }
        preferences.observe(BooleanKey.NsClientUseWifi).drop(1).onEach(onNetworkPrefChange).launchIn(scope)
        preferences.observe(BooleanKey.NsClientUseCellular).drop(1).onEach(onNetworkPrefChange).launchIn(scope)
        preferences.observe(StringKey.NsClientWifiSsids).drop(1).onEach(onNetworkPrefChange).launchIn(scope)
        preferences.observe(BooleanKey.NsClientUseRoaming).drop(1).onEach(onNetworkPrefChange).launchIn(scope)
        val onChargingPrefChange: suspend (Any) -> Unit = { receiverStatusStore.broadcastChargingState() }
        preferences.observe(BooleanKey.NsClientUseOnCharging).drop(1).onEach(onChargingPrefChange).launchIn(scope)
        preferences.observe(BooleanKey.NsClientUseOnBattery).drop(1).onEach(onChargingPrefChange).launchIn(scope)
        rxBus.toFlow(EventNetworkChange::class.java)
            .onEach { ev -> onNetworkChange(ev) }
            .launchIn(scope)
        rxBus.toFlow(EventChargingState::class.java)
            .onEach { ev -> onChargingStateChange(ev) }
            .launchIn(scope)
        // Until the app is fully initialized, some EventConnectivityOptionChanged may be lost
        // Send again when app is initialized
        rxBus.toFlow(EventAppInitialized::class.java)
            .onEach { rxBus.send(EventConnectivityOptionChanged("App start", receiverStatusStore.isConnected)) }
            .launchIn(scope)
    }

    fun grabReceiversState() {
        receiverStatusStore.updateNetworkStatus()
    }

    private fun onChargingStateChange(ev: EventChargingState) {
        val newChargingState = calculateStatus(ev)
        if (newChargingState != allowedChargingState) {
            allowedChargingState = newChargingState
            if (!newChargingState) blockingReason = rh.gs(R.string.blocked_by_charging)
            processStateChange()
        }
    }

    private fun onNetworkChange(ev: EventNetworkChange) {
        val newNetworkState = calculateStatus(ev)
        if (newNetworkState != allowedNetworkState) {
            allowedNetworkState = newNetworkState
            if (!newNetworkState) {
                blockingReason =
                    if (!ev.isAnyConnection) rh.gs(R.string.no_connectivity)
                    else rh.gs(R.string.blocked_by_connectivity)
            }
            processStateChange()
        }
    }

    private fun processStateChange() {
        val newAllowedState = allowedChargingState == true && allowedNetworkState == true
        if (newAllowedState != allowed) {
            allowed = newAllowedState
            if (allowed) blockingReason = "Connected"
            rxBus.send(EventConnectivityOptionChanged(blockingReason, receiverStatusStore.isConnected))
        }
    }

    fun calculateStatus(ev: EventChargingState): Boolean =
        !ev.isCharging && preferences.get(BooleanKey.NsClientUseOnBattery) ||
            ev.isCharging && preferences.get(BooleanKey.NsClientUseOnCharging)

    fun calculateStatus(ev: EventNetworkChange): Boolean =
        ev.mobileConnected && preferences.get(BooleanKey.NsClientUseCellular) && !ev.roaming ||
            ev.mobileConnected && preferences.get(BooleanKey.NsClientUseCellular) && ev.roaming && preferences.get(BooleanKey.NsClientUseRoaming) ||
            ev.wifiConnected && preferences.get(BooleanKey.NsClientUseWifi) && preferences.get(StringKey.NsClientWifiSsids).isEmpty() ||
            ev.wifiConnected && preferences.get(BooleanKey.NsClientUseWifi) && preferences.get(StringKey.NsClientWifiSsids).split(";").contains(ev.ssid)
}