package app.aaps.implementation.protection

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.protection.ExportPasswordPlatform
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/**
 * Keeps the encrypted export password in the iOS Keychain.
 *
 * Written from the Windows side because moving `ExportPasswordDataStoreImpl` to commonMain is what
 * left iOS without this binding - the placeholder that used to answer for the whole feature is gone,
 * and only this small piece is still platform specific. Change it freely if the Keychain details
 * want doing differently; the rules it feeds are shared and are not affected.
 *
 * ## What is stored
 *
 * One item, holding the timestamp and the encrypted envelope separated by a newline. One item rather
 * than two so a read cannot see a secret with the wrong timestamp: two Keychain entries can be
 * written separately, and a half written pair would look like a fresh password that is actually old.
 *
 * The envelope is already encrypted by `IosSecureEncrypt`, so the Keychain is a second layer rather
 * than the only one. It is still the right place: unlike a file it is not in an iTunes or iCloud
 * backup, and `AppleKeychain` stores `AfterFirstUnlockThisDeviceOnly`, so a remembered password
 * cannot travel to another device.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class IosExportPasswordPlatform @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val keychain: Keychain = AppleKeychain(KEYCHAIN_SERVICE)
) : ExportPasswordPlatform {

    override fun read(): ExportPasswordPlatform.Stored? {
        val raw = keychain.load(ALIAS)?.decodeToString() ?: return null
        val timestamp = raw.substringBefore(SEPARATOR, "").toLongOrNull()
        val secret = raw.substringAfter(SEPARATOR, "")
        if (timestamp == null || secret.isEmpty()) {
            // Written by a different version, or damaged. Asking the user again is the safe answer.
            aapsLogger.error(LTag.CORE, "Stored export password is not readable, ignoring it")
            return null
        }
        return ExportPasswordPlatform.Stored(secret, timestamp)
    }

    override fun write(secret: String, timestamp: Long) {
        keychain.store(ALIAS, "$timestamp$SEPARATOR$secret".encodeToByteArray())
    }

    override fun clear() {
        keychain.delete(ALIAS)
    }

    /**
     * Never shortened on iOS.
     *
     * The marker files this answers to are an Android developer aid that reads its export directory.
     * There is no iOS equivalent, and inventing one would mean shipping a way to shorten the life of
     * a stored password.
     */
    override fun shortenedValidity(): ExportPasswordPlatform.Validity? = null

    private companion object {

        /** Its own service, so this cannot collide with the keys `IosSecureEncrypt` stores. */
        const val KEYCHAIN_SERVICE = "app.aaps.exportpassword"
        const val ALIAS = "unattendedExport"
        const val SEPARATOR = "\n"
    }
}
