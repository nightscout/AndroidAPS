package info.nightscout.androidaps.plugins.NSClientInternal;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.wifi.WifiManager;

import com.squareup.otto.Bus;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.events.EventChargingState;
import info.nightscout.androidaps.events.EventNetworkChange;
import info.nightscout.androidaps.events.EventPreferenceChange;
import info.nightscout.androidaps.receivers.ChargingStateReceiver;
import info.nightscout.androidaps.receivers.NetworkChangeReceiver;
import info.nightscout.utils.SP;

class NsClientReceiverDelegate {

    private final Context context;
    private final Bus bus;

    private NetworkChangeReceiver networkChangeReceiver = new NetworkChangeReceiver();
    private ChargingStateReceiver chargingStateReceiver = new ChargingStateReceiver();

    private boolean allowedChargingState = true;
    private boolean allowedNetworkState = true;
    boolean allowed = true;

    NsClientReceiverDelegate(Context context, Bus bus) {
        this.context = context;
        this.bus = bus;
    }

    void registerReceivers() {
        Context context = MainApp.instance().getApplicationContext();
        // register NetworkChangeReceiver --> https://developer.android.com/training/monitoring-device-state/connectivity-monitoring.html
        // Nougat is not providing Connectivity-Action anymore ;-(
        context.registerReceiver(networkChangeReceiver,
                new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
        context.registerReceiver(networkChangeReceiver,
                new IntentFilter(WifiManager.WIFI_STATE_CHANGED_ACTION));

        EventNetworkChange event = networkChangeReceiver.grabNetworkStatus(context);
        if (event != null)
            bus.post(event);

        context.registerReceiver(chargingStateReceiver,
                new IntentFilter(Intent.ACTION_POWER_CONNECTED));
        context.registerReceiver(chargingStateReceiver,
                new IntentFilter(Intent.ACTION_POWER_DISCONNECTED));

        EventChargingState eventChargingState = chargingStateReceiver.grabChargingState(context);
        if (eventChargingState != null)
            bus.post(eventChargingState);

    }

    void unregisterReceivers() {
        context.unregisterReceiver(networkChangeReceiver);
        context.unregisterReceiver(chargingStateReceiver);
    }

    void onStatusEvent(EventPreferenceChange ev) {
        if (ev.isChanged(R.string.key_ns_wifionly) ||
                ev.isChanged(R.string.key_ns_wifi_ssids) ||
                ev.isChanged(R.string.key_ns_allowroaming)
                ) {
            EventNetworkChange event = networkChangeReceiver.grabNetworkStatus(MainApp.instance().getApplicationContext());
            if (event != null)
                bus.post(event);
        } else if (ev.isChanged(R.string.key_ns_chargingonly)) {
            EventChargingState event = chargingStateReceiver.grabChargingState(MainApp.instance().getApplicationContext());
            if (event != null)
                bus.post(event);
        }
    }

    void onStatusEvent(final EventChargingState ev) {
        boolean newChargingState = calculateStatus(ev);

        if (newChargingState != allowedChargingState) {
            allowedChargingState = newChargingState;
            processStateChange();
        }
    }

    void onStatusEvent(final EventNetworkChange ev) {
        boolean newNetworkState = calculateStatus(ev);

        if (newNetworkState != allowedNetworkState) {
            allowedNetworkState = newNetworkState;
            processStateChange();
        }
    }

    void processStateChange() {
        boolean newAllowedState =  allowedChargingState && allowedNetworkState;
        if (newAllowedState != allowed) {
            allowed = newAllowedState;
            bus.post(new EventPreferenceChange(R.string.key_nsclientinternal_paused));
        }
    }

    boolean calculateStatus(final EventChargingState ev) {
        boolean chargingOnly = SP.getBoolean(R.string.key_ns_chargingonly, false);

        boolean newAllowedState = true;

        if (!ev.isCharging && chargingOnly) newAllowedState = false;

        return newAllowedState;
    }

    boolean calculateStatus(final EventNetworkChange ev) {
        boolean wifiOnly = SP.getBoolean(R.string.key_ns_wifionly, false);
        String allowedSSIDs = SP.getString(R.string.key_ns_wifi_ssids, "");
        boolean allowRoaming = SP.getBoolean(R.string.key_ns_allowroaming, true);

        boolean newAllowedState = true;

        if (ev.wifiConnected) {
            if (!allowedSSIDs.trim().isEmpty() && !allowedSSIDs.contains(ev.ssid)) {
                newAllowedState = false;
            }
        } else {
            if ((!allowRoaming && ev.roaming) || wifiOnly) {
                newAllowedState = false;
            }
        }


        return newAllowedState;
    }

}
