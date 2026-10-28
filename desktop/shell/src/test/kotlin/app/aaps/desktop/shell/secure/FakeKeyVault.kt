package app.aaps.desktop.shell.secure

/**
 * A key vault in memory, for tests that must not touch a real Keychain or keyring.
 *
 * [failure] is thrown from every call when set. [forgetsWrites] makes [store] accept a key without
 * keeping it, the way a broken key store could. [loads] counts the reads that reached it.
 */
class FakeKeyVault(override val isOsBacked: Boolean = true, override val promptsPerItem: Boolean = false) : KeyVault {

    override val name = "fake vault"
    val keys = mutableMapOf<String, ByteArray>()
    var failure: Exception? = null
    var forgetsWrites = false
    var returnsOtherBytes = false
    var loads = 0

    override fun load(alias: String): ByteArray? {
        failure?.let { throw it }
        loads++
        val key = keys[alias] ?: return null
        return if (returnsOtherBytes) key.reversedArray() + 1 else key.copyOf()
    }

    override fun store(alias: String, key: ByteArray) {
        failure?.let { throw it }
        if (!forgetsWrites) keys[alias] = key.copyOf()
    }

    override fun delete(alias: String): Boolean {
        failure?.let { throw it }
        return keys.remove(alias) != null
    }
}
