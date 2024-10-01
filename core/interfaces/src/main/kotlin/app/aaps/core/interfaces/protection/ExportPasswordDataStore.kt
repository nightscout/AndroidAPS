package app.aaps.core.interfaces.protection

import android.content.Context

interface ExportPasswordDataStore {

    /***
     * TODO: While in dev only (delete when implementation is excepted):
     * Check Export password functionality
     * Returns true when Export password store is enabled.
     */
    fun exportPasswordStoreEnabled() : Boolean

    /***
     * Clear password currently stored.
     */
    fun clearPasswordDataStore(context: Context): String

    /***
     * Put password to local phone's datastore.
     */
    fun putPasswordToDataStore(context: Context, password: String): String

    /***
     * Get password from local phone's data store.
     * Return pair (true,<password>) or (false,"")
     */
    fun getPasswordFromDataStore(context: Context): Pair<Boolean, String>

}