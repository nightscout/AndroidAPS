package app.aaps.core.nssdk.remotemodel

import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName

/**
 * DeviceStatus coming from uploader or AAPS
 *
 **/
internal data class RemoteDeviceStatus(
    @SerializedName("app") var app: String? = null,
    @SerializedName("identifier")
    val identifier: String? = null, // string Main addressing, required field that identifies document in the collection. The client should not create the identifier, the server automatically assigns it when the document is inserted.
    @SerializedName("srvCreated")
    val srvCreated: Long? = null,   // integer($int64) example: 1525383610088 The server's timestamp of document insertion into the database (Unix epoch in ms). This field appears only for documents which were inserted by API v3.
    @SerializedName("srvModified")
    val srvModified: Long? = null, // integer($int64) example: 1525383610088 The server's timestamp of the last document modification in the database (Unix epoch in ms). This field appears only for documents which were somehow modified by API v3 (inserted, updated or deleted).
    @SerializedName("created_at")
    val createdAt: String? = null,  // string or string timestamp on previous version of api, in my examples, a lot of treatments don't have date, only created_at, some of them with string others with long...
    @SerializedName("date") val date: Long? = null,                     // date as milliseconds
    @SerializedName("uploaderBattery") val uploaderBattery: Int? = null,// integer($int64)
    @SerializedName("isCharging") val isCharging: Boolean? = null,
    @SerializedName("device") val device: String? = null,               // "openaps://samsung SM-G970F"

    @SerializedName("uploader") val uploader: Uploader? = null,
    @SerializedName("pump") val pump: Pump? = null,
    @SerializedName("openaps") val openaps: OpenAps? = null
) {

    data class Pump(
        @SerializedName("clock") val clock: String? = null, // timestamp in ISO
        @SerializedName("reservoir") val reservoir: Double? = null,
        @SerializedName("reservoir_display_override") val reservoirDisplayOverride: String? = null,
        @SerializedName("battery") val battery: Battery? = null,
        @SerializedName("status") val status: Status? = null,
        @SerializedName("extended") val extended: JsonObject? = null   // Gson, content depending on pump driver
    ) {

        data class Battery(
            @SerializedName("percent") val percent: Int? = null,
            @SerializedName("voltage") val voltage: Double? = null
        )

        data class Status(
            @SerializedName("status") val status: String? = null,
            @SerializedName("timestamp") val timestamp: String? = null
        )
    }

    data class OpenAps(
        @SerializedName("suggested") val suggested: JsonObject? = null, // Gson
        @SerializedName("enacted") val enacted: JsonObject? = null,     // Gson
        @SerializedName("iob") val iob: JsonObject? = null              // Gson
    )

    data class Uploader(
        @SerializedName("battery") val battery: Int? = null
    )
}
