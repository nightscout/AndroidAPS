package info.nightscout.plugins.sync.nsclient

import info.nightscout.androidaps.annotations.OpenForTesting
import info.nightscout.core.utils.fabric.FabricPrivacy
import info.nightscout.interfaces.receivers.ReceiverStatusStore
import info.nightscout.plugins.sync.R
import info.nightscout.plugins.sync.nsShared.events.EventConnectivityOptionChanged
import info.nightscout.rx.AapsSchedulers
import info.nightscout.rx.bus.RxBus
import info.nightscout.rx.events.EventChargingState
import info.nightscout.rx.events.EventNetworkChange
import info.nightscout.rx.events.EventPreferenceChange
import info.nightscout.shared.interfaces.ResourceHelper
import info.nightscout.shared.sharedPreferences.SP
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import javax.inject.Inject
import javax.inject.Singleton

@OpenForTesting
@Singleton
class ReceiverDelegate @Inject constructor(
    private val rxBus: RxBus,
    private val rh: ResourceHelper,
    private val sp: SP,
    private val receiverStatusStore: ReceiverStatusStore,
    aapsSchedulers: AapsSchedulers,
    fabricPrivacy: FabricPrivacy
) {

    private var allowedChargingState: Boolean? = null
    private var allowedNetworkState: Boolean? = null
    var allowed: Boolean = false
    var blockingReason = "Status not available"

    private val disposable = CompositeDisposable()

    init {
        disposable += rxBus
            .toObservable(EventPreferenceChange::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ ev -> onPreferenceChange(ev) }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventNetworkChange::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ ev -> onNetworkChange(ev) }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventChargingState::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ ev -> onChargingStateChange(ev) }, fabricPrivacy::logException)
    }

    fun grabReceiversState() {
        receiverStatusStore.updateNetworkStatus()
    }

    private fun onPreferenceChange(ev: EventPreferenceChange) {
        when {
            ev.isChanged(rh.gs(R.string.key_ns_wifi)) ||
                ev.isChanged(rh.gs(R.string.key_ns_cellular)) ||
                ev.isChanged(rh.gs(R.string.key_ns_wifi_ssids)) ||
                ev.isChanged(rh.gs(R.string.key_ns_allow_roaming)) -> {
                receiverStatusStore.updateNetworkStatus()
                receiverStatusStore.lastNetworkEvent?.let { onNetworkChange(it) }
            }

            ev.isChanged(rh.gs(R.string.key_ns_charging)) ||
                ev.isChanged(rh.gs(R.string.key_ns_battery))       -> {
                receiverStatusStore.broadcastChargingState()
            }
        }
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
            if (!newNetworkState) blockingReason = rh.gs(R.string.blocked_by_connectivity)
            processStateChange()
        }
    }

    private fun processStateChange() {
        val newAllowedState = allowedChargingState == true && allowedNetworkState == true
        if (newAllowedState != allowed) {
            allowed = newAllowedState
            if (allowed) blockingReason = ""
            rxBus.send(EventConnectivityOptionChanged(blockingReason))
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