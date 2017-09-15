package info.nightscout.androidaps.plugins.PumpDanaRS.services;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.SystemClock;

import com.cozmo.danar.util.BleCommandUtil;
import com.squareup.otto.Subscribe;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.db.Treatment;
import info.nightscout.androidaps.events.EventAppExit;
import info.nightscout.androidaps.events.EventInitializationChanged;
import info.nightscout.androidaps.events.EventPumpStatusChanged;
import info.nightscout.androidaps.plugins.Overview.Notification;
import info.nightscout.androidaps.plugins.Overview.events.EventNewNotification;
import info.nightscout.androidaps.plugins.PumpDanaR.DanaRPump;
import info.nightscout.androidaps.plugins.PumpDanaR.events.EventDanaRNewStatus;
import info.nightscout.androidaps.plugins.PumpDanaRS.DanaRSPlugin;
import info.nightscout.androidaps.plugins.PumpDanaRS.activities.PairingHelperActivity;
import info.nightscout.androidaps.plugins.PumpDanaRS.comm.DanaRSMessageHashTable;
import info.nightscout.androidaps.plugins.PumpDanaRS.comm.DanaRS_Packet;
import info.nightscout.androidaps.plugins.PumpDanaRS.comm.DanaRS_Packet_Basal_Get_Basal_Rate;
import info.nightscout.androidaps.plugins.PumpDanaRS.comm.DanaRS_Packet_Basal_Get_Profile_Number;
import info.nightscout.androidaps.plugins.PumpDanaRS.comm.DanaRS_Packet_Basal_Temporary_Basal_State;
import info.nightscout.androidaps.plugins.PumpDanaRS.comm.DanaRS_Packet_Bolus_Get_Bolus_Option;
import info.nightscout.androidaps.plugins.PumpDanaRS.comm.DanaRS_Packet_Bolus_Get_CIR_CF_Array;
import info.nightscout.androidaps.plugins.PumpDanaRS.comm.DanaRS_Packet_Bolus_Get_Calculation_Information;
import info.nightscout.androidaps.plugins.PumpDanaRS.comm.DanaRS_Packet_Bolus_Get_Extended_Bolus_State;
import info.nightscout.androidaps.plugins.PumpDanaRS.comm.DanaRS_Packet_Bolus_Get_Step_Bolus_Information;
import info.nightscout.androidaps.plugins.PumpDanaRS.comm.DanaRS_Packet_Bolus_Set_Step_Bolus_Start;
import info.nightscout.androidaps.plugins.PumpDanaRS.comm.DanaRS_Packet_Bolus_Set_Step_Bolus_Stop;
import info.nightscout.androidaps.plugins.PumpDanaRS.comm.DanaRS_Packet_General_Get_Shipping_Information;
import info.nightscout.androidaps.plugins.PumpDanaRS.comm.DanaRS_Packet_General_Initial_Screen_Information;
import info.nightscout.androidaps.plugins.PumpDanaRS.comm.DanaRS_Packet_Notify_Delivery_Complete;
import info.nightscout.androidaps.plugins.PumpDanaRS.comm.DanaRS_Packet_Notify_Delivery_Rate_Display;
import info.nightscout.androidaps.plugins.PumpDanaRS.comm.DanaRS_Packet_Option_Get_Pump_Time;
import info.nightscout.androidaps.plugins.PumpDanaRS.comm.DanaRS_Packet_Option_Set_Pump_Time;
import info.nightscout.androidaps.plugins.PumpDanaRS.events.EventDanaRSPacket;
import info.nightscout.androidaps.plugins.PumpDanaRS.events.EventDanaRSPairingSuccess;
import info.nightscout.utils.NSUpload;
import info.nightscout.utils.SP;

public class DanaRSService extends Service {
    private static Logger log = LoggerFactory.getLogger(DanaRSService.class);

    private static final long WRITE_DELAY_MILLIS = 50;

    public static String UART_READ_UUID = "0000fff1-0000-1000-8000-00805f9b34fb";
    public static String UART_WRITE_UUID = "0000fff2-0000-1000-8000-00805f9b34fb";

    private byte PACKET_START_BYTE = (byte) 0xA5;
    private byte PACKET_END_BYTE = (byte) 0x5A;

    private BluetoothManager mBluetoothManager = null;
    private BluetoothAdapter mBluetoothAdapter = null;
    private BluetoothDevice mBluetoothDevice = null;
    private String mBluetoothDeviceAddress = null;
    private String mBluetoothDeviceName = null;
    private BluetoothGatt mBluetoothGatt = null;

