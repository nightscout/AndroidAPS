package info.nightscout.sdk.remotemodel

import com.google.gson.annotations.SerializedName

/**
 * Depending on the type, different other fields are present.
 * Those technically need to be optional.
 *
 * On upload a sanity check still needs to be done to verify that all mandatory fields for that type are there.
 *
 **/
internal data class RemoteFood(
    @SerializedName("type") val type: String, // we are interesting in type "food"
    @SerializedName("date") val date: Long?,
    @SerializedName("name") val name: String,
    @SerializedName("category") val category: String?,
    @SerializedName("subcategory") val subcategory: String?,
    @SerializedName("unit") val unit: String?,
    @SerializedName("portion") val portion: Double,
    @SerializedName("carbs") val carbs: Int,
    @SerializedName("gi") val gi: Int?,
    @SerializedName("energy") val energy: Int?,
    @SerializedName("protein") val protein: Int?,
    @SerializedName("fat") val fat: Int?,
    @SerializedName("identifier")
    val identifier: String?,       // string Main addressing, required field that identifies document in the collection. The client should not create the identifier, the server automatically assigns it when the document is inserted.
    @SerializedName("isValid")
    val isValid: Boolean?, // A flag set by the server only for deleted documents. This field appears only within history operation and for documents which were deleted by API v3 (and they always have a false value)
    @SerializedName("isReadOnly")
    val isReadOnly: Boolean?, // A flag set by client that locks the document from any changes. Every document marked with isReadOnly=true is forever immutable and cannot even be deleted.
    @SerializedName("app") var app: String? = null,                   // Application or system in which the record was entered by human or device for the first time.
    @SerializedName("device") val device: String? = null,              // string The device from which the data originated (including serial number of the device, if it is relevant and safe).
    @SerializedName("srvCreated")
    val srvCreated: Long? = null,         // integer($int64) example: 1525383610088 The server's timestamp of document insertion into the database (Unix epoch in ms). This field appears only for documents which were inserted by API v3.
    @SerializedName("subject")
    val subject: String? = null,            // string Name of the security subject (within Nightscout scope) which has created the document. This field is automatically set by the server from the passed token or JWT.
    @SerializedName("srvModified")
    val srvModified: Long? = null,       // integer($int64) example: 1525383610088 The server's timestamp of the last document modification in the database (Unix epoch in ms). This field appears only for documents which were somehow modified by API v3 (inserted, updated or deleted).
    @SerializedName("modifiedBy")
    val modifiedBy: String? = null      // string Name of the security subject (within Nightscout scope) which has patched or deleted the document for the last time. This field is automatically set by the server.
)
