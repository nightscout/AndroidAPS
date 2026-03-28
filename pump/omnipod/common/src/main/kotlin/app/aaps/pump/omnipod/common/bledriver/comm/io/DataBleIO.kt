package app.aaps.pump.omnipod.common.bledriver.comm.io

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.pump.omnipod.common.bledriver.comm.callbacks.BleCommCallbacks
import java.util.concurrent.BlockingQueue

class DataBleIO(
    logger: AAPSLogger,
    characteristic: BluetoothGattCharacteristic,
    incomingPackets: BlockingQueue<ByteArray>,
    gatt: BluetoothGatt,
    bleCommCallbacks: BleCommCallbacks
) : BleIO(
    logger,
    characteristic,
    incomingPackets,
    gatt,
    bleCommCallbacks,
    CharacteristicType.DATA
)
