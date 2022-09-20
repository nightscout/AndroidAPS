package info.nightscout.sdk.remotemodel

import com.google.gson.annotations.SerializedName

/*
* Depending on the type, different other fields are present.
* Those technically need to be optional.
*
* On upload a sanity check still needs to be done to verify that all mandatory fields for that type are there.
*
* TODO: Find out all types with their optional and mandatory fields
*
* */
internal data class RemoteEntry(
    @SerializedName("type") val type: String, // sgv, mbg, cal, etc
    @SerializedName("sgv") val sgv: Double?, // number The glucose reading. (only available for sgv types)
    @SerializedName("dateString") val dateString: String,
    @SerializedName("date") val date: Long, // required ? TODO: date and dateString are redundant - are both needed? how to handle inconsistency then? Only expose one to clients?
    @SerializedName("device") val device: String?, // The device from which the data originated (including serial number of the device, if it is relevant and safe).
    @SerializedName("direction") val direction: String?, // TODO: what implicit convention for the directions exists?
    @SerializedName("identifier") val identifier: String,
    @SerializedName("srvModified") val srvModified: Long,
    @SerializedName("srvCreated") val srvCreated: Long,
    // Philoul Others fields below found in API v3 doc
    // @SerializedName("app") val app : String,                      // TODO required ? Application or system in which the record was entered by human or device for the first time.
    @SerializedName("utcOffset") val utcOffset: Int?, // Local UTC offset (timezone) of the event in minutes. This field can be set either directly by the client (in the incoming document) or it is automatically parsed from the date field.
    @SerializedName("subject") val subject: String?, // Name of the security subject (within Nightscout scope) which has created the document. This field is automatically set by the server from the passed token or JWT.
    @SerializedName("modifiedBy") val modifiedBy: String?, // Name of the security subject (within Nightscout scope) which has patched or deleted the document for the last time. This field is automatically set by the server.
    @SerializedName("isValid") val isValid: Boolean?, // A flag set by the server only for deleted documents. This field appears only within history operation and for documents which were deleted by API v3 (and they always have a false value)
    @SerializedName("isReadOnly") val isReadOnly: Boolean?, // A flag set by client that locks the document from any changes. Every document marked with isReadOnly=true is forever immutable and cannot even be deleted.
    @SerializedName("noise") val noise: Int?, // 0 or 1 found in the export, I don't know if other values possible ?
    @SerializedName("filtered") val filtered: Double?, // The raw filtered value directly from CGM transmitter. (only available for sgv types)
    @SerializedName("unfiltered") val unfiltered: Double?, // The raw unfiltered value directly from CGM transmitter. (only available for sgv types)
    @SerializedName("units") val units: String? // The units for the glucose value, mg/dl or mmol/l. It is strongly recommended to fill in this field.

    // TODO: add fields for other types (currently only basic "sgv" is covered)
    // @SerializedName("_id") val _id : String?,                   // Internally assigned database id. This field is for internal server purposes only, clients communicate with API by using identifier field.
    //
)