    private boolean isConnected = false;
    private boolean isConnecting = false;

    private BluetoothGattCharacteristic UART_Read;
    private BluetoothGattCharacteristic UART_Write;

    private PowerManager.WakeLock mWakeLock;
    private IBinder mBinder = new LocalBinder();

    private DanaRPump danaRPump = DanaRPump.getInstance();
    private Treatment bolusingTreatment = null;

    private Object mConfirmConnect = null;

    private static final ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor();
    private static ScheduledFuture<?> scheduledDisconnection = null;

    private DanaRS_Packet processsedMessage = null;
    private ArrayList<byte[]> mSendQueue = new ArrayList<>();


    public DanaRSService() {
        try {
            MainApp.bus().unregister(this);
        } catch (RuntimeException x) {
            // Ignore
        }
        initialize();
        MainApp.bus().register(this);

        PowerManager powerManager = (PowerManager) MainApp.instance().getApplicationContext().getSystemService(Context.POWER_SERVICE);
        mWakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, DanaRSService.class.getSimpleName());
    }

    private boolean getPumpStatus() {
        try {
            MainApp.bus().post(new EventPumpStatusChanged(MainApp.sResources.getString(R.string.gettingpumpstatus)));

            sendMessage(new DanaRS_Packet_General_Initial_Screen_Information());
            sendMessage(new DanaRS_Packet_Bolus_Get_Extended_Bolus_State());
            sendMessage(new DanaRS_Packet_Bolus_Get_Step_Bolus_Information()); // last bolus
            sendMessage(new DanaRS_Packet_Basal_Temporary_Basal_State());

            Date now = new Date();
            if (danaRPump.lastSettingsRead.getTime() + 60 * 60 * 1000L < now.getTime() || !MainApp.getSpecificPlugin(DanaRSPlugin.class).isInitialized()) {
                sendMessage(new DanaRS_Packet_General_Get_Shipping_Information());
                sendMessage(new DanaRS_Packet_Basal_Get_Profile_Number());
                sendMessage(new DanaRS_Packet_Bolus_Get_Bolus_Option()); // isExtendedEnabled
                sendMessage(new DanaRS_Packet_Basal_Get_Basal_Rate()); // basal profile
                sendMessage(new DanaRS_Packet_Bolus_Get_Step_Bolus_Information()); // bolusStep, maxBolus
                sendMessage(new DanaRS_Packet_Basal_Get_Basal_Rate()); // basalStep, maxBasal
                sendMessage(new DanaRS_Packet_Bolus_Get_Calculation_Information()); // target
                sendMessage(new DanaRS_Packet_Bolus_Get_CIR_CF_Array());
                sendMessage(new DanaRS_Packet_Option_Get_Pump_Time());
                long timeDiff = (danaRPump.pumpTime.getTime() - System.currentTimeMillis()) / 1000L;
                log.debug("Pump time difference: " + timeDiff + " seconds");
                if (Math.abs(timeDiff) > 10) {
                    sendMessage(new DanaRS_Packet_Option_Set_Pump_Time(new Date()));
                    sendMessage(new DanaRS_Packet_Option_Get_Pump_Time());
                    timeDiff = (danaRPump.pumpTime.getTime() - System.currentTimeMillis()) / 1000L;
                    log.debug("Pump time difference: " + timeDiff + " seconds");
                }
                danaRPump.lastSettingsRead = now;
            }

            loadEvents();

            danaRPump.lastConnection = now;
            MainApp.bus().post(new EventDanaRNewStatus());
            MainApp.bus().post(new EventInitializationChanged());
            NSUpload.uploadDeviceStatus();
            if (danaRPump.dailyTotalUnits > danaRPump.maxDailyTotalUnits * Constants.dailyLimitWarning) {
                log.debug("Approaching daily limit: " + danaRPump.dailyTotalUnits + "/" + danaRPump.maxDailyTotalUnits);
                Notification reportFail = new Notification(Notification.APPROACHING_DAILY_LIMIT, MainApp.sResources.getString(R.string.approachingdailylimit), Notification.URGENT);
                MainApp.bus().post(new EventNewNotification(reportFail));
                NSUpload.uploadError(MainApp.sResources.getString(R.string.approachingdailylimit) + ": " + danaRPump.dailyTotalUnits + "/" + danaRPump.maxDailyTotalUnits + "U");
            }
        } catch (Exception e) {
            log.error("Unhandled exception", e);
        }
        return true;
    }

    private void loadEvents() {
    }

    public boolean bolus(double insulin, int carbs, long l, Treatment t) {
        bolusingTreatment = t;
        int speed = SP.getInt(R.string.key_danars_bolusspeed, 0);
        DanaRS_Packet_Bolus_Set_Step_Bolus_Start start = new DanaRS_Packet_Bolus_Set_Step_Bolus_Start(insulin, speed);
        DanaRS_Packet_Bolus_Set_Step_Bolus_Stop stop = new DanaRS_Packet_Bolus_Set_Step_Bolus_Stop(insulin, t);
        DanaRS_Packet_Notify_Delivery_Complete complete = new DanaRS_Packet_Notify_Delivery_Complete(insulin, t);

        if (!isConnected()) return false;

        if (carbs > 0) {
//            MsgSetCarbsEntry msg = new MsgSetCarbsEntry(carbtime, carbs);
//            sendMessage(msg);
//            MsgSetHistoryEntry_v2 msgSetHistoryEntry_v2 = new MsgSetHistoryEntry_v2(DanaRPump.CARBS, carbtime, carbs, 0);
//            sendMessage(msgSetHistoryEntry_v2);
//            lastHistoryFetched = carbtime - 60000;
        }
        if (insulin > 0) {
            DanaRS_Packet_Notify_Delivery_Rate_Display progress = new DanaRS_Packet_Notify_Delivery_Rate_Display(insulin, t); // initialize static variables

            if (!stop.stopped) {
                sendMessage(start);
            } else {
                t.insulin = 0d;
                return false;
            }
            while (!stop.stopped && !start.failed) {
                SystemClock.sleep(100);
                if ((System.currentTimeMillis() - progress.lastReceive) > 5 * 1000L) { // if i didn't receive status for more than 5 sec expecting broken comm
                    stop.stopped = true;
                    stop.forced = true;
                    log.debug("Communication stopped");
                }
            }
        }
        SystemClock.sleep(3000);
        bolusingTreatment = null;
        loadEvents();
        return true;
    }

    public void bolusStop() {
    }

    public boolean tempBasal(Integer percent, int durationInHours) {
        return false;
    }

    public boolean highTempBasal(Integer percent) {
        return false;
    }

    public void tempBasalStop() {
    }

    public boolean extendedBolus(Double insulin, int durationInHalfHours) {
        return false;
    }

    public void extendedBolusStop() {
    }

    public boolean updateBasalsInPump(Profile profile) {
        return false;
    }

    public boolean loadHistory(byte type) {
        return false;
    }


    public class LocalBinder extends Binder {
        public DanaRSService getServiceInstance() {
            return DanaRSService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Subscribe
    public void onStatusEvent(EventAppExit event) {
        if (Config.logFunctionCalls)
            log.debug("EventAppExit received");

        stopSelf();
        if (Config.logFunctionCalls)
            log.debug("EventAppExit finished");
    }

    private boolean initialize() {
        log.debug("Initializing service.");

        if (mBluetoothManager == null) {
            mBluetoothManager = ((BluetoothManager) MainApp.instance().getApplicationContext().getSystemService(Context.BLUETOOTH_SERVICE));
            if (mBluetoothManager == null) {
                log.debug("Unable to initialize BluetoothManager.");
                return false;
            }
        }

        mBluetoothAdapter = mBluetoothManager.getAdapter();
        if (mBluetoothAdapter == null) {
            log.debug("Unable to obtain a BluetoothAdapter.");
            return false;
        }

        return true;
    }

    public boolean isConnected() {
        return isConnected;
    }

    public boolean isConnecting() {
        return isConnecting;
    }

    public boolean connect(String from, String address, Object confirmConnect) {
        mConfirmConnect = confirmConnect;
        BluetoothManager tBluetoothManager = ((BluetoothManager) MainApp.instance().getApplicationContext().getSystemService(Context.BLUETOOTH_SERVICE));
        if (tBluetoothManager == null) {
            return false;
        }

        BluetoothAdapter tBluetoothAdapter = tBluetoothManager.getAdapter();
        if (tBluetoothAdapter == null) {
            return false;
        }

        if (mBluetoothAdapter == null) {
            if (!initialize()) {
                return false;
            }
        }

        if (address == null) {
            log.debug("unspecified address.");
            return false;
        }

        mWakeLock.acquire();

        MainApp.bus().post(new EventPumpStatusChanged(EventPumpStatusChanged.CONNECTING));
        isConnecting = true;
        if ((mBluetoothDeviceAddress != null) && (address.equals(mBluetoothDeviceAddress)) && (mBluetoothGatt != null)) {
            log.debug("Trying to use an existing mBluetoothGatt for connection.");
            if (mBluetoothGatt.connect()) {
                setCharacteristicNotification(getUARTReadBTGattChar(), true);
                mWakeLock.release();
                return true;
            }
            mWakeLock.release();
            return false;
        }

        BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        if (device == null) {
            log.debug("Device not found.  Unable to connect.");
            mWakeLock.release();
            return false;
        }

        mBluetoothGatt = device.connectGatt(getApplicationContext(), false, mGattCallback);
        setCharacteristicNotification(getUARTReadBTGattChar(), true);
        log.debug("Trying to create a new connection.");
        mBluetoothDevice = device;
        mBluetoothDeviceAddress = address;
        mBluetoothDeviceName = device.getName();
        mWakeLock.release();
        return true;
    }

    public void disconnect(String from) {
        log.debug("disconnect from: " + from);
        if ((mBluetoothAdapter == null) || (mBluetoothGatt == null)) {
            return;
        }
        setCharacteristicNotification(getUARTReadBTGattChar(), false);
        mBluetoothGatt.disconnect();
        isConnected = false;
    }

    public void close() {
        log.debug("BluetoothAdapter close");
        if (mBluetoothGatt == null) {
            return;
        }
        mBluetoothGatt.close();
        mBluetoothGatt = null;
    }

    public BluetoothDevice getConnectDevice() {
        return mBluetoothDevice;
    }

    public String getConnectDeviceAddress() {
        return mBluetoothDeviceAddress;
    }

    public String getConnectDeviceName() {
        return mBluetoothDeviceName;
    }

    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            log.debug("onConnectionStateChange");

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                mBluetoothGatt.discoverServices();
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                close();
                isConnected = false;
                MainApp.bus().post(new EventPumpStatusChanged(EventPumpStatusChanged.DISCONNECTED));
            }
        }

        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            log.debug("onServicesDiscovered");

            isConnecting = false;

            if (status == BluetoothGatt.GATT_SUCCESS) {
                findCharacteristic();
            }

            // 1st message sent to pump after connect
            byte[] bytes = BleCommandUtil.getInstance().getEncryptedPacket(BleCommandUtil.DANAR_PACKET__OPCODE_ENCRYPTION__PUMP_CHECK, null, getConnectDeviceName());
            log.debug(">>>>> " + "ENCRYPTION__PUMP_CHECK (0x00)" + " " + DanaRS_Packet.toHexString(bytes));
            writeCharacteristic_NO_RESPONSE(getUARTWriteBTGattChar(), bytes);
        }

        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            log.debug("onCharacteristicRead" + (characteristic != null ? ":" + DanaRS_Packet.toHexString(characteristic.getValue()) : ""));
            addToReadBuffer(characteristic.getValue());
            readDataParsing();
        }

        public void onCharacteristicChanged(BluetoothGatt gatt, final BluetoothGattCharacteristic characteristic) {
            log.debug("onCharacteristicChanged" + (characteristic != null ? ":" + DanaRS_Packet.toHexString(characteristic.getValue()) : ""));
            addToReadBuffer(characteristic.getValue());
            new Thread(new Runnable() {
                @Override
                public void run() {
                    readDataParsing();
                }
            }).start();
        }

        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            log.debug("onCharacteristicWrite" + (characteristic != null ? ":" + DanaRS_Packet.toHexString(characteristic.getValue()) : ""));
            new Thread(new Runnable() {
                @Override
                public void run() {
                    synchronized (mSendQueue) {
                        // after message sent, check if there is the rest of the message waiting and send it
                        if (mSendQueue.size() > 0) {
                            byte[] bytes = mSendQueue.get(0);
                            mSendQueue.remove(0);
                            writeCharacteristic_NO_RESPONSE(getUARTWriteBTGattChar(), bytes);
                        }
                    }
                }
            }).start();
        }
    };

    public void setCharacteristicNotification(BluetoothGattCharacteristic characteristic, boolean enabled) {
        log.debug("setCharacteristicNotification");
        if ((mBluetoothAdapter == null) || (mBluetoothGatt == null)) {
            log.debug("BluetoothAdapter not initialized_ERROR");
            return;
        }
        mBluetoothGatt.setCharacteristicNotification(characteristic, enabled);
    }

    public void readCharacteristic(BluetoothGattCharacteristic characteristic) {
        log.debug("readCharacteristic");
        if ((mBluetoothAdapter == null) || (mBluetoothGatt == null)) {
            log.debug("BluetoothAdapter not initialized_ERROR");
            return;
        }
        mBluetoothGatt.readCharacteristic(characteristic);
    }

    public void writeCharacteristic_NO_RESPONSE(final BluetoothGattCharacteristic characteristic, final byte[] data) {
        if ((mBluetoothAdapter == null) || (mBluetoothGatt == null)) {
            log.debug("BluetoothAdapter not initialized_ERROR");
            return;
        }

        new Thread(new Runnable() {
            public void run() {
                SystemClock.sleep(WRITE_DELAY_MILLIS);
                characteristic.setValue(data);
                characteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
                log.debug("writeCharacteristic:" + DanaRS_Packet.toHexString(data));
                mBluetoothGatt.writeCharacteristic(characteristic);
            }
        }).start();
    }

    public BluetoothGattCharacteristic getUARTReadBTGattChar() {
        if (UART_Read == null) {
            UART_Read = new BluetoothGattCharacteristic(UUID.fromString(UART_READ_UUID), BluetoothGattCharacteristic.PROPERTY_READ | BluetoothGattCharacteristic.PROPERTY_NOTIFY, 0);
        }
        return UART_Read;
    }

    public BluetoothGattCharacteristic getUARTWriteBTGattChar() {
        if (UART_Write == null) {
            UART_Write = new BluetoothGattCharacteristic(UUID.fromString(UART_WRITE_UUID), BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE, 0);
        }
        return UART_Write;
    }

    public List<BluetoothGattService> getSupportedGattServices() {
        log.debug("getSupportedGattServices");
        if ((mBluetoothAdapter == null) || (mBluetoothGatt == null)) {
            log.debug("BluetoothAdapter not initialized_ERROR");
            return null;
        }

        return mBluetoothGatt.getServices();
    }

    private void findCharacteristic() {
        List<BluetoothGattService> gattServices = getSupportedGattServices();

        if (gattServices == null) {
            return;
        }
        String uuid = null;

        for (BluetoothGattService gattService : gattServices) {
            List<BluetoothGattCharacteristic> gattCharacteristics = gattService.getCharacteristics();
            for (BluetoothGattCharacteristic gattCharacteristic : gattCharacteristics) {
                uuid = gattCharacteristic.getUuid().toString();
                if (UART_READ_UUID.equals(uuid)) {
                    UART_Read = gattCharacteristic;
                    setCharacteristicNotification(UART_Read, true);
                }
                if (UART_WRITE_UUID.equals(uuid)) {
                    UART_Write = gattCharacteristic;
                }
            }
        }
    }

    private byte[] readBuffer = new byte[1024];
    private int bufferLength = 0;

    private void addToReadBuffer(byte[] buffer) {
        //log.debug("addToReadBuffer " + DanaRS_Packet.toHexString(buffer));
        if (buffer == null || buffer.length == 0) {
            return;
        }
        synchronized (readBuffer) {
            // Append incomming data to input buffer
            System.arraycopy(buffer, 0, readBuffer, bufferLength, buffer.length);
            bufferLength += buffer.length;
        }
    }

    private void readDataParsing() {
        boolean startSignatureFound = false, packetIsValid = false;
        boolean isProcessing;

        isProcessing = true;

        while (isProcessing) {
            int length = 0;
            synchronized (readBuffer) {
                // Find packet start [A5 A5]
                if (bufferLength >= 6) {
                    for (int idxStartByte = 0; idxStartByte < bufferLength - 2; idxStartByte++) {
                        if ((readBuffer[idxStartByte] == PACKET_START_BYTE) && (readBuffer[idxStartByte + 1] == PACKET_START_BYTE)) {
                            if (idxStartByte > 0) {
                                // if buffer doesn't start with signature remove the leading trash
                                log.debug("Shifting the input buffer by " + idxStartByte + " bytes");
                                System.arraycopy(readBuffer, idxStartByte, readBuffer, 0, bufferLength - idxStartByte);
                                bufferLength -= idxStartByte;
                            }
                            startSignatureFound = true;
                            break;
                        }
                    }
                }
                // A5 A5 LEN TYPE CODE PARAMS CHECKSUM1 CHECKSUM2 5A 5A
                //           ^---- LEN -----^
                // total packet length 2 + 1 + readBuffer[2] + 2 + 2
                if (startSignatureFound) {
                    length = readBuffer[2];
                    // test if there is enough data loaded
                    if (length + 7 > bufferLength)
                        return;
                    // Verify packed end [5A 5A]
                    if ((readBuffer[length + 5] == PACKET_END_BYTE) && (readBuffer[length + 6] == PACKET_END_BYTE)) {
                        packetIsValid = true;
                    }
                }
            }
            if (packetIsValid) {
                byte[] inputBuffer = new byte[length + 7];
                synchronized (readBuffer) {
                    // copy packet to input buffer
                    System.arraycopy(readBuffer, 0, inputBuffer, 0, length + 7);
                    // Cut off the message from readBuffer
                    System.arraycopy(readBuffer, length + 7, readBuffer, 0, bufferLength - (length + 7));
                    bufferLength -= (length + 7);
                }
                // now we have encrypted packet in inputBuffer
                try {
                    // decrypt the packet
                    inputBuffer = BleCommandUtil.getInstance().getDecryptedPacket(inputBuffer);

                    if (inputBuffer == null) {
                        log.debug("Null decryptedInputBuffer");
                        return;
                    }

                    switch (inputBuffer[0]) {
                        // initial handshake packet
                        case (byte) BleCommandUtil.DANAR_PACKET__TYPE_ENCRYPTION_RESPONSE:
                            switch (inputBuffer[1]) {
                                // 1st packet
                                case (byte) BleCommandUtil.DANAR_PACKET__OPCODE_ENCRYPTION__PUMP_CHECK:
                                    if (inputBuffer.length == 4 && inputBuffer[2] == 'O' && inputBuffer[3] == 'K') {
                                        log.debug("<<<<< " + "ENCRYPTION__PUMP_CHECK (OK)" + " " + DanaRS_Packet.toHexString(inputBuffer));
                                        // Grab pairing key from preferences if exists
                                        String pairingKey = SP.getString(R.string.key_danars_pairingkey, null);
                                        log.debug("Using stored pairing key: " + pairingKey);
                                        if (pairingKey != null) {
                                            byte[] encodedPairingKey = DanaRS_Packet.hexToBytes(pairingKey);
                                            byte[] bytes = BleCommandUtil.getInstance().getEncryptedPacket(BleCommandUtil.DANAR_PACKET__OPCODE_ENCRYPTION__CHECK_PASSKEY, encodedPairingKey, null);
                                            log.debug(">>>>> " + "ENCRYPTION__CHECK_PASSKEY" + " " + DanaRS_Packet.toHexString(bytes));
                                            writeCharacteristic_NO_RESPONSE(getUARTWriteBTGattChar(), bytes);
                                        } else {
                                            // Stored pairing key does not exists, request pairing
                                            SendPairingRequest();
                                        }

                                    } else if (inputBuffer.length == 6 && inputBuffer[2] == 'B' && inputBuffer[3] == 'U' && inputBuffer[4] == 'S' && inputBuffer[5] == 'Y') {
                                        log.debug("<<<<< " + "ENCRYPTION__PUMP_CHECK (BUSY)" + " " + DanaRS_Packet.toHexString(inputBuffer));
                                        mSendQueue.clear();
                                        MainApp.bus().post(new EventPumpStatusChanged(EventPumpStatusChanged.DISCONNECTED, MainApp.sResources.getString(R.string.pumpbusy)));
                                    } else {
                                        log.debug("<<<<< " + "ENCRYPTION__PUMP_CHECK (ERROR)" + " " + DanaRS_Packet.toHexString(inputBuffer));
                                        mSendQueue.clear();
                                        MainApp.bus().post(new EventPumpStatusChanged(EventPumpStatusChanged.DISCONNECTED, MainApp.sResources.getString(R.string.connectionerror)));
                                    }
                                    break;
                                // 2nd packet, pairing key
                                case (byte) BleCommandUtil.DANAR_PACKET__OPCODE_ENCRYPTION__CHECK_PASSKEY:
                                    log.debug("<<<<< " + "ENCRYPTION__CHECK_PASSKEY" + " " + DanaRS_Packet.toHexString(inputBuffer));
                                    if (inputBuffer[2] == (byte) 0x00) {
                                        // Paring is not requested, sending time info
                                        SendTimeInfo();
                                    } else {
                                        // Pairing on pump is requested
                                        SendPairingRequest();
                                    }
                                    break;
                                case (byte) BleCommandUtil.DANAR_PACKET__OPCODE_ENCRYPTION__PASSKEY_REQUEST:
                                    log.debug("<<<<< " + "ENCRYPTION__PASSKEY_REQUEST " + DanaRS_Packet.toHexString(inputBuffer));
                                    if (inputBuffer[2] != (byte) 0x00) {
                                        disconnect("passkey request failed");
                                    }
                                    break;
                                // Paring response, OK button on pump pressed
                                case (byte) BleCommandUtil.DANAR_PACKET__OPCODE_ENCRYPTION__PASSKEY_RETURN:
                                    log.debug("<<<<< " + "ENCRYPTION__PASSKEY_RETURN " + DanaRS_Packet.toHexString(inputBuffer));
                                    // Paring is successfull, sending time info
                                    MainApp.bus().post(new EventDanaRSPairingSuccess());
                                    SendTimeInfo();
                                    byte[] pairingKey = {inputBuffer[2], inputBuffer[3]};
                                    // store pairing key to preferences
                                    SP.putString(R.string.key_danars_pairingkey, DanaRS_Packet.bytesToHex(pairingKey));
                                    log.debug("Got pairing key: " + DanaRS_Packet.bytesToHex(pairingKey));
                                    break;
                                // time and user password information. last packet in handshake
                                case (byte) BleCommandUtil.DANAR_PACKET__OPCODE_ENCRYPTION__TIME_INFORMATION:
                                    log.debug("<<<<< " + "ENCRYPTION__TIME_INFORMATION " + /*message.getMessageName() + " " + */ DanaRS_Packet.toHexString(inputBuffer));
                                    int size = inputBuffer.length;
                                    int pass = ((inputBuffer[size - 1] & 0x000000FF) << 8) + ((inputBuffer[size - 2] & 0x000000FF));
                                    pass = pass ^ 3463;
                                    DanaRPump.getInstance().rs_password = Integer.toHexString(pass);
                                    log.debug("Pump user password: " + Integer.toHexString(pass));
                                    MainApp.bus().post(new EventPumpStatusChanged(EventPumpStatusChanged.CONNECTED));

                                    getPumpStatus();
                                    scheduleDisconnection();
                                    isConnected = true;
                                    isConnecting = false;
                                    if (mConfirmConnect != null) {
                                        synchronized (mConfirmConnect) {
                                            mConfirmConnect.notify();
                                            mConfirmConnect = null;
                                        }
                                    }
                                    break;
                            }
                            break;
                        // common data packet
                        default:
                            DanaRS_Packet message;
                            // Retrieve message code from received buffer and last message sent
                            int originalCommand = processsedMessage != null ? processsedMessage.getCommand() : 0xFFFF;
                            int receivedCommand = DanaRS_Packet.getCommand(inputBuffer);
                            if (originalCommand == receivedCommand) {
                                // it's response to last message
                                message = processsedMessage;
                            } else {
                                // it's not response to last message, create new instance
                                message = DanaRSMessageHashTable.findMessage(receivedCommand);
                            }
                            if (message != null) {
                                log.debug("<<<<< " + message.getFriendlyName() + " " + DanaRS_Packet.toHexString(inputBuffer));
                                // process received data
                                message.handleMessage(inputBuffer);
                                message.setReceived();
                                synchronized (message) {
                                    // notify to sendMessage
                                    message.notify();
                                }
                                MainApp.bus().post(new EventDanaRSPacket(message));
                            } else {
                                log.error("Unknown message received " + DanaRS_Packet.toHexString(inputBuffer));
                            }
                            scheduleDisconnection();
                            break;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                startSignatureFound = false;
                packetIsValid = false;
                if (bufferLength < 6) {
                    // stop the loop
                    isProcessing = false;
                }
            } else {
                // stop the loop
                isProcessing = false;
            }
        }
    }

    public void sendMessage(DanaRS_Packet message) {
        processsedMessage = message;
        if (message == null)
            return;

        byte[] command = {(byte) message.getType(), (byte) message.getOpCode()};
        byte[] params = message.getRequestParams();
        log.debug(">>>>> " + message.getFriendlyName() + " " + DanaRS_Packet.toHexString(command) + " " + DanaRS_Packet.toHexString(params));
        byte[] bytes = BleCommandUtil.getInstance().getEncryptedPacket(message.getOpCode(), params, null);
        // If there is another message not completely sent, add to queue only
        if (mSendQueue.size() > 0) {
            // Split to parts per 20 bytes max
            for (; ; ) {
                if (bytes.length > 20) {
                    byte[] addBytes = new byte[20];
                    System.arraycopy(bytes, 0, addBytes, 0, addBytes.length);
                    byte[] reBytes = new byte[bytes.length - addBytes.length];
                    System.arraycopy(bytes, addBytes.length, reBytes, 0, reBytes.length);
                    bytes = reBytes;
                    synchronized (mSendQueue) {
                        mSendQueue.add(addBytes);
                    }
                } else {
                    synchronized (mSendQueue) {
                        mSendQueue.add(bytes);
                    }
                    break;
                }
            }

        } else {
            if (bytes.length > 20) {
                // Cut first 20 bytes
                byte[] sendBytes = new byte[20];
                System.arraycopy(bytes, 0, sendBytes, 0, sendBytes.length);
                byte[] reBytes = new byte[bytes.length - sendBytes.length];
                System.arraycopy(bytes, sendBytes.length, reBytes, 0, reBytes.length);
                bytes = reBytes;
                // and send
                writeCharacteristic_NO_RESPONSE(getUARTWriteBTGattChar(), sendBytes);
                // The rest split to parts per 20 bytes max
                for (; ; ) {
                    if (bytes.length > 20) {
                        byte[] addBytes = new byte[20];
                        System.arraycopy(bytes, 0, addBytes, 0, addBytes.length);
                        reBytes = new byte[bytes.length - addBytes.length];
                        System.arraycopy(bytes, addBytes.length, reBytes, 0, reBytes.length);
                        bytes = reBytes;
                        synchronized (mSendQueue) {
                            mSendQueue.add(addBytes);
                        }
                    } else {
                        synchronized (mSendQueue) {
                            mSendQueue.add(bytes);
                        }
                        break;
                    }
                }
            } else {
                writeCharacteristic_NO_RESPONSE(getUARTWriteBTGattChar(), bytes);
            }
        }
        // The rest from queue is send from onCharasteristicWrite (after sending 1st part)
        synchronized (message) {
            try {
                message.wait(5000);
            } catch (InterruptedException e) {
                log.error("sendMessage InterruptedException", e);
                e.printStackTrace();
            }
        }

        SystemClock.sleep(200);
        if (!message.isReceived()) {
            log.warn("Reply not received " + message.getFriendlyName());
        }
        scheduleDisconnection();
    }

    private void SendPairingRequest() {
        // Start activity which is waiting 20sec
        // On pump pairing request is displayed and is waiting for conformation
        Intent i = new Intent();
        i.setClass(MainApp.instance(), PairingHelperActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        MainApp.instance().startActivity(i);

        byte[] bytes = BleCommandUtil.getInstance().getEncryptedPacket(BleCommandUtil.DANAR_PACKET__OPCODE_ENCRYPTION__PASSKEY_REQUEST, null, null);
        log.debug(">>>>> " + "ENCRYPTION__PASSKEY_REQUEST" + " " + DanaRS_Packet.toHexString(bytes));
        writeCharacteristic_NO_RESPONSE(getUARTWriteBTGattChar(), bytes);
    }

    private void SendTimeInfo() {
        byte[] bytes = BleCommandUtil.getInstance().getEncryptedPacket(BleCommandUtil.DANAR_PACKET__OPCODE_ENCRYPTION__TIME_INFORMATION, null, null);
        log.debug(">>>>> " + "ENCRYPTION__TIME_INFORMATION" + " " + DanaRS_Packet.toHexString(bytes));
        writeCharacteristic_NO_RESPONSE(getUARTWriteBTGattChar(), bytes);
    }

    public void scheduleDisconnection() {
        class DisconnectRunnable implements Runnable {
            public void run() {
                disconnect("scheduleDisconnection");
                scheduledDisconnection = null;
            }
        }
        // prepare task for execution in 5 sec
        // cancel waiting task to prevent sending multiple disconnections
        if (scheduledDisconnection != null)
            scheduledDisconnection.cancel(false);
        Runnable task = new DisconnectRunnable();
        final int sec = 5;
        scheduledDisconnection = worker.schedule(task, sec, TimeUnit.SECONDS);
    }

}
