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

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Semaphore;

import javax.inject.Inject;
import javax.inject.Singleton;

import info.nightscout.androidaps.logging.AAPSLogger;
import info.nightscout.androidaps.logging.LTag;
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
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service.RileyLinkServiceData;
import info.nightscout.androidaps.plugins.pump.common.utils.ByteUtil;
import info.nightscout.androidaps.plugins.pump.common.utils.ThreadUtil;
import info.nightscout.androidaps.utils.sharedPreferences.SP;

/**
 * Created by geoff on 5/26/16.
 * Added: State handling, configuration of RF for different configuration ranges, connection handling
 */
@Singleton
public class RileyLinkBLE {

    @Inject AAPSLogger aapsLogger;
    @Inject RileyLinkServiceData rileyLinkServiceData;
    @Inject RileyLinkUtil rileyLinkUtil;
    @Inject SP sp;

    private final Context context;
    private final boolean gattDebugEnabled = true;
    private boolean manualDisconnect = false;
    private final BluetoothAdapter bluetoothAdapter;
    private final BluetoothGattCallback bluetoothGattCallback;
    private BluetoothDevice rileyLinkDevice;
    private BluetoothGatt bluetoothConnectionGatt = null;
    private BLECommOperation mCurrentOperation;
    private final Semaphore gattOperationSema = new Semaphore(1, true);
    private Runnable radioResponseCountNotified;
    private boolean mIsConnected = false;

