package app.aaps.plugins.sync.nsclient

import app.aaps.core.interfaces.receivers.ReceiverStatusStore
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventAppInitialized
import app.aaps.core.interfaces.rx.events.EventChargingState
import app.aaps.core.interfaces.rx.events.EventNetworkChange
import app.aaps.core.interfaces.rx.events.EventPreferenceChange
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.StringKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.plugins.sync.R
import app.aaps.plugins.sync.nsShared.events.EventConnectivityOptionChanged
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReceiverDelegate @Inject constructor(
    private val rxBus: RxBus,
    private val rh: ResourceHelper,
    private val preferences: Preferences,
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
        // Until the app is fully initialized, some EventConnectivityOptionChanged may be lost
        // Send again when app is initialized
        disposable += rxBus
            .toObservable(EventAppInitialized::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ rxBus.send(EventConnectivityOptionChanged("App start", receiverStatusStore.isConnected)) }, fabricPrivacy::logException)
    }

    fun grabReceiversState() {
        receiverStatusStore.updateNetworkStatus()
    }

    private fun onPreferenceChange(ev: EventPreferenceChange) {
        when {
            ev.isChanged(BooleanKey.NsClientUseWifi.key) ||
                ev.isChanged(BooleanKey.NsClientUseCellular.key) ||
                ev.isChanged(StringKey.NsClientWifiSsids.key) ||
                ev.isChanged(BooleanKey.NsClientUseRoaming.key)   -> {
                receiverStatusStore.updateNetworkStatus()
                receiverStatusStore.lastNetworkEvent?.let { onNetworkChange(it) }
            }

            ev.isChanged(BooleanKey.NsClientUseOnCharging.key) ||
                ev.isChanged(BooleanKey.NsClientUseOnBattery.key) -> {
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