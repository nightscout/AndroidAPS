package info.nightscout.androidaps.plugins.general.nsclient;

import java.util.Arrays;

import javax.inject.Inject;
import javax.inject.Singleton;

import info.nightscout.androidaps.R;
import info.nightscout.androidaps.events.EventChargingState;
import info.nightscout.androidaps.events.EventNetworkChange;
import info.nightscout.androidaps.events.EventPreferenceChange;
import info.nightscout.androidaps.plugins.bus.RxBusWrapper;
import info.nightscout.androidaps.receivers.ReceiverStatusStore;
import info.nightscout.androidaps.utils.resources.ResourceHelper;
import info.nightscout.androidaps.utils.sharedPreferences.SP;

@Singleton
class NsClientReceiverDelegate {

    private boolean allowedChargingState = true;
    private boolean allowedNetworkState = true;
    boolean allowed = true;

    private RxBusWrapper rxBus;
    private ResourceHelper resourceHelper;
    private SP sp;
    private ReceiverStatusStore receiverStatusStore;

    @Inject
    public NsClientReceiverDelegate(
            RxBusWrapper rxBus,
            ResourceHelper resourceHelper,
            SP sp,
            ReceiverStatusStore receiverStatusStore
    ) {
        this.rxBus = rxBus;
        this.resourceHelper = resourceHelper;
        this.sp = sp;
        this.receiverStatusStore = receiverStatusStore;
    }

    void grabReceiversState() {

        receiverStatusStore.updateNetworkStatus();
    }

    void onStatusEvent(EventPreferenceChange ev) {
        if (ev.isChanged(resourceHelper, R.string.key_ns_wifionly) ||
                ev.isChanged(resourceHelper, R.string.key_ns_wifi_ssids) ||
                ev.isChanged(resourceHelper, R.string.key_ns_allowroaming)
        ) {
            receiverStatusStore.updateNetworkStatus();
            onStatusEvent(receiverStatusStore.getLastNetworkEvent());
        } else if (ev.isChanged(resourceHelper, R.string.key_ns_chargingonly)) {
            receiverStatusStore.broadcastChargingState();
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

    private void processStateChange() {
        boolean newAllowedState = allowedChargingState && allowedNetworkState;
        if (newAllowedState != allowed) {
            allowed = newAllowedState;
            rxBus.send(new EventPreferenceChange(resourceHelper.gs(R.string.key_nsclientinternal_paused)));
        }
    }

    boolean calculateStatus(final EventChargingState ev) {
        boolean chargingOnly = sp.getBoolean(R.string.key_ns_chargingonly, false);
        boolean newAllowedState = true;

        if (!ev.isCharging() && chargingOnly) {
            newAllowedState = false;
        }

        return newAllowedState;
    }

    boolean calculateStatus(final EventNetworkChange ev) {
        boolean wifiOnly = sp.getBoolean(R.string.key_ns_wifionly, false);
        String allowedSSIDstring = sp.getString(R.string.key_ns_wifi_ssids, "");
        String[] allowedSSIDs = allowedSSIDstring.split(";");
        if (allowedSSIDstring.isEmpty()) allowedSSIDs = new String[0];
        boolean allowRoaming = sp.getBoolean(R.string.key_ns_allowroaming, true);

        boolean newAllowedState = true;

        if (ev.getWifiConnected()) {
            if (allowedSSIDs.length != 0 && !Arrays.asList(allowedSSIDs).contains(ev.getSsid())) {
                newAllowedState = false;
            }
        } else {
            if ((!allowRoaming && ev.getRoaming()) || wifiOnly) {
                newAllowedState = false;
            }
        }

        return newAllowedState;
    }
}
