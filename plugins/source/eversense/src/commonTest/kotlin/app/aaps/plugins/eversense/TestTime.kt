package app.aaps.plugins.eversense

import app.aaps.core.interfaces.receivers.ReceiverStatusStore
import app.aaps.core.interfaces.receivers.ReceiverStatusStore.ChargingStatus
import app.aaps.core.interfaces.receivers.ReceiverStatusStore.NetworkStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent

/** Moves virtual time on by [millis] and runs what is due at the new time too. */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun TestScope.advance(millis: Long) {
    advanceTimeBy(millis)
    runCurrent()
}

/** The phone's network, online unless a test says otherwise; nothing else of the receiver state matters to the driver. */
internal class FakeNetwork(var online: Boolean = true) : ReceiverStatusStore {

    override val networkStatusFlow: StateFlow<NetworkStatus?> = MutableStateFlow(null)
    override fun setNetworkStatus(event: NetworkStatus) {}
    override val isWifiConnected: Boolean get() = online
    override val isKnownNetworkStatus: Boolean get() = true
    override val isConnected: Boolean get() = online
    override fun updateNetworkStatus() {}
    override val chargingStatusFlow: StateFlow<ChargingStatus?> = MutableStateFlow(null)
    override fun setChargingStatus(event: ChargingStatus) {}
    override val isCharging: Boolean get() = false
    override val batteryLevel: Int get() = 100
}
