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

        @SerializedName("devicestatus") var devicestatus: Long, // devicestatus collection
        @SerializedName("entries") var entries: Long,           // entries collection
        @SerializedName("profile") var profile: Long,           // profile collection
        @SerializedName("treatments") var treatments: Long      // treatments collection
    )
}
