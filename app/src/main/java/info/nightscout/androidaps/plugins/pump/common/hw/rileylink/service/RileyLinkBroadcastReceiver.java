package info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service;

/**
 * Created by andy on 10/23/18.
 */

import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;

import dagger.android.DaggerBroadcastReceiver;
import dagger.android.HasAndroidInjector;
import info.nightscout.androidaps.interfaces.ActivePluginProvider;
import info.nightscout.androidaps.logging.AAPSLogger;
import info.nightscout.androidaps.logging.LTag;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.RileyLinkConst;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.defs.RileyLinkFirmwareVersion;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.RileyLinkError;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.RileyLinkPumpDevice;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.RileyLinkServiceState;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service.tasks.DiscoverGattServicesTask;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service.tasks.InitializePumpManagerTask;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service.tasks.ServiceTask;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service.tasks.ServiceTaskExecutor;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service.tasks.WakeAndTuneTask;
import info.nightscout.androidaps.utils.sharedPreferences.SP;

/**
 * I added this class outside of RileyLinkService, because for now it's very important part of RL framework and
 * where we get a lot of problems. Especially merging between AAPS and RileyLinkAAPS. I might put it back at
 * later time
 */
public class RileyLinkBroadcastReceiver extends DaggerBroadcastReceiver {

    @Inject HasAndroidInjector injector;
    @Inject SP sp;
    @Inject AAPSLogger aapsLogger;
    @Inject RileyLinkServiceData rileyLinkServiceData;
    @Inject ServiceTaskExecutor serviceTaskExecutor;
    @Inject ActivePluginProvider activePlugin;

    RileyLinkService serviceInstance;
    protected Map<String, List<String>> broadcastIdentifiers = null;
    //String deviceSpecificPrefix;

    public RileyLinkBroadcastReceiver(RileyLinkService serviceInstance) {
        this.serviceInstance = serviceInstance;

        createBroadcastIdentifiers();
    }


    private void createBroadcastIdentifiers() {

        this.broadcastIdentifiers = new HashMap<>();

        // Bluetooth
        this.broadcastIdentifiers.put("Bluetooth", Arrays.asList( //
                RileyLinkConst.Intents.BluetoothConnected, //
                RileyLinkConst.Intents.BluetoothReconnected));

        // TuneUp
        this.broadcastIdentifiers.put("TuneUp", Arrays.asList( //
                RileyLinkConst.IPC.MSG_PUMP_tunePump, //
                RileyLinkConst.IPC.MSG_PUMP_quickTune));

        // RileyLink
        this.broadcastIdentifiers.put("RileyLink", Arrays.asList( //
                RileyLinkConst.Intents.RileyLinkDisconnected, //
                RileyLinkConst.Intents.RileyLinkReady, //
                RileyLinkConst.Intents.RileyLinkDisconnected, //
                RileyLinkConst.Intents.RileyLinkNewAddressSet, //
                RileyLinkConst.Intents.RileyLinkDisconnect));

        // Device Specific
        // TODO remove from service instance
        //deviceSpecificPrefix = serviceInstance.getDeviceSpecificBroadcastsIdentifierPrefix();

        // Application specific

    }

