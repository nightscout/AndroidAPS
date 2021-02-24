package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;

import java.util.Map;
import java.util.concurrent.BlockingQueue;

import info.nightscout.androidaps.logging.AAPSLogger;
import info.nightscout.androidaps.logging.LTag;
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.exceptions.CouldNotSendBleException;


public class BleIO {
    private static final int DEFAULT_IO_TIMEOUT_MS = 1000;

    private final AAPSLogger aapsLogger;
    private final Map<CharacteristicType, BluetoothGattCharacteristic> chars;
    private final Map<CharacteristicType, BlockingQueue<byte[]>> incomingPackets;
    private final BluetoothGatt gatt;


    public BleIO(AAPSLogger aapsLogger, Map<CharacteristicType, BluetoothGattCharacteristic> chars, Map<CharacteristicType, BlockingQueue<byte[]>> incomingPackets, BluetoothGatt gatt) {
        this.aapsLogger = aapsLogger;
        this.chars = chars;
        this.incomingPackets = incomingPackets;
        this.gatt = gatt;
    }

    /***
     *
     * @param characteristic where to read from(CMD or DATA)
     * @return a byte array with the received data
     */
    public byte[] receiveData(CharacteristicType characteristic) {
        return null;
    }

    /***
     *
     * @param characteristic where to write to(CMD or DATA)
     * @param packet the data to send
     * @throws CouldNotSendBleException
     */
    public void sendAndConfirmData(CharacteristicType characteristic, byte[] packet)
            throws CouldNotSendBleException {
        aapsLogger.debug(LTag.PUMPBTCOMM, "BleIO: Sending data on " + characteristic.name() + " :: "  +packet.toString());
        BluetoothGattCharacteristic ch = chars.get(characteristic);
        boolean set = ch.setValue(packet);
        if (!set) {
            throw new CouldNotSendBleException("setValue");
        }
        boolean sent = this.gatt.writeCharacteristic(ch);
        if (!sent) {
            throw new CouldNotSendBleException("writeCharacteristic");
        }
        // TODO: wait for confirmation callback
    }

    /**
     *  Called before sending a new message.
     *  The incoming queues should be empty, so we log when they are not.
     */
    public void flushIncomingQueues() {

    }

    /**
     * Enable intentions on the characteristics.
     * This will signal the pod it can start sending back data
     */
    public void readyToRead()
        throws CouldNotSendBleException {

    }
}
