package app.aaps.implementation.protection

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.protection.ExportPasswordDataStore
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.IntKey
import dagger.Reusable
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

// Internal constant stings
const val datastoreName : String = "app.aaps.plugins.configuration.maintenance.ImportExport.datastore"
const val passwordPreferenceName = "$datastoreName.password_value"

@Reusable
class ExportPasswordDataStoreImpl @Inject constructor(
    private var log: AAPSLogger,
    private val sp: SP
    ) : ExportPasswordDataStore {

    @Inject lateinit var dateUtil: DateUtil

    // TODO: Review security aspects on temporarily storing password in phone's local data store

    private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
        name = datastoreName
    )

    private var exportPasswordStoreIsEnabled = false
    private var passwordValidityWindowSeconds: Long = 0

    /***
     * Check Export password functionality
     * Returns true when Export password store is enabled.
     */
    override fun exportPasswordStoreEnabled() : Boolean {
        // Is password storing enabled?
        exportPasswordStoreIsEnabled = sp.getBoolean(BooleanKey.MaintenanceEnableExportSettingsAutomation.key, false)
        // Password validity window
        passwordValidityWindowSeconds = sp.getLong(IntKey.AutoExportPasswordExpiryDays.key, 7) * 24 * 3600 * 1000
        log.debug(LTag.CORE, "ExportPassword Store Supported: $exportPasswordStoreIsEnabled, expiry days=$passwordValidityWindowSeconds")
        return exportPasswordStoreIsEnabled
    }

    /***
     * Clear password currently stored to "empty"
     */
    override fun clearPasswordDataStore(context: Context): String {
        // TODO: For now always clear - also when general functionality is disabled?
        // if (!exportPasswordStoreEnabled()) return ""

        log.debug(LTag.CORE, "clearPasswordDataStore")
        // Store & update to empty password and return
        return this.storePassword(context, "")
    }

    /***
     * Put password to local phone's datastore
     */
    override fun putPasswordToDataStore(context: Context, password: String): String {
        if (!exportPasswordStoreEnabled()) return ""

        log.debug(LTag.CORE, "putPasswordToDataStore")
        return this.storePassword(context, password)
    }

    /***
     * Get password from local phone's data store
     * Return pair (true,<password>) or (false,"")
     */
    override fun getPasswordFromDataStore(context: Context): Pair<Boolean, String> {
        if (!exportPasswordStoreEnabled()) return Pair (false, "")

        val password = this.retrievePassword(context)
        if (password.isNotEmpty()) {  // And not expired
            log.debug(LTag.CORE, "getPasswordFromDataStore")
            return Pair(true, password)
        }
        return Pair (false, "")
    }

    /*************************************************************************
     * Private functions
    *************************************************************************/

    /***
     * Check if timestamp is in validity window T...T+duration
     */
    private fun isInValidityWindow(timestamp: Long, @Suppress("SameParameterValue") duration: Long?): Boolean {
        return dateUtil.now() in timestamp..timestamp + (duration ?: 0L)
    }

    /***
     * Store password and set timestamp to current
     */
    private fun storePassword(context: Context, password: String): String {

        // Write setting to android datastore and return password
        fun updatePrefString(name: String, str: String)  = runBlocking {
            val preferencesKeyPassword = stringPreferencesKey("$name.key")
            val preferencesKeyTimestamp = stringPreferencesKey("$name.ts")
            context.dataStore.edit { settings ->
                // Update password and timestamp to "now" as string value
                settings[preferencesKeyPassword] = str
                settings[preferencesKeyTimestamp] = dateUtil.now().toString()
            }[preferencesKeyPassword].toString()
        }

        // Update & return password string
        return updatePrefString(passwordPreferenceName, encrypt(password))
    }

    /***
     * Retrieve password from local phone's data store.
     * Reset password when validity expired
    ***/
    private fun retrievePassword(context: Context): String {

        // Read string value from phone's local datastore using key name
        var passwordStr = ""
        var timestampStr = ""

        runBlocking {
            val keyName = passwordPreferenceName
            val preferencesKeyVal = stringPreferencesKey("$keyName.key")
            val preferencesKeyTs = stringPreferencesKey("$keyName.ts")
            context.dataStore.edit { settings ->
                passwordStr = settings[preferencesKeyVal] ?:""
                timestampStr = (settings[preferencesKeyTs] ?:"")
            }
        }

        // Get the password value stored
        if (passwordStr.isEmpty())
            return ""

        // Password is defined: Check for password expiry:
        val timestamp = if (timestampStr.isEmpty()) 0L else timestampStr.toLong()
        if (!isInValidityWindow(timestamp, passwordValidityWindowSeconds))
            // Password validity ended - need to renew:
            // Clear/update password in data store
            return this.clearPasswordDataStore(context)

        // Store/update password and return
        return this.storePassword(context, decrypt(passwordStr))
    }

    /***
     * Preparing for encryption/decryption (needs additional research)
     */
    private fun encrypt(str: String): String {
        return str
    }

    private fun decrypt(str: String): String {
        return str
    }

}
