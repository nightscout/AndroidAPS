package app.aaps.implementation.receivers

import android.content.Context
import android.content.Intent
import app.aaps.core.interfaces.receivers.ReceiverStatusStore
import app.aaps.core.interfaces.receivers.ReceiverStatusStore.ChargingStatus
import app.aaps.core.interfaces.receivers.ReceiverStatusStore.NetworkStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReceiverStatusStoreImpl @Inject constructor(val context: Context) : ReceiverStatusStore {

    private val _networkStatusFlow = MutableStateFlow<NetworkStatus?>(null)
    override val networkStatusFlow: StateFlow<NetworkStatus?> = _networkStatusFlow

    override fun setNetworkStatus(event: NetworkStatus) {
        _networkStatusFlow.value = event
    }

    override val isWifiConnected: Boolean
        get() = _networkStatusFlow.value?.wifiConnected == true

    override val isKnownNetworkStatus: Boolean
        get() = _networkStatusFlow.value != null

    override val isConnected: Boolean
        get() = _networkStatusFlow.value?.let { it.wifiConnected || it.mobileConnected } == true

    override fun updateNetworkStatus() {
        context.sendBroadcast(Intent(context, NetworkChangeReceiver::class.java))
    }

    private val _chargingStatusFlow = MutableStateFlow<ChargingStatus?>(null)
    override val chargingStatusFlow: StateFlow<ChargingStatus?> = _chargingStatusFlow

    override fun setChargingStatus(event: ChargingStatus) {
        _chargingStatusFlow.value = event
    }

    override val isCharging: Boolean
        get() = _chargingStatusFlow.value?.isCharging == true

    override val batteryLevel: Int
        get() = _chargingStatusFlow.value?.batteryLevel ?: 0
}
