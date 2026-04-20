package info.nightscout.androidaps.plugins.pump.carelevo.data.model.ble

sealed class BleResponse<out T : Any> {
    data class RspResponse<out T : Any>(val data : T) : BleResponse<T>()
    data class Failure(val message : String) : BleResponse<Nothing>()
    data class Error(val e : Throwable) : BleResponse<Nothing>()
}