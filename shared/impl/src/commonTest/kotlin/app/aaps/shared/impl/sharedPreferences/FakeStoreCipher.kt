package app.aaps.shared.impl.sharedPreferences

import app.aaps.core.interfaces.sharedPreferences.SealedDataUnreadableException
import app.aaps.core.interfaces.sharedPreferences.StoreCipher

/**
 * A stand-in cipher: XOR with [key], behind a two byte mark that names the key.
 *
 * Not encryption, but enough to show what the store relies on: the plain text does not appear in the
 * output, data made with another key is refused as unreadable, and a key store that does not answer
 * ([unavailable]) is a different failure from a wrong key.
 */
internal class FakeStoreCipher(private val key: Byte = 0x5A) : StoreCipher {

    /** Thrown from [decrypt] when set, standing for a key store that does not answer. */
    var unavailable: Exception? = null

    override fun encrypt(plain: ByteArray): ByteArray = byteArrayOf(MARK, key) + plain.xor()

    override fun decrypt(sealed: ByteArray): ByteArray {
        unavailable?.let { throw it }
        if (sealed.size < 2 || sealed[0] != MARK || sealed[1] != key) throw SealedDataUnreadableException("made with another key")
        return sealed.copyOfRange(2, sealed.size).xor()
    }

    private fun ByteArray.xor() = ByteArray(size) { (this[it].toInt() xor key.toInt()).toByte() }

    private companion object {

        const val MARK: Byte = 0x7E
    }
}
