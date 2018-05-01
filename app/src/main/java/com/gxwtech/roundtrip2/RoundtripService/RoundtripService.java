package com.gxwtech.roundtrip2.RoundtripService;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Message;
import android.os.Parcel;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.widget.Toast;

import com.gxwtech.roundtrip2.RT2Const;
import com.gxwtech.roundtrip2.RoundtripService.RileyLink.PumpManager;
import com.gxwtech.roundtrip2.RoundtripService.RileyLinkBLE.RFSpy;
import com.gxwtech.roundtrip2.RoundtripService.RileyLinkBLE.RileyLinkBLE;
import com.gxwtech.roundtrip2.RoundtripService.Tasks.DiscoverGattServicesTask;
import com.gxwtech.roundtrip2.RoundtripService.Tasks.FetchPumpHistoryTask;
import com.gxwtech.roundtrip2.RoundtripService.Tasks.InitializePumpManagerTask;
import com.gxwtech.roundtrip2.RoundtripService.Tasks.ReadBolusWizardCarbProfileTask;
import com.gxwtech.roundtrip2.RoundtripService.Tasks.ReadISFProfileTask;
import com.gxwtech.roundtrip2.RoundtripService.Tasks.ReadPumpClockTask;
import com.gxwtech.roundtrip2.RoundtripService.Tasks.RetrieveHistoryPageTask;
import com.gxwtech.roundtrip2.RoundtripService.Tasks.ServiceTask;
import com.gxwtech.roundtrip2.RoundtripService.Tasks.ServiceTaskExecutor;
import com.gxwtech.roundtrip2.RoundtripService.Tasks.UpdatePumpStatusTask;
import com.gxwtech.roundtrip2.RoundtripService.Tasks.WakeAndTuneTask;
import com.gxwtech.roundtrip2.RoundtripService.medtronic.PumpData.ISFTable;
import com.gxwtech.roundtrip2.RoundtripService.medtronic.PumpData.Page;
import com.gxwtech.roundtrip2.RoundtripService.medtronic.PumpData.PumpHistoryManager;
import com.gxwtech.roundtrip2.RoundtripService.medtronic.PumpModel;
import com.gxwtech.roundtrip2.RoundtripService.medtronic.TimeFormat;
import com.gxwtech.roundtrip2.ServiceData.ServiceNotification;
import com.gxwtech.roundtrip2.ServiceData.ServiceResult;
import com.gxwtech.roundtrip2.ServiceData.ServiceTransport;
import com.gxwtech.roundtrip2.util.ByteUtil;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;

/**
 * RoundtripService is intended to stay running when the gui-app is closed.
 */
public class  RoundtripService extends Service {
    private static final String TAG="RoundtripService";
    private static RoundtripService instance;
    private static final String WAKELOCKNAME = "com.gxwtech.roundtrip2.RoundtripServiceWakeLock";
    private static volatile PowerManager.WakeLock lockStatic = null;

    private boolean needBluetoothPermission = true;

    private BroadcastReceiver mBroadcastReceiver;
    private Context mContext;
    private RoundtripServiceIPCConnection serviceConnection;
    private BluetoothManager mBluetoothManager;
    private BluetoothAdapter mBluetoothAdapter;


    // saved settings

    public SharedPreferences sharedPref;
    private String pumpIDString;
    private byte[] pumpIDBytes;
    private String mRileylinkAddress;

    // cache of most recently received set of pump history pages. Probably shouldn't be here.
    ArrayList<Page> mHistoryPages;
    PumpHistoryManager pumpHistoryManager;


    // Our hardware/software connection
    public RileyLinkBLE rileyLinkBLE; // android-bluetooth management
    private RFSpy rfspy; // interface for 916MHz radio.
    public PumpManager pumpManager; // interface to Minimed
    private static ServiceTask currentTask = null;

    public RoundtripService() {
        super();
        instance = this;
        Log.d(TAG, "RoundtripService newly constructed");
    }

    public static RoundtripService getInstance() {
        return instance;
    }

    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate");
        mContext = getApplicationContext();
        serviceConnection = new RoundtripServiceIPCConnection(mContext);

