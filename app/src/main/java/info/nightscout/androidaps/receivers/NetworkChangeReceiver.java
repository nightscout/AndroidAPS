package info.nightscout.androidaps.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.support.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.events.EventNetworkChange;

public class NetworkChangeReceiver extends BroadcastReceiver {

    private static Logger log = LoggerFactory.getLogger(NetworkChangeReceiver.class);

    @Override
    public void onReceive(final Context context, final Intent intent) {
        EventNetworkChange event = grabNetworkStatus(context);
        if (event != null)
            MainApp.bus().post(event);
    }

    @Nullable
    public EventNetworkChange grabNetworkStatus(final Context context) {
        EventNetworkChange event = new EventNetworkChange();

        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return null;
        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();

        if (activeNetwork != null) {
            if (activeNetwork.getType() == ConnectivityManager.TYPE_WIFI) {
                event.wifiConnected = true;
                WifiManager wifiManager = (WifiManager) MainApp.instance().getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                if (wifiManager != null) {
                    WifiInfo wifiInfo = wifiManager.getConnectionInfo();
                    if (wifiInfo.getSupplicantState() == SupplicantState.COMPLETED) {
                        event.ssid = wifiInfo.getSSID();
                    }
                    log.debug("NETCHANGE: Wifi connected. SSID: " + event.ssid);
                }
            }

            if (activeNetwork.getType() == ConnectivityManager.TYPE_MOBILE) {
                event.mobileConnected = true;
                event.roaming = activeNetwork.isRoaming();
                log.debug("NETCHANGE: Mobile connected. Roaming: " + event.roaming);
            }
        } else {
            log.debug("NETCHANGE: Disconnected.");
        }

        return event;
    }
}