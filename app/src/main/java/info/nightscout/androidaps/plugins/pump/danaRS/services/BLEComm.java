package info.nightscout.androidaps.plugins.pump.danaRS.services;

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
import android.os.SystemClock;

import com.cozmo.danar.util.BleCommandUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.events.EventPumpStatusChanged;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.general.overview.events.EventNewNotification;
import info.nightscout.androidaps.plugins.general.overview.notifications.Notification;
import info.nightscout.androidaps.plugins.pump.danaR.DanaRPump;
import info.nightscout.androidaps.plugins.pump.danaRS.DanaRSPlugin;
import info.nightscout.androidaps.plugins.pump.danaRS.activities.PairingHelperActivity;
import info.nightscout.androidaps.plugins.pump.danaRS.comm.DanaRSMessageHashTable;
import info.nightscout.androidaps.plugins.pump.danaRS.comm.DanaRS_Packet;
import info.nightscout.androidaps.plugins.pump.danaRS.events.EventDanaRSPacket;
import info.nightscout.androidaps.plugins.pump.danaRS.events.EventDanaRSPairingSuccess;
import info.nightscout.androidaps.plugins.general.nsclient.NSUpload;
import info.nightscout.androidaps.utils.SP;

/**
 * Created by mike on 23.09.2017.
 */

public class BLEComm {
    private Logger log = LoggerFactory.getLogger(L.PUMPBTCOMM);

    private final long WRITE_DELAY_MILLIS = 50;

    private String UART_READ_UUID = "0000fff1-0000-1000-8000-00805f9b34fb";
    private String UART_WRITE_UUID = "0000fff2-0000-1000-8000-00805f9b34fb";

    private final byte PACKET_START_BYTE = (byte) 0xA5;
    private final byte PACKET_END_BYTE = (byte) 0x5A;
    private static BLEComm instance = null;

    public static BLEComm getInstance(DanaRSService service) {
        if (instance == null)
            instance = new BLEComm(service);
        return instance;
    }

    private ScheduledFuture<?> scheduledDisconnection = null;

    private DanaRS_Packet processsedMessage = null;
    private final ArrayList<byte[]> mSendQueue = new ArrayList<>();

    private BluetoothManager mBluetoothManager = null;
    private BluetoothAdapter mBluetoothAdapter = null;
    private String mBluetoothDeviceName = null;
    private BluetoothGatt mBluetoothGatt = null;

    protected boolean isConnected = false;
    protected boolean isConnecting = false;

    private BluetoothGattCharacteristic UART_Read;
    private BluetoothGattCharacteristic UART_Write;

    private DanaRSService service;

    private BLEComm(DanaRSService service) {
        this.service = service;
        initialize();
    }

    private boolean initialize() {
        if (L.isEnabled(L.PUMPBTCOMM))
            log.debug("Initializing BLEComm.");

        if (mBluetoothManager == null) {
            mBluetoothManager = ((BluetoothManager) MainApp.instance().getApplicationContext().getSystemService(Context.BLUETOOTH_SERVICE));
            if (mBluetoothManager == null) {
                log.error("Unable to initialize BluetoothManager.");
                return false;
            }
        }

        mBluetoothAdapter = mBluetoothManager.getAdapter();
        if (mBluetoothAdapter == null) {
            log.error("Unable to obtain a BluetoothAdapter.");
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
            log.error("unspecified address.");
            return false;
        }

        isConnecting = true;

        BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        if (device == null) {
            log.error("Device not found.  Unable to connect from: " + from);
            return false;
        }

        if (L.isEnabled(L.PUMPBTCOMM))
            log.debug("Trying to create a new connection from: " + from);
        mBluetoothDeviceName = device.getName();
        mBluetoothGatt = device.connectGatt(service.getApplicationContext(), false, mGattCallback);
        setCharacteristicNotification(getUARTReadBTGattChar(), true);
        return true;
    }

    public void stopConnecting() {
        isConnecting = false;
    }

    public synchronized void disconnect(String from) {
        if (L.isEnabled(L.PUMPBTCOMM))
            log.debug("disconnect from: " + from);

        // cancel previous scheduled disconnection to prevent closing upcomming connection
        if (scheduledDisconnection != null)
            scheduledDisconnection.cancel(false);
        scheduledDisconnection = null;

        if ((mBluetoothAdapter == null) || (mBluetoothGatt == null)) {
            log.error("disconnect not possible: (mBluetoothAdapter == null) " + (mBluetoothAdapter == null));
            log.error("disconnect not possible: (mBluetoothGatt == null) " + (mBluetoothGatt == null));
            return;
        }
        setCharacteristicNotification(getUARTReadBTGattChar(), false);
        mBluetoothGatt.disconnect();
        isConnected = false;
        SystemClock.sleep(2000);
    }

