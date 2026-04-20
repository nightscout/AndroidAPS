package info.nightscout.androidaps.plugins.pump.carelevo.data.common

import android.bluetooth.BluetoothGattCharacteristic
import info.nightscout.androidaps.plugins.pump.carelevo.ble.core.BleCommand
import info.nightscout.androidaps.plugins.pump.carelevo.ble.core.CarelevoBleController
import info.nightscout.androidaps.plugins.pump.carelevo.data.model.ble.BleResponse

inline fun <T : Any, reified V : Any> handleBtResponse(
    map : () -> V?
) : BleResponse<V> = runCatching {
    map()
}.fold(
    onSuccess = { model ->
        model?.run {
            BleResponse.RspResponse(this)
        } ?: BleResponse.Failure("parser error")
    },
    onFailure = {
        BleResponse.Error(it)
    }
)

internal fun createMessage(vararg msgPrice : ByteArray) : ByteArray {
    return msgPrice.reduce { acc, byteArray ->
        acc + byteArray
    }
}

internal fun buildWriteCommand(
    bleController : CarelevoBleController,
    msg : ByteArray
) : BleCommand {
    return CarelevoWriteCommandBuilder()
        .address(bleController.getConnectedAddress() ?: "")
        .rxUuid(bleController.getParams().rxUUID)
        .writeType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
        .payload(msg)
        .build()
}