package info.nightscout.sdk.remotemodel

import com.google.gson.annotations.SerializedName
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import org.json.JSONObject

/**
 * DeviceStatus coming from uploader or AAPS
 *
 **/
@Serializable
data class RemoteProfileStore(
    @SerializedName("app") var app: String? = null,
    @SerializedName("identifier") val identifier: String? = null, // string Main addressing, required field that identifies document in the collection. The client should not create the identifier, the server automatically assigns it when the document is inserted.
    @SerializedName("srvCreated") val srvCreated: Long? = null,   // integer($int64) example: 1525383610088 The server's timestamp of document insertion into the database (Unix epoch in ms). This field appears only for documents which were inserted by API v3.
    @SerializedName("srvModified") val srvModified: Long? = null, // integer($int64) example: 1525383610088 The server's timestamp of the last document modification in the database (Unix epoch in ms). This field appears only for documents which were somehow modified by API v3 (inserted, updated or deleted).
    @SerializedName("created_at") val createdAt: String? = null,  // string or string timestamp on previous version of api, in my examples, a lot of treatments don't have date, only created_at, some of them with string others with long...
    @SerializedName("date") val date: Long?,                      // date as milliseconds
    @SerializedName("startDate") val startDate: Long?,            // record valid from
    @SerializedName("defaultProfile") val defaultProfile: String,// default profile in store

    //@Serializable(with = JSONSerializer::class)
    @Contextual @SerializedName("store") val store: JSONObject
) {
/*
    @Serializable data class Store(
        val names: ArrayList<String>,
        val profiles: ArrayList<SimpleProfile>
    )

    @Serializable data class SimpleProfile(
        @SerializedName("dia") val dia: Double,
        @SerializedName("carbratio") val carbratio: ArrayList<ProfileEntry>,
        @SerializedName("sens") val sens: ArrayList<ProfileEntry>,
        @SerializedName("basal") val basal: ArrayList<ProfileEntry>,
        @SerializedName("target_low") val target_low: ArrayList<ProfileEntry>,
        @SerializedName("target_high") val target_high: ArrayList<ProfileEntry>,
        @SerializedName("units") val units: String,                // string The units for the glucose value, mg/dl or mmoll
        @SerializedName("timezone") val timezone: String
    )

    @Serializable data class ProfileEntry(
        @SerializedName("time") val time: String,
        @SerializedName("timeAsSeconds") val timeAsSeconds: Long?,
        @SerializedName("value") val value: Double
    )
*/
}