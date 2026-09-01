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
 * This replaces a placeholder that reported the store as switched off, so the user was asked for the
 * export password every time.
 *
 * ## Why the Keychain rather than NSUserDefaults
 *
 * The interface only asks for somewhere durable and private, and says the value is already encrypted
 * by `SecureEncrypt`. `NSUserDefaults` would satisfy the letter of that and break the sentence after
 * it: **a remembered password must not travel to another device**, and preferences are included in
 * an iCloud or iTunes backup, so restoring a backup onto a new phone would carry the secret with it.
 *
 * Keychain items written by [AppleKeychain] are `AfterFirstUnlockThisDeviceOnly`, which is exactly
 * the guarantee wanted: never synced to iCloud, never restored onto another device, and readable
 * once the phone has been unlocked after boot so a background export still works.
 *
 * A different service name from the encryption keys, so clearing one cannot delete the other.
 *
 * ## The stored form
 *
 * One entry holding `<timestamp>:<encrypted secret>`. The timestamp is what the shared rules measure
 * the validity window from, so it has to survive with the secret rather than beside it - two entries
 * could be left half written, and a secret with no timestamp would look infinitely old or infinitely
 * fresh depending on which way the code guessed. The secret is base64 and never contains a colon, so
 * the first one separates the two.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class IosExportPasswordPlatform @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val keychain: Keychain = AppleKeychain(service = "app.aaps.exportpassword")
) : ExportPasswordPlatform {

    override fun read(): ExportPasswordPlatform.Stored? {
        val raw = keychain.load(ALIAS)?.decodeToString() ?: return null
        val at = raw.indexOf(SEPARATOR)
        if (at <= 0) {
            aapsLogger.error(LTag.CORE, "$MODULE: stored value is not in the timestamp:secret form, dropping it")
            clear()
            return null
        }
        val timestamp = raw.substring(0, at).toLongOrNull()
        if (timestamp == null) {
            aapsLogger.error(LTag.CORE, "$MODULE: stored timestamp is not a number, dropping it")
            clear()
            return null
        }
        return ExportPasswordPlatform.Stored(secret = raw.substring(at + 1), timestamp = timestamp)
    }

    override fun write(secret: String, timestamp: Long) {
        keychain.store(ALIAS, "$timestamp$SEPARATOR$secret".encodeToByteArray())
    }

    override fun clear() {
        if (keychain.delete(ALIAS)) aapsLogger.debug(LTag.CORE, "$MODULE: forgot the stored export password")
    }

    /**
     * Never shortened on iOS.
     *
     * Android switches this on with a marker file in its export directory. iOS has no such directory
     * a developer can drop a file into, and inventing another way in would be a way of making a
     * release build expire passwords in minutes. The debug aid is not worth that.
     */
    override fun shortenedValidity(): ExportPasswordPlatform.Validity? = null

    companion object {

        private const val MODULE = "IosExportPasswordPlatform"
        private const val ALIAS = "export-password"
        private const val SEPARATOR = ':'
    }
}
