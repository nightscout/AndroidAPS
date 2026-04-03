package app.aaps.core.interfaces.sync

import app.aaps.core.interfaces.nsclient.NSAlarm
import app.aaps.core.interfaces.profile.Profile

/**
 * Plugin providing communication with Nightscout server
 */
interface NsClient : Sync {

    /**
     * NS URL
     */
    val address: String

    /**
     * Set plugin in paused state
     */
    fun pause(newState: Boolean)

    /**
     * Initiate new round of upload/download
     *
     * @param reason identification of caller
     */
    fun resend(reason: String)

    /**
     * Used data sync selector
     */
    val dataSyncSelector: DataSyncSelector

    /**
     * Version of NS server
     * @return Returns detected version of NS server
     */
    fun detectedNsVersion(): String?

    enum class Collection { ENTRIES, TREATMENTS, FOODS, PROFILE }

    /**
     * NSC v3 does first load of all data
     * next loads are using srvModified property for sync
     * not used for NSCv1
     *
     * @return true if inside first load of NSCv3, true for NSCv1
     */
    fun isFirstLoad(collection: Collection): Boolean = true

    /**
     * Update newest loaded timestamp for entries collection (first load or NSCv1)
     * Update newest srvModified (sync loads)
     *
     * @param latestReceived timestamp
     *
     */
    fun updateLatestBgReceivedIfNewer(latestReceived: Long)

    /**
     * Update newest loaded timestamp for treatments collection (first load or NSCv1)
     * Update newest srvModified (sync loads)
     *
     * @param latestReceived timestamp
     *
     */
    fun updateLatestTreatmentReceivedIfNewer(latestReceived: Long)

    /**
     * Send alarm confirmation to NS
     *
     * @param originalAlarm alarm to be cleared
     * @param silenceTimeInMilliseconds silence alarm for specified duration
     */
    fun handleClearAlarm(originalAlarm: NSAlarm, silenceTimeInMilliseconds: Long)

    /**
     * Clear synchronization status
     *
     * Next synchronization will start from scratch
     */
    suspend fun resetToFullSync()

    /**
     * Upload new record to NS
     *
     * @param collection target ns collection
     * @param dataPair data to upload (data.first) and id of changed record (data.second)
     * @param progress progress of sync in format "number/number". Only for display in fragment
     * @return true for successful upload
     */
    suspend fun nsAdd(collection: String, dataPair: DataSyncSelector.DataPair, progress: String, profile: Profile? = null): Boolean

    /**
     * Upload updated record to NS
     *
     * @param collection target ns collection
     * @param dataPair data to upload (data.first) and id of changed record (data.second)
     * @param progress progress of sync in format "number/number". Only for display in fragment
     * @return true for successful upload
     */
    suspend fun nsUpdate(collection: String, dataPair: DataSyncSelector.DataPair, progress: String, profile: Profile? = null): Boolean
}