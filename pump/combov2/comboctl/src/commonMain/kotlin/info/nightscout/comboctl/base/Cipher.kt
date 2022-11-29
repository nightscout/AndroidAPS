package info.nightscout.comboctl.base

const val CIPHER_KEY_SIZE = 16
const val CIPHER_BLOCK_SIZE = 16

/**
 * Class for en- and decrypting packets going to and coming from the Combo.
 *
 * The packets are encrypted using the Twofish symmetric block cipher.
 * It en- and decrypts blocks of 128 bits (16 bytes). Key size too is 128 bits.
 *
 * @property key The 128-bit key for en- and decrypting. Initially set to null.
 *           Callers must first set this to a valid non-null value before any
 *           en- and decrypting can be performed.
 */
class Cipher(val key: ByteArray) {

    init {
        require(key.size == CIPHER_KEY_SIZE)
    }

    private val keyObject = Twofish.processKey(key)

    /**
     * Encrypts a 128-bit block of cleartext, producing a 128-bit ciphertext block.
     *
     * The key must have been set to a valid value before calling this function.
     *
     * @param cleartext Array of 16 bytes (128 bits) of cleartext to encrypt.
     * @return Array of 16 bytes (128 bits) of ciphertext.
     */
    fun encrypt(cleartext: ByteArray): ByteArray {
        require(cleartext.size == CIPHER_BLOCK_SIZE)
        return Twofish.blockEncrypt(cleartext, 0, keyObject)
    }

    /**
     * Decrypts a 128-bit block of ciphertext, producing a 128-bit cleartext block.
     *
     * The key must have been set to a valid value before calling this function.
     *
     * @param ciphertext Array of 16 bytes (128 bits) of ciphertext to decrypt.
     * @return Array of 16 bytes (128 bits) of cleartext.
     */
    fun decrypt(ciphertext: ByteArray): ByteArray {
        require(ciphertext.size == CIPHER_BLOCK_SIZE)
        return Twofish.blockDecrypt(ciphertext, 0, keyObject)
    }

    override fun toString() = key.toHexString(" ")
}

fun String.toCipher() = Cipher(this.split(" ").map { it.toInt(radix = 16).toByte() }.toByteArray())

/**
 * Generates a weak key out of a 10-digit PIN.
 *
 * The weak key is needed during the Combo pairing process. The
 * 10-digit PIN is displayed on the Combo's LCD, and the user has
 * to enter it into whatever program is being paired with the Combo.
 * Out of that PIN, the "weak key" is generated. That key is used
 * for decrypting a subsequently incoming packet that contains
 * additional keys that are used for en- and decrypting followup
 * packets coming from and going to the Combo.
 *
 * @param PIN Pairing PIN to use for generating the weak key.
 * @return 16 bytes containing the generated 128-bit weak key.
 */
fun generateWeakKeyFromPIN(PIN: PairingPIN): ByteArray {
    // Verify that the PIN is smaller than the cipher key.
    // NOTE: This could be a compile-time check, since these
    // are constants. But currently, it is not known how to
    // do this check at compile time.
    require(PAIRING_PIN_SIZE < CIPHER_KEY_SIZE)

    val generatedKey = ByteArray(CIPHER_KEY_SIZE)

    // The weak key generation algorithm computes the first
    // 10 bytes simply by looking at the first 10 PIN
    // digits, interpreting them as characters, and using the
    // ASCII indices of these characters. For example, suppose
    // that the first PIN digit is 2. It is interpreted as
    // character "2". That character has ASCII index 50.
    // Therefore, the first byte in the key is set to 50.
    for (i in 0 until PAIRING_PIN_SIZE) {
        val pinDigit = PIN[i]

        val pinAsciiIndex = pinDigit + '0'.code
        generatedKey[i] = pinAsciiIndex.toByte()
    }

    // The PIN has 10 digits, not 16, but the key has 16
    // bytes. For the last 6 bytes, the first 6 digits are
    // treated just like above, except that the ASCII index
    // is XORed with 0xFF.
    for (i in 0 until 6) {
        val pinDigit = PIN[i]

        val pinAsciiIndex = pinDigit + '0'.code
        generatedKey[i + PAIRING_PIN_SIZE] = (0xFF xor pinAsciiIndex).toByte()
    }

    return generatedKey
}
