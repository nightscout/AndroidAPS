package app.aaps.desktop.shell.secure

import app.aaps.core.utils.hexStringToByteArray
import app.aaps.core.utils.toHex

/**
 * Keeps every key of this client in one item of [inner], so that the user is asked at most once.
 *
 * Used where [KeyVault.promptsPerItem] is true. The macOS Keychain asks before a new version of an app
 * that is not signed reads an item, and it asks once per item. A desktop client has two or three keys
 * (the settings store, the pairing, maybe the export password); in one item, an update asks once.
 *
 * The item is read once and its keys are kept in memory, so later reads do not reach the key store. A
 * write rewrites the one item, which the key store changes in place.
 *
 * A key that an earlier build stored in an item of its own is moved into the one item the first time it
 * is asked for, and its own item is deleted.
 *
 * ```
 * AAPS keys 1
 * <alias>=<key as hex>
 * ```
 */
class OneItemKeyVault(private val inner: KeyVault) : KeyVault {

    override val name = "${inner.name}, all keys in one item"
    override val isOsBacked = inner.isOsBacked

    private var keys: Map<String, ByteArray>? = null

    @Synchronized
    override fun load(alias: String): ByteArray? {
        keys()[alias]?.let { return it.copyOf() }
        // Not in the one item: an earlier build may have stored it in an item of its own.
        val own = inner.load(alias) ?: return null
        write(keys() + (alias to own))
        inner.delete(alias)
        return own.copyOf()
    }

    @Synchronized
    override fun store(alias: String, key: ByteArray) {
        require(alias.isNotEmpty() && alias != ITEM_ALIAS && '=' !in alias && '\n' !in alias) { "Not a usable key alias: $alias" }
        write(keys() + (alias to key.copyOf()))
        // An own item left by an earlier build would come back through load() after a later delete.
        inner.delete(alias)
    }

    @Synchronized
    override fun delete(alias: String): Boolean {
        val ownDeleted = inner.delete(alias)
        val current = keys()
        if (alias !in current) return ownDeleted
        write(current - alias)
        return true
    }

    private fun keys(): Map<String, ByteArray> = keys ?: decode(inner.load(ITEM_ALIAS)).also { keys = it }

    private fun write(newKeys: Map<String, ByteArray>) {
        inner.store(ITEM_ALIAS, encode(newKeys))
        keys = newKeys
    }

    private fun encode(keys: Map<String, ByteArray>): ByteArray =
        (listOf(HEADER) + keys.map { (alias, key) -> "$alias=${key.toHex()}" }).joinToString("\n").encodeToByteArray()

    /** Throws [KeyVaultUnavailableException] for an item it cannot read: an empty answer would mean new keys. */
    private fun decode(item: ByteArray?): Map<String, ByteArray> {
        if (item == null) return emptyMap()
        val lines = item.decodeToString().lines()
        if (lines.first() != HEADER) throw KeyVaultUnavailableException("The key item in ${inner.name} is not in a known format")
        return lines.drop(1).filter { it.isNotEmpty() }.associate { line ->
            val at = line.indexOf('=')
            if (at <= 0) throw KeyVaultUnavailableException("The key item in ${inner.name} is damaged")
            line.substring(0, at) to line.substring(at + 1).hexStringToByteArray()
        }
    }

    companion object {

        /** The alias of the one item in [inner]. */
        const val ITEM_ALIAS = "aaps-keys"
        private const val HEADER = "AAPS keys 1"
    }
}
