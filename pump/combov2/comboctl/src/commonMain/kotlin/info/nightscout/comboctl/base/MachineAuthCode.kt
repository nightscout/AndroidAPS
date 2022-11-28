package info.nightscout.comboctl.base

const val NUM_MAC_BYTES = 8

/**
 * Class containing an 8-byte machine authentication code.
 */
data class MachineAuthCode(private val macBytes: List<Byte>) : Iterable<Byte> {
    /**
     * Number of MAC bytes (always 8).
     *
     * This mainly exists to make this class compatible with
     * code that operates on collections.
     */
    val size = NUM_MAC_BYTES

    init {
        require(macBytes.size == size)
    }

    operator fun get(index: Int) = macBytes[index]

    override operator fun iterator() = macBytes.iterator()

    override fun toString() = macBytes.toHexString()
}

/**
 * MAC consisting of 8 nullbytes. Useful for initializations and
 * for the first few pairing packets that don't use MACs.
 */
val NullMachineAuthCode = MachineAuthCode(List(NUM_MAC_BYTES) { 0x00 })
