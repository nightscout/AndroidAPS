package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.callbacks;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothProfile;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import info.nightscout.androidaps.logging.AAPSLogger;
import info.nightscout.androidaps.logging.LTag;
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.CharacteristicType;
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.exceptions.CouldNotConfirmDescriptorWriteException;
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.exceptions.CouldNotConfirmWrite;

public class BleCommCallbacks extends BluetoothGattCallback {
    private final static int WRITE_CONFIRM_TIMEOUT_MS = 10; // the other thread should be waiting for the exchange

    private final CountDownLatch serviceDiscoveryComplete;
    private final CountDownLatch connected;
    private final AAPSLogger aapsLogger;
    private final Map<CharacteristicType, BlockingQueue<byte[]>> incomingPackets;
    private final BlockingQueue<CharacteristicWriteConfirmation> writeQueue;
    private final BlockingQueue<DescriptorWriteConfirmation> descriptorWriteQueue;

    public BleCommCallbacks(AAPSLogger aapsLogger, Map<CharacteristicType, BlockingQueue<byte[]>> incomingPackets) {
        this.serviceDiscoveryComplete = new CountDownLatch(1);
        this.connected = new CountDownLatch(1);
        this.aapsLogger = aapsLogger;
        this.incomingPackets = incomingPackets;
        this.writeQueue = new LinkedBlockingQueue<>(1);
        this.descriptorWriteQueue = new LinkedBlockingQueue<>(1);
    }

    @Override public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
        super.onConnectionStateChange(gatt, status, newState);
        this.aapsLogger.debug(LTag.PUMPBTCOMM, "OnConnectionStateChange discovered with status/state" + status + "/" + newState);
        if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
            this.connected.countDown();
        }
    }

    @Override public void onServicesDiscovered(BluetoothGatt gatt, int status) {
        super.onServicesDiscovered(gatt, status);
        this.aapsLogger.debug(LTag.PUMPBTCOMM, "OnServicesDiscovered with status" + status);
        if (status == BluetoothGatt.GATT_SUCCESS) {
            this.serviceDiscoveryComplete.countDown();
        }
    }

    public void waitForConnection(int timeout_ms)
            throws InterruptedException {
        this.connected.await(timeout_ms, TimeUnit.MILLISECONDS);
    }

    public void waitForServiceDiscovery(int timeout_ms)
            throws InterruptedException {
        this.serviceDiscoveryComplete.await(timeout_ms, TimeUnit.MILLISECONDS);
    }

    public void confirmWrite(CharacteristicType characteristicType, byte[] expectedPayload, int timeout_ms) throws InterruptedException, TimeoutException, CouldNotConfirmWrite {
        CharacteristicWriteConfirmation received = this.writeQueue.poll(timeout_ms, TimeUnit.MILLISECONDS);
        if (received == null ) {
            throw new TimeoutException();
        }
        if (!Arrays.equals(expectedPayload, received.payload)) {
            this.aapsLogger.warn(LTag.PUMPBTCOMM, "Could not confirm write. Got " + received.payload + ".Excepted: " + expectedPayload + ". Status: "+received.status);
            throw new CouldNotConfirmWrite(expectedPayload, received.payload);
        }
        this.aapsLogger.debug(LTag.PUMPBTCOMM, "Confirmed write with value: " + received.payload);
    }

    @Override public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
        super.onCharacteristicWrite(gatt, characteristic, status);
        byte[] received = null;

        if (status == BluetoothGatt.GATT_SUCCESS) {
            received = characteristic.getValue();
            this.aapsLogger.debug(LTag.PUMPBTCOMM, "OnCharacteristicWrite value " + characteristic.getStringValue(0));
        }

        this.aapsLogger.debug(LTag.PUMPBTCOMM, "OnCharacteristicWrite with status/char/value " +
                status + "/" +
                CharacteristicType.byValue(characteristic.getUuid().toString()) + "/" +
                received);
        try {
            if (this.writeQueue.size() > 0) {
                this.aapsLogger.warn(LTag.PUMPBTCOMM, "Write confirm queue should be empty. found: "+ this.writeQueue.size());
                this.writeQueue.clear();
            }
            boolean offered = this.writeQueue.offer(new CharacteristicWriteConfirmation(received, status), WRITE_CONFIRM_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            if (!offered) {
                this.aapsLogger.warn(LTag.PUMPBTCOMM, "Received delayed write confirmation");
            }
        } catch (InterruptedException e) {
            this.aapsLogger.warn(LTag.PUMPBTCOMM, "Interrupted while sending write confirmation");
        }
    }

    @Override public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
        super.onCharacteristicChanged(gatt, characteristic);

        byte[] payload = characteristic.getValue();
        CharacteristicType characteristicType = CharacteristicType.byValue(characteristic.getUuid().toString());
        this.aapsLogger.debug(LTag.PUMPBTCOMM, "OnCharacteristicChanged with char/value " +
                characteristicType + "/" +
                payload);
        this.incomingPackets.get(characteristicType).add(payload);
    }

    public void confirmWriteDescriptor(String descriptorUUID, int timeout_ms) throws InterruptedException, CouldNotConfirmDescriptorWriteException {
        DescriptorWriteConfirmation confirmed = this.descriptorWriteQueue.poll(timeout_ms, TimeUnit.MILLISECONDS);
        if (!descriptorUUID.equals(confirmed.uuid)) {
            this.aapsLogger.warn(LTag.PUMPBTCOMM, "Could not confirm descriptor write. Got " + confirmed.uuid + ".Expected: " + descriptorUUID + ". Status: " + confirmed.status);
            throw new CouldNotConfirmDescriptorWriteException(confirmed.uuid, descriptorUUID);
        }
    }

    @Override public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
        super.onDescriptorWrite(gatt, descriptor, status);
        String uuid = null;
        if (status == BluetoothGatt.GATT_SUCCESS) {
            uuid = descriptor.getUuid().toString();
        }
        DescriptorWriteConfirmation confirmation = new DescriptorWriteConfirmation(status, uuid);
        try {
            if (this.descriptorWriteQueue.size() > 0) {
                this.aapsLogger.warn(LTag.PUMPBTCOMM, "Descriptor write queue should be empty, found: "+ this.descriptorWriteQueue.size());
                this.descriptorWriteQueue.clear();
            }

            boolean offered = this.descriptorWriteQueue.offer(confirmation, WRITE_CONFIRM_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            if (!offered) {
                this.aapsLogger.warn(LTag.PUMPBTCOMM, "Received delayed descriptor write confirmation");
            }
        } catch (InterruptedException e) {
            this.aapsLogger.warn(LTag.PUMPBTCOMM, "Interrupted while sending descriptor write confirmation");
        }
    }
}
