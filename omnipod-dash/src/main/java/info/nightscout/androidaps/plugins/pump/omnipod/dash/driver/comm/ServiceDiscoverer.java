package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;

import java.math.BigInteger;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

import info.nightscout.androidaps.logging.AAPSLogger;
import info.nightscout.androidaps.logging.LTag;
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.callbacks.BleCommCallbacks;
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.exceptions.CharacteristicNotFoundException;
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.exceptions.ServiceNotFoundException;

public class ServiceDiscoverer {
    private static final String SERVICE_UUID = "1a7e-4024-e3ed-4464-8b7e-751e03d0dc5f";
    private static final int DISCOVER_SERVICES_TIMEOUT_MS = 5000;

    private final BluetoothGatt gatt;
    private final BleCommCallbacks bleCallbacks;
    private final AAPSLogger logger;
    private Map<CharacteristicType, BluetoothGattCharacteristic> chars;

    public ServiceDiscoverer(AAPSLogger logger, BluetoothGatt gatt, BleCommCallbacks bleCallbacks) {
        this.gatt = gatt;
        this.bleCallbacks = bleCallbacks;
        this.logger = logger;
    }

    private static UUID uuidFromString(String s) {
        return new UUID(
                new BigInteger(s.replace("-", "").substring(0, 16), 16).longValue(),
                new BigInteger(s.replace("-", "").substring(16), 16).longValue()
        );
    }

    /***
     *  This is first step after connection establishment
     */
    public Map<CharacteristicType, BluetoothGattCharacteristic> discoverServices()
            throws InterruptedException,
            ServiceNotFoundException,
            CharacteristicNotFoundException {

        logger.debug(LTag.PUMPBTCOMM, "Discovering services");
        gatt.discoverServices();
        this.bleCallbacks.waitForServiceDiscovery(DISCOVER_SERVICES_TIMEOUT_MS);
        logger.debug(LTag.PUMPBTCOMM, "Services discovered");

        BluetoothGattService service = gatt.getService(
                uuidFromString(SERVICE_UUID));
        if (service == null) {
            throw new ServiceNotFoundException(SERVICE_UUID);
        }
        BluetoothGattCharacteristic cmdChar = service.getCharacteristic(CharacteristicType.CMD.getUUID());
        if (cmdChar == null) {
            throw new CharacteristicNotFoundException(CharacteristicType.CMD.getValue());
        }
        BluetoothGattCharacteristic dataChar = service.getCharacteristic(CharacteristicType.DATA.getUUID());
        if (dataChar == null) {
            throw new CharacteristicNotFoundException(CharacteristicType.DATA.getValue());
        }
        Map<CharacteristicType, BluetoothGattCharacteristic> chars = new EnumMap(CharacteristicType.class);
        chars.put(CharacteristicType.CMD, cmdChar);
        chars.put(CharacteristicType.DATA, dataChar);
        this.chars = Collections.unmodifiableMap(chars);
        return this.chars;
    }

}

