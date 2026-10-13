package app.aaps.core.nssdk.remotemodel

import com.google.gson.annotations.SerializedName
import app.aaps.core.nssdk.localmodel.treatment.EventType
import org.joda.time.DateTime
import org.joda.time.format.ISODateTimeFormat

/*
* Depending on the type, different other fields are present.
* Those technically need to be optional.
*
* On upload a sanity check still needs to be done to verify that all mandatory fields for that type are there.
*
* TODO: Find out all types with their optional and mandatory fields
*
* */
internal data class RemoteTreatment(
    @SerializedName("identifier") val identifier: String?,       // string Main addressing, required field that identifies document in the collection. The client should not create the identifier, the server automatically assigns it when the document is inserted.
    @SerializedName("date") var date: Long? = null,                    // integer($int64) or string required timestamp when the record or event occurred, you can choose from three input formats Unix epoch in milliseconds (1525383610088), Unix epoch in seconds (1525383610), ISO 8601 with optional timezone ('2018-05-03T21:40:10.088Z' or '2018-05-03T23:40:10.088+02:00')
    @SerializedName("mills") val mills: Long? = null,                  // integer($int64) or string required timestamp when the record or event occurred, you can choose from three input formats Unix
    @SerializedName("timestamp") val timestamp: Long? = null,          // integer($int64) or string required timestamp when the record or event occurred, you can choose from three input formats Unix  epoch in milliseconds (1525383610088), Unix epoch in seconds (1525383610), ISO 8601 with optional timezone ('2018-05-03T21:40:10.088Z' or '2018-05-03T23:40:10.088+02:00')
    @SerializedName("created_at") val created_at: String? = null,       // integer($int64) or string timestamp on previous version of api, in my examples, a lot of treatments don't have date, only created_at, some of them with string others with long...
    @SerializedName("utcOffset") var utcOffset: Long? = null,          // integer Local UTC offset (timezone) of the event in minutes. This field can be set either directly by the client (in the incoming document) or it is automatically parsed from the date field.
    @SerializedName("app") var app : String? = null,                   // Application or system in which the record was entered by human or device for the first time.
    @SerializedName("device") val device: String? = null,              // string The device from which the data originated (including serial number of the device, if it is relevant and safe).
    @SerializedName("srvCreated") val srvCreated: Long? = null,         // integer($int64) example: 1525383610088 The server's timestamp of document insertion into the database (Unix epoch in ms). This field appears only for documents which were inserted by API v3.
    @SerializedName("subject") val subject: String? = null,            // string Name of the security subject (within Nightscout scope) which has created the document. This field is automatically set by the server from the passed token or JWT.
    @SerializedName("srvModified") val srvModified: Long? = null,       // integer($int64) example: 1525383610088 The server's timestamp of the last document modification in the database (Unix epoch in ms). This field appears only for documents which were somehow modified by API v3 (inserted, updated or deleted).
    @SerializedName("modifiedBy") val modifiedBy: String? = null,      // string Name of the security subject (within Nightscout scope) which has patched or deleted the document for the last time. This field is automatically set by the server.
    @SerializedName("isValid") val isValid: Boolean? = null,           // boolean A flag set by the server only for deleted documents. This field appears only within history operation and for documents which were deleted by API v3 (and they always have a false value)
    @SerializedName("isReadOnly") val isReadOnly: Boolean? = null,     // boolean A flag set by client that locks the document from any changes. Every document marked with isReadOnly=true is forever immutable and cannot even be deleted.
    @SerializedName("eventType") val eventType: EventType?,      // string "BG Check", "Snack Bolus", "Meal Bolus", "Correction Bolus", "Carb Correction", "Combo Bolus", "Announcement", "Note", "Question", "Exercise", "Site Change", "Sensor Start", "Sensor Change", "Pump Battery Change", "Insulin Change", "Temp Basal", "Profile Switch", "D.A.D. Alert", "Temporary Target", "OpenAPS Offline", "Bolus Wizard"
    @SerializedName("glucose") val glucose: Double? = null,            // double Current glucose
    @SerializedName("glucoseType") val glucoseType: String? = null,    // string example: "Sensor", "Finger", "Manual"
    @SerializedName("units") val units: String? = null,                // string The units for the glucose value, mg/dl or mmol/l. It is strongly recommended to fill in this field.
    @SerializedName("carbs") val carbs: Double? = null,                // number... Amount of carbs given.
    @SerializedName("protein") val protein: Int? = null,               // number... Amount of protein given.
    @SerializedName("fat") val fat: Int? = null,                       // number... Amount of fat given.
    @SerializedName("insulin") val insulin: Double? = null,            // number... Amount of insulin, if any.
    /** Duration in minutes */
    @SerializedName("duration") val duration: Long? = null,             // number... Duration in minutes.
    /** Duration in milliseconds */
    @SerializedName("durationInMilliseconds") val durationInMilliseconds: Long? = null, // number... Duration in milliseconds.
    @SerializedName("preBolus") val preBolus: Int? = null,             // number... How many minutes the bolus was given before the meal started.
    @SerializedName("splitNow") val splitNow: Int? = null,             // number... Immediate part of combo bolus (in percent).
    @SerializedName("splitExt") val splitExt: Int? = null,             // number... Extended part of combo bolus (in percent).
    @SerializedName("percent") val percent: Double? = null,            // number... Eventual basal change in percent.
    @SerializedName("absolute") val absolute: Double? = null,          // number... Eventual basal change in absolute value (insulin units per hour).
    @SerializedName("targetTop") val targetTop: Double? = null,        // number... Top limit of temporary target.
    @SerializedName("targetBottom") val targetBottom: Double? = null,  // number... Bottom limit of temporary target.
    @SerializedName("profile") val profile: String? = null,            // string Name of the profile to which the pump has been switched.
    @SerializedName("reason") val reason: String? = null,              // string For example the reason why the profile has been switched or why the temporary target has been set.
    @SerializedName("mode") val mode: String? = null,                  // string RunningMode
    @SerializedName("location") val location: String? = null,              // string Location for site management defined in TE.Location
    @SerializedName("arrow") val arrow: String? = null,                 // string Arrow for site management defined in TE.Arrow
    @SerializedName("autoForced") val autoForced: Boolean? = null,     // boolean RunningMode
    @SerializedName("reasons") val reasons: String? = null,            // string RunningMode
    @SerializedName("notes") val notes: String? = null,                // string Description/notes of treatment.
    @SerializedName("enteredBy") val enteredBy: String? = null,        // string Who entered the treatment.

    @SerializedName("endId") val endId: Long? = null,                  // long id of record which ended this
    @SerializedName("pumpId") val pumpId: Long? = null,                // long or "Meal Bolus", "Correction Bolus", "Combo Bolus" ex  4102 not sure if long or int
    @SerializedName("pumpType") val pumpType: String? = null,          // string "Meal Bolus", "Correction Bolus", "Combo Bolus" ex "ACCU_CHEK_INSIGHT_BLUETOOTH",
    @SerializedName("pumpSerial") val pumpSerial: String? = null,      // string "Meal Bolus", "Correction Bolus", "Combo Bolus" "33013206",

    // other fields found in examples but not in documentation
    @SerializedName("profileJson") val profileJson: String? = null,            // string "Profile Switch" ex json toString "{\"units\":\"mg\\/dl\",\"dia\":5,\"timezone\":\"Africa\\/Cairo\",
    // \"sens\":[{\"time\":\"00:00\",\"timeAsSeconds\":0,\"value\":60},{\"time\":\"07:00\",\"timeAsSeconds\":25200,\"value\":60},{\"time\":\"08:00\",\"timeAsSeconds\":28800,\"value\":61.33333333333333},{\"time\":\"09:00\",\"timeAsSeconds\":32400,\"value\":65.33333333333333},{\"time\":\"10:00\",\"timeAsSeconds\":36000,\"value\":69.33333333333333},{\"time\":\"11:00\",\"timeAsSeconds\":39600,\"value\":73.33333333333333},{\"time\":\"13:00\",\"timeAsSeconds\":46800,\"value\":72},{\"time\":\"14:00\",\"timeAsSeconds\":50400,\"value\":68},{\"time\":\"15:00\",\"timeAsSeconds\":54000,\"value\":65.33333333333333},{\"time\":\"16:00\",\"timeAsSeconds\":57600,\"value\":65.33333333333333}],\"carbratio\":[{\"time\":\"00:00\",\"timeAsSeconds\":0,\"value\":5.7333333333333325},{\"time\":\"11:00\",\"timeAsSeconds\":39600,\"value\":7.333333333333333},{\"time\":\"16:00\",\"timeAsSeconds\":57600,\"value\":6.666666666666666}],\"basal\":[{\"time\":\"00:00\",\"timeAsSeconds\":0,\"value\":0.5249999999999999},{\"time\":\"01:00\",\"timeAsSeconds\":3600,\"value\":0.585},{\"time\":\"02:00\",\"timeAsSeconds\":7200,\"value\":0.6375},{\"time\":\"03:00\",\"timeAsSeconds\":10800,\"value\":0.5625},{\"time\":\"04:00\",\"timeAsSeconds\":14400,\"value\":0.4575},{\"time\":\"05:00\",\"timeAsSeconds\":18000,\"value\":0.5175},{\"time\":\"06:00\",\"timeAsSeconds\":21600,\"value\":0.48},{\"time\":\"07:00\",\"timeAsSeconds\":25200,\"value\":0.51},{\"time\":\"08:00\",\"timeAsSeconds\":28800,\"value\":0.48750000000000004},{\"time\":\"09:00\",\"timeAsSeconds\":32400,\"value\":0.48},{\"time\":\"10:00\",\"timeAsSeconds\":36000,\"value\":0.48750000000000004},{\"time\":\"11:00\",\"timeAsSeconds\":39600,\"value\":0.5025000000000001},{\"time\":\"12:00\",\"timeAsSeconds\":43200,\"value\":0.5549999999999999},{\"time\":\"13:00\",\"timeAsSeconds\":46800,\"value\":0.5700000000000001},{\"time\":\"14:00\",\"timeAsSeconds\":50400,\"value\":0.5700000000000001},{\"time\":\"15:00\",\"timeAsSeconds\":54000,\"value\":0.5775},{\"time\":\"16:00\",\"timeAsSeconds\":57600,\"value\":0.51},{\"time\":\"17:00\",\"timeAsSeconds\":61200,\"value\":0.54},{\"time\":\"18:00\",\"timeAsSeconds\":64800,\"value\":0.48750000000000004},{\"time\":\"19:00\",\"timeAsSeconds\":68400,\"value\":0.5249999999999999},{\"time\":\"20:00\",\"timeAsSeconds\":72000,\"value\":0.46499999999999997},{\"time\":\"21:00\",\"timeAsSeconds\":75600,\"value\":0.46499999999999997},{\"time\":\"22:00\",\"timeAsSeconds\":79200,\"value\":0.43499999999999994},{\"time\":\"23:00\",\"timeAsSeconds\":82800,\"value\":0.41250000000000003}],\"target_low\":[{\"time\":\"00:00\",\"timeAsSeconds\":0,\"value\":100},{\"time\":\"06:00\",\"timeAsSeconds\":21600,\"value\":90},{\"time\":\"09:00\",\"timeAsSeconds\":32400,\"value\":100},{\"time\":\"11:00\",\"timeAsSeconds\":39600,\"value\":90},{\"time\":\"14:00\",\"timeAsSeconds\":50400,\"value\":100},{\"time\":\"18:00\",\"timeAsSeconds\":64800,\"value\":90},{\"time\":\"21:00\",\"timeAsSeconds\":75600,\"value\":100}],\"target_high\":[{\"time\":\"00:00\",\"timeAsSeconds\":0,\"value\":100},{\"time\":\"06:00\",\"timeAsSeconds\":21600,\"value\":90},{\"time\":\"09:00\",\"timeAsSeconds\":32400,\"value\":100},{\"time\":\"11:00\",\"timeAsSeconds\":39600,\"value\":90},{\"time\":\"14:00\",\"timeAsSeconds\":50400,\"value\":100},{\"time\":\"18:00\",\"timeAsSeconds\":64800,\"value\":90},{\"time\":\"21:00\",\"timeAsSeconds\":75600,\"value\":100}]}",
    @SerializedName("originalProfileName") val originalProfileName: String? = null, // string "Effective Profile Switch"
    @SerializedName("originalCustomizedName") val originalCustomizedName: String? = null, // string "Effective Profile Switch"
    @SerializedName("originalTimeshift") val originalTimeshift: Long? = null,  // long "Effective Profile Switch"
    @SerializedName("originalPercentage") val originalPercentage: Int? = null, // int "Effective Profile Switch"
    @SerializedName("originalDuration") val originalDuration: Long? = null,    // long "Effective Profile Switch", RunningMode
    @SerializedName("originalEnd") val originalEnd: Long? = null,              // long "Effective Profile Switch"

    @SerializedName("bolusCalculatorResult") val bolusCalculatorResult: String? = null, // string "Bolus Wizard" json toString ex "bolusCalculatorResult": "{\"basalIOB\":-0.247,\"bolusIOB\":-1.837,\"carbs\":45.0,\"carbsInsulin\":9.0,\"cob\":0.0,\"cobInsulin\":0.0,\"dateCreated\":1626202788810,\"glucoseDifference\":44.0,\"glucoseInsulin\":0.8979591836734694,\"glucoseTrend\":5.5,\"glucoseValue\":134.0,\"ic\":5.0,\"id\":331,\"interfaceIDs_backing\":{\"nightscoutId\":\"60ede2a4c574da0004a3869d\"},\"isValid\":true,\"isf\":49.0,\"note\":\"\",\"otherCorrection\":0.0,\"percentageCorrection\":90,\"profileName\":\"Tuned 13/01 90%Lyum\",\"superbolusInsulin\":0.0,\"targetBGHigh\":90.0,\"targetBGLow\":90.0,\"timestamp\":1626202783325,\"totalInsulin\":7.34,\"trendInsulin\":0.336734693877551,\"utcOffset\":7200000,\"version\":1,\"wasBasalIOBUsed\":true,\"wasBolusIOBUsed\":true,\"wasCOBUsed\":true,\"wasGlucoseUsed\":true,\"wasSuperbolusUsed\":false,\"wasTempTargetUsed\":false,\"wasTrendUsed\":true,\"wereCarbsUsed\":false}",
    @SerializedName("type") val type: String? = null,                          // string "Meal Bolus", "Correction Bolus", "Combo Bolus", "Temp Basal" type of bolus "NORMAL", "SMB", "FAKE_EXTENDED"
    @SerializedName("isSMB") val isSMB: Boolean? = null,                        // boolean "Meal Bolus", "Correction Bolus", "Combo Bolus"
    @SerializedName("enteredinsulin") val enteredinsulin: Double? = null,      // number... "Combo Bolus" insulin is missing only enteredinsulin field found
    @SerializedName("relative") val relative: Double? = null,                  // number... "Combo Bolus", "extendedEmulated" (not in doc see below)
    @SerializedName("isEmulatingTempBasal") val isEmulatingTempBasal: Boolean? = null,  // boolean "Combo Bolus", "extendedEmulated" (not in doc see below)
    @SerializedName("isAnnouncement") val isAnnouncement: Boolean? = null,      // boolean "Announcement"
    @SerializedName("rate") val rate: Double? = null,                          // Double "Temp Basal" absolute rate (could be calculated with percent and profile information...)
    @SerializedName("extendedEmulated") var extendedEmulated: RemoteTreatment? = null,  // Gson of emulated EB
    @SerializedName("timeshift") val timeshift: Long? = null,                   // integer "Profile Switch"
    @SerializedName("percentage") val percentage: Int? = null,                 // integer "Profile Switch"
    @SerializedName("isBasalInsulin") val isBasalInsulin: Boolean? = null      // boolean "Bolus"
) {

    fun timestamp(): Long {
        return date ?: mills ?: timestamp ?: created_at?. let { fromISODateString(created_at) } ?: 0L
    }

    private fun fromISODateString(isoDateString: String): Long =
        try {
            val parser = ISODateTimeFormat.dateTimeParser()
            val dateTime = DateTime.parse(isoDateString, parser)
            dateTime.toDate().time
        } catch (e: Exception) {
            0L
        }
}
