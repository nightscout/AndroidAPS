package app.aaps.core.nssdk.localmodel.devicestatus

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * NS DeviceStatus coming from uploader or AAPS
 *
 **/
@Serializable
data class NSDeviceStatus(
    var app: String? = null,
    val identifier: String? = null, // string Main addressing, required field that identifies document in the collection. The client should not create the identifier, the server automatically assigns it when the document is inserted.
    val srvCreated: Long? = null,   // integer($int64) example: 1525383610088 The server's timestamp of document insertion into the database (Unix epoch in ms). This field appears only for documents which were inserted by API v3.
    val srvModified: Long? = null, // integer($int64) example: 1525383610088 The server's timestamp of the last document modification in the database (Unix epoch in ms). This field appears only for documents which were somehow modified by API v3 (inserted, updated or deleted).
    @SerialName("created_at")
    val createdAt: String? = null,  // string or string timestamp on previous version of api, in my examples, a lot of treatments don't have date, only created_at, some of them with string others with long...
    val date: Long? = null,                     // date as milliseconds
    val uploaderBattery: Int? = null,// integer($int64)
    val isCharging: Boolean? = null,
    val device: String? = null,               // "openaps://samsung SM-G970F"

    val uploader: Uploader? = null,
    val pump: Pump? = null,
    val openaps: OpenAps? = null
) {

    @Serializable data class Pump(
        val clock: String? = null, // timestamp in ISO
        val reservoir: Double? = null,
        @SerialName("reservoir_display_override") val reservoirDisplayOverride: String? = null,
        val battery: Battery? = null,
        val status: Status? = null,
        val extended: JsonObject? = null
    ) {

        @Serializable data class Battery(
            val percent: Int? = null,
            val voltage: Double? = null
        )

        @Serializable data class Status(
            val status: String? = null,
            val timestamp: String? = null
        )
    }

    @Serializable data class OpenAps(
        val suggested: JsonObject? = null,
        val enacted: JsonObject? = null,
        val iob: JsonObject? = null
    )

    @Serializable data class Uploader(
        val battery: Int? = null
    )
}