        //sharedPref = mContext.getSharedPreferences(RT2Const.serviceLocal.sharedPreferencesKey, Context.MODE_PRIVATE);
        sharedPref = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());

        // get most recently used pumpID
        pumpIDString = sharedPref.getString(RT2Const.serviceLocal.pumpIDKey,"000000");
        pumpIDBytes = ByteUtil.fromHexString(pumpIDString);
        if (pumpIDBytes == null) {
            Log.e(TAG,"Invalid pump ID? " + ByteUtil.shortHexString(pumpIDBytes));
            pumpIDBytes = new byte[] {0,0,0};
            pumpIDString = "000000";
        }
        if (pumpIDBytes.length != 3) {
            Log.e(TAG,"Invalid pump ID? " + ByteUtil.shortHexString(pumpIDBytes));
            pumpIDBytes = new byte[] {0,0,0};
            pumpIDString = "000000";
        }
        if (pumpIDString.equals("000000")) {
            Log.e(TAG,"Using pump ID "+pumpIDString);
        } else {
            Log.i(TAG,"Using pump ID "+pumpIDString);
        }

        // get most recently used RileyLink address
        mRileylinkAddress = sharedPref.getString(RT2Const.serviceLocal.rileylinkAddressKey,"");

        pumpHistoryManager = new PumpHistoryManager(getApplicationContext());
        rileyLinkBLE = new RileyLinkBLE(this);
        rfspy = new RFSpy(mContext,rileyLinkBLE);
        rfspy.startReader();
        pumpManager = new PumpManager(mContext,rfspy,pumpIDBytes);

        mBroadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
            /* here we can listen for local broadcasts, then send ourselves
             * a specific intent to deal with them, if we wish
              */
                if (intent == null) {
                    Log.e(TAG,"onReceive: received null intent");
                } else {
                    String action = intent.getAction();
                    if (action == null) {
                        Log.e(TAG,"onReceive: null action");
                    } else {
                        if (action.equals(RT2Const.serviceLocal.bluetooth_connected)) {
                            Log.w(TAG,"serviceLocal.bluetooth_connected");
                            serviceConnection.sendNotification(new ServiceNotification(RT2Const.IPC.MSG_note_FindingRileyLink),null);
                            ServiceTaskExecutor.startTask(new DiscoverGattServicesTask());
                            // If this is successful,
                            // We will get a broadcast of RT2Const.serviceLocal.BLE_services_discovered
                        } else if (action.equals(RT2Const.serviceLocal.BLE_services_discovered)) {
                            Log.w(TAG,"serviceLocal.BLE_services_discovered");
                            serviceConnection.sendNotification(new ServiceNotification(RT2Const.IPC.MSG_note_WakingPump),null);
                            rileyLinkBLE.enableNotifications();
                            rfspy.startReader(); // call startReader from outside?
                            ServiceTask task = new InitializePumpManagerTask();
                            ServiceTaskExecutor.startTask(task);
                            Log.i(TAG, "Announcing RileyLink open For business");
                        } else if (action.equals(RT2Const.serviceLocal.ipcBound)) {
                            // If we still need permission for bluetooth, ask now.
                            if (needBluetoothPermission) {
                                sendBLERequestForAccess();
                            }

                        } else if (RT2Const.IPC.MSG_BLE_accessGranted.equals(action)) {
                            //initializeLeAdapter();
                            //BluetoothInit();
                        } else if (RT2Const.IPC.MSG_BLE_accessDenied.equals(action)) {
                            stopSelf(); // This will stop the service.
                        } else if (action.equals(RT2Const.IPC.MSG_PUMP_tunePump)) {
                            doTunePump();
                        } else if (action.equals(RT2Const.IPC.MSG_PUMP_quickTune)) {
                            doTunePump();
                        } else if (action.equals(RT2Const.IPC.MSG_PUMP_fetchHistory)) {
                            mHistoryPages = pumpManager.getAllHistoryPages();
                            final boolean savePages = true;
                            if (savePages) {
                                for (int i = 0; i < mHistoryPages.size(); i++) {
                                    String filename = "PumpHistoryPage-" + i;
                                    Log.w(TAG, "Saving history page to file " + filename);
                                    FileOutputStream outputStream;
                                    try {
                                        outputStream = openFileOutput(filename, 0);
                                        byte[] rawData= mHistoryPages.get(i).getRawData();
                                        if (rawData != null) {
                                            outputStream.write(rawData);
                                        }
                                        outputStream.close();
                                    } catch (FileNotFoundException fnf) {
                                        fnf.printStackTrace();
                                    } catch (IOException ioe) {
                                        ioe.printStackTrace();
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                    }

                                }
                            }

                            Message msg = Message.obtain(null, RT2Const.IPC.MSG_IPC, 0, 0);
                            // Create a bundle with the data
                            Bundle bundle = new Bundle();
                            bundle.putString(RT2Const.IPC.messageKey, RT2Const.IPC.MSG_PUMP_history);
                            ArrayList<Bundle> packedPages = new ArrayList<>();
                            for (Page page : mHistoryPages) {
                                packedPages.add(page.pack());
                            }
                            bundle.putParcelableArrayList(RT2Const.IPC.MSG_PUMP_history_key, packedPages);

                            // save it to SQL.
                            pumpHistoryManager.clearDatabase();
                            pumpHistoryManager.initFromPages(bundle);
                            // write html page to documents folder
                            pumpHistoryManager.writeHtmlPage();

                            // Set payload
                            msg.setData(bundle);
                            serviceConnection.sendMessage(msg,null/*broadcast*/);
                            Log.d(TAG, "sendMessage: sent Full history report");
                        } else if (RT2Const.IPC.MSG_PUMP_fetchSavedHistory.equals(action)) {
                            Log.i(TAG,"Fetching saved history");
                            FileInputStream inputStream;
                            ArrayList<Page> storedHistoryPages = new ArrayList<>();
                            for (int i = 0; i < 16; i++) {

                                String filename = "PumpHistoryPage-" + i;
                                try {
                                    inputStream = openFileInput(filename);
                                    byte[] buffer = new byte[1024];
                                    int numRead = inputStream.read(buffer, 0, 1024);
                                    if (numRead == 1024) {
                                        Page p = new Page();
                                        //p.parseFrom(buffer, PumpModel.MM522);
                                        p.parseFrom(buffer, PumpModel.MM522);
                                        storedHistoryPages.add(p);
                                    } else {
                                        Log.e(TAG, filename + " error: short file");
                                    }
                                } catch (FileNotFoundException fnf) {
                                    Log.e(TAG, "Failed to open " + filename + " for reading.");
                                } catch (IOException e) {
                                    Log.e(TAG, "Failed to read " + filename);
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }
                            mHistoryPages = storedHistoryPages;
                            if (storedHistoryPages.isEmpty()) {
                                Log.e(TAG, "No stored history pages loaded");
                            } else {
                                Message msg = Message.obtain(null, RT2Const.IPC.MSG_IPC, 0, 0);
                                // Create a bundle with the data
                                Bundle bundle = new Bundle();
                                bundle.putString(RT2Const.IPC.messageKey, RT2Const.IPC.MSG_PUMP_history);
                                ArrayList<Bundle> packedPages = new ArrayList<>();
                                for (Page page : mHistoryPages) {
                                    packedPages.add(page.pack());
                                }
                                bundle.putParcelableArrayList(RT2Const.IPC.MSG_PUMP_history_key, packedPages);

                                // save it to SQL.
                                pumpHistoryManager.clearDatabase();
                                pumpHistoryManager.initFromPages(bundle);
                                // write html page to documents folder
                                pumpHistoryManager.writeHtmlPage();

                                // Set payload
                                msg.setData(bundle);
                                serviceConnection.sendMessage(msg,null/*broadcast*/);

                            }
                        } else if (RT2Const.IPC.MSG_ServiceCommand.equals(action)) {
                            Bundle bundle = intent.getBundleExtra(RT2Const.IPC.bundleKey);

                            handleIncomingServiceTransport(new ServiceTransport(bundle));
                        } else if (RT2Const.serviceLocal.INTENT_sessionCompleted.equals(action)) {
                            Bundle bundle = intent.getBundleExtra(RT2Const.IPC.bundleKey);
                            if (bundle != null) {
                                ServiceTransport transport = new ServiceTransport(bundle);
                                serviceConnection.sendTransport(transport, transport.getSenderHashcode());
                            } else {
                                Log.e(TAG,"sessionCompleted: no bundle!");
                            }
                        } else {
                            Log.e(TAG, "Unhandled broadcast: action=" + action);
                        }
                    }
                }
            }
        };
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(RT2Const.serviceLocal.bluetooth_connected);
        intentFilter.addAction(RT2Const.serviceLocal.bluetooth_disconnected);
        intentFilter.addAction(RT2Const.serviceLocal.BLE_services_discovered);
        intentFilter.addAction(RT2Const.serviceLocal.ipcBound);
        intentFilter.addAction(RT2Const.IPC.MSG_BLE_accessGranted);
        intentFilter.addAction(RT2Const.IPC.MSG_BLE_accessDenied);
        intentFilter.addAction(RT2Const.IPC.MSG_BLE_useThisDevice);
        intentFilter.addAction(RT2Const.IPC.MSG_PUMP_tunePump);
        intentFilter.addAction(RT2Const.IPC.MSG_PUMP_fetchHistory);
        intentFilter.addAction(RT2Const.IPC.MSG_PUMP_useThisAddress);
        intentFilter.addAction(RT2Const.IPC.MSG_PUMP_fetchSavedHistory);
        intentFilter.addAction(RT2Const.IPC.MSG_ServiceCommand);
        intentFilter.addAction(RT2Const.serviceLocal.INTENT_sessionCompleted);

        LocalBroadcastManager.getInstance(mContext).registerReceiver(mBroadcastReceiver, intentFilter);

        Log.d(TAG, "onCreate(): It's ALIVE!");
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.w(TAG,"onUnbind");
        return super.onUnbind(intent);
    }

    @Override
    public void onRebind(Intent intent) {
        Log.w(TAG,"onRebind");
        super.onRebind(intent);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        Log.w(TAG,"onConfigurationChanged");
        super.onConfigurationChanged(newConfig);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return serviceConnection.doOnBind(intent);
    }

    // Here is where the wake-lock begins:
    // We've received a service startCommand, we grab the lock.
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand");
        if (intent != null) {
            PowerManager.WakeLock lock = getLock(this.getApplicationContext());

            if (!lock.isHeld() || (flags & START_FLAG_REDELIVERY) != 0) {
                lock.acquire();
            }

            // This will end up running onHandleIntent
            super.onStartCommand(intent, flags, startId);
        } else {
            Log.e(TAG, "Received null intent?");
        }
        BluetoothInit(); // this kicks off our process of device discovery.
        return (START_REDELIVER_INTENT | START_STICKY);
    }

    synchronized private static PowerManager.WakeLock getLock(Context context) {
        if (lockStatic == null) {
            PowerManager mgr =
                    (PowerManager) context.getSystemService(Context.POWER_SERVICE);

            lockStatic = mgr.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKELOCKNAME);
            lockStatic.setReferenceCounted(true);
        }

        return lockStatic;
    }

    // FIXME: This needs to be run in a session so that is interruptable, has a separate thread, etc.
    private void doTunePump() {
        double lastGoodFrequency = sharedPref.getFloat(RT2Const.serviceLocal.prefsLastGoodPumpFrequency,(float)0.0);
        double newFrequency;
        if (lastGoodFrequency != 0.0) {
            Log.i(TAG,String.format("Checking for pump near last saved frequency of %.2fMHz",lastGoodFrequency));
            // we have an old frequency, so let's start there.
            newFrequency = pumpManager.quickTuneForPump(lastGoodFrequency);
            if (newFrequency == 0.0) {
                // quick scan failed to find pump.  Try full scan
                Log.w(TAG,String.format("Failed to find pump near last saved frequency, doing full scan"));
                newFrequency = pumpManager.tuneForPump();
            }
        } else {
            Log.w(TAG,"No saved frequency for pump, doing full scan.");
            // we don't have a saved frequency, so do the full scan.
            newFrequency = pumpManager.tuneForPump();

        }
        if ((newFrequency!=0.0) && (newFrequency != lastGoodFrequency)) {
            Log.i(TAG,String.format("Saving new pump frequency of %.2fMHz",newFrequency));
            SharedPreferences.Editor ed = sharedPref.edit();
            ed.putFloat(RT2Const.serviceLocal.prefsLastGoodPumpFrequency, (float)newFrequency);
            ed.apply();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.e(TAG, "I die! I die!");
    }

    /* private functions */

    void BluetoothInit() {
        if (mBluetoothManager == null) {
            mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            if (mBluetoothManager == null) {
                Log.e(TAG, "Unable to initialize BluetoothManager.");
            }
        }
        // Ensures Bluetooth is available on the device and it is enabled. If not,
        // displays a dialog requesting user permission to enable Bluetooth.
        if ((mBluetoothAdapter==null) || (!mBluetoothAdapter.isEnabled())) {
            sendBLERequestForAccess();
        } else {
            needBluetoothPermission = false;
            initializeLeAdapter();
        }
    }

    public boolean initializeLeAdapter() {
        Log.d(TAG,"initializeLeAdapter: attempting to get an adapter");
        mBluetoothAdapter = mBluetoothManager.getAdapter();
        if (mBluetoothAdapter == null) {
            Log.e(TAG, "Unable to obtain a BluetoothAdapter.");
            return false;
        } else if (!mBluetoothAdapter.isEnabled()) {
            // NOTE: This does not work!
            Log.e(TAG, "Bluetooth is not enabled.");
        }
        return true;
    }

    private void setPumpIDString(String idString) {
        if (idString.length() != 6) {
            Log.e(TAG,"setPumpIDString: invalid pump id string: " + idString);
        }
        pumpIDString = idString;
        pumpIDBytes = ByteUtil.fromHexString(pumpIDString);
        SharedPreferences prefs = mContext.getSharedPreferences(RT2Const.serviceLocal.sharedPreferencesKey, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(RT2Const.serviceLocal.pumpIDKey,pumpIDString);
        editor.apply();
        Log.i(TAG,"setPumpIDString: saved pumpID "+pumpIDString);
    }

    private void sendBLERequestForAccess() {
        //serviceConnection.sendMessage(RT2Const.IPC.MSG_BLE_requestAccess);
    }

    private void reportPumpFound() {
        //serviceConnection.sendMessage(RT2Const.IPC.MSG_PUMP_pumpFound);
    }

    public void setCurrentTask(ServiceTask task) {
        if (currentTask == null) {
            currentTask = task;
        } else {
            Log.e(TAG,"setCurrentTask: Cannot replace current task");
        }
    }

    public void finishCurrentTask(ServiceTask task) {
        if (task != currentTask) {
            Log.e(TAG,"finishCurrentTask: task does not match");
        }
        // hack to force deep copy of transport contents
        ServiceTransport transport = task.getServiceTransport().clone();

        if (transport.hasServiceResult()) {
            sendServiceTransportResponse(transport,transport.getServiceResult());
        }
        currentTask = null;
    }

    private void handleIncomingServiceTransport(ServiceTransport serviceTransport) {
        if (serviceTransport.getServiceCommand().isPumpCommand()) {
            switch (serviceTransport.getOriginalCommandName()) {
                case "ReadPumpClock":
                    ServiceTaskExecutor.startTask(new ReadPumpClockTask(serviceTransport));
                    break;
                case "FetchPumpHistory":
                    ServiceTaskExecutor.startTask(new FetchPumpHistoryTask(serviceTransport));
                    break;
                case "RetrieveHistoryPage":
                    ServiceTask task = new RetrieveHistoryPageTask(serviceTransport);
                    ServiceTaskExecutor.startTask(task);
                    break;
                case "ReadISFProfile":
                    ServiceTaskExecutor.startTask(new ReadISFProfileTask(serviceTransport));
                /*
                ISFTable table = pumpManager.getPumpISFProfile();
                ServiceResult result = new ServiceResult();
                if (table.isValid()) {
                    // convert from ISFTable to ISFProfile
                    Bundle map = result.getMap();
                    map.putIntArray("times", table.getTimes());
                    map.putFloatArray("rates", table.getRates());
                    map.putString("ValidDate", TimeFormat.standardFormatter().print(table.getValidDate()));
                    result.setMap(map);
                    result.setResultOK();
                }
                sendServiceTransportResponse(serviceTransport,result);
                */
                    break;
                case "ReadBolusWizardCarbProfile":
                    ServiceTaskExecutor.startTask(new ReadBolusWizardCarbProfileTask());
                    break;
                case "UpdatePumpStatus":
                    ServiceTaskExecutor.startTask(new UpdatePumpStatusTask());
                    break;
                case "WakeAndTune":
                    ServiceTaskExecutor.startTask(new WakeAndTuneTask());
                default:
                    Log.e(TAG,"Failed to handle pump command: " + serviceTransport.getOriginalCommandName());
                    break;
            }
        } else {
            switch (serviceTransport.getOriginalCommandName()) {
                case "SetPumpID":
                    // This one is a command to RoundtripService, not to the PumpManager
                    String pumpID = serviceTransport.getServiceCommand().getMap().getString("pumpID", "");
                    ServiceResult result = new ServiceResult();
                    if ((pumpID != null) && (pumpID.length() == 6)) {
                        setPumpIDString(pumpID);
                        result.setResultOK();
                    } else {
                        Log.e(TAG, "handleIncomingServiceTransport: SetPumpID bundle missing 'pumpID' value");
                        result.setResultError(-1, "Invalid parameter (missing pumpID)");
                    }
                    sendServiceTransportResponse(serviceTransport, result);
                    break;
                case "UseThisRileylink":
                    // If we are not connected, connect using the given address.
                    // If we are connected and the addresses differ, disconnect, connect to new.
                    // If we are connected and the addresses are the same, ignore.
                    String deviceAddress = serviceTransport.getServiceCommand().getMap().getString("rlAddress", "");
                    if ("".equals(deviceAddress)) {
                        Log.e(TAG, "handleIPCMessage: null RL address passed");
                    } else {
                        reconfigureRileylink(deviceAddress);
                    }
                    break;
                default:
                    Log.e(TAG, "handleIncomingServiceTransport: Failed to handle service command '" + serviceTransport.getOriginalCommandName() + "'");
                    break;
            }
        }
    }

    // returns true if our Rileylink configuration changed
    public boolean reconfigureRileylink(String deviceAddress) {
        if (rileyLinkBLE.isConnected()) {
            if (deviceAddress.equals(mRileylinkAddress)) {
                Log.i(TAG, "No change to RL address.  Not reconnecting.");
                return false;
            } else {
                Log.w(TAG, "Disconnecting from old RL (" + mRileylinkAddress + "), reconnecting to new: " + deviceAddress);
                rileyLinkBLE.disconnect();
                // prolly need to shut down listening thread too?
                SharedPreferences.Editor ed = sharedPref.edit();
                ed.putString("rlAddress", deviceAddress);
                ed.apply();
                mRileylinkAddress = deviceAddress;
                rileyLinkBLE.findRileyLink(mRileylinkAddress);
                return true;
            }
        } else {
            Toast.makeText(mContext, "Using RL " + deviceAddress, Toast.LENGTH_SHORT).show();
            Log.d(TAG, "handleIPCMessage: Using RL " + deviceAddress);
            if (mBluetoothAdapter == null) {
                mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            }
            if (mBluetoothAdapter != null) {
                if (mBluetoothAdapter.isEnabled()) {
                    // FIXME: this may be a long running function:
                    rileyLinkBLE.findRileyLink(deviceAddress);
                    // If successful, we will get a broadcast from RileyLinkBLE: RT2Const.serviceLocal.bluetooth_connected
                    return true;
                } else {
                    Log.e(TAG, "Bluetooth is not enabled.");
                    return false;
                }
            } else {
                Log.e(TAG, "Failed to get adapter");
                return false;
            }
        }
    }

    public void announceProgress(int progressPercent) {
        if (currentTask != null) {
            ServiceNotification note = new ServiceNotification(RT2Const.IPC.MSG_note_TaskProgress);
            note.getMap().putInt("progress",progressPercent);
            note.getMap().putString("task",currentTask.getServiceTransport().getOriginalCommandName());
            Integer senderHashcode = currentTask.getServiceTransport().getSenderHashcode();
            serviceConnection.sendNotification(note, senderHashcode);
        } else {
            Log.e(TAG,"announceProgress: No current task");
        }
    }

    public void sendServiceTransportResponse(ServiceTransport transport, ServiceResult serviceResult) {
        // get the key (hashcode) of the client who requested this
        Integer clientHashcode = transport.getSenderHashcode();
        // make a new bundle to send as the message data
        transport.setServiceResult(serviceResult);
        transport.setTransportType(RT2Const.IPC.MSG_ServiceResult);
        serviceConnection.sendTransport(transport,clientHashcode);
    }

    public boolean sendNotification(ServiceNotification notification, Integer clientHashcode) {
        return serviceConnection.sendNotification(notification, clientHashcode);
    }

    public void saveHistoryPage(int pagenumber, Page page) {
        if ((page == null) || (page.getRawData() == null)) {
            return;
        }
        String filename = "history-" + pagenumber;
        FileOutputStream os;
        try {
            os = openFileOutput(filename, Context.MODE_PRIVATE);
            os.write(page.getRawData());
            os.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

