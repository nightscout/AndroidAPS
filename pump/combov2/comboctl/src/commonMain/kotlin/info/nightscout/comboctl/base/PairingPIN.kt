package info.nightscout.comboctl.base

const val PAIRING_PIN_SIZE = 10

/**
 * Class containing a 10-digit pairing PIN.
 *
 * This PIN is needed during the pairing process. The Combo shows a 10-digit
 * PIN on its display. The user then has to enter that PIN in the application.
 * This class contains such entered PINs.
 *
 * @param pinDigits PIN digits. Must be an array of exactly 10 Ints.
 *        The Ints must be in the 0-9 range.
 */
data class PairingPIN(val pinDigits: IntArray) : Iterable<Int> {
    // This code was adapted from:
    // https://discuss.kotlinlang.org/t/defining-a-type-for-a-fixed-length-array/12817/2

    /**
     * Number of PIN digits (always 10).
     *
     * This mainly exists to make this class compatible with
     * code that operates on collections.
     */
    val size = PAIRING_PIN_SIZE

    init {
        require(pinDigits.size == PAIRING_PIN_SIZE)

        // Verify that all ints are a number between 0 and 9,
        // since they are supposed to be digits.
        for (i in 0 until PAIRING_PIN_SIZE) {
            val pinDigit = pinDigits[i]
            require((pinDigit >= 0) && (pinDigit <= 9))
        }
    }

    /**
     * Get operator to access the digit with the given index.
     *
     * @param index Digit index. Must be a value between 0 and 9 (since the PIN has 10 digits).
     * @return The digit.
     */
    operator fun get(index: Int) = pinDigits[index]

    /**
     * Set operator to set the digit with the given index.
     *
     * @param index Digit index. Must be a value between 0 and 9 (since the PIN has 10 digits).
     * @param pinDigit The new digit to set at the given index.
     */
    operator fun set(index: Int, pinDigit: Int) {
        require((pinDigit >= 0) && (pinDigit <= 9))
        pinDigits[index] = pinDigit
    }

    // Custom equals operator to compare content,
    // which doesn't happen by default with arrays.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null) return false
        if (this::class != other::class) return false
        other as PairingPIN
        if (!pinDigits.contentEquals(other.pinDigits)) return false
        return true
    }

    override fun hashCode(): Int {
        return pinDigits.contentHashCode()
    }

    override operator fun iterator() = pinDigits.iterator()

    /**
     * Utility function to print the PIN in the format the Combo prints it on its display.
     *
     * The format is 012-345-6789.
     */
    override fun toString() = "${pinDigits[0]}${pinDigits[1]}${pinDigits[2]}-" +
                              "${pinDigits[3]}${pinDigits[4]}${pinDigits[5]}-" +
                              "${pinDigits[6]}${pinDigits[7]}${pinDigits[8]}${pinDigits[9]}"
}

fun nullPairingPIN() = PairingPIN(intArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0, 0))
