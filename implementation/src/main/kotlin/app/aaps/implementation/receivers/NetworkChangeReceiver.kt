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
import app.aaps.core.interfaces.receivers.ReceiverStatusStore.NetworkStatus
import app.aaps.core.utils.receivers.StringUtils
import dagger.android.DaggerBroadcastReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

class NetworkChangeReceiver : DaggerBroadcastReceiver() {

    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var receiverStatusStore: ReceiverStatusStore

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        CoroutineScope(Dispatchers.IO).launch {
            grabNetworkStatus(context)
        }
    }

    @Suppress("DEPRECATION")
    private fun grabNetworkStatus(context: Context) {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        var wifiConnected = false
        var mobileConnected = false
        var vpnConnected = false
        var ssid = ""
        var roaming = false
        var metered = false

        val networks: Array<Network> = cm.allNetworks
        networks.forEach {
            val capabilities = cm.getNetworkCapabilities(it) ?: return@forEach
            wifiConnected = wifiConnected || capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                || capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
            mobileConnected = mobileConnected || capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
            vpnConnected = vpnConnected || capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
            if (wifiConnected) {
                val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                val wifiInfo = wifiManager.connectionInfo
                if (wifiInfo.supplicantState == SupplicantState.COMPLETED) {
                    ssid = StringUtils.removeSurroundingQuotes(wifiInfo.ssid)
                }
            }
            if (mobileConnected) {
                roaming = roaming || !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING)
                metered = metered || !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
                aapsLogger.debug(LTag.CORE, "NETCHANGE: Mobile connected. Roaming: $roaming Metered: $metered")
            }
        }

        val status = NetworkStatus(mobileConnected, wifiConnected, vpnConnected, ssid, roaming, metered)
        aapsLogger.debug(LTag.CORE, status.toString())
        receiverStatusStore.setNetworkStatus(status)
    }
}
