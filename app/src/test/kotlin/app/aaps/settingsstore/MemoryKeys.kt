package app.aaps.settingsstore

import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/** A key in memory in place of the Android Keystore, which a JVM test does not have. */
class MemoryKeys : KeystoreStoreCipher.Keys {

    var key: SecretKey? = null

    /** When set, every call fails the way a Keystore that does not answer fails. */
    var unavailable: Exception? = null
    var loads = 0

    override fun load(): SecretKey? {
        loads++
        unavailable?.let { throw it }
        return key
    }

    override fun create(): SecretKey {
        unavailable?.let { throw it }
        return KeyGenerator.getInstance("AES").apply { init(256) }.generateKey().also { key = it }
    }

    override fun delete() {
        key = null
    }
}
