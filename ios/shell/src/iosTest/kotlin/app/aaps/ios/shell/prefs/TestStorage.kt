package app.aaps.ios.shell.prefs

import app.aaps.implementation.protection.Keychain
import platform.Foundation.NSTemporaryDirectory
import kotlin.random.Random

/**
 * Points [IosStorage] at a folder of its own and a Keychain in memory, for the tests that build a graph.
 *
 * A test binary has no Keychain, and it must not read the defaults that an older build left on the
 * simulator. Call it before the first graph is built; the first call wins, later ones change nothing.
 */
internal object TestStorage {

    fun install() = IosStorage.useForTests(
        IosStorage.Setup(
            directory = "${NSTemporaryDirectory().trimEnd('/')}/aaps-test-store-${Random.nextLong().toULong()}",
            keychain = MemoryKeychain(),
            defaults = null,
            domain = ""
        )
    )
}

/** A Keychain in memory. A test binary has no Keychain of its own. */
internal class MemoryKeychain : Keychain {

    val entries = mutableMapOf<String, ByteArray>()

    override fun load(alias: String): ByteArray? = entries[alias]

    override fun store(alias: String, key: ByteArray) {
        entries[alias] = key
    }

    override fun delete(alias: String): Boolean = entries.remove(alias) != null
}
