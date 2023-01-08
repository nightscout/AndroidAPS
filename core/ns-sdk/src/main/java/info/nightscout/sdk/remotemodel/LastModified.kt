package info.nightscout.sdk.remotemodel

import com.google.gson.annotations.SerializedName
import kotlinx.serialization.Serializable

/**
 * Timestamp of last modification of every collection
 *
 **/
@Serializable
data class LastModified(
    @SerializedName("collections") val collections: Collections
) {

    @Serializable
    data class Collections(

        @SerializedName("devicestatus") var devicestatus: Long = 0, // devicestatus collection
        @SerializedName("entries") var entries: Long = 0,           // entries collection
        @SerializedName("profile") var profile: Long = 0,           // profile collection
        @SerializedName("treatments") var treatments: Long = 0,     // treatments collection
        @SerializedName("foods") var foods: Long = 0,               // foods collection
        @SerializedName("settings") var settings: Long = 0          // settings collection
    )
}
