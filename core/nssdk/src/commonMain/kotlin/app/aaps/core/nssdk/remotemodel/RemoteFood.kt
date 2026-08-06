package app.aaps.core.nssdk.remotemodel

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Depending on the type, different other fields are present.
 * Those technically need to be optional.
 *
 * On upload a sanity check still needs to be done to verify that all mandatory fields for that type are there.
 *
 **/
@Serializable
internal data class RemoteFood(
    // These four carry defaults on purpose. The Nightscout `food` collection is shared: besides food
    // records it also holds "quickpick" documents written by the Nightscout food editor, which have
    // no portion and no carbs. Gson filled a missing non-null field with null / 0 / 0.0 and the
    // `else -> return null` branch in FoodMapper then dropped the document, which is what that
    // branch is for. kotlinx instead treats a non-null field without a default as **mandatory** and
    // throws - and `v3/food` is decoded as one list, so a single quickpick would lose the whole food
    // database. The defaults below reproduce Gson's values exactly, so the old tolerant behaviour is
    // restored. `encodeDefaults = true` means the upload format does not change.
    @SerialName("type") val type: String = "", // we are interesting in type "food"
    @SerialName("date") val date: Long? = null,
    @SerialName("name") val name: String = "",
    @SerialName("category") val category: String? = null,
    @SerialName("subcategory") val subcategory: String? = null,
    @SerialName("unit") val unit: String? = null,
    @SerialName("portion") val portion: Double = 0.0,
    @SerialName("carbs") val carbs: Int = 0,
    @SerialName("gi") val gi: Int? = null,
    @SerialName("energy") val energy: Int? = null,
    @SerialName("protein") val protein: Int? = null,
    @SerialName("fat") val fat: Int? = null,
    @SerialName("identifier")
    val identifier: String?,       // string Main addressing, required field that identifies document in the collection. The client should not create the identifier, the server automatically assigns it when the document is inserted.
    @SerialName("isValid")
    val isValid: Boolean?, // A flag set by the server only for deleted documents. This field appears only within history operation and for documents which were deleted by API v3 (and they always have a false value)
    @SerialName("isReadOnly")
    val isReadOnly: Boolean?, // A flag set by client that locks the document from any changes. Every document marked with isReadOnly=true is forever immutable and cannot even be deleted.
    @SerialName("app") var app: String? = null,                   // Application or system in which the record was entered by human or device for the first time.
    @SerialName("device") val device: String? = null,              // string The device from which the data originated (including serial number of the device, if it is relevant and safe).
    @SerialName("srvCreated")
    val srvCreated: Long? = null,         // integer($int64) example: 1525383610088 The server's timestamp of document insertion into the database (Unix epoch in ms). This field appears only for documents which were inserted by API v3.
    @SerialName("subject")
    val subject: String? = null,            // string Name of the security subject (within Nightscout scope) which has created the document. This field is automatically set by the server from the passed token or JWT.
    @SerialName("srvModified")
    val srvModified: Long? = null,       // integer($int64) example: 1525383610088 The server's timestamp of the last document modification in the database (Unix epoch in ms). This field appears only for documents which were somehow modified by API v3 (inserted, updated or deleted).
    @SerialName("modifiedBy")
    val modifiedBy: String? = null      // string Name of the security subject (within Nightscout scope) which has patched or deleted the document for the last time. This field is automatically set by the server.
)
