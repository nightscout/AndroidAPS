package app.aaps.core.nssdk.remotemodel

import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable

/**
 * DeviceStatus coming from uploader or AAPS
 *
 **/
@Serializable
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
    @SerializedName("date") val date: Long?,                     // date as milliseconds
    @SerializedName("uploaderBattery") val uploaderBattery: Int?,// integer($int64)
    @SerializedName("isCharging") val isCharging: Boolean?,
    @SerializedName("device") val device: String?,               // "openaps://samsung SM-G970F"

    @SerializedName("uploader") val uploader: Uploader?,
    @SerializedName("pump") val pump: Pump?,
    @SerializedName("openaps") val openaps: OpenAps?,
    @SerializedName("configuration") val configuration: Configuration?
) {

    @Serializable data class Pump(
        @SerializedName("clock") val clock: String?, // timestamp in ISO
        @SerializedName("reservoir") val reservoir: Double?,
        @SerializedName("reservoir_display_override") val reservoirDisplayOverride: String?,
        @SerializedName("battery") val battery: Battery?,
        @SerializedName("status") val status: Status?,
        @Contextual @SerializedName("extended") val extended: JsonObject?   // Gson, content depending on pump driver
    ) {

        @Serializable data class Battery(
            @SerializedName("percent") val percent: Int?,
            @SerializedName("voltage") val voltage: Double?
        )

        @Serializable data class Status(
            @SerializedName("status") val status: String?,
            @SerializedName("timestamp") val timestamp: String?
        )
    }

    @Serializable data class OpenAps(
        @Contextual @SerializedName("suggested") val suggested: JsonObject?, // Gson
        @Contextual @SerializedName("enacted") val enacted: JsonObject?,     // Gson
        @Contextual @SerializedName("iob") val iob: JsonObject?              // Gson
    )

    @Serializable data class Uploader(
        @SerializedName("battery") val battery: Int?
    )

    @Serializable data class Configuration(
        @SerializedName("pump") val pump: String?,
        @SerializedName("version") val version: String?,
        @SerializedName("insulin") val insulin: Int?,
        @SerializedName("sensitivity") val sensitivity: Int?,
        @SerializedName("smoothing") val smoothing: String?,
        @Contextual @SerializedName("insulinConfiguration") val insulinConfiguration: JsonObject?,
        @Contextual @SerializedName("sensitivityConfiguration") val sensitivityConfiguration: JsonObject?,
        @Contextual @SerializedName("overviewConfiguration") val overviewConfiguration: JsonObject?,
        @Contextual @SerializedName("safetyConfiguration") val safetyConfiguration: JsonObject?
    )
}
