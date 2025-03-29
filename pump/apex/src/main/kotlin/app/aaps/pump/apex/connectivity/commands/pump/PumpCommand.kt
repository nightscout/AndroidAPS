package app.aaps.pump.apex.connectivity.commands.pump

import app.aaps.pump.apex.connectivity.commands.CommandId
import androidx.annotation.VisibleForTesting
import app.aaps.core.utils.toHex
import app.aaps.pump.apex.utils.ApexCrypto

// Read-only commands which we get from the pump.
// Format is:
// AA [UByte length*] [UByte flags] [UByte id] [data...] [UShort crc]
// * - heartbeat (a5) has incorrect length, 06 expected vs 08 actual.
class PumpCommand(private var data: ByteArray) {
    companion object {
        const val MIN_SIZE = 6
    }

    // Use getters here cause data may be changed
    val type: Int get() = data[0].toUByte().toInt() // Should always be AA
    val length: Int get() = data[1].toUByte().toInt() // May be greater or less than packet length!

    val objectType: Int get() = data[2].toUByte().toInt()
    val id: CommandId? get() = CommandId.entries.find { it.raw.toUByte() == data[3].toUByte() }
    val objectData: ByteArray get() = data.copyOfRange(4, realLength() - 2)

    val checksum: ByteArray
        get() = data.copyOfRange(data.size - 2, data.size)

    private fun realLength(): Int =
        if (id == CommandId.Heartbeat)
            length + 2
        else length

    @VisibleForTesting
    fun calculatedChecksum(): ByteArray {
        return ApexCrypto.crc16(data, realLength() - 2)
    }

    /** Verify checksum */
    fun verify(): Boolean {
        val calc = calculatedChecksum()
        return calc.contentEquals(checksum)
    }

    /** Is command complete? */
    fun isCompleteCommand(): Boolean {
        return data.size >= realLength()
    }

    /** Returns the next trailing command if present. */
    val trailing: PumpCommand?
        get() {
            // Trailing is present only on GetValue
            if (id != CommandId.GetValue) return null
            if (data.size <= length) return null
            if (data.size - length < MIN_SIZE) return null
            return PumpCommand(data.copyOfRange(length, data.size))
        }

    /** Add remaining data to the command. */
    fun update(remainingData: ByteArray): Boolean {
        data += remainingData
        return isCompleteCommand()
    }

    override fun toString(): String  = "PumpCommand(type=0x${type.toString(16)}, objType=0x${objectType.toString(16)}, data=${objectData.toHex()})"
}