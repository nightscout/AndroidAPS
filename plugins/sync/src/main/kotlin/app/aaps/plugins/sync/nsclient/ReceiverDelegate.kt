package app.aaps.plugins.sync.nsclient

import app.aaps.annotations.OpenForTesting
import app.aaps.core.interfaces.receivers.ReceiverStatusStore
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventChargingState
import app.aaps.core.interfaces.rx.events.EventNetworkChange
import app.aaps.core.interfaces.rx.events.EventPreferenceChange
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.plugins.sync.R
import app.aaps.plugins.sync.nsShared.events.EventConnectivityOptionChanged
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
            rxBus.send(EventConnectivityOptionChanged(blockingReason, receiverStatusStore.isConnected))
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