    @Inject
    public RileyLinkBLE(final Context context) {
        this.context = context;
        this.bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        bluetoothGattCallback = new BluetoothGattCallback() {

            @Override
            public void onCharacteristicChanged(final BluetoothGatt gatt,
                                                final BluetoothGattCharacteristic characteristic) {
                super.onCharacteristicChanged(gatt, characteristic);
                if (gattDebugEnabled) {
                    aapsLogger.debug(LTag.PUMPBTCOMM, ThreadUtil.sig() + "onCharacteristicChanged "
                            + GattAttributes.lookup(characteristic.getUuid()) + " "
                            + ByteUtil.getHex(characteristic.getValue()));
                    if (characteristic.getUuid().equals(UUID.fromString(GattAttributes.CHARA_RADIO_RESPONSE_COUNT))) {
                        aapsLogger.debug(LTag.PUMPBTCOMM, "Response Count is " + ByteUtil.shortHexString(characteristic.getValue()));
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
                if (gattDebugEnabled) {
                    aapsLogger.debug(LTag.PUMPBTCOMM, ThreadUtil.sig() + "onCharacteristicRead ("
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
                if (gattDebugEnabled) {
                    aapsLogger.debug(LTag.PUMPBTCOMM, ThreadUtil.sig() + "onCharacteristicWrite " + getGattStatusMessage(status) + " "
                            + uuidString + " " + ByteUtil.shortHexString(characteristic.getValue()));
                }
                mCurrentOperation.gattOperationCompletionCallback(characteristic.getUuid(), characteristic.getValue());
            }


            @Override
            public void onConnectionStateChange(final BluetoothGatt gatt, final int status, final int newState) {
                super.onConnectionStateChange(gatt, status, newState);

                // https://github.com/NordicSemiconductor/puck-central-android/blob/master/PuckCentral/app/src/main/java/no/nordicsemi/puckcentral/bluetooth/gatt/GattManager.java#L117
                if (status == 133) {
                    aapsLogger.error(LTag.PUMPBTCOMM, "Got the status 133 bug, closing gatt");
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

                    aapsLogger.warn(LTag.PUMPBTCOMM, "onConnectionStateChange " + getGattStatusMessage(status) + " " + stateMessage);
                }

                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        rileyLinkUtil.sendBroadcastMessage(RileyLinkConst.Intents.BluetoothConnected, context);
                    } else {
                        aapsLogger.debug(LTag.PUMPBTCOMM, "BT State connected, GATT status {} ({})", status, getGattStatusMessage(status));
                    }

                } else if ((newState == BluetoothProfile.STATE_CONNECTING) || //
                        (newState == BluetoothProfile.STATE_DISCONNECTING)) {
                    aapsLogger.debug(LTag.PUMPBTCOMM, "We are in {} state.", status == BluetoothProfile.STATE_CONNECTING ? "Connecting" :
                            "Disconnecting");
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    rileyLinkUtil.sendBroadcastMessage(RileyLinkConst.Intents.RileyLinkDisconnected, context);
                    if (manualDisconnect)
                        close();
                    aapsLogger.warn(LTag.PUMPBTCOMM, "RileyLink Disconnected.");
                } else {
                    aapsLogger.warn(LTag.PUMPBTCOMM, "Some other state: (status={},newState={})", status, newState);
                }
            }


            @Override
            public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
                super.onDescriptorWrite(gatt, descriptor, status);
                if (gattDebugEnabled) {
                    aapsLogger.warn(LTag.PUMPBTCOMM, "onDescriptorWrite " + GattAttributes.lookup(descriptor.getUuid()) + " "
                            + getGattStatusMessage(status) + " written: " + ByteUtil.getHex(descriptor.getValue()));
                }
                mCurrentOperation.gattOperationCompletionCallback(descriptor.getUuid(), descriptor.getValue());
            }


            @Override
            public void onDescriptorRead(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
                super.onDescriptorRead(gatt, descriptor, status);
                mCurrentOperation.gattOperationCompletionCallback(descriptor.getUuid(), descriptor.getValue());
                if (gattDebugEnabled) {
                    aapsLogger.warn(LTag.PUMPBTCOMM, "onDescriptorRead " + getGattStatusMessage(status) + " status " + descriptor);
                }
            }


            @Override
            public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
                super.onMtuChanged(gatt, mtu, status);
                if (gattDebugEnabled) {
                    aapsLogger.warn(LTag.PUMPBTCOMM, "onMtuChanged " + mtu + " status " + status);
                }
            }


            @Override
            public void onReadRemoteRssi(final BluetoothGatt gatt, int rssi, int status) {
                super.onReadRemoteRssi(gatt, rssi, status);
                if (gattDebugEnabled) {
                    aapsLogger.warn(LTag.PUMPBTCOMM, "onReadRemoteRssi " + getGattStatusMessage(status) + ": " + rssi);
                }
            }


            @Override
            public void onReliableWriteCompleted(BluetoothGatt gatt, int status) {
                super.onReliableWriteCompleted(gatt, status);
                if (gattDebugEnabled) {
                    aapsLogger.warn(LTag.PUMPBTCOMM, "onReliableWriteCompleted status " + status);
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

                    if (gattDebugEnabled) {
                        aapsLogger.warn(LTag.PUMPBTCOMM, "onServicesDiscovered " + getGattStatusMessage(status));
                    }

                    aapsLogger.info(LTag.PUMPBTCOMM, "Gatt device is RileyLink device: " + rileyLinkFound);

                    if (rileyLinkFound) {
                        mIsConnected = true;
                        rileyLinkUtil.sendBroadcastMessage(RileyLinkConst.Intents.RileyLinkReady, context);
                    } else {
                        mIsConnected = false;
                        rileyLinkServiceData.setServiceState(RileyLinkServiceState.RileyLinkError,
                                RileyLinkError.DeviceIsNotRileyLink);
                    }

                } else {
                    aapsLogger.debug(LTag.PUMPBTCOMM, "onServicesDiscovered " + getGattStatusMessage(status));
                    rileyLinkUtil.sendBroadcastMessage(RileyLinkConst.Intents.RileyLinkGattFailed, context);
                }
            }
        };
    }

    @Inject
    public void onInit() {
        aapsLogger.debug(LTag.PUMPBTCOMM, "BT Adapter: " + this.bluetoothAdapter);
    }


