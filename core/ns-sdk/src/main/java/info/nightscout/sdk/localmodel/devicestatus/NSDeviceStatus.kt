package info.nightscout.sdk.localmodel.devicestatus

import com.google.gson.annotations.SerializedName
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import org.json.JSONObject

/**
 * NS DeviceStatus coming from uploader or AAPS
 *
 **/
@Serializable
data class NSDeviceStatus(
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
        @Contextual @SerializedName("extended") val extended: JSONObject?   // Gson, content depending on pump driver
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
        @Contextual @SerializedName("suggested") val suggested: JSONObject?, // Gson
        @Contextual @SerializedName("enacted") val enacted: JSONObject?,     // Gson
        @Contextual @SerializedName("iob") val iob: JSONObject?              // Gson
    )

    @Serializable data class Uploader(
        @SerializedName("battery") val battery: Int?,
    )

    @Serializable data class Configuration(
        @SerializedName("pump") val pump: String?,
        @SerializedName("version") val version: String?,
        @SerializedName("insulin") val insulin: Int?,
        @SerializedName("sensitivity") val sensitivity: Int?,
        @SerializedName("smoothing") val smoothing: String?,
        @Contextual @SerializedName("insulinConfiguration") val insulinConfiguration: JSONObject?,
        @Contextual @SerializedName("sensitivityConfiguration") val sensitivityConfiguration: JSONObject?,
        @Contextual @SerializedName("overviewConfiguration") val overviewConfiguration: JSONObject?,
        @Contextual @SerializedName("safetyConfiguration") val safetyConfiguration: JSONObject?
    )
}
