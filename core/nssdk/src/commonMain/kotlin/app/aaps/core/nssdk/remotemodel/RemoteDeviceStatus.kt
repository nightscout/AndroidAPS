package app.aaps.core.nssdk.remotemodel

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * DeviceStatus coming from uploader or AAPS
 *
 **/
@Serializable
internal data class RemoteDeviceStatus(
    @SerialName("app") var app: String? = null,
    @SerialName("identifier")
    val identifier: String? = null, // string Main addressing, required field that identifies document in the collection. The client should not create the identifier, the server automatically assigns it when the document is inserted.
    @SerialName("srvCreated")
    val srvCreated: Long? = null,   // integer($int64) example: 1525383610088 The server's timestamp of document insertion into the database (Unix epoch in ms). This field appears only for documents which were inserted by API v3.
    @SerialName("srvModified")
    val srvModified: Long? = null, // integer($int64) example: 1525383610088 The server's timestamp of the last document modification in the database (Unix epoch in ms). This field appears only for documents which were somehow modified by API v3 (inserted, updated or deleted).
    @SerialName("created_at")
    val createdAt: String? = null,  // string or string timestamp on previous version of api, in my examples, a lot of treatments don't have date, only created_at, some of them with string others with long...
    @SerialName("date") val date: Long? = null,                     // date as milliseconds
    @SerialName("uploaderBattery") val uploaderBattery: Int? = null,// integer($int64)
    @SerialName("isCharging") val isCharging: Boolean? = null,
    @SerialName("device") val device: String? = null,               // "openaps://samsung SM-G970F"

    @SerialName("uploader") val uploader: Uploader? = null,
    @SerialName("pump") val pump: Pump? = null,
    @SerialName("openaps") val openaps: OpenAps? = null
) {

    @Serializable
    data class Pump(
        @SerialName("clock") val clock: String? = null, // timestamp in ISO
        @SerialName("reservoir") val reservoir: Double? = null,
        @SerialName("reservoir_display_override") val reservoirDisplayOverride: String? = null,
        @SerialName("battery") val battery: Battery? = null,
        @SerialName("status") val status: Status? = null,
        @SerialName("extended") val extended: JsonObject? = null   // schema-less, content depends on the pump driver
    ) {

        @Serializable
        data class Battery(
            @SerialName("percent") val percent: Int? = null,
            @SerialName("voltage") val voltage: Double? = null
        )

        @Serializable
        data class Status(
            @SerialName("status") val status: String? = null,
            @SerialName("timestamp") val timestamp: String? = null
        )
    }

    @Serializable
    data class OpenAps(
        @SerialName("suggested") val suggested: JsonObject? = null,
        @SerialName("enacted") val enacted: JsonObject? = null,
        @SerialName("iob") val iob: JsonObject? = null
    )

    @Serializable
    data class Uploader(
        @SerialName("battery") val battery: Int? = null
    )
}
