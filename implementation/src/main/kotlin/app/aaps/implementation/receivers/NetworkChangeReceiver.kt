package app.aaps.implementation.receivers

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.SupplicantState
import android.net.wifi.WifiManager
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.receivers.ReceiverStatusStore
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventNetworkChange
import app.aaps.core.utils.receivers.StringUtils
import dagger.android.DaggerBroadcastReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

class NetworkChangeReceiver : DaggerBroadcastReceiver() {

    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var receiverStatusStore: ReceiverStatusStore

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        CoroutineScope(Dispatchers.IO).launch {
            rxBus.send(grabNetworkStatus(context))
        }
    }

    @Suppress("DEPRECATION")
    private fun grabNetworkStatus(context: Context): EventNetworkChange {
        val event = EventNetworkChange()
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networks: Array<Network> = cm.allNetworks
        networks.forEach {
            val capabilities = cm.getNetworkCapabilities(it) ?: return@forEach
            event.wifiConnected = event.wifiConnected || (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                || capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))
            event.mobileConnected = event.mobileConnected || capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
            event.vpnConnected = event.vpnConnected || capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
            // if (event.vpnConnected) aapsLogger.debug(LTag.CORE, "NETCHANGE: VPN connected.")
            if (event.wifiConnected) {
                val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                val wifiInfo = wifiManager.connectionInfo
                if (wifiInfo.supplicantState == SupplicantState.COMPLETED) {
                    event.ssid = StringUtils.removeSurroundingQuotes(wifiInfo.ssid)
                    // aapsLogger.debug(LTag.CORE, "NETCHANGE: Wifi connected. SSID: ${event.connectedSsid()}")
                }
            }
            if (event.mobileConnected) {
                event.mobileConnected = true
                event.roaming = event.roaming || !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING)
                event.metered = event.metered || !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
                aapsLogger.debug(LTag.CORE, "NETCHANGE: Mobile connected. Roaming: ${event.roaming} Metered: ${event.metered}")
            }
            // aapsLogger.info(LTag.CORE, "Network: $it")
        }

        aapsLogger.debug(LTag.CORE, event.toString())
        receiverStatusStore.lastNetworkEvent = event
        return event
    }
}