package info.nightscout.androidaps.plugins.PumpCommon.hw.rileylink.service;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.support.v4.content.LocalBroadcastManager;
import android.widget.Toast;

import com.gxwtech.roundtrip2.RT2Const;
import com.gxwtech.roundtrip2.RoundtripService.Tasks.InitializePumpManagerTask;
import com.gxwtech.roundtrip2.RoundtripService.Tasks.ServiceTask;
import com.gxwtech.roundtrip2.RoundtripService.Tasks.ServiceTaskExecutor;
import com.gxwtech.roundtrip2.ServiceData.ServiceResult;
import com.gxwtech.roundtrip2.ServiceData.ServiceTransport;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.GregorianCalendar;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.plugins.PumpCommon.hw.rileylink.RileyLinkCommunicationManager;
import info.nightscout.androidaps.plugins.PumpCommon.hw.rileylink.RileyLinkConst;
import info.nightscout.androidaps.plugins.PumpCommon.hw.rileylink.RileyLinkUtil;
import info.nightscout.androidaps.plugins.PumpCommon.hw.rileylink.ble.RFSpy;
import info.nightscout.androidaps.plugins.PumpCommon.hw.rileylink.ble.RileyLinkBLE;
import info.nightscout.androidaps.plugins.PumpCommon.hw.rileylink.data.RLHistoryItem;
import info.nightscout.androidaps.plugins.PumpMedtronic.util.MedtronicConst;
import info.nightscout.utils.SP;


/**
 * Created by andy on 5/6/18.
 * Split from original file and renamed.
 */
public abstract class RileyLinkService extends Service {

    private static final Logger LOG = LoggerFactory.getLogger(RileyLinkService.class);


    protected BluetoothAdapter bluetoothAdapter;

    // Our hardware/software connection
    public RileyLinkBLE rileyLinkBLE; // android-bluetooth management
    protected RFSpy rfspy; // interface for xxx Mhz (916MHz) radio.
    protected boolean needBluetoothPermission = true;
    //protected RileyLinkIPCConnection rileyLinkIPCConnection;
    protected Context context;
    public RileyLinkCommunicationManager pumpCommunicationManager;
    protected BroadcastReceiver mBroadcastReceiver;

    protected RileyLinkServiceData rileyLinkServiceData;

    //protected static final String WAKELOCKNAME = "com.gxwtech.roundtrip2.RoundtripServiceWakeLock";
    //protected static volatile PowerManager.WakeLock lockStatic = null;


    public RileyLinkService() {
        super();
        this.context = MainApp.instance().getApplicationContext();
        RileyLinkUtil.setContext(this.context);
        initRileyLinkServiceData();
    }


    /**
     * If you have customized RileyLinkServiceData you need to override this
     */
    public abstract void initRileyLinkServiceData();
//    {
//        rileyLinkServiceData = new RileyLinkServiceData();
//    }


    @Override
    public boolean onUnbind(Intent intent) {
        LOG.warn("onUnbind");
        return super.onUnbind(intent);
    }


    @Override
    public void onRebind(Intent intent) {
        LOG.warn("onRebind");
        super.onRebind(intent);
    }


    @Override
    public void onDestroy() {
        super.onDestroy();
        LOG.error("I die! I die!");

        if (rileyLinkBLE != null) {
            rileyLinkBLE.disconnect(); // dispose of Gatt (disconnect and close)
            rileyLinkBLE = null;
        }
    }


    @Override
    public void onCreate() {
        super.onCreate();
        LOG.debug("onCreate");

        RileyLinkUtil.setRileyLinkServiceData(rileyLinkServiceData);

        //rileyLinkIPCConnection = new RileyLinkIPCConnection(context); // We might be able to remove this -- Andy

        // get most recently used RileyLink address
        rileyLinkServiceData.rileylinkAddress = SP.getString(MedtronicConst.Prefs.RileyLinkAddress, "");

        rileyLinkBLE = new RileyLinkBLE(this);
        rfspy = new RFSpy(context, rileyLinkBLE);
        rfspy.startReader();

        RileyLinkUtil.setRileyLinkBLE(rileyLinkBLE);

        loadPumpCommunicationManager();

        mBroadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
            /* here we can listen for local broadcasts, then send ourselves
             * a specific intent to deal with them, if we wish
              */
                if (intent == null) {
                    LOG.error("onReceive: received null intent");
                } else {
                    String action = intent.getAction();
                    if (action == null) {
                        LOG.error("onReceive: null action");
                    } else {
                        if (action.equals(RileyLinkConst.Intents.BluetoothConnected)) {
                            LOG.warn("serviceLocal.bluetooth_connected");
                            //rileyLinkIPCConnection.sendNotification(new ServiceNotification(RT2Const.IPC.MSG_note_FindingRileyLink), null);
                            // ServiceTaskExecutor.startTask(new DiscoverGattServicesTask());
                            rileyLinkBLE.discoverServices();


                            // If this is successful,
                            // We will get a broadcast of RT2Const.serviceLocal.BLE_services_discovered
                        } else if (action.equals(RileyLinkConst.Intents.RileyLinkReady)) {
                            LOG.warn("MedtronicConst.Intents.RileyLinkReady");
                            // FIXME
                            //rileyLinkIPCConnection.sendNotification(new ServiceNotification(RT2Const.IPC.MSG_note_WakingPump), null);
                            rileyLinkBLE.enableNotifications();
                            rfspy.startReader(); // call startReader from outside?
                            ServiceTask task = new InitializePumpManagerTask();
                            ServiceTaskExecutor.startTask(task);
                            LOG.info("Announcing RileyLink open For business");
                        } else if (action.equals(RT2Const.serviceLocal.ipcBound)) {
                            // If we still need permission for bluetooth, ask now.
                            // FIXME removed Andy - doesn't do anything
//                            if (needBluetoothPermission) {
//                                sendBLERequestForAccess();
//                            }

                        } else if (RT2Const.IPC.MSG_BLE_accessGranted.equals(action)) {
                            //initializeLeAdapter();
                            //bluetoothInit();
                        } else if (RT2Const.IPC.MSG_BLE_accessDenied.equals(action)) {
                            LOG.error("BLE_Access_Denied recived. Stoping the service.");
                            stopSelf(); // This will stop the service.
                        } else if (action.equals(RT2Const.IPC.MSG_PUMP_tunePump)) {
                            doTunePump();
                        } else if (action.equals(RT2Const.IPC.MSG_PUMP_quickTune)) {
                            doTunePump();
                        } else if (action.startsWith("MSG_PUMP_")) {
                            handlePumpSpecificIntents(intent);
                        } else if (RT2Const.IPC.MSG_ServiceCommand.equals(action)) {
                            handleIncomingServiceTransport(intent);
                        }
//                        } else
//                            if (RT2Const.serviceLocal.INTENT_sessionCompleted.equals(action)) {
//                            Bundle bundle = intent.getBundleExtra(RT2Const.IPC.bundleKey);
//                            if (bundle != null) {
//                                ServiceTransport transport = new ServiceTransport(bundle);
//                                rileyLinkIPCConnection.sendTransport(transport, transport.getSenderHashcode());
//                            } else {
//                                LOG.error("sessionCompleted: no bundle!");
//                            }
//                        }
                        else {
                            LOG.error("Unhandled broadcast: action=" + action);
                        }
                    }
                }
            }
        };

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(RileyLinkConst.Intents.BluetoothConnected);
        intentFilter.addAction(RileyLinkConst.Intents.BluetoothDisconnected);
        //intentFilter.addAction(RT2Const.serviceLocal.BLE_services_discovered); AAPS - RileyLinkReady
        intentFilter.addAction(RT2Const.serviceLocal.ipcBound);
        intentFilter.addAction(RT2Const.IPC.MSG_BLE_accessGranted);
        intentFilter.addAction(RT2Const.IPC.MSG_BLE_accessDenied);
        //intentFilter.addAction(RT2Const.IPC.MSG_BLE_useThisDevice);
        intentFilter.addAction(RT2Const.IPC.MSG_PUMP_tunePump);

        //intentFilter.addAction(RT2Const.IPC.MSG_PUMP_useThisAddress);

        intentFilter.addAction(RT2Const.IPC.MSG_ServiceCommand);
        intentFilter.addAction(RT2Const.serviceLocal.INTENT_sessionCompleted);
        // after AAPS refactoring
        intentFilter.addAction(RileyLinkConst.Intents.RileyLinkReady);


        addPumpSpecificIntents(intentFilter);

        LocalBroadcastManager.getInstance(context).registerReceiver(mBroadcastReceiver, intentFilter);

        LOG.debug("onCreate(): It's ALIVE!");
    }


    public abstract void addPumpSpecificIntents(IntentFilter intentFilter);

    public abstract void handlePumpSpecificIntents(Intent intent);

    public abstract void loadPumpCommunicationManager();

    public abstract void handleIncomingServiceTransport(Intent intent);


    // Here is where the wake-lock begins:
    // We've received a service startCommand, we grab the lock.
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        LOG.debug("onStartCommand");
        return START_STICKY;
    }


    protected void bluetoothInit() {
        LOG.debug("bluetoothInit: attempting to get an adapter");
        setServiceState(RileyLinkServiceState.InitializingBluetooth);

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        if (bluetoothAdapter == null) {
            LOG.error("Unable to obtain a BluetoothAdapter.");
            setServiceState(RileyLinkServiceState.BluetoothNotAvailable, RileyLinkErrorCode.UnableToObtainBluetoothAdapter);
        } else {

            if (!bluetoothAdapter.isEnabled()) {

                sendBLERequestForAccess();

                LOG.error("Bluetooth is not enabled.");
                setServiceState(RileyLinkServiceState.BlueToothDisabled, RileyLinkErrorCode.BluetoothDisabled);
            } else {
                setServiceState(RileyLinkServiceState.BlueToothEnabled);
            }
        }

    }


    // returns true if our Rileylink configuration changed
    public boolean reconfigureRileylink(String deviceAddress) {

        setServiceState(RileyLinkServiceState.RileyLinkInitializing);

        if (rileyLinkBLE.isConnected()) {
            if (deviceAddress.equals(rileyLinkServiceData.rileylinkAddress)) {
                LOG.info("No change to RL address.  Not reconnecting.");
                return false;
            } else {
                LOG.warn("Disconnecting from old RL (" + rileyLinkServiceData.rileylinkAddress + "), reconnecting to new: " + deviceAddress);
                rileyLinkBLE.disconnect();
                // prolly need to shut down listening thread too?
                SP.putString(MedtronicConst.Prefs.RileyLinkAddress, deviceAddress);

                rileyLinkServiceData.rileylinkAddress = deviceAddress;
                rileyLinkBLE.findRileyLink(rileyLinkServiceData.rileylinkAddress);
                return true;
            }
        } else {
            Toast.makeText(context, "Using RL " + deviceAddress, Toast.LENGTH_SHORT).show();
            LOG.debug("handleIPCMessage: Using RL " + deviceAddress);
            if (bluetoothAdapter == null) {
                bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            }

            if (bluetoothAdapter != null) {
                if (bluetoothAdapter.isEnabled()) {
                    // FIXME: this may be a long running function:
                    rileyLinkBLE.findRileyLink(deviceAddress);
                    // If successful, we will get a broadcast from RileyLinkBLE: RT2Const.serviceLocal.bluetooth_connected
                    return true;
                } else {
                    LOG.error("Bluetooth is not enabled.");
                    setServiceState(RileyLinkServiceState.BlueToothDisabled, RileyLinkErrorCode.BluetoothDisabled);
                    return false;
                }
            } else {
                LOG.error("Failed to get adapter");
                setServiceState(RileyLinkServiceState.BluetoothNotAvailable, RileyLinkErrorCode.UnableToObtainBluetoothAdapter);
                return false;
            }
        }
    }


    protected void setServiceState(RileyLinkServiceState newState) {
        setServiceState(newState, null);
    }


    protected void setServiceState(RileyLinkServiceState newState, RileyLinkErrorCode errorCode) {
        this.rileyLinkServiceData.serviceState = newState;

        if (errorCode != null)
            this.rileyLinkServiceData.errorCode = errorCode;

        RileyLinkUtil.addHistoryEntry(new RLHistoryItem(new GregorianCalendar(), newState, errorCode));
    }


//    public synchronized static PowerManager.WakeLock getLock(Context context) {
//        if (lockStatic == null) {
//            PowerManager mgr = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
//
//            lockStatic = mgr.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKELOCKNAME);
//            lockStatic.setReferenceCounted(true);
//        }
//
//        return lockStatic;
//    }


    public void sendServiceTransportResponse(ServiceTransport transport, ServiceResult serviceResult) {

        LOG.warn("UNWANTED: {}", "sendServiceTransportResponse");

        // get the key (hashcode) of the client who requested this
        Integer clientHashcode = transport.getSenderHashcode();
        // make a new bundle to send as the message data
        transport.setServiceResult(serviceResult);
        // FIXME
        transport.setTransportType(RT2Const.IPC.MSG_ServiceResult);
        //rileyLinkIPCConnection.sendTransport(transport, clientHashcode);
    }


//    public boolean sendNotification(ServiceNotification notification, Integer clientHashcode) {
//        //return rileyLinkIPCConnection.sendNotification(notification, clientHashcode);
//        return false;
//    }


    protected void sendBLERequestForAccess() {
        // FIXME
        //serviceConnection.sendMessage(RT2Const.IPC.MSG_BLE_requestAccess);

        //Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        //startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
    }


    // FIXME: This needs to be run in a session so that is interruptable, has a separate thread, etc.
    protected void doTunePump() {

        setServiceState(RileyLinkServiceState.TuneUpPump);

        double lastGoodFrequency = 0.0d;

        if (rileyLinkServiceData.lastGoodFrequency == null) {
            lastGoodFrequency = SP.getDouble(MedtronicConst.Prefs.LastGoodPumpFrequency, 0.0d);
        } else {
            lastGoodFrequency = rileyLinkServiceData.lastGoodFrequency;
        }

        //double lastGoodFrequency = SP.getFloat(MedtronicConst.Prefs.LastGoodPumpFrequency, 0.0f);
        double newFrequency;
        if (lastGoodFrequency != 0.0) {
            LOG.info("Checking for pump near last saved frequency of {}MHz", lastGoodFrequency);
            // we have an old frequency, so let's start there.
            newFrequency = pumpCommunicationManager.quickTuneForPump(lastGoodFrequency);
            if (newFrequency == 0.0) {
                // quick scan failed to find pump.  Try full scan
                LOG.warn("Failed to find pump near last saved frequency, doing full scan");
                newFrequency = pumpCommunicationManager.tuneForPump();
            }
        } else {
            LOG.warn("No saved frequency for pump, doing full scan.");
            // we don't have a saved frequency, so do the full scan.
            newFrequency = pumpCommunicationManager.tuneForPump();

        }
        if ((newFrequency != 0.0) && (newFrequency != lastGoodFrequency)) {
            LOG.info("Saving new pump frequency of {}MHz", newFrequency);
            SP.putDouble(MedtronicConst.Prefs.LastGoodPumpFrequency, newFrequency);
            rileyLinkServiceData.lastGoodFrequency = newFrequency;
            rileyLinkServiceData.tuneUpDone = true;
            rileyLinkServiceData.lastTuneUpTime = System.currentTimeMillis();
        }

        if (newFrequency == 0.0d) {
            // error tuning pump, pump not present ??
            //this.errorCode = RileyLinkErrorCode.TuneUpOfPumpFailed;
            setServiceState(RileyLinkServiceState.RileyLinkReady, RileyLinkErrorCode.TuneUpOfPumpFailed);
        }

    }


    // PumpInterface

    public boolean isInitialized() {
        return this.rileyLinkServiceData.serviceState == RileyLinkServiceState.RileyLinkReady;
    }


    public boolean isConnected() {
        return this.rileyLinkServiceData.serviceState == RileyLinkServiceState.RileyLinkReady;
    }

    public boolean isConnecting() {
        return this.rileyLinkServiceData.serviceState != RileyLinkServiceState.RileyLinkReady;
    }

    public void connect(String reason) {
        bluetoothInit();
    }


    public void disconnect(String reason) {

    }


    public void stopConnecting() {

    }

}
