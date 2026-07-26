package app.aaps.implementation.protection

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.maintenance.FileListProvider
import app.aaps.core.interfaces.protection.ExportPasswordDataStore
import app.aaps.core.interfaces.protection.SecureEncrypt
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.StringKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.objects.crypto.CryptoUtil
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Class ExportPasswordDataStore (interface + implementation)
 *
 * This class is used to keep/store password state in the phones DataStore, that is:
 * Password (encryption key) and creation timestamp are stored, "expiry" state and "about to expire"
 * are calculated based on timestamp and the validity window defined.
 *
 * Note:
 * - Password secret and state are stored on the phone's "Android DataStore", not in AAPS preferences.
 * - Password encryption and decryption uses encryption keys securely stored in the phone's "Android KeyStore".
 * - The actual password is not stored, only a secret "key" containing encrypted data and parameters.
 * - The secret "key" is used for secure access to the password in the "Android KeyStore"
 *
 * Dependency: Class SecureEncrypt
 *
 */

@Singleton
class ExportPasswordDataStoreImpl @Inject constructor(
    private var aapsLogger: AAPSLogger,
    private var preferences: Preferences,
    private var config: Config
) : ExportPasswordDataStore {

    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var secureEncrypt: SecureEncrypt
    @Inject lateinit var cryptoUtil: CryptoUtil

    // Remove for release? (Debug only!)
    @Inject lateinit var fileListProvider: FileListProvider

    companion object {

        // Internal constant stings
        const val MODULE = "ExportPasswordDataStore"

        // KeyStore alias for unattended export password. V2 bump forces regeneration so existing users
        // gain the hardened (StrongBox-backed where supported) key, see SecureEncryptImpl.
        const val KEYSTORE_ALIAS = "UnattendedExportAliasV2"

        // Android DataStore: used for keeping password state on local phone storage
        const val DATASTORE_NAME: String = "app.aaps.plugins.configuration.maintenance.ImportExport.datastore"
        const val PASSWORD_PREFERENCE_NAME = "$DATASTORE_NAME.unattended_export"

        // On enabling & password expiry (fixed defaults)
        private var exportPasswordStoreIsEnabled = false                   // Set from prefs, disabled by default
        private var passwordValidityWindow: Long = 35 * 24 * 3600 * 1000L  // 5 weeks (including grace period)
        private var passwordExpiryGracePeriod: Long = 7 * 24 * 3600 * 1000L  // 1 week
    }

    // Declare DataStore
    private val Context.dataStore: DataStore<androidx.datastore.preferences.core.Preferences> by preferencesDataStore(
        name = DATASTORE_NAME
    )

    /***
     * Data class holding password status
     */
    data class ClassPasswordData(
        var password: String,
        var timestamp: Long,
        var isExpired: Boolean,
        var isAboutToExpire: Boolean
    )

    /***
     * Check if ExportPasswordDataStore is enabled
     * Returns true when Export password store is enabled.
     * see also:
     * - var passwordValidityWindow
     * - var passwordExpiryGracePeriod
     */
    override fun exportPasswordStoreEnabled(): Boolean {
        // Is password storing enabled?
        exportPasswordStoreIsEnabled = preferences.get(BooleanKey.MaintenanceEnableExportSettingsAutomation)
        if (!exportPasswordStoreIsEnabled) return false // Easy, done!

        // Use fixed defaults for password validity window, optional overrule defaults from prefs:
        // passwordValidityWindow = (sp.getLong(IntKey.AutoExportPasswordExpiryDays.key.....

        // To be removed for final PR/release...
        // START
        if (config.isEngineeringMode() && config.isDev()) {
            // Enable debug mode when file 'DebugUnattendedExport' exists
            val debug = fileListProvider.ensureExtraDirExists()?.findFile("DebugUnattendedExport") != null
            val debugDev = fileListProvider.ensureExtraDirExists()?.findFile("DebugUnattendedExportDev") != null
            if (debugDev) {
                aapsLogger.warn(LTag.CORE, "$MODULE: ExportPasswordDataStore running DEBUG(DEV) mode!")
                /*** Debug/testing mode ***/
                passwordValidityWindow = 20 * 60 * 1000L                // Valid for 20 min
                passwordExpiryGracePeriod = passwordValidityWindow / 2    // Grace period 10 min
            } else if (debug) {
                aapsLogger.warn(LTag.CORE, "$MODULE: ExportPasswordDataStore running DEBUG mode!")
                /*** Debug mode ***/
                passwordValidityWindow = 2 * 24 * 3600 * 1000L           // 2 Days (including grace period)
                passwordExpiryGracePeriod = passwordValidityWindow / 2 // Grace period 1 days
            }
        }
        // END

        aapsLogger.info(LTag.CORE, "$MODULE: ExportPasswordDataStore is enabled: $exportPasswordStoreIsEnabled, expiry millis=$passwordValidityWindow")
        return exportPasswordStoreIsEnabled
    }

    /***
     * Clear password currently stored in DataStore to "empty"
     */
    override fun clearPasswordDataStore(context: Context): String {
        if (!exportPasswordStoreEnabled()) return "" // Do nothing, return empty

        // Store & update to empty password and return
        aapsLogger.debug(LTag.CORE, "$MODULE: clearPasswordDataStore")
        return this.clearPassword(context)
    }

    /***
     * Store password in local phone's DataStore
     * Return: password
     */
    override fun putPasswordToDataStore(context: Context, password: String): String {
        if (!exportPasswordStoreEnabled()) return password // Just return the password
        aapsLogger.debug(LTag.CORE, "$MODULE: putPasswordToDataStore")
        return this.storePassword(context, password)
    }

    /***
     * Get password from local phone's DataStore
     * Return Triple (ok, password string, isExpired, isAboutToExpire)
     */
    override fun getPasswordFromDataStore(context: Context): Triple<String, Boolean, Boolean> {
        if (!exportPasswordStoreEnabled()) return Triple("", true, true)

        val passwordData = this.retrievePassword(context)
        with(passwordData) {
            if (password.isNotEmpty()) {  // And not expired
                // The stored password must stay in sync with the master password. Decrypt the stored secret and
                // verify it against the current master hash; if it no longer matches (master changed via ANY path
                // — Settings, Setup Wizard, reset — or a stale/legacy blob) clear it so the user is re-prompted and
                // re-verified. This is the single point of truth that also protects the unattended-export path.
                val masterHash = preferences.getIfExists(StringKey.ProtectionMasterPassword)
                if (masterHash.isNullOrEmpty() || !cryptoUtil.checkPassword(secureEncrypt.decrypt(password), masterHash)) {
                    aapsLogger.info(LTag.CORE, "$MODULE: stored password no longer matches the master password, clearing")
                    clearPasswordDataStore(context)
                    return Triple("", true, true)
                }
                aapsLogger.debug(LTag.CORE, "$MODULE: getPasswordFromDataStore")
                return Triple(password, isExpired, isAboutToExpire)
            }
        }
        return Triple("", true, true)
    }

    /*************************************************************************
     * Private functions
     *************************************************************************/

    /***
     * Check if timestamp is in validity window T...T+duration
     */
    private fun isInValidityWindow(timestamp: Long, duration: Long?, gracePeriod: Long?): Pair<Boolean, Boolean> {
        val expired = dateUtil.now() !in timestamp..timestamp + (duration ?: 0L)
        val expires = dateUtil.now() !in timestamp..timestamp + (duration ?: 0L) - (gracePeriod ?: 0L)
        return Pair(expired, expires)
    }

    /***
     * Clear password and timestamp
     */
    private fun clearPassword(context: Context): String {

        // Write setting to android datastore and return password
        fun updatePrefString(name: String) = runBlocking {
            context.dataStore.edit { settings ->
                // Clear password as string value
                settings[stringPreferencesKey("$name.key")] = ""    // Password
                settings[stringPreferencesKey("$name.ts")] = "0"    // Timestamp
            }[stringPreferencesKey("$name.key")].toString()         // Return updated password
        }

        // Clear password stored in DataStore
        return updatePrefString(PASSWORD_PREFERENCE_NAME)
    }

    /***
     * Store password and set timestamp to current
     */
    private fun storePassword(context: Context, password: String): String {

        // Write encrypted password key and timestamp to the local phone's android datastore and return password
        fun updatePrefString(name: String, str: String) = runBlocking {
            context.dataStore.edit { settings ->
                // If current password is empty, update to new timestamp "now" or else leave it
                settings[stringPreferencesKey("$name.ts")] = dateUtil.now().toString()
                // Update password as string value
                settings[stringPreferencesKey("$name.key")] = str
            }[stringPreferencesKey("$name.key")].toString()
        }

        // Update DataStore & return password string
        return updatePrefString(PASSWORD_PREFERENCE_NAME, secureEncrypt.encrypt(password, KEYSTORE_ALIAS))
    }

    /***
     * Retrieve password from local phone's data store.
     * Reset password when validity expired
     ***/
    private fun retrievePassword(context: Context): ClassPasswordData {

        // Read encrypted password key and timestamp from the local phone's android datastore and return password
        var passwordStr = ""
        var timestampStr = ""

        runBlocking {
            val keyName = PASSWORD_PREFERENCE_NAME
            context.dataStore.edit { settings ->
                passwordStr = settings[stringPreferencesKey("$keyName.key")] ?: ""
                timestampStr = (settings[stringPreferencesKey("$keyName.ts")] ?: "")
            }
        }

        // Envelope format from SecureEncryptImpl: <sha256>:<alias>:<iv>:<cipher>.
        // If the stored alias doesn't match the current one, the blob is from a pre-V2 install:
        // clear it so the empty-password path triggers re-entry and a new hardened key is generated.
        if (passwordStr.isNotEmpty()) {
            val aliasInBlob = passwordStr.split(":").getOrNull(1)
            if (aliasInBlob != null && aliasInBlob != KEYSTORE_ALIAS) {
                aapsLogger.info(LTag.CORE, "$MODULE: legacy alias '$aliasInBlob' in stored password, clearing for re-entry with hardened key")
                clearPassword(context)
                passwordStr = ""
                timestampStr = ""
                secureEncrypt.deleteKey(aliasInBlob)
            }
        }

        val classPasswordData = ClassPasswordData(
            password = passwordStr,
            timestamp = if (timestampStr.isEmpty()) 0L else timestampStr.toLong(),
            isExpired = true,
            isAboutToExpire = true
        )

        // Get the password value stored
        with(classPasswordData) {
            if (password.isEmpty()) return classPasswordData

            // Password is defined: Check for password expiry:
            val (expired, expires) = isInValidityWindow(timestamp, passwordValidityWindow, passwordExpiryGracePeriod)
            isExpired = expired
            isAboutToExpire = expires

            // When expired, need to renew: clear/update password in data store
            if (isExpired) password = clearPasswordDataStore(context)
        }
        // Store/update password and return
        return classPasswordData
    }

}
