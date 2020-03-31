package info.nightscout.androidaps.receivers

import android.content.Context
import android.content.Intent
import info.nightscout.androidaps.events.EventNetworkChange
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReceiverStatusStore @Inject constructor(val context: Context) {

    var lastNetworkEvent: EventNetworkChange? = null

    val isWifiConnected: Boolean
        get() = lastNetworkEvent?.wifiConnected ?: false

    val isConnected: Boolean
        get() = lastNetworkEvent?.wifiConnected ?: false || lastNetworkEvent?.mobileConnected ?: false

    fun updateNetworkStatus() {
        context.sendBroadcast(Intent(context, NetworkChangeReceiver::class.java))
    }
}