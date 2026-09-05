package app.aaps.implementation.protection

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.protection.ExportPasswordDataStore
import app.aaps.core.interfaces.protection.ExportPasswordPlatform
import app.aaps.core.interfaces.protection.PasswordHasher
import app.aaps.core.interfaces.protection.SecureEncrypt
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.StringKey
import app.aaps.core.keys.interfaces.Preferences
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/**
 * Keeps the export password so that an unattended export does not have to ask for it every time.
 *
 * The password itself is never stored. What is kept is the encrypted envelope from [SecureEncrypt]
 * plus the time it was written, and the expiry is worked out from that timestamp against a fixed
 * validity window.
 *
 * ## Why the stored password is checked against the master password
 *
 * A remembered password that outlives the master password it was taken from would let an unattended
 * export keep running with a secret the user has since changed. So every read decrypts the stored
 * envelope and compares it with the current master hash, and drops it when they no longer match -
 * whichever way the master was changed, through Settings, the setup wizard or a reset. That check is
 * the single point of truth for the unattended export path, which is why it is here and not at the
 * call sites.
 *
 * ## Three ways a stored password is dropped
 *
 * - the validity window has passed,
 * - it no longer matches the master password,
 * - it was written by an older version under a different keystore alias. That last one clears the old
 *   key as well, so the next write generates a hardened one.
 *
 * Storage itself is [ExportPasswordPlatform], because where the envelope is kept is the only part
 * that differs per platform.
 */
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
class ExportPasswordDataStoreImpl @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val preferences: Preferences,
    private val platform: ExportPasswordPlatform,
    private val dateUtil: DateUtil,
    private val secureEncrypt: SecureEncrypt,
    private val passwordHasher: PasswordHasher
) : ExportPasswordDataStore {

    // Instance state rather than the companion object it used to live in. There is one instance, so
    // the behaviour is the same, but a `var` shared by every instance of a class is a trap waiting
    // for the second one.
    private var storeIsEnabled = false
    private var validityWindow = DEFAULT_VALIDITY_WINDOW
    private var expiryGracePeriod = DEFAULT_GRACE_PERIOD

    /**
     * Whether the user has turned this on, and the window it runs with.
     *
     * Called before every other operation, because it is also where the validity window is settled -
     * a developer build can shorten it, and removing that switch puts the normal window back without
     * a restart.
     */
    override fun exportPasswordStoreEnabled(): Boolean {
        storeIsEnabled = preferences.get(BooleanKey.MaintenanceEnableExportSettingsAutomation)
        if (!storeIsEnabled) return false

        val shortened = platform.shortenedValidity()
        if (shortened != null) aapsLogger.warn(LTag.CORE, "$MODULE: running with a shortened validity window")
        validityWindow = shortened?.window ?: DEFAULT_VALIDITY_WINDOW
        expiryGracePeriod = shortened?.gracePeriod ?: DEFAULT_GRACE_PERIOD

        aapsLogger.info(LTag.CORE, "$MODULE: enabled, expiry millis=$validityWindow")
        return true
    }

    override fun clearPasswordDataStore(): String {
        if (!exportPasswordStoreEnabled()) return ""
        aapsLogger.debug(LTag.CORE, "$MODULE: clearPasswordDataStore")
        return clearPassword()
    }

    /** Returns the encrypted secret, or [password] unchanged when the store is off. */
    override fun putPasswordToDataStore(password: String): String {
        if (!exportPasswordStoreEnabled()) return password
        aapsLogger.debug(LTag.CORE, "$MODULE: putPasswordToDataStore")
        return storePassword(password)
    }

    /** The encrypted secret with its expiry state, or empty and expired when there is nothing usable. */
    override fun getPasswordFromDataStore(): Triple<String, Boolean, Boolean> {
        if (!exportPasswordStoreEnabled()) return EXPIRED

        val stored = retrievePassword()
        if (stored.password.isEmpty()) return EXPIRED

        val masterHash = preferences.getIfExists(StringKey.ProtectionMasterPassword)
        if (masterHash.isNullOrEmpty() || !passwordHasher.checkPassword(secureEncrypt.decrypt(stored.password), masterHash)) {
            aapsLogger.info(LTag.CORE, "$MODULE: stored password no longer matches the master password, clearing")
            clearPasswordDataStore()
            return EXPIRED
        }

        aapsLogger.debug(LTag.CORE, "$MODULE: getPasswordFromDataStore")
        return Triple(stored.password, stored.isExpired, stored.isAboutToExpire)
    }

    /** Whether [timestamp] has passed the window, and whether it has passed the warning point. */
    private fun isInValidityWindow(timestamp: Long): Pair<Boolean, Boolean> {
        val now = dateUtil.now()
        val expired = now !in timestamp..timestamp + validityWindow
        val aboutToExpire = now !in timestamp..timestamp + validityWindow - expiryGracePeriod
        return expired to aboutToExpire
    }

    private fun clearPassword(): String {
        platform.clear()
        return ""
    }

    private fun storePassword(password: String): String {
        val secret = secureEncrypt.encrypt(password, KEYSTORE_ALIAS)
        platform.write(secret, dateUtil.now())
        return secret
    }

    /**
     * What is stored, with its expiry worked out. Clears the store when the password has expired or
     * was written under an older alias, so the caller sees an empty password and asks the user again.
     */
    private fun retrievePassword(): PasswordData {
        val stored = platform.read() ?: return PasswordData("", 0L)

        // The envelope is <sha256>:<alias>:<iv>:<cipher>. An alias that is not the current one means
        // a pre-V2 install, so it is dropped and its key deleted rather than decrypted with it.
        val aliasInBlob = stored.secret.split(":").getOrNull(1)
        if (stored.secret.isNotEmpty() && aliasInBlob != null && aliasInBlob != KEYSTORE_ALIAS) {
            aapsLogger.info(LTag.CORE, "$MODULE: legacy alias '$aliasInBlob' in stored password, clearing for re-entry with hardened key")
            clearPassword()
            secureEncrypt.deleteKey(aliasInBlob)
            return PasswordData("", 0L)
        }
        if (stored.secret.isEmpty()) return PasswordData("", 0L)

        val (expired, aboutToExpire) = isInValidityWindow(stored.timestamp)
        if (expired) {
            clearPasswordDataStore()
            return PasswordData("", stored.timestamp, isExpired = true, isAboutToExpire = true)
        }
        return PasswordData(stored.secret, stored.timestamp, expired, aboutToExpire)
    }

    private data class PasswordData(
        val password: String,
        val timestamp: Long,
        val isExpired: Boolean = true,
        val isAboutToExpire: Boolean = true
    )

    companion object {

        private const val MODULE = "ExportPasswordDataStore"

        /**
         * Keystore alias for the unattended export password. The V2 bump forces a new key, so that
         * an existing user moves to the hardened one - StrongBox backed where the device has it.
         */
        const val KEYSTORE_ALIAS = "UnattendedExportAliasV2"

        /** Five weeks, the grace period included. */
        private const val DEFAULT_VALIDITY_WINDOW = 35 * 24 * 3600 * 1000L

        /** The last week of the window, where the user is told it is about to expire. */
        private const val DEFAULT_GRACE_PERIOD = 7 * 24 * 3600 * 1000L

        /** Nothing usable: no password, expired, and about to expire. */
        private val EXPIRED = Triple("", true, true)
    }
}