    public synchronized void close() {
        if (L.isEnabled(L.PUMPBTCOMM))
            log.debug("BluetoothAdapter close");
        if (mBluetoothGatt == null) {
            return;
        }

        mBluetoothGatt.close();
        mBluetoothGatt = null;
    }


    private String getConnectDeviceName() {
        return mBluetoothDeviceName;
    }

    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            onConnectionStateChangeSynchronized(gatt, status, newState); // call it synchronized
        }

        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (L.isEnabled(L.PUMPBTCOMM))
                log.debug("onServicesDiscovered");
            if (status == BluetoothGatt.GATT_SUCCESS) {
                findCharacteristic();
            }
            SendPumpCheck();
            // 1st message sent to pump after connect
        }

        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            if (L.isEnabled(L.PUMPBTCOMM))
                log.debug("onCharacteristicRead" + (characteristic != null ? ":" + DanaRS_Packet.toHexString(characteristic.getValue()) : ""));
            addToReadBuffer(characteristic.getValue());
            readDataParsing();
        }

        public void onCharacteristicChanged(BluetoothGatt gatt, final BluetoothGattCharacteristic characteristic) {
            if (L.isEnabled(L.PUMPBTCOMM))
                log.debug("onCharacteristicChanged" + (characteristic != null ? ":" + DanaRS_Packet.toHexString(characteristic.getValue()) : ""));
            addToReadBuffer(characteristic.getValue());
            new Thread(() -> readDataParsing()).start();
        }

        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            if (L.isEnabled(L.PUMPBTCOMM))
                log.debug("onCharacteristicWrite" + (characteristic != null ? ":" + DanaRS_Packet.toHexString(characteristic.getValue()) : ""));
            new Thread(() -> {
                synchronized (mSendQueue) {
                    // after message sent, check if there is the rest of the message waiting and send it
                    if (mSendQueue.size() > 0) {
                        byte[] bytes = mSendQueue.get(0);
                        mSendQueue.remove(0);
                        writeCharacteristic_NO_RESPONSE(getUARTWriteBTGattChar(), bytes);
                    }
                }
            }).start();
        }
    };

    private synchronized void setCharacteristicNotification(BluetoothGattCharacteristic characteristic, boolean enabled) {
        if (L.isEnabled(L.PUMPBTCOMM))
            log.debug("setCharacteristicNotification");
        if ((mBluetoothAdapter == null) || (mBluetoothGatt == null)) {
            log.error("BluetoothAdapter not initialized_ERROR");
            isConnecting = false;
            isConnected = false;
            return;
        }
        mBluetoothGatt.setCharacteristicNotification(characteristic, enabled);
    }

    public synchronized void readCharacteristic(BluetoothGattCharacteristic characteristic) {
        if (L.isEnabled(L.PUMPBTCOMM))
            log.debug("readCharacteristic");
        if ((mBluetoothAdapter == null) || (mBluetoothGatt == null)) {
            log.error("BluetoothAdapter not initialized_ERROR");
            isConnecting = false;
            isConnected = false;
            return;
        }
        mBluetoothGatt.readCharacteristic(characteristic);
    }

    private synchronized void writeCharacteristic_NO_RESPONSE(final BluetoothGattCharacteristic characteristic, final byte[] data) {
        new Thread(() -> {
            SystemClock.sleep(WRITE_DELAY_MILLIS);

            if ((mBluetoothAdapter == null) || (mBluetoothGatt == null)) {
                log.error("BluetoothAdapter not initialized_ERROR");
                isConnecting = false;
                isConnected = false;
                return;
            }

            characteristic.setValue(data);
            characteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
            if (L.isEnabled(L.PUMPBTCOMM))
                log.debug("writeCharacteristic:" + DanaRS_Packet.toHexString(data));
            mBluetoothGatt.writeCharacteristic(characteristic);
        }).start();
    }

    private BluetoothGattCharacteristic getUARTReadBTGattChar() {
        if (UART_Read == null) {
            UART_Read = new BluetoothGattCharacteristic(UUID.fromString(UART_READ_UUID), BluetoothGattCharacteristic.PROPERTY_READ | BluetoothGattCharacteristic.PROPERTY_NOTIFY, 0);
        }
        return UART_Read;
    }

    private BluetoothGattCharacteristic getUARTWriteBTGattChar() {
        if (UART_Write == null) {
            UART_Write = new BluetoothGattCharacteristic(UUID.fromString(UART_WRITE_UUID), BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE, 0);
        }
        return UART_Write;
    }

    private List<BluetoothGattService> getSupportedGattServices() {
        if (L.isEnabled(L.PUMPBTCOMM))
            log.debug("getSupportedGattServices");
        if ((mBluetoothAdapter == null) || (mBluetoothGatt == null)) {
            log.error("BluetoothAdapter not initialized_ERROR");
            isConnecting = false;
            isConnected = false;
            return null;
        }

        return mBluetoothGatt.getServices();
    }

    private void findCharacteristic() {
        List<BluetoothGattService> gattServices = getSupportedGattServices();

        if (gattServices == null) {
            return;
        }
        String uuid;

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

    private synchronized void onConnectionStateChangeSynchronized(BluetoothGatt gatt, int status, int newState) {
        if (L.isEnabled(L.PUMPBTCOMM))
            log.debug("onConnectionStateChange");

        if (newState == BluetoothProfile.STATE_CONNECTED) {
            mBluetoothGatt.discoverServices();
        } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
            close();
            isConnected = false;
            isConnecting = false;
            MainApp.bus().post(new EventPumpStatusChanged(EventPumpStatusChanged.DISCONNECTED));
            if (L.isEnabled(L.PUMPBTCOMM))
                log.debug("Device was disconnected " + gatt.getDevice().getName());//Device was disconnected
        }
    }

    private final byte[] readBuffer = new byte[1024];
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
            byte[] inputBuffer = null;
            synchronized (readBuffer) {
                // Find packet start [A5 A5]
                if (bufferLength >= 6) {
                    for (int idxStartByte = 0; idxStartByte < bufferLength - 2; idxStartByte++) {
                        if ((readBuffer[idxStartByte] == PACKET_START_BYTE) && (readBuffer[idxStartByte + 1] == PACKET_START_BYTE)) {
                            if (idxStartByte > 0) {
                                // if buffer doesn't start with signature remove the leading trash
                                if (L.isEnabled(L.PUMPBTCOMM))
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
                if (packetIsValid) {
                    inputBuffer = new byte[length + 7];
                    // copy packet to input buffer
                    System.arraycopy(readBuffer, 0, inputBuffer, 0, length + 7);
                    // Cut off the message from readBuffer
                    try {
                        System.arraycopy(readBuffer, length + 7, readBuffer, 0, bufferLength - (length + 7));
                    } catch (Exception e) {
                        log.error("length: " + length + "bufferLength: " + bufferLength);
                        throw e;
                    }
                    bufferLength -= (length + 7);
                    // now we have encrypted packet in inputBuffer
                }
            }
            if (packetIsValid) {
                try {
                    // decrypt the packet
                    inputBuffer = BleCommandUtil.getInstance().getDecryptedPacket(inputBuffer);

                    if (inputBuffer == null) {
                        log.error("Null decryptedInputBuffer");
                        return;
                    }

                    switch (inputBuffer[0]) {
                        // initial handshake packet
                        case (byte) BleCommandUtil.DANAR_PACKET__TYPE_ENCRYPTION_RESPONSE:
                            switch (inputBuffer[1]) {
                                // 1st packet
                                case (byte) BleCommandUtil.DANAR_PACKET__OPCODE_ENCRYPTION__PUMP_CHECK:
                                    if (inputBuffer.length == 4 && inputBuffer[2] == 'O' && inputBuffer[3] == 'K') {
                                        if (L.isEnabled(L.PUMPBTCOMM))
                                            log.debug("<<<<< " + "ENCRYPTION__PUMP_CHECK (OK)" + " " + DanaRS_Packet.toHexString(inputBuffer));
                                        // Grab pairing key from preferences if exists
                                        String pairingKey = SP.getString(MainApp.gs(R.string.key_danars_pairingkey) + DanaRSPlugin.mDeviceName, null);
                                        if (L.isEnabled(L.PUMPBTCOMM))
                                            log.debug("Using stored pairing key: " + pairingKey);
                                        if (pairingKey != null) {
                                            byte[] encodedPairingKey = DanaRS_Packet.hexToBytes(pairingKey);
                                            byte[] bytes = BleCommandUtil.getInstance().getEncryptedPacket(BleCommandUtil.DANAR_PACKET__OPCODE_ENCRYPTION__CHECK_PASSKEY, encodedPairingKey, null);
                                            if (L.isEnabled(L.PUMPBTCOMM))
                                                log.debug(">>>>> " + "ENCRYPTION__CHECK_PASSKEY" + " " + DanaRS_Packet.toHexString(bytes));
                                            writeCharacteristic_NO_RESPONSE(getUARTWriteBTGattChar(), bytes);
                                        } else {
                                            // Stored pairing key does not exists, request pairing
                                            SendPairingRequest();
                                        }

                                    } else if (inputBuffer.length == 6 && inputBuffer[2] == 'P' && inputBuffer[3] == 'U' && inputBuffer[4] == 'M' && inputBuffer[5] == 'P') {
                                        if (L.isEnabled(L.PUMPBTCOMM))
                                            log.debug("<<<<< " + "ENCRYPTION__PUMP_CHECK (PUMP)" + " " + DanaRS_Packet.toHexString(inputBuffer));
                                        mSendQueue.clear();
                                        MainApp.bus().post(new EventPumpStatusChanged(EventPumpStatusChanged.DISCONNECTED, MainApp.gs(R.string.pumperror)));
                                        NSUpload.uploadError(MainApp.gs(R.string.pumperror));
                                        Notification n = new Notification(Notification.PUMPERROR, MainApp.gs(R.string.pumperror), Notification.URGENT);
                                        MainApp.bus().post(new EventNewNotification(n));
                                    } else if (inputBuffer.length == 6 && inputBuffer[2] == 'B' && inputBuffer[3] == 'U' && inputBuffer[4] == 'S' && inputBuffer[5] == 'Y') {
                                        if (L.isEnabled(L.PUMPBTCOMM))
                                            log.debug("<<<<< " + "ENCRYPTION__PUMP_CHECK (BUSY)" + " " + DanaRS_Packet.toHexString(inputBuffer));
                                        mSendQueue.clear();
                                        MainApp.bus().post(new EventPumpStatusChanged(EventPumpStatusChanged.DISCONNECTED, MainApp.gs(R.string.pumpbusy)));
                                    } else {
                                        // ERROR in response, wrong serial number
                                        if (L.isEnabled(L.PUMPBTCOMM))
                                            log.debug("<<<<< " + "ENCRYPTION__PUMP_CHECK (ERROR)" + " " + DanaRS_Packet.toHexString(inputBuffer));
                                        mSendQueue.clear();
                                        MainApp.bus().post(new EventPumpStatusChanged(EventPumpStatusChanged.DISCONNECTED, MainApp.gs(R.string.connectionerror)));
                                        SP.remove(MainApp.gs(R.string.key_danars_pairingkey) + DanaRSPlugin.mDeviceName);
                                        Notification n = new Notification(Notification.WRONGSERIALNUMBER, MainApp.gs(R.string.wrongpassword), Notification.URGENT);
                                        MainApp.bus().post(new EventNewNotification(n));
                                    }
                                    break;
                                // 2nd packet, pairing key
                                case (byte) BleCommandUtil.DANAR_PACKET__OPCODE_ENCRYPTION__CHECK_PASSKEY:
                                    if (L.isEnabled(L.PUMPBTCOMM))
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
                                    if (L.isEnabled(L.PUMPBTCOMM))
                                        log.debug("<<<<< " + "ENCRYPTION__PASSKEY_REQUEST " + DanaRS_Packet.toHexString(inputBuffer));
                                    if (inputBuffer[2] != (byte) 0x00) {
                                        disconnect("passkey request failed");
                                    }
                                    break;
                                // Paring response, OK button on pump pressed
                                case (byte) BleCommandUtil.DANAR_PACKET__OPCODE_ENCRYPTION__PASSKEY_RETURN:
                                    if (L.isEnabled(L.PUMPBTCOMM))
                                        log.debug("<<<<< " + "ENCRYPTION__PASSKEY_RETURN " + DanaRS_Packet.toHexString(inputBuffer));
                                    // Paring is successfull, sending time info
                                    MainApp.bus().post(new EventDanaRSPairingSuccess());
                                    SendTimeInfo();
                                    byte[] pairingKey = {inputBuffer[2], inputBuffer[3]};
                                    // store pairing key to preferences
                                    SP.putString(MainApp.gs(R.string.key_danars_pairingkey) + DanaRSPlugin.mDeviceName, DanaRS_Packet.bytesToHex(pairingKey));
                                    if (L.isEnabled(L.PUMPBTCOMM))
                                        log.debug("Got pairing key: " + DanaRS_Packet.bytesToHex(pairingKey));
                                    break;
                                // time and user password information. last packet in handshake
                                case (byte) BleCommandUtil.DANAR_PACKET__OPCODE_ENCRYPTION__TIME_INFORMATION:
                                    if (L.isEnabled(L.PUMPBTCOMM))
                                        log.debug("<<<<< " + "ENCRYPTION__TIME_INFORMATION " + /*message.getMessageName() + " " + */ DanaRS_Packet.toHexString(inputBuffer));
                                    int size = inputBuffer.length;
                                    int pass = ((inputBuffer[size - 1] & 0x000000FF) << 8) + ((inputBuffer[size - 2] & 0x000000FF));
                                    pass = pass ^ 3463;
                                    DanaRPump.getInstance().rs_password = Integer.toHexString(pass);
                                    if (L.isEnabled(L.PUMPBTCOMM))
                                        log.debug("Pump user password: " + Integer.toHexString(pass));

                                    MainApp.bus().post(new EventPumpStatusChanged(EventPumpStatusChanged.CONNECTED));
                                    isConnected = true;
                                    isConnecting = false;
                                    if (L.isEnabled(L.PUMPBTCOMM))
                                        log.debug("RS connected and status read");
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
                                if (L.isEnabled(L.PUMPBTCOMM))
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
        if (L.isEnabled(L.PUMPBTCOMM))
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

        //SystemClock.sleep(200);
        if (!message.isReceived()) {
            log.warn("Reply not received " + message.getFriendlyName());
        }
    }

    private void SendPairingRequest() {
        // Start activity which is waiting 20sec
        // On pump pairing request is displayed and is waiting for conformation
        Intent i = new Intent();
        i.setClass(MainApp.instance(), PairingHelperActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        MainApp.instance().startActivity(i);

        byte[] bytes = BleCommandUtil.getInstance().getEncryptedPacket(BleCommandUtil.DANAR_PACKET__OPCODE_ENCRYPTION__PASSKEY_REQUEST, null, null);
        if (L.isEnabled(L.PUMPBTCOMM))
            log.debug(">>>>> " + "ENCRYPTION__PASSKEY_REQUEST" + " " + DanaRS_Packet.toHexString(bytes));
        writeCharacteristic_NO_RESPONSE(getUARTWriteBTGattChar(), bytes);
    }

    private void SendPumpCheck() {
        // 1st message sent to pump after connect
        String devicename = getConnectDeviceName();
        if(devicename == null || devicename.equals("")){
            Notification n = new Notification(Notification.DEVICENOTPAIRED, MainApp.gs(R.string.pairfirst), Notification.URGENT);
            MainApp.bus().post(new EventNewNotification(n));
            return;
        }
        byte[] bytes = BleCommandUtil.getInstance().getEncryptedPacket(BleCommandUtil.DANAR_PACKET__OPCODE_ENCRYPTION__PUMP_CHECK, null, devicename);
        if (L.isEnabled(L.PUMPBTCOMM))
            log.debug(">>>>> " + "ENCRYPTION__PUMP_CHECK (0x00)" + " " + DanaRS_Packet.toHexString(bytes));
        writeCharacteristic_NO_RESPONSE(getUARTWriteBTGattChar(), bytes);
    }

    private void SendTimeInfo() {
        byte[] bytes = BleCommandUtil.getInstance().getEncryptedPacket(BleCommandUtil.DANAR_PACKET__OPCODE_ENCRYPTION__TIME_INFORMATION, null, null);
        if (L.isEnabled(L.PUMPBTCOMM))
            log.debug(">>>>> " + "ENCRYPTION__TIME_INFORMATION" + " " + DanaRS_Packet.toHexString(bytes));
        writeCharacteristic_NO_RESPONSE(getUARTWriteBTGattChar(), bytes);
    }

}
