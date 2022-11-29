package info.nightscout.plugins.sync.nsclient

import info.nightscout.interfaces.receivers.ReceiverStatusStore
import info.nightscout.plugins.sync.R
import info.nightscout.rx.bus.RxBus
import info.nightscout.rx.events.EventChargingState
import info.nightscout.rx.events.EventNetworkChange
import info.nightscout.rx.events.EventPreferenceChange
import info.nightscout.shared.interfaces.ResourceHelper
import info.nightscout.shared.sharedPreferences.SP
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NsClientReceiverDelegate @Inject constructor(
    private val rxBus: RxBus,
    private val rh: ResourceHelper,
    private val sp: SP,
    private val receiverStatusStore: ReceiverStatusStore
) {

    private var allowedChargingState = true
    private var allowedNetworkState = true
    var allowed = true
    var blockingReason = ""

    fun grabReceiversState() {
        receiverStatusStore.updateNetworkStatus()
    }

    fun onStatusEvent(ev: EventPreferenceChange) {
        when {
            ev.isChanged(rh.gs(R.string.key_ns_wifi)) ||
                ev.isChanged(rh.gs(R.string.key_ns_cellular)) ||
                ev.isChanged(rh.gs(R.string.key_ns_wifi_ssids)) ||
                ev.isChanged(rh.gs(R.string.key_ns_allow_roaming)) -> {
                receiverStatusStore.updateNetworkStatus()
                receiverStatusStore.lastNetworkEvent?.let { onStatusEvent(it) }
            }

            ev.isChanged(rh.gs(R.string.key_ns_charging)) ||
                ev.isChanged(rh.gs(R.string.key_ns_battery))       -> {
                receiverStatusStore.broadcastChargingState()
            }
        }
    }

    fun onStatusEvent(ev: EventChargingState) {
        val newChargingState = calculateStatus(ev)
        if (newChargingState != allowedChargingState) {
            allowedChargingState = newChargingState
            blockingReason = rh.gs(R.string.blocked_by_charging)
            processStateChange()
        }
    }

    fun onStatusEvent(ev: EventNetworkChange) {
        val newNetworkState = calculateStatus(ev)
        if (newNetworkState != allowedNetworkState) {
            allowedNetworkState = newNetworkState
            blockingReason = rh.gs(R.string.blocked_by_connectivity)
            processStateChange()
        }
    }

    private fun processStateChange() {
        val newAllowedState = allowedChargingState && allowedNetworkState
        if (newAllowedState != allowed) {
            allowed = newAllowedState
            rxBus.send(EventPreferenceChange(rh.gs(R.string.key_ns_client_paused)))
        }
    }

    fun calculateStatus(ev: EventChargingState): Boolean =
        !ev.isCharging && sp.getBoolean(R.string.key_ns_battery, true) ||
            ev.isCharging && sp.getBoolean(R.string.key_ns_charging, true)

    fun calculateStatus(ev: EventNetworkChange): Boolean =
        ev.mobileConnected && sp.getBoolean(R.string.key_ns_cellular, true) && !ev.roaming ||
            ev.mobileConnected && sp.getBoolean(R.string.key_ns_cellular, true) && ev.roaming && sp.getBoolean(R.string.key_ns_allow_roaming, true) ||
            ev.wifiConnected && sp.getBoolean(R.string.key_ns_wifi, true) && sp.getString(R.string.key_ns_wifi_ssids, "").isEmpty() ||
            ev.wifiConnected && sp.getBoolean(R.string.key_ns_wifi, true) && sp.getString(R.string.key_ns_wifi_ssids, "").split(";").contains(ev.ssid)
}