package info.nightscout.androidaps.plugins.pump.carelevo.ble.data

import com.google.android.gms.common.ConnectionResult
import java.util.UUID

data class CharacterResult(
    val uuidCharacteristic : UUID? = null,
    val value : ByteArray? = null,
    val codeStatus : Int? = null
) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as CharacterResult

        if (uuidCharacteristic != other.uuidCharacteristic) return false
        if (value != null) {
            if (other.value == null) return false
            if (!value.contentEquals(other.value)) return false
        } else if (other.value != null) return false
        if (codeStatus != other.codeStatus) return false

        return true
    }

    override fun hashCode(): Int {
        var result = uuidCharacteristic?.hashCode() ?: 0
        result = 31 * result + (value?.contentHashCode() ?: 0)
        result = 31 * result + (codeStatus ?: 0)
        return result
    }
}

sealed class PeripheralScanResult(
    open val value : List<ScannedDevice>
) {
    data class Init(
        override val value : List<ScannedDevice>
    ) : PeripheralScanResult(value)

    data class Success(
        override val value : List<ScannedDevice>
    ) : PeripheralScanResult(value)

    data class Failed(
        override val value : List<ScannedDevice>
    ) : PeripheralScanResult(value)
}

sealed class CommandResult<out T : Any> {
    data class Pending<out T : Any>(val data : T) : CommandResult<T>()
    data class Success<out T : Any>(val data : T) : CommandResult<T>()
    data class Failure(val state : FailureState, val message : String) : CommandResult<Nothing>()
    data class Error(val e : Throwable) : CommandResult<Nothing>()
}