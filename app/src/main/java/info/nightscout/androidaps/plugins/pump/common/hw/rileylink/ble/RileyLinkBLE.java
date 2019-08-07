package info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.os.SystemClock;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Semaphore;

import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.RileyLinkConst;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.RileyLinkUtil;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.data.GattAttributes;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.operations.BLECommOperation;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.operations.BLECommOperationResult;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.operations.CharacteristicReadOperation;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.operations.CharacteristicWriteOperation;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.operations.DescriptorWriteOperation;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.RileyLinkError;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.RileyLinkServiceState;
import info.nightscout.androidaps.plugins.pump.common.utils.ByteUtil;
import info.nightscout.androidaps.plugins.pump.common.utils.ThreadUtil;

/**
 * Created by geoff on 5/26/16.
 * Added: State handling, configuration of RF for different configuration ranges, connection handling
 */
public class RileyLinkBLE {

    private static final Logger LOG = LoggerFactory.getLogger(L.PUMPBTCOMM);

    private final Context context;
    public boolean gattDebugEnabled = true;
    boolean manualDisconnect = false;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothGattCallback bluetoothGattCallback;
    private BluetoothDevice rileyLinkDevice;
    private BluetoothGatt bluetoothConnectionGatt = null;
    private BLECommOperation mCurrentOperation;
    private Semaphore gattOperationSema = new Semaphore(1, true);
    private Runnable radioResponseCountNotified;
    private boolean mIsConnected = false;


