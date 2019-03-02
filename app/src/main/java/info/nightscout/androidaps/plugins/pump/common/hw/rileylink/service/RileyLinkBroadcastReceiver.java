package info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service;

/**
 * Created by andy on 10/23/18.
 */

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.support.v4.content.LocalBroadcastManager;

import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.RileyLinkConst;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.RileyLinkUtil;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.defs.RileyLinkFirmwareVersion;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.RileyLinkError;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.RileyLinkServiceState;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service.tasks.DiscoverGattServicesTask;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service.tasks.InitializePumpManagerTask;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service.tasks.ServiceTask;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service.tasks.ServiceTaskExecutor;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service.tasks.WakeAndTuneTask;
import info.nightscout.androidaps.utils.SP;

/**
 * I added this class outside of RileyLinkService, because for now it's very important part of RL framework and
 * where we get a lot of problems. Especially merging between AAPS and RileyLinkAAPS. I might put it back at
 * later time
 */
public class RileyLinkBroadcastReceiver extends BroadcastReceiver {

    private static final Logger LOG = LoggerFactory.getLogger(RileyLinkBroadcastReceiver.class);

    RileyLinkService serviceInstance;
    // protected RileyLinkIPCConnection rileyLinkIPCConnection;
    protected Map<String, List<String>> broadcastIdentifiers = null;
    String deviceSpecificPrefix;
    Context context;


    public RileyLinkBroadcastReceiver(RileyLinkService serviceInstance, Context context) {
        this.serviceInstance = serviceInstance;
        this.context = context;

        // TODO remove in AAPS -- Andy
        // rileyLinkIPCConnection = new RileyLinkIPCConnection(context);
        // RileyLinkUtil.setRileyLinkIPCConnection(rileyLinkIPCConnection);

        createBroadcastIdentifiers();
    }


    private void createBroadcastIdentifiers() {

        this.broadcastIdentifiers = new HashMap<>();

        // Bluetooth
        this.broadcastIdentifiers.put("Bluetooth", Arrays.asList( //
            RileyLinkConst.Intents.BluetoothConnected, //
            RileyLinkConst.Intents.BluetoothReconnected, //
            RileyLinkConst.Intents.BluetoothReconnected));

        // TuneUp
        this.broadcastIdentifiers.put("TuneUp", Arrays.asList( //
            // RT2Const.IPC.MSG_PUMP_tunePump, //
            // RT2Const.IPC.MSG_PUMP_quickTune, //
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
        deviceSpecificPrefix = serviceInstance.getDeviceSpecificBroadcastsIdentifierPrefix();

        // Application specific
        // this.broadcastIdentifiers.put("AppSpecific", Arrays.asList( //
        // RT2Const.serviceLocal.ipcBound, //
        // RT2Const.IPC.MSG_ServiceCommand, //
        // RT2Const.serviceLocal.INTENT_sessionCompleted));

    }


    @Override
    public void onReceive(Context context, Intent intent) {

        if (intent == null) {
            LOG.error("onReceive: received null intent");
        } else {
            String action = intent.getAction();
            if (action == null) {
                LOG.error("onReceive: null action");
            } else {
                LOG.debug("Received Broadcast: " + action);

                if (!processBluetoothBroadcasts(action) && //
                    !processRileyLinkBroadcasts(action) && //
                    !processTuneUpBroadcasts(action) && //
                    !processDeviceSpecificBroadcasts(action, intent) && //
                    !processApplicationSpecificBroadcasts(action, intent) //
                ) {
                    LOG.error("Unhandled broadcast: action=" + action);
                }
            }
        }
    }


    public void registerBroadcasts() {

        IntentFilter intentFilter = new IntentFilter();

        for (Map.Entry<String, List<String>> stringListEntry : broadcastIdentifiers.entrySet()) {

            for (String intentKey : stringListEntry.getValue()) {
                System.out.println("Intent: " + intentKey);
                intentFilter.addAction(intentKey);
            }
        }

        if (deviceSpecificPrefix != null) {
            serviceInstance.registerDeviceSpecificBroadcasts(intentFilter);
        }

        LocalBroadcastManager.getInstance(context).registerReceiver(this, intentFilter);

    }


    private boolean processRileyLinkBroadcasts(String action) {

        if (action.equals(RileyLinkConst.Intents.RileyLinkDisconnected)) {
            if (BluetoothAdapter.getDefaultAdapter().isEnabled()) {
                RileyLinkUtil
                    .setServiceState(RileyLinkServiceState.BluetoothError, RileyLinkError.RileyLinkUnreachable);
            } else {
                RileyLinkUtil.setServiceState(RileyLinkServiceState.BluetoothError, RileyLinkError.BluetoothDisabled);
            }

            return true;
        } else if (action.equals(RileyLinkConst.Intents.RileyLinkReady)) {
            LOG.warn("MedtronicConst.Intents.RileyLinkReady");
            // sendIPCNotification(RT2Const.IPC.MSG_note_WakingPump);
            this.serviceInstance.rileyLinkBLE.enableNotifications();
            this.serviceInstance.rfspy.startReader(); // call startReader from outside?

            this.serviceInstance.rfspy.initializeRileyLink();
            String bleVersion = this.serviceInstance.rfspy.getBLEVersionCached();
            RileyLinkFirmwareVersion rlVersion = this.serviceInstance.rfspy.getRLVersionCached();

            LOG.debug("RfSpy version (BLE113): " + bleVersion);
            this.serviceInstance.rileyLinkServiceData.versionBLE113 = bleVersion;

            LOG.debug("RfSpy Radio version (CC110): " + rlVersion.name());
            this.serviceInstance.rileyLinkServiceData.versionCC110 = rlVersion;

            ServiceTask task = new InitializePumpManagerTask(RileyLinkUtil.getTargetDevice());
            ServiceTaskExecutor.startTask(task);
            LOG.info("Announcing RileyLink open For business");

            return true;
        } else if (action.equals(RileyLinkConst.Intents.RileyLinkNewAddressSet)) {
            String RileylinkBLEAddress = SP.getString(RileyLinkConst.Prefs.RileyLinkAddress, "");
            if (RileylinkBLEAddress.equals("")) {
                LOG.error("No Rileylink BLE Address saved in app");
            } else {
                // showBusy("Configuring Service", 50);
                // rileyLinkBLE.findRileyLink(RileylinkBLEAddress);
                this.serviceInstance.reconfigureRileyLink(RileylinkBLEAddress);
                // MainApp.getServiceClientConnection().setThisRileylink(RileylinkBLEAddress);
            }

            return true;
        } else if (action.equals(RileyLinkConst.Intents.RileyLinkDisconnect)) {
            this.serviceInstance.disconnectRileyLink();

            return true;
        } else {
            return false;
        }

    }


    public boolean processBluetoothBroadcasts(String action) {

        if (action.equals(RileyLinkConst.Intents.BluetoothConnected)) {
            LOG.debug("Bluetooth - Connected");
            // sendIPCNotification(RT2Const.IPC.MSG_note_FindingRileyLink);
            ServiceTaskExecutor.startTask(new DiscoverGattServicesTask());

            return true;

        } else if (action.equals(RileyLinkConst.Intents.BluetoothReconnected)) {
            LOG.debug("Bluetooth - Reconnecting");
            // sendIPCNotification(RT2Const.IPC.MSG_note_FindingRileyLink);
            serviceInstance.bluetoothInit();
            ServiceTaskExecutor.startTask(new DiscoverGattServicesTask(true));

            return true;
        } else if (action.equals(RileyLinkConst.Intents.BluetoothReconnected)) {
            LOG.debug("Bluetooth - Reconnected");
            // sendIPCNotification(RT2Const.IPC.MSG_note_FindingRileyLink);
            serviceInstance.bluetoothInit();
            ServiceTaskExecutor.startTask(new DiscoverGattServicesTask(true));

            return true;
        } else {

            return false;
        }

    }


    private boolean processTuneUpBroadcasts(String action) {

        if (this.broadcastIdentifiers.get("TuneUp").contains(action)) {
            if (serviceInstance.getRileyLinkTargetDevice().isTuneUpEnabled()) {
                ServiceTaskExecutor.startTask(new WakeAndTuneTask());
            }
            return true;
        } else {
            return false;
        }
    }


    public boolean processDeviceSpecificBroadcasts(String action, Intent intent) {

        if (this.deviceSpecificPrefix == null) {
            return false;
        }

        if (action.startsWith(this.deviceSpecificPrefix)) {
            return this.serviceInstance.handleDeviceSpecificBroadcasts(intent);
        } else
            return false;
    }


    public boolean processApplicationSpecificBroadcasts(String action, Intent intent) {
        // if (action.equals(RT2Const.serviceLocal.ipcBound)) {
        // // If we still need permission for bluetooth, ask now.
        // // if (needBluetoothPermission) {
        // // sendBLERequestForAccess();
        // // }
        // return true;
        // } else if (RT2Const.IPC.MSG_ServiceCommand.equals(action)) {
        // serviceInstance.handleIncomingServiceTransport(intent);
        // return true;
        // } else if (RT2Const.serviceLocal.INTENT_sessionCompleted.equals(action)) {
        // Bundle bundle = intent.getBundleExtra(RT2Const.IPC.bundleKey);
        // if (bundle != null) {
        // ServiceTransport transport = new ServiceTransport(bundle);
        // rileyLinkIPCConnection.sendTransport(transport, transport.getSenderHashcode());
        // } else {
        // LOG.error("sessionCompleted: no bundle!");
        // }
        // return true;
        // } else {
        // return false;
        // }
        return false;
    }


    public void sendIPCNotification(String notification) {
        // rileyLinkIPCConnection.sendNotification(new ServiceNotification(notification), null);
    }

}
