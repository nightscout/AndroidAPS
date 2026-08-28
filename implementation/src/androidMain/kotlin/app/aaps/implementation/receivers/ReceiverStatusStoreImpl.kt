package app.aaps.implementation.receivers

import android.content.Context
import android.content.Intent
import app.aaps.core.interfaces.receivers.ReceiverStatusStore
import app.aaps.core.interfaces.receivers.ReceiverStatusStore.ChargingStatus
import app.aaps.core.interfaces.receivers.ReceiverStatusStore.NetworkStatus
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

// Metro builds this now; Dagger gets it through a @Provides delegate in `:app`. Scoped with Metro's
// @SingleIn, not javax @Singleton - the graph is generated in `:app`, which has no Dagger interop, so
// a javax scope there is ignored and every read would build a new one.
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
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
