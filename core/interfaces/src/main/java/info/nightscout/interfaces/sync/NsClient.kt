package info.nightscout.interfaces.sync

import android.text.Spanned
import info.nightscout.interfaces.nsclient.NSAlarm
import org.json.JSONObject

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

    fun updateLatestBgReceivedIfNewer(latestReceived: Long)
    fun updateLatestTreatmentReceivedIfNewer(latestReceived: Long)
    fun handleClearAlarm(originalAlarm: NSAlarm, silenceTimeInMilliseconds: Long)

    fun resetToFullSync()

    fun dbAdd(collection: String, data: JSONObject, originalObject: Any, progress: String)
    fun dbUpdate(collection: String, _id: String?, data: JSONObject?, originalObject: Any, progress: String)
}