package info.nightscout.comboctl.base

const val NUM_BLUETOOTH_ADDRESS_BYTES = 6

/**
 * Class containing a 6-byte Bluetooth address.
 *
 * The address bytes are stored in the printed order.
 * For example, a Bluetooth address 11:22:33:44:55:66
 * is stored as a 0x11, 0x22, 0x33, 0x44, 0x55, 0x66
 * array, with 0x11 being the first byte. This is how
 * Android stores Bluetooth address bytes. Note though
 * that some Bluetooth stacks like BlueZ store the
 * bytes in the reverse order.
 */
data class BluetoothAddress(private val addressBytes: List<Byte>) : Iterable<Byte> {
    /**
     * Number of address bytes (always 6).
     *
     * This mainly exists to make this class compatible with
     * code that operates on collections.
     */
    val size = NUM_BLUETOOTH_ADDRESS_BYTES

    init {
        require(addressBytes.size == size)
    }

    operator fun get(index: Int) = addressBytes[index]

    override operator fun iterator() = addressBytes.iterator()

    override fun toString() = addressBytes.toHexString(":")

    fun toByteArray() = addressBytes.toByteArray()
}

/**
 * Converts a 6-byte bytearray to a BluetoothAddress.
 *
 * @return BluetoothAddress variant of the 6 bytearray bytes.
 * @throws IllegalArgumentException if the bytearray's length
 *         is not exactly 6 bytes.
 */
fun ByteArray.toBluetoothAddress() = BluetoothAddress(this.toList())

fun String.toBluetoothAddress() = BluetoothAddress(this.split(":").map { it.toInt(radix = 16).toByte() })
