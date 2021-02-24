package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeoutException;

import javax.inject.Inject;
import javax.inject.Singleton;

import info.nightscout.androidaps.logging.AAPSLogger;
import info.nightscout.androidaps.logging.LTag;
import info.nightscout.androidaps.plugins.pump.omnipod.dash.BuildConfig;
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.callbacks.BleCommCallbacks;
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.command.BleCommandHello;
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.exceptions.BleIOBusyException;
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.exceptions.CouldNotConfirmDescriptorWriteException;
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.exceptions.CouldNotConfirmWrite;
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.exceptions.CouldNotEnableNotifications;
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.exceptions.DescriptorNotFoundException;
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.io.BleIO;
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.exceptions.CouldNotSendBleException;
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.exceptions.FailedToConnectException;
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.exceptions.ScanFailException;
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.scan.PodScanner;

@Singleton
public class BleManager implements OmnipodDashCommunicationManager {
    private static final int CONNECT_TIMEOUT_MS = 5000;
    private static final int CONTROLLER_ID = 4242; // TODO read from preferences or somewhere else.

    private static BleManager instance = null;
    private final Context context;
    private final BluetoothAdapter bluetoothAdapter;
    private final BluetoothManager bluetoothManager;
    @Inject AAPSLogger aapsLogger;
    private String podAddress;
    private BluetoothGatt gatt;
    private BleIO bleio;

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

    public void activateNewPod()
            throws InterruptedException,
            ScanFailException,
            FailedToConnectException,
            CouldNotSendBleException,
            BleIOBusyException,
            TimeoutException,
            CouldNotConfirmWrite, CouldNotEnableNotifications, DescriptorNotFoundException, CouldNotConfirmDescriptorWriteException {
        this.aapsLogger.info(LTag.PUMPBTCOMM, "starting new pod activation");
        PodScanner podScanner = new PodScanner(this.aapsLogger, this.bluetoothAdapter);
        this.podAddress = podScanner.scanForPod(PodScanner.SCAN_FOR_SERVICE_UUID, PodScanner.POD_ID_NOT_ACTIVATED).getScanResult().getDevice().getAddress();
        // For tests: this.podAddress = "B8:27:EB:1D:7E:BB";
        this.connect();
    }

    public void connect()
            throws FailedToConnectException,
            CouldNotSendBleException,
            InterruptedException,
            BleIOBusyException, TimeoutException, CouldNotConfirmWrite, CouldNotEnableNotifications, DescriptorNotFoundException, CouldNotConfirmDescriptorWriteException {
        // TODO: locking?

        BluetoothDevice podDevice = this.bluetoothAdapter.getRemoteDevice(this.podAddress);

        Map<CharacteristicType, BlockingQueue<byte[]>> incomingPackets = new EnumMap<CharacteristicType, BlockingQueue<byte[]>>(CharacteristicType.class);
        incomingPackets.put(CharacteristicType.CMD, new LinkedBlockingDeque<>());
        incomingPackets.put(CharacteristicType.DATA, new LinkedBlockingDeque<>());
        incomingPackets = Collections.unmodifiableMap(incomingPackets);

        BleCommCallbacks bleCommCallbacks = new BleCommCallbacks(aapsLogger, incomingPackets);

        aapsLogger.debug(LTag.PUMPBTCOMM, "Connecting to " + this.podAddress);
        boolean autoConnect = true;
        if (BuildConfig.DEBUG) {
            autoConnect = false;
            // TODO: remove this in the future
            // it's easier to start testing from scratch on each run.
        }
        gatt = podDevice.connectGatt(this.context, autoConnect, bleCommCallbacks, BluetoothDevice.TRANSPORT_LE);

        bleCommCallbacks.waitForConnection(CONNECT_TIMEOUT_MS);

        int connectionState = this.bluetoothManager.getConnectionState(podDevice, BluetoothProfile.GATT);
        aapsLogger.debug(LTag.PUMPBTCOMM, "GATT connection state: " + connectionState);
        if (connectionState != BluetoothProfile.STATE_CONNECTED) {
            throw new FailedToConnectException(this.podAddress);
        }

        ServiceDiscoverer discoverer = new ServiceDiscoverer(this.aapsLogger, gatt, bleCommCallbacks);
        Map<CharacteristicType, BluetoothGattCharacteristic> chars = discoverer.discoverServices();

        this.bleio = new BleIO(aapsLogger, chars, incomingPackets, gatt, bleCommCallbacks);
        this.aapsLogger.debug(LTag.PUMPBTCOMM, "Saying hello to the pod");
        this.bleio.sendAndConfirmPacket(CharacteristicType.CMD, new BleCommandHello(CONTROLLER_ID).asByteArray());
        this.bleio.readyToRead();
    }
}
