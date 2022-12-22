package info.nightscout.interfaces.sync

import android.text.Spanned
import info.nightscout.interfaces.nsclient.NSAlarm

interface NsClient : Sync {
    enum class Version {
        NONE, V1, V3
    }

    val version: Version
    val address: String

    fun pause(newState: Boolean)
    fun resend(reason: String)
    fun textLog(): Spanned
    fun clearLog()

    enum class Collection { ENTRIES, TREATMENTS}
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
    fun handleClearAlarm(originalAlarm: NSAlarm, silenceTimeInMilliseconds: Long)

    fun resetToFullSync()

    fun dbAdd(collection: String, dataPair: DataSyncSelector.DataPair, progress: String)
    fun dbUpdate(collection: String, dataPair: DataSyncSelector.DataPair, progress: String)
}