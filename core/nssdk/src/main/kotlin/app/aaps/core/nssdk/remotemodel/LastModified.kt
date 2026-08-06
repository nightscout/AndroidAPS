package app.aaps.core.nssdk.remotemodel

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Timestamp of last modification of every collection
 *
 **/
@Serializable
data class LastModified(
    @SerialName("collections") val collections: Collections
) {

    @Serializable
    data class Collections(

        @SerialName("devicestatus") var devicestatus: Long = 0, // devicestatus collection
        @SerialName("entries") var entries: Long = 0,           // entries collection
        @SerialName("profile") var profile: Long = 0,           // profile collection
        @SerialName("treatments") var treatments: Long = 0,     // treatments collection
        @SerialName("foods") var foods: Long = 0,               // foods collection
        @SerialName("settings") var settings: Long = 0          // settings collection
    )

    fun set(colName: String, value: Long) {
        when (colName) {
            "devicestatus" -> collections.devicestatus = value
            "entries"      -> collections.entries = value
            "profile"      -> collections.profile = value
            "treatments"   -> collections.treatments = value
            "foods"        -> collections.foods = value
            "settings"     -> collections.settings = value
        }
    }
}
