package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;

import java.math.BigInteger;
import java.util.UUID;

import javax.inject.Inject;
import javax.inject.Singleton;

import info.nightscout.androidaps.logging.AAPSLogger;
import info.nightscout.androidaps.logging.LTag;
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.blecommand.BleCommand;
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.blecommand.BleCommandHello;
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.blecommand.BleCommandType;
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.exceptions.CharacteristicNotFoundException;
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.exceptions.CouldNotSendBleCmdException;
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.exceptions.CouldNotSendBleException;
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.exceptions.FailedToConnectException;
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.exceptions.ScanFailException;
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.exceptions.ServiceNotFoundException;
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.scan.PodScanner;

@Singleton
public class BleManager implements OmnipodDashCommunicationManager {
    private static final int CONNECT_TIMEOUT_MS = 5000;
    private static final int DISCOVER_SERVICES_TIMEOUT_MS = 5000;
    private static final String SERVICE_UUID = "1a7e-4024-e3ed-4464-8b7e-751e03d0dc5f";
    private static final String CMD_CHARACTERISTIC_UUID = "1a7e-2441-e3ed-4464-8b7e-751e03d0dc5f";
    private static final String DATA_CHARACTERISTIC_UUID = "1a7e-2442-e3ed-4464-8b7e-751e03d0dc5f";
    private static final int CONTROLLER_ID = 4242; // TODO read from preferences or somewhere else.
    private static BleManager instance = null;
    private final Context context;
    private final BluetoothAdapter bluetoothAdapter;
    private final BluetoothManager bluetoothManager;
    @Inject AAPSLogger aapsLogger;
    private String podAddress;
    private BluetoothGatt gatt;

    private BluetoothGattCharacteristic cmdCharacteristic;
    private BluetoothGattCharacteristic dataCharacteristic;

    @Inject
    public BleManager(Context context) {
        this.context = context;
        this.bluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        this.bluetoothAdapter = bluetoothManager.getAdapter();
    }

    public static BleManager getInstance(Context context) {
        BleManager ret;
        synchronized (BleManager.class) {
            if (instance == null) {
                instance = new BleManager(context);
            }
            ret = instance;
        }
        return ret;
    }

    private static UUID uuidFromString(String s) {
        return new UUID(
                new BigInteger(s.replace("-", "").substring(0, 16), 16).longValue(),
                new BigInteger(s.replace("-", "").substring(16), 16).longValue()
        );
    }

    public void activateNewPod()
            throws InterruptedException,
            ScanFailException,
            FailedToConnectException,
            CouldNotSendBleException {
        this.aapsLogger.info(LTag.PUMPBTCOMM, "starting new pod activation");
        PodScanner podScanner = new PodScanner(this.aapsLogger, this.bluetoothAdapter);
        this.podAddress = podScanner.scanForPod(PodScanner.SCAN_FOR_SERVICE_UUID, PodScanner.POD_ID_NOT_ACTIVATED).getScanResult().getDevice().getAddress();
        // For tests: this.podAddress = "B8:27:EB:1D:7E:BB";
        this.connect();
        // do the dance: send SP0, SP1, etc
        // get and save LTK
    }

    public void connect()
            throws FailedToConnectException,
            CouldNotSendBleException {
        // TODO: locking?

        BluetoothDevice podDevice = this.bluetoothAdapter.getRemoteDevice(this.podAddress);
        BluetoothGattCallback bleCommCallback = new BleCommCallbacks();

        aapsLogger.debug(LTag.PUMPBTCOMM, "Connecting to " + this.podAddress);
        gatt = podDevice.connectGatt(this.context, true, bleCommCallback, BluetoothDevice.TRANSPORT_LE);

        try {
            Thread.sleep(CONNECT_TIMEOUT_MS);
        } catch (InterruptedException e) {
            // we get interrupted on successful connection
            // TODO: interrupt this thread onConnect()
        }

        int connectionState = this.bluetoothManager.getConnectionState(podDevice, BluetoothProfile.GATT);
        aapsLogger.debug(LTag.PUMPBTCOMM, "GATT connection state: " + connectionState);
        if (connectionState != BluetoothProfile.STATE_CONNECTED) {
            throw new FailedToConnectException(this.podAddress);
        }
        this.discoverServicesAndSayHello(gatt);

    }

    private void discoverServicesAndSayHello(BluetoothGatt gatt)
            throws FailedToConnectException,
            CouldNotSendBleException {
        gatt.discoverServices();
        try {
            Thread.sleep(CONNECT_TIMEOUT_MS);
        } catch (InterruptedException e) {
            // we get interrupted on successfull connection
            // TODO: interrupt this thread onConnect()
        }

        BluetoothGattService service = gatt.getService(uuidFromString(SERVICE_UUID));
        if (service == null) {
            throw new ServiceNotFoundException(SERVICE_UUID);
        }
        BluetoothGattCharacteristic cmdChar = service.getCharacteristic(uuidFromString(CMD_CHARACTERISTIC_UUID));
        if (cmdChar == null) {
            throw new CharacteristicNotFoundException(CMD_CHARACTERISTIC_UUID);
        }
        BluetoothGattCharacteristic dataChar = service.getCharacteristic(uuidFromString(DATA_CHARACTERISTIC_UUID));
        if (dataChar == null) {
            throw new CharacteristicNotFoundException(DATA_CHARACTERISTIC_UUID);
        }
        this.cmdCharacteristic = cmdChar;
        this.dataCharacteristic = dataChar;

        BleCommand hello = new BleCommandHello(CONTROLLER_ID);
        if (!this.sendCmd(hello.asByteArray())) {
            throw new CouldNotSendBleCmdException();
        }
        aapsLogger.debug(LTag.PUMPBTCOMM, "saying hello to the pod" + hello.asByteArray());

    }

    private boolean sendCmd(byte[] payload) {
        // TODO move out of here
        this.cmdCharacteristic.setValue(payload);
        boolean ret = this.gatt.writeCharacteristic(cmdCharacteristic);
        aapsLogger.debug(LTag.PUMPBTCOMM, "Sending command status. data:" + payload.toString() + "status: " + ret);
        return ret;
    }
}

