package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.io;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;

import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import info.nightscout.androidaps.logging.AAPSLogger;
import info.nightscout.androidaps.logging.LTag;
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.callbacks.BleCommCallbacks;
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.CharacteristicType;
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.exceptions.BleIOBusyException;
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.exceptions.CouldNotConfirmDescriptorWriteException;
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.exceptions.CouldNotConfirmWrite;
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.exceptions.CouldNotEnableNotifications;
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.exceptions.CouldNotSendBleException;
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.exceptions.DescriptorNotFoundException;


public class BleIO {
    private static final int DEFAULT_IO_TIMEOUT_MS = 1000;

    private final AAPSLogger aapsLogger;
    private final Map<CharacteristicType, BluetoothGattCharacteristic> chars;
    private final Map<CharacteristicType, BlockingQueue<byte[]>> incomingPackets;
    private final BluetoothGatt gatt;
    private final BleCommCallbacks bleCommCallbacks;

    private IOState state;

    public BleIO(AAPSLogger aapsLogger, Map<CharacteristicType, BluetoothGattCharacteristic> chars, Map<CharacteristicType, BlockingQueue<byte[]>> incomingPackets, BluetoothGatt gatt, BleCommCallbacks bleCommCallbacks) {
        this.aapsLogger = aapsLogger;
        this.chars = chars;
        this.incomingPackets = incomingPackets;
        this.gatt = gatt;
        this.bleCommCallbacks = bleCommCallbacks;
        this.state = IOState.IDLE;
    }

    /***
     *
     * @param characteristic where to read from(CMD or DATA)
     * @return a byte array with the received data
     */
    public byte[] receivePacket(CharacteristicType characteristic) throws
            BleIOBusyException,
            InterruptedException,
            TimeoutException {
        synchronized (this.state) {
            if (this.state != IOState.IDLE) {
                throw new BleIOBusyException();
            }
            this.state = IOState.READING;
        }
        byte[] ret = this.incomingPackets.get(characteristic).poll(DEFAULT_IO_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        if (ret == null) {
            throw new TimeoutException();
        }
        synchronized (this.state) {
            this.state = IOState.IDLE;
        }
        return ret;
    }

    /***
     *
     * @param characteristic where to write to(CMD or DATA)
     * @param payload the data to send
     * @throws CouldNotSendBleException
     */
    public void sendAndConfirmPacket(CharacteristicType characteristic, byte[] payload)
            throws CouldNotSendBleException,
            BleIOBusyException,
            InterruptedException,
            CouldNotConfirmWrite,
            TimeoutException {
        synchronized (this.state) {
            if (this.state != IOState.IDLE) {
                throw new BleIOBusyException();
            }
            this.state = IOState.WRITING;
        }

        aapsLogger.debug(LTag.PUMPBTCOMM, "BleIO: Sending data on" + characteristic.name() + "/" + payload.toString());
        BluetoothGattCharacteristic ch = chars.get(characteristic);
        boolean set = ch.setValue(payload);
        if (!set) {
            throw new CouldNotSendBleException("setValue");
        }
        boolean sent = this.gatt.writeCharacteristic(ch);
        if (!sent) {
            throw new CouldNotSendBleException("writeCharacteristic");
        }
        this.bleCommCallbacks.confirmWrite(CharacteristicType.CMD, payload, DEFAULT_IO_TIMEOUT_MS);
        synchronized (this.state) {
            this.state = IOState.IDLE;
        }
    }

    /**
     * Called before sending a new message.
     * The incoming queues should be empty, so we log when they are not.
     */
    public void flushIncomingQueues() {

    }

    /**
     * Enable intentions on the characteristics.
     * This will signal the pod it can start sending back data
     * @return
     */
    public void readyToRead()
            throws CouldNotSendBleException,
            CouldNotEnableNotifications,
            DescriptorNotFoundException,
            InterruptedException, CouldNotConfirmDescriptorWriteException {

        for (CharacteristicType type : CharacteristicType.values()) {
            BluetoothGattCharacteristic ch = this.chars.get(type);
            boolean notificationSet = this.gatt.setCharacteristicNotification(ch, true);
            if (!notificationSet) {
                throw new CouldNotEnableNotifications(type);
            }
            List<BluetoothGattDescriptor> descriptors = ch.getDescriptors();
            if (descriptors.size() != 1) {
                throw new DescriptorNotFoundException();
            }
            BluetoothGattDescriptor descriptor = descriptors.get(0);
            descriptor.setValue(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE);
            gatt.writeDescriptor(descriptor);
            bleCommCallbacks.confirmWriteDescriptor(descriptor.getUuid().toString(), DEFAULT_IO_TIMEOUT_MS);
        }
    }
}