    private boolean isAnyRileyLinkServiceFound(BluetoothGattService service) {

        boolean found = GattAttributes.isRileyLink(service.getUuid());

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

            aapsLogger.warn(LTag.PUMPBTCOMM, stringBuilder.toString());

            List<BluetoothGattService> includedServices = service.getIncludedServices();

            for (BluetoothGattService serviceI : includedServices) {
                debugService(serviceI, indentCount + 4);
            }
        }
    }


    void registerRadioResponseCountNotification(Runnable notifier) {
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
            aapsLogger.warn(LTag.PUMPBTCOMM, "Starting to discover GATT Services.");
            return true;
        } else {
            aapsLogger.error(LTag.PUMPBTCOMM, "Cannot discover GATT Services.");
            return false;
        }
    }


    public boolean enableNotifications() {
        BLECommOperationResult result = setNotification_blocking(UUID.fromString(GattAttributes.SERVICE_RADIO), //
                UUID.fromString(GattAttributes.CHARA_RADIO_RESPONSE_COUNT));
        if (result.resultCode != BLECommOperationResult.RESULT_SUCCESS) {
            aapsLogger.error(LTag.PUMPBTCOMM, "Error setting response count notification");
            return false;
        }
        return true;
    }


    public void findRileyLink(String RileyLinkAddress) {
        aapsLogger.debug(LTag.PUMPBTCOMM, "RileyLink address: " + RileyLinkAddress);
        // Must verify that this is a valid MAC, or crash.

        rileyLinkDevice = bluetoothAdapter.getRemoteDevice(RileyLinkAddress);
        // if this succeeds, we get a connection state change callback?

        if (rileyLinkDevice != null) {
            connectGatt();
        } else {
            aapsLogger.error(LTag.PUMPBTCOMM, "RileyLink device not found with address: " + RileyLinkAddress);
        }
    }


    // This function must be run on UI thread.
    public void connectGatt() {
        if (this.rileyLinkDevice == null) {
            aapsLogger.error(LTag.PUMPBTCOMM, "RileyLink device is null, can't do connectGatt.");
            return;
        }

        bluetoothConnectionGatt = rileyLinkDevice.connectGatt(context, true, bluetoothGattCallback);
        // , BluetoothDevice.TRANSPORT_LE
        if (bluetoothConnectionGatt == null) {
            aapsLogger.error(LTag.PUMPBTCOMM, "Failed to connect to Bluetooth Low Energy device at " + bluetoothAdapter.getAddress());
        } else {
            if (gattDebugEnabled) {
                aapsLogger.debug(LTag.PUMPBTCOMM, "Gatt Connected.");
            }

            String deviceName = bluetoothConnectionGatt.getDevice().getName();
            if (StringUtils.isNotEmpty(deviceName)) {
                // Update stored name upon connecting (also for backwards compatibility for device where a name was not yet stored)
                sp.putString(RileyLinkConst.Prefs.RileyLinkName, deviceName);
            } else {
                sp.remove(RileyLinkConst.Prefs.RileyLinkName);
            }

            rileyLinkServiceData.rileyLinkName = deviceName;
            rileyLinkServiceData.rileyLinkAddress = bluetoothConnectionGatt.getDevice().getAddress();
        }
    }


    public void disconnect() {
        mIsConnected = false;
        aapsLogger.warn(LTag.PUMPBTCOMM, "Closing GATT connection");
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


    private BLECommOperationResult setNotification_blocking(UUID serviceUUID, UUID charaUUID) {
        BLECommOperationResult rval = new BLECommOperationResult();
        if (bluetoothConnectionGatt != null) {

            try {
                gattOperationSema.acquire();
                SystemClock.sleep(1); // attempting to yield thread, to make sequence of events easier to follow
            } catch (InterruptedException e) {
                aapsLogger.error(LTag.PUMPBTCOMM, "setNotification_blocking: interrupted waiting for gattOperationSema");
                return rval;
            }
            if (mCurrentOperation != null) {
                rval.resultCode = BLECommOperationResult.RESULT_BUSY;
            } else {
                if (bluetoothConnectionGatt.getService(serviceUUID) == null) {
                    // Catch if the service is not supported by the BLE device
                    rval.resultCode = BLECommOperationResult.RESULT_NONE;
                    aapsLogger.error(LTag.PUMPBTCOMM, "BT Device not supported");
                    // TODO: 11/07/2016 UI update for user
                    // xyz rileyLinkServiceData.setServiceState(RileyLinkServiceState.BluetoothError, RileyLinkError.NoBluetoothAdapter);
                } else {
                    BluetoothGattCharacteristic chara = bluetoothConnectionGatt.getService(serviceUUID)
                            .getCharacteristic(charaUUID);
                    // Tell Android that we want the notifications
                    bluetoothConnectionGatt.setCharacteristicNotification(chara, true);
                    List<BluetoothGattDescriptor> list = chara.getDescriptors();
                    if (gattDebugEnabled) {
                        for (int i = 0; i < list.size(); i++) {
                            aapsLogger.debug(LTag.PUMPBTCOMM, "Found descriptor: " + list.get(i).toString());
                        }
                    }
                    BluetoothGattDescriptor descr = list.get(0);
                    // Tell the remote device to send the notifications
                    mCurrentOperation = new DescriptorWriteOperation(aapsLogger, bluetoothConnectionGatt, descr,
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
            aapsLogger.error(LTag.PUMPBTCOMM, "setNotification_blocking: not configured!");
            rval.resultCode = BLECommOperationResult.RESULT_NOT_CONFIGURED;
        }
        return rval;
    }


    // call from main
    BLECommOperationResult writeCharacteristic_blocking(UUID serviceUUID, UUID charaUUID, byte[] value) {
        BLECommOperationResult rval = new BLECommOperationResult();
        if (bluetoothConnectionGatt != null) {
            rval.value = value;
            try {
                gattOperationSema.acquire();
                SystemClock.sleep(1); // attempting to yield thread, to make sequence of events easier to follow
            } catch (InterruptedException e) {
                aapsLogger.error(LTag.PUMPBTCOMM, "writeCharacteristic_blocking: interrupted waiting for gattOperationSema");
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
                    aapsLogger.error(LTag.PUMPBTCOMM, "BT Device not supported");
                    // TODO: 11/07/2016 UI update for user
                    // xyz rileyLinkServiceData.setServiceState(RileyLinkServiceState.BluetoothError, RileyLinkError.NoBluetoothAdapter);
                } else {
                    BluetoothGattCharacteristic chara = bluetoothConnectionGatt.getService(serviceUUID)
                            .getCharacteristic(charaUUID);
                    mCurrentOperation = new CharacteristicWriteOperation(aapsLogger, bluetoothConnectionGatt, chara, value);
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
            aapsLogger.error(LTag.PUMPBTCOMM, "writeCharacteristic_blocking: not configured!");
            rval.resultCode = BLECommOperationResult.RESULT_NOT_CONFIGURED;
        }
        return rval;
    }


    BLECommOperationResult readCharacteristic_blocking(UUID serviceUUID, UUID charaUUID) {
        BLECommOperationResult rval = new BLECommOperationResult();
        if (bluetoothConnectionGatt != null) {
            try {
                gattOperationSema.acquire();
                SystemClock.sleep(1); // attempting to yield thread, to make sequence of events easier to follow
            } catch (InterruptedException e) {
                aapsLogger.error(LTag.PUMPBTCOMM, "readCharacteristic_blocking: Interrupted waiting for gattOperationSema");
                return rval;
            }
            if (mCurrentOperation != null) {
                rval.resultCode = BLECommOperationResult.RESULT_BUSY;
            } else {
                if (bluetoothConnectionGatt.getService(serviceUUID) == null) {
                    // Catch if the service is not supported by the BLE device
                    rval.resultCode = BLECommOperationResult.RESULT_NONE;
                    aapsLogger.error(LTag.PUMPBTCOMM, "BT Device not supported");
                    // TODO: 11/07/2016 UI update for user
                    // xyz rileyLinkServiceData.setServiceState(RileyLinkServiceState.BluetoothError, RileyLinkError.NoBluetoothAdapter);
                } else {
                    BluetoothGattCharacteristic chara = bluetoothConnectionGatt.getService(serviceUUID).getCharacteristic(
                            charaUUID);
                    mCurrentOperation = new CharacteristicReadOperation(aapsLogger, bluetoothConnectionGatt, chara);
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
            aapsLogger.error(LTag.PUMPBTCOMM, "readCharacteristic_blocking: not configured!");
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
}