    public RileyLinkBLE(final Context context) {
        this.context = context;
        this.bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        if (isLogEnabled())
            LOG.debug("BT Adapter: " + this.bluetoothAdapter);
        bluetoothGattCallback = new BluetoothGattCallback() {

            @Override
            public void onCharacteristicChanged(final BluetoothGatt gatt,
                                                final BluetoothGattCharacteristic characteristic) {
                super.onCharacteristicChanged(gatt, characteristic);
                if (gattDebugEnabled && isLogEnabled()) {
                    LOG.trace(ThreadUtil.sig() + "onCharacteristicChanged "
                            + GattAttributes.lookup(characteristic.getUuid()) + " "
                            + ByteUtil.getHex(characteristic.getValue()));
                    if (characteristic.getUuid().equals(UUID.fromString(GattAttributes.CHARA_RADIO_RESPONSE_COUNT))) {
                        LOG.debug("Response Count is " + ByteUtil.shortHexString(characteristic.getValue()));
                    }
                }
                if (radioResponseCountNotified != null) {
                    radioResponseCountNotified.run();
                }
            }


            @Override
            public void onCharacteristicRead(final BluetoothGatt gatt,
                                             final BluetoothGattCharacteristic characteristic, int status) {
                super.onCharacteristicRead(gatt, characteristic, status);

                final String statusMessage = getGattStatusMessage(status);
                if (gattDebugEnabled && isLogEnabled()) {
                    LOG.trace(ThreadUtil.sig() + "onCharacteristicRead ("
                            + GattAttributes.lookup(characteristic.getUuid()) + ") " + statusMessage + ":"
                            + ByteUtil.getHex(characteristic.getValue()));
                }
                mCurrentOperation.gattOperationCompletionCallback(characteristic.getUuid(), characteristic.getValue());
            }


            @Override
            public void onCharacteristicWrite(final BluetoothGatt gatt,
                                              final BluetoothGattCharacteristic characteristic, int status) {
                super.onCharacteristicWrite(gatt, characteristic, status);

                final String uuidString = GattAttributes.lookup(characteristic.getUuid());
                if (gattDebugEnabled && isLogEnabled()) {
                    LOG.trace(ThreadUtil.sig() + "onCharacteristicWrite " + getGattStatusMessage(status) + " "
                            + uuidString + " " + ByteUtil.shortHexString(characteristic.getValue()));
                }
                mCurrentOperation.gattOperationCompletionCallback(characteristic.getUuid(), characteristic.getValue());
            }


            @Override
            public void onConnectionStateChange(final BluetoothGatt gatt, final int status, final int newState) {
                super.onConnectionStateChange(gatt, status, newState);

                // https://github.com/NordicSemiconductor/puck-central-android/blob/master/PuckCentral/app/src/main/java/no/nordicsemi/puckcentral/bluetooth/gatt/GattManager.java#L117
                if (status == 133) {
                    LOG.error("Got the status 133 bug, closing gatt");
                    disconnect();
                    SystemClock.sleep(500);
                    return;
                }

                if (gattDebugEnabled) {
                    final String stateMessage;
                    if (newState == BluetoothProfile.STATE_CONNECTED) {
                        stateMessage = "CONNECTED";
                    } else if (newState == BluetoothProfile.STATE_CONNECTING) {
                        stateMessage = "CONNECTING";
                    } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                        stateMessage = "DISCONNECTED";
                    } else if (newState == BluetoothProfile.STATE_DISCONNECTING) {
                        stateMessage = "DISCONNECTING";
                    } else {
                        stateMessage = "UNKNOWN newState (" + newState + ")";
                    }

                    if (isLogEnabled())
                        LOG.warn("onConnectionStateChange " + getGattStatusMessage(status) + " " + stateMessage);
                }

                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        RileyLinkUtil.sendBroadcastMessage(RileyLinkConst.Intents.BluetoothConnected);
                    } else {
                        if (isLogEnabled())
                            LOG.debug("BT State connected, GATT status {} ({})", status, getGattStatusMessage(status));
                    }

                } else if ((newState == BluetoothProfile.STATE_CONNECTING) || //
                        (newState == BluetoothProfile.STATE_DISCONNECTING)) {
                    // LOG.debug("We are in {} state.", status == BluetoothProfile.STATE_CONNECTING ? "Connecting" :
                    // "Disconnecting");
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    RileyLinkUtil.sendBroadcastMessage(RileyLinkConst.Intents.RileyLinkDisconnected);
                    if (manualDisconnect)
                        close();
                    LOG.warn("RileyLink Disconnected.");
                } else {
                    LOG.warn("Some other state: (status={},newState={})", status, newState);
                }
            }


            @Override
            public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
                super.onDescriptorWrite(gatt, descriptor, status);
                if (gattDebugEnabled && isLogEnabled()) {
                    LOG.warn("onDescriptorWrite " + GattAttributes.lookup(descriptor.getUuid()) + " "
                            + getGattStatusMessage(status) + " written: " + ByteUtil.getHex(descriptor.getValue()));
                }
                mCurrentOperation.gattOperationCompletionCallback(descriptor.getUuid(), descriptor.getValue());
            }


            @Override
            public void onDescriptorRead(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
                super.onDescriptorRead(gatt, descriptor, status);
                mCurrentOperation.gattOperationCompletionCallback(descriptor.getUuid(), descriptor.getValue());
                if (gattDebugEnabled && isLogEnabled()) {
                    LOG.warn("onDescriptorRead " + getGattStatusMessage(status) + " status " + descriptor);
                }
            }


            @Override
            public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
                super.onMtuChanged(gatt, mtu, status);
                if (gattDebugEnabled && isLogEnabled()) {
                    LOG.warn("onMtuChanged " + mtu + " status " + status);
                }
            }


            @Override
            public void onReadRemoteRssi(final BluetoothGatt gatt, int rssi, int status) {
                super.onReadRemoteRssi(gatt, rssi, status);
                if (gattDebugEnabled && isLogEnabled()) {
                    LOG.warn("onReadRemoteRssi " + getGattStatusMessage(status) + ": " + rssi);
                }
            }


            @Override
            public void onReliableWriteCompleted(BluetoothGatt gatt, int status) {
                super.onReliableWriteCompleted(gatt, status);
                if (gattDebugEnabled && isLogEnabled()) {
                    LOG.warn("onReliableWriteCompleted status " + status);
                }
            }


            @Override
            public void onServicesDiscovered(final BluetoothGatt gatt, int status) {
                super.onServicesDiscovered(gatt, status);

                if (status == BluetoothGatt.GATT_SUCCESS) {
                    final List<BluetoothGattService> services = gatt.getServices();

                    boolean rileyLinkFound = false;

                    for (BluetoothGattService service : services) {
                        final UUID uuidService = service.getUuid();

                        if (isAnyRileyLinkServiceFound(service)) {
                            rileyLinkFound = true;
                        }

                        if (gattDebugEnabled) {
                            debugService(service, 0);
                        }
                    }

                    if (gattDebugEnabled && isLogEnabled()) {
                        LOG.warn("onServicesDiscovered " + getGattStatusMessage(status));
                    }

                    LOG.info("Gatt device is RileyLink device: " + rileyLinkFound);

                    if (rileyLinkFound) {
                        mIsConnected = true;
                        RileyLinkUtil.sendBroadcastMessage(RileyLinkConst.Intents.RileyLinkReady);
                        // RileyLinkUtil.sendNotification(new
                        // ServiceNotification(RileyLinkConst.Intents.RileyLinkReady), null);
                    } else {
                        mIsConnected = false;
                        RileyLinkUtil.setServiceState(RileyLinkServiceState.RileyLinkError,
                                RileyLinkError.DeviceIsNotRileyLink);
                    }

                } else {
                    if (isLogEnabled())
                        LOG.debug("onServicesDiscovered " + getGattStatusMessage(status));
                    RileyLinkUtil.sendBroadcastMessage(RileyLinkConst.Intents.RileyLinkGattFailed);
                }
            }
        };
    }


    private boolean isAnyRileyLinkServiceFound(BluetoothGattService service) {

        boolean found = false;

        found = GattAttributes.isRileyLink(service.getUuid());

        if (found) {
            return true;
        } else {
            List<BluetoothGattService> includedServices = service.getIncludedServices();

            for (BluetoothGattService serviceI : includedServices) {
                if (isAnyRileyLinkServiceFound(serviceI)) {
                    return true;
                }

            }
        }

        return false;
    }


    public BluetoothDevice getRileyLinkDevice() {
        return this.rileyLinkDevice;
    }


    public void debugService(BluetoothGattService service, int indentCount) {

        String indentString = StringUtils.repeat(' ', indentCount);

        final UUID uuidService = service.getUuid();

        if (gattDebugEnabled) {
            final String uuidServiceString = uuidService.toString();

            StringBuilder stringBuilder = new StringBuilder();

            stringBuilder.append(indentString);
            stringBuilder.append(GattAttributes.lookup(uuidServiceString, "Unknown service"));
            stringBuilder.append(" (" + uuidServiceString + ")");

            for (BluetoothGattCharacteristic character : service.getCharacteristics()) {
                final String uuidCharacteristicString = character.getUuid().toString();

                stringBuilder.append("\n    ");
                stringBuilder.append(indentString);
                stringBuilder.append(" - " + GattAttributes.lookup(uuidCharacteristicString, "Unknown Characteristic"));
                stringBuilder.append(" (" + uuidCharacteristicString + ")");
            }

            stringBuilder.append("\n\n");

            LOG.warn(stringBuilder.toString());

            List<BluetoothGattService> includedServices = service.getIncludedServices();

            for (BluetoothGattService serviceI : includedServices) {
                debugService(serviceI, indentCount + 4);
            }
        }
    }


    public void registerRadioResponseCountNotification(Runnable notifier) {
        radioResponseCountNotified = notifier;
    }


    public boolean isConnected() {
        return mIsConnected;
    }


    public boolean discoverServices() {

        if (bluetoothConnectionGatt == null) {
            // shouldn't happen, but if it does we exit
            return false;
        }

        if (bluetoothConnectionGatt.discoverServices()) {
            if (isLogEnabled())
                LOG.warn("Starting to discover GATT Services.");
            return true;
        } else {
            LOG.error("Cannot discover GATT Services.");
            return false;
        }
    }


    public boolean enableNotifications() {
        BLECommOperationResult result = setNotification_blocking(UUID.fromString(GattAttributes.SERVICE_RADIO), //
                UUID.fromString(GattAttributes.CHARA_RADIO_RESPONSE_COUNT));
        if (result.resultCode != BLECommOperationResult.RESULT_SUCCESS) {
            LOG.error("Error setting response count notification");
            return false;
        }
        return true;
    }


    public void findRileyLink(String RileyLinkAddress) {
        if (isLogEnabled())
            LOG.debug("RileyLink address: " + RileyLinkAddress);
        // Must verify that this is a valid MAC, or crash.

        rileyLinkDevice = bluetoothAdapter.getRemoteDevice(RileyLinkAddress);
        // if this succeeds, we get a connection state change callback?

        if (rileyLinkDevice!=null) {
            connectGatt();
        } else {
            LOG.error("RileyLink device not found with address: " + RileyLinkAddress);
        }
    }


    // This function must be run on UI thread.
    public void connectGatt() {
        if (this.rileyLinkDevice==null) {
            LOG.error("RileyLink device is null, can't do connectGatt.");
            return;
        }

        bluetoothConnectionGatt = rileyLinkDevice.connectGatt(context, true, bluetoothGattCallback);
        // , BluetoothDevice.TRANSPORT_LE
        if (bluetoothConnectionGatt == null) {
            LOG.error("Failed to connect to Bluetooth Low Energy device at " + bluetoothAdapter.getAddress());
        } else {
            if (gattDebugEnabled) {
                if (isLogEnabled())
                    LOG.debug("Gatt Connected.");
            }
        }
    }


    public void disconnect() {
        mIsConnected = false;
        if (isLogEnabled())
            LOG.warn("Closing GATT connection");
        // Close old conenction
        if (bluetoothConnectionGatt != null) {
            // Not sure if to disconnect or to close first..
            bluetoothConnectionGatt.disconnect();
            manualDisconnect = true;
        }
    }


    public void close() {
        if (bluetoothConnectionGatt != null) {
            bluetoothConnectionGatt.close();
            bluetoothConnectionGatt = null;
        }
    }


    public BLECommOperationResult setNotification_blocking(UUID serviceUUID, UUID charaUUID) {
        BLECommOperationResult rval = new BLECommOperationResult();
        if (bluetoothConnectionGatt != null) {

            try {
                gattOperationSema.acquire();
                SystemClock.sleep(1); // attempting to yield thread, to make sequence of events easier to follow
            } catch (InterruptedException e) {
                LOG.error("setNotification_blocking: interrupted waiting for gattOperationSema");
                return rval;
            }
            if (mCurrentOperation != null) {
                rval.resultCode = BLECommOperationResult.RESULT_BUSY;
            } else {
                if (bluetoothConnectionGatt.getService(serviceUUID) == null) {
                    // Catch if the service is not supported by the BLE device
                    rval.resultCode = BLECommOperationResult.RESULT_NONE;
                    LOG.error("BT Device not supported");
                    // TODO: 11/07/2016 UI update for user
                } else {
                    BluetoothGattCharacteristic chara = bluetoothConnectionGatt.getService(serviceUUID)
                            .getCharacteristic(charaUUID);
                    // Tell Android that we want the notifications
                    bluetoothConnectionGatt.setCharacteristicNotification(chara, true);
                    List<BluetoothGattDescriptor> list = chara.getDescriptors();
                    if (gattDebugEnabled) {
                        for (int i = 0; i < list.size(); i++) {
                            if (isLogEnabled())
                                LOG.debug("Found descriptor: " + list.get(i).toString());
                        }
                    }
                    BluetoothGattDescriptor descr = list.get(0);
                    // Tell the remote device to send the notifications
                    mCurrentOperation = new DescriptorWriteOperation(bluetoothConnectionGatt, descr,
                            BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                    mCurrentOperation.execute(this);
                    if (mCurrentOperation.timedOut) {
                        rval.resultCode = BLECommOperationResult.RESULT_TIMEOUT;
                    } else if (mCurrentOperation.interrupted) {
                        rval.resultCode = BLECommOperationResult.RESULT_INTERRUPTED;
                    } else {
                        rval.resultCode = BLECommOperationResult.RESULT_SUCCESS;
                    }
                }
                mCurrentOperation = null;
                gattOperationSema.release();
            }
        } else {
            LOG.error("setNotification_blocking: not configured!");
            rval.resultCode = BLECommOperationResult.RESULT_NOT_CONFIGURED;
        }
        return rval;
    }


    // call from main
    public BLECommOperationResult writeCharacteristic_blocking(UUID serviceUUID, UUID charaUUID, byte[] value) {
        BLECommOperationResult rval = new BLECommOperationResult();
        if (bluetoothConnectionGatt != null) {
            rval.value = value;
            try {
                gattOperationSema.acquire();
                SystemClock.sleep(1); // attempting to yield thread, to make sequence of events easier to follow
            } catch (InterruptedException e) {
                LOG.error("writeCharacteristic_blocking: interrupted waiting for gattOperationSema");
                return rval;
            }

            if (mCurrentOperation != null) {
                rval.resultCode = BLECommOperationResult.RESULT_BUSY;
            } else {
                if (bluetoothConnectionGatt.getService(serviceUUID) == null) {
                    // Catch if the service is not supported by the BLE device
                    // GGW: Tue Jul 12 01:14:01 UTC 2016: This can also happen if the
                    // app that created the bluetoothConnectionGatt has been destroyed/created,
                    // e.g. when the user switches from portrait to landscape.
                    rval.resultCode = BLECommOperationResult.RESULT_NONE;
                    LOG.error("BT Device not supported");
                    // TODO: 11/07/2016 UI update for user
                } else {
                    BluetoothGattCharacteristic chara = bluetoothConnectionGatt.getService(serviceUUID)
                            .getCharacteristic(charaUUID);
                    mCurrentOperation = new CharacteristicWriteOperation(bluetoothConnectionGatt, chara, value);
                    mCurrentOperation.execute(this);
                    if (mCurrentOperation.timedOut) {
                        rval.resultCode = BLECommOperationResult.RESULT_TIMEOUT;
                    } else if (mCurrentOperation.interrupted) {
                        rval.resultCode = BLECommOperationResult.RESULT_INTERRUPTED;
                    } else {
                        rval.resultCode = BLECommOperationResult.RESULT_SUCCESS;
                    }
                }
                mCurrentOperation = null;
                gattOperationSema.release();
            }
        } else {
            LOG.error("writeCharacteristic_blocking: not configured!");
            rval.resultCode = BLECommOperationResult.RESULT_NOT_CONFIGURED;
        }
        return rval;
    }


    public BLECommOperationResult readCharacteristic_blocking(UUID serviceUUID, UUID charaUUID) {
        BLECommOperationResult rval = new BLECommOperationResult();
        if (bluetoothConnectionGatt != null) {
            try {
                gattOperationSema.acquire();
                SystemClock.sleep(1); // attempting to yield thread, to make sequence of events easier to follow
            } catch (InterruptedException e) {
                LOG.error("readCharacteristic_blocking: Interrupted waiting for gattOperationSema");
                return rval;
            }
            if (mCurrentOperation != null) {
                rval.resultCode = BLECommOperationResult.RESULT_BUSY;
            } else {
                if (bluetoothConnectionGatt.getService(serviceUUID) == null) {
                    // Catch if the service is not supported by the BLE device
                    rval.resultCode = BLECommOperationResult.RESULT_NONE;
                    LOG.error("BT Device not supported");
                    // TODO: 11/07/2016 UI update for user
                } else {
                    BluetoothGattCharacteristic chara = bluetoothConnectionGatt.getService(serviceUUID).getCharacteristic(
                            charaUUID);
                    mCurrentOperation = new CharacteristicReadOperation(bluetoothConnectionGatt, chara);
                    mCurrentOperation.execute(this);
                    if (mCurrentOperation.timedOut) {
                        rval.resultCode = BLECommOperationResult.RESULT_TIMEOUT;
                    } else if (mCurrentOperation.interrupted) {
                        rval.resultCode = BLECommOperationResult.RESULT_INTERRUPTED;
                    } else {
                        rval.resultCode = BLECommOperationResult.RESULT_SUCCESS;
                        rval.value = mCurrentOperation.getValue();
                    }
                }
            }
            mCurrentOperation = null;
            gattOperationSema.release();
        } else {
            LOG.error("readCharacteristic_blocking: not configured!");
            rval.resultCode = BLECommOperationResult.RESULT_NOT_CONFIGURED;
        }
        return rval;
    }


    private String getGattStatusMessage(final int status) {
        final String statusMessage;
        if (status == BluetoothGatt.GATT_SUCCESS) {
            statusMessage = "SUCCESS";
        } else if (status == BluetoothGatt.GATT_FAILURE) {
            statusMessage = "FAILED";
        } else if (status == BluetoothGatt.GATT_WRITE_NOT_PERMITTED) {
            statusMessage = "NOT PERMITTED";
        } else if (status == 133) {
            statusMessage = "Found the strange 133 bug";
        } else {
            statusMessage = "UNKNOWN (" + status + ")";
        }

        return statusMessage;
    }

    private boolean isLogEnabled() {
        return L.isEnabled(L.PUMPBTCOMM);
    }

}
