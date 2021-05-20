package info.nightscout.androidaps.plugins.general.nsclient

import info.nightscout.androidaps.R
import info.nightscout.androidaps.events.EventChargingState
import info.nightscout.androidaps.events.EventNetworkChange
import info.nightscout.androidaps.events.EventPreferenceChange
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.receivers.ReceiverStatusStore
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.sharedPreferences.SP
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NsClientReceiverDelegate @Inject constructor(
    private val rxBus: RxBusWrapper,
    private val resourceHelper: ResourceHelper,
    private val sp: SP,
    private val receiverStatusStore: ReceiverStatusStore
) {

    private var allowedChargingState = true
    private var allowedNetworkState = true
    var allowed = true

    fun grabReceiversState() {
        receiverStatusStore.updateNetworkStatus()
    }

    fun onStatusEvent(ev: EventPreferenceChange) {
        if (ev.isChanged(resourceHelper, R.string.key_ns_wifionly) ||
            ev.isChanged(resourceHelper, R.string.key_ns_wifi_ssids) ||
            ev.isChanged(resourceHelper, R.string.key_ns_allowroaming)) {
            receiverStatusStore.updateNetworkStatus()
            onStatusEvent(receiverStatusStore.lastNetworkEvent)
        } else if (ev.isChanged(resourceHelper, R.string.key_ns_chargingonly)) {
            receiverStatusStore.broadcastChargingState()
        }
    }

    fun onStatusEvent(ev: EventChargingState) {
        val newChargingState = calculateStatus(ev)
        if (newChargingState != allowedChargingState) {
            allowedChargingState = newChargingState
            processStateChange()
        }
    }

    fun onStatusEvent(ev: EventNetworkChange?) {
        val newNetworkState = calculateStatus(ev)
        if (newNetworkState != allowedNetworkState) {
            allowedNetworkState = newNetworkState
            processStateChange()
        }
    }

    private fun processStateChange() {
        val newAllowedState = allowedChargingState && allowedNetworkState
        if (newAllowedState != allowed) {
            allowed = newAllowedState
            rxBus.send(EventPreferenceChange(resourceHelper.gs(R.string.key_nsclientinternal_paused)))
        }
    }

    fun calculateStatus(ev: EventChargingState): Boolean {
        val chargingOnly = sp.getBoolean(R.string.key_ns_chargingonly, false)
        var newAllowedState = true
        if (!ev.isCharging && chargingOnly) {
            newAllowedState = false
        }
        return newAllowedState
    }

    fun calculateStatus(ev: EventNetworkChange?): Boolean {
        val wifiOnly = sp.getBoolean(R.string.key_ns_wifionly, false)
        val allowedSsidString = sp.getString(R.string.key_ns_wifi_ssids, "")
        val allowedSSIDs: List<String> = if (allowedSsidString.isEmpty()) List(0) { "" } else allowedSsidString.split(";")
        val allowRoaming = sp.getBoolean(R.string.key_ns_allowroaming, true)
        var newAllowedState = true
        if (ev?.wifiConnected == true) {
            if (allowedSSIDs.isNotEmpty() && !allowedSSIDs.contains(ev.ssid)) {
                newAllowedState = false
            }
        } else {
            if (!allowRoaming && ev?.roaming == true || wifiOnly) {
                newAllowedState = false
            }
        }
        return newAllowedState
    }
}