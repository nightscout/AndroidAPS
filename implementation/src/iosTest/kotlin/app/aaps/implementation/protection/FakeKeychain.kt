package app.aaps.implementation.protection

/**
 * A Keychain in memory. A test binary has no Keychain of its own (see [AppleKeychainQueryTest]).
 *
 * Set [unavailableStatus] to stand for a Keychain that does not answer: [loadStrict] then throws, and
 * [load] answers null, the way [AppleKeychain] does.
 */
internal class FakeKeychain : Keychain {

    val entries = mutableMapOf<String, ByteArray>()
    var unavailableStatus: Int? = null

    override fun load(alias: String): ByteArray? = if (unavailableStatus != null) null else entries[alias]

    override fun loadStrict(alias: String): ByteArray? {
        unavailableStatus?.let { throw KeychainUnavailableException(it) }
        return entries[alias]
    }

    override fun store(alias: String, key: ByteArray) {
        check(unavailableStatus == null) { "Keychain refused to store $alias" }
        entries[alias] = key
    }

    override fun delete(alias: String): Boolean = entries.remove(alias) != null
}