    private RileyLinkService getServiceInstance() {
        RileyLinkPumpDevice pumpDevice = (RileyLinkPumpDevice)activePlugin.getActivePump();
        return pumpDevice.getRileyLinkService();
    }


    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);

        if (intent == null) {
            aapsLogger.error(LTag.PUMPCOMM, "onReceive: received null intent");
        } else {
            String action = intent.getAction();
            if (action == null) {
                aapsLogger.error("onReceive: null action");
            } else {
                aapsLogger.debug(LTag.PUMPCOMM, "Received Broadcast: " + action);

                if (!processBluetoothBroadcasts(action) && //
                        !processRileyLinkBroadcasts(action, context) && //
                        !processTuneUpBroadcasts(action) && //
                        !processApplicationSpecificBroadcasts(action, intent) //
                ) {
                    aapsLogger.error(LTag.PUMPCOMM, "Unhandled broadcast: action=" + action);
                }
            }
        }
    }


    public void registerBroadcasts(Context context) {

        IntentFilter intentFilter = new IntentFilter();

        for (Map.Entry<String, List<String>> stringListEntry : broadcastIdentifiers.entrySet()) {

            for (String intentKey : stringListEntry.getValue()) {
                intentFilter.addAction(intentKey);
            }
        }

        LocalBroadcastManager.getInstance(context).registerReceiver(this, intentFilter);
    }


    private boolean processRileyLinkBroadcasts(String action, Context context) {

        RileyLinkService rileyLinkService = getServiceInstance();

        if (action.equals(RileyLinkConst.Intents.RileyLinkDisconnected)) {
            if (BluetoothAdapter.getDefaultAdapter().isEnabled()) {
                rileyLinkServiceData.setServiceState(RileyLinkServiceState.BluetoothError, RileyLinkError.RileyLinkUnreachable);
            } else {
                rileyLinkServiceData.setServiceState(RileyLinkServiceState.BluetoothError, RileyLinkError.BluetoothDisabled);
            }

            return true;
        } else if (action.equals(RileyLinkConst.Intents.RileyLinkReady)) {

            aapsLogger.warn(LTag.PUMPCOMM, "RileyLinkConst.Intents.RileyLinkReady");
            // sendIPCNotification(RT2Const.IPC.MSG_note_WakingPump);

            rileyLinkService.rileyLinkBLE.enableNotifications();
            rileyLinkService.rfspy.startReader(); // call startReader from outside?

            rileyLinkService.rfspy.initializeRileyLink();
            String bleVersion = rileyLinkService.rfspy.getBLEVersionCached();
            RileyLinkFirmwareVersion rlVersion = rileyLinkServiceData.firmwareVersion;

//            if (isLoggingEnabled())
            aapsLogger.debug(LTag.PUMPCOMM, "RfSpy version (BLE113): " + bleVersion);
            rileyLinkService.rileyLinkServiceData.versionBLE113 = bleVersion;

//            if (isLoggingEnabled())
            aapsLogger.debug(LTag.PUMPCOMM, "RfSpy Radio version (CC110): " + rlVersion.name());
            this.rileyLinkServiceData.versionCC110 = rlVersion;

            ServiceTask task = new InitializePumpManagerTask(injector, context);
            serviceTaskExecutor.startTask(task);
            aapsLogger.info(LTag.PUMPCOMM, "Announcing RileyLink open For business");

            return true;
        } else if (action.equals(RileyLinkConst.Intents.RileyLinkNewAddressSet)) {
            String RileylinkBLEAddress = sp.getString(RileyLinkConst.Prefs.RileyLinkAddress, "");
            if (RileylinkBLEAddress.equals("")) {
                aapsLogger.error("No Rileylink BLE Address saved in app");
            } else {
                // showBusy("Configuring Service", 50);
                // rileyLinkBLE.findRileyLink(RileylinkBLEAddress);
                rileyLinkService.reconfigureRileyLink(RileylinkBLEAddress);
                // MainApp.getServiceClientConnection().setThisRileylink(RileylinkBLEAddress);
            }

            return true;
        } else if (action.equals(RileyLinkConst.Intents.RileyLinkDisconnect)) {
            rileyLinkService.disconnectRileyLink();

            return true;
        } else {
            return false;
        }

    }


    public boolean processBluetoothBroadcasts(String action) {

        if (action.equals(RileyLinkConst.Intents.BluetoothConnected)) {
            aapsLogger.debug(LTag.PUMPCOMM, "Bluetooth - Connected");
            serviceTaskExecutor.startTask(new DiscoverGattServicesTask(injector));

            return true;

        } else if (action.equals(RileyLinkConst.Intents.BluetoothReconnected)) {
            aapsLogger.debug(LTag.PUMPCOMM, "Bluetooth - Reconnecting");

            getServiceInstance().bluetoothInit();
            serviceTaskExecutor.startTask(new DiscoverGattServicesTask(injector, true));

            return true;
        } else {

            return false;
        }
    }


    private boolean processTuneUpBroadcasts(String action) {

        if (this.broadcastIdentifiers.get("TuneUp").contains(action)) {
            if (serviceInstance.getRileyLinkTargetDevice().isTuneUpEnabled()) {
                serviceTaskExecutor.startTask(new WakeAndTuneTask(injector));
            }
            return true;
        } else {
            return false;
        }
    }


    public boolean processApplicationSpecificBroadcasts(String action, Intent intent) {
        return false;
    }

    public void unregisterBroadcasts(Context context) {
        LocalBroadcastManager.getInstance(context).unregisterReceiver(this);
    }
}
