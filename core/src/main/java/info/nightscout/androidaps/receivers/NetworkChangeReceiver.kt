package info.nightscout.androidaps.receivers

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.SupplicantState
import android.net.wifi.WifiManager
import dagger.android.DaggerBroadcastReceiver
import info.nightscout.androidaps.events.EventNetworkChange
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.utils.StringUtils
import javax.inject.Inject

class NetworkChangeReceiver : DaggerBroadcastReceiver() {
    @Inject lateinit var rxBus: RxBusWrapper
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var receiverStatusStore: ReceiverStatusStore

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        rxBus.send(grabNetworkStatus(context, aapsLogger))
    }

    fun grabNetworkStatus(context: Context, aapsLogger: AAPSLogger): EventNetworkChange {
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
