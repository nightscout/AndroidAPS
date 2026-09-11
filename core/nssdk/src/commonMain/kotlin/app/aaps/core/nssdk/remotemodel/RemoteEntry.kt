package app.aaps.core.nssdk.remotemodel

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/*
* Depending on the type, different other fields are present.
* Those technically need to be optional.
*
* On upload a sanity check still needs to be done to verify that all mandatory fields for that type are there.
*
* TODO: Find out all types with their optional and mandatory fields
*
* */
@Serializable
internal data class RemoteEntry(
    // Default on purpose, same reason as RemoteFood: `entries` is a shared multi-writer collection
    // and Nightscout does not mark `type` required. Gson left it null and both SvgMapper and
    // MbgMapper then skipped the record; kotlinx would treat it as mandatory and throw for the whole
    // page, and LoadBgWorker only advances its cursor after a successful decode - so one such record
    // would stop all glucose reception and re-request the same page forever.
    @SerialName("type") val type: String = "", // sgv, mbg, cal, etc;  Bolus type NORMAL, SMB, PRIMING
    @SerialName("sgv") val sgv: Double? = null, // number The glucose reading. (only available for sgv types)
    @SerialName("dateString") val dateString: String? = null,
    @SerialName("date") var date: Long? = null, // required ? TODO: date and dateString are redundant - are both needed? how to handle inconsistency then? Only expose one to clients?
    @SerialName("device") val device: String? = null, // The device from which the data originated (including serial number of the device, if it is relevant and safe).
    @SerialName("direction") val direction: String? = null, // TODO: what implicit convention for the directions exists?
    @SerialName("identifier") val identifier: String? = null,
    @SerialName("srvModified") val srvModified: Long? = null,
    @SerialName("srvCreated") val srvCreated: Long? = null,
    // Philoul Others fields below found in API v3 doc
    @SerialName("app") var app: String? = null,
    @SerialName("utcOffset") var utcOffset: Long? = null, // Local UTC offset (timezone) of the event in minutes. This field can be set either directly by the client (in the incoming document) or it is
    // automatically parsed from the date field.
    @SerialName("subject") val subject: String? = null, // Name of the security subject (within Nightscout scope) which has created the document. This field is automatically set by the server from the passed token or JWT.
    @SerialName("modifiedBy") val modifiedBy: String? = null, // Name of the security subject (within Nightscout scope) which has patched or deleted the document for the last time. This field is automatically set by the server.
    @SerialName("isValid") val isValid: Boolean? = null, // A flag set by the server only for deleted documents. This field appears only within history operation and for documents which were deleted by API v3 (and they always have a false value)
    @SerialName("isReadOnly") val isReadOnly: Boolean? = null, // A flag set by client that locks the document from any changes. Every document marked with isReadOnly=true is forever immutable and cannot even be deleted.
    @SerialName("noise") val noise: Double? = null, // 0 or 1 found in the export, I don't know if other values possible ?
    @SerialName("filtered") val filtered: Double? = null, // The raw filtered value directly from CGM transmitter. (only available for sgv types)
    @SerialName("unfiltered") val unfiltered: Double? = null, // The raw unfiltered value directly from CGM transmitter. (only available for sgv types)
    @SerialName("units") val units: String? = null, // The units for the glucose value, mg/dl or mmol/l. It is strongly recommended to fill in this field.
    @SerialName("mbg") val mbg: Double? = null, // Manual blood glucose reading (only available for mbg types). AAPS uses a marked mbg to carry a calibration fingerstick.
    // AAPS-specific calibration fields, carried on a marked `mbg` entry so a follower can re-fit the calibration curve.
    @SerialName("sensorMgdlAtPairing") val sensorMgdlAtPairing: Double? = null, // sensor value at the moment the fingerstick was entered
    @SerialName("isCalibration") val isCalibration: Boolean? = null // marker: true when this mbg is an AAPS calibration pair (vs a foreign manual BG)
)
