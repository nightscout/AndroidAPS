package info.nightscout.androidaps.database.entities

import androidx.annotation.StringRes

sealed class XXXValueWithUnit {

    object UNKNOWN : XXXValueWithUnit() // formerly None used as fallback

    data class SimpleString(val value: String) : XXXValueWithUnit() // formerly one usage of None

    data class SimpleInt(val value: Int) : XXXValueWithUnit() // formerly one usage of None

    class Mgdl(val value: Double) : XXXValueWithUnit()

    class Mmoll(val value: Double) : XXXValueWithUnit()

    class Timestamp(val value: Long) : XXXValueWithUnit()

    class Insulin(val value: Double) : XXXValueWithUnit()

    class UnitPerHour(val value: Double) : XXXValueWithUnit()

    class Gram(val value: Int) : XXXValueWithUnit()

    class Minute(val value: Int) : XXXValueWithUnit()

    class Hour(val value: Int) : XXXValueWithUnit()

    class Percent(val value: Int) : XXXValueWithUnit()

    class TherapyEventType(val value: TherapyEvent.Type) : XXXValueWithUnit()

    class TherapyEventMeterType(val value: TherapyEvent.MeterType) : XXXValueWithUnit()

    class TherapyEventTTReason(val value: TemporaryTarget.Reason) : XXXValueWithUnit()

    class StringResource(@StringRes val value: Int, val params: List<XXXValueWithUnit> = listOf()) : XXXValueWithUnit()

    companion object {

        fun fromGlucoseUnit(value: Double, string: String): XXXValueWithUnit? = when (string) {
            "mg/dl", "mgdl" -> Mgdl(value)
            "mmol", "mmol/l" -> Mmoll(value)
            else             -> null
        }
    }
}



/***
 * Idea: Leverage sealed classes for units
 * Advantage: it is clear what type of data a Unit contains. Still we are exhaustive on when
 *
 * The condition "condition" that is used to check if an item should be logged can be replaced by .takeIf { condition }.
 * The value then would not have to be handled but the logging could simply discard null value.
 *
 * [x] new sealed classes
 * [x] use entry type directly, not String
 * [ ] database
 * [x] generate presentation string
 * [ ] update fragment
 * [ ] generate csv
 *
 */

// just do develop in this file. Remove when done.
interface Translator {

    fun translate(units: UserEntry.Units): String
    fun translate(meterType: TherapyEvent.MeterType): String
    fun translate(type: TherapyEvent.Type): String
    fun translate(reason: TemporaryTarget.Reason): String
}

enum class Sources(val text: String) {
    @SerializedName("TreatmentDialog") TreatmentDialog ("TreatmentDialog"),
    @SerializedName("InsulinDialog") InsulinDialog ("InsulinDialog"),
    @SerializedName("CarbDialog") CarbDialog ("CarbDialog"),
    @SerializedName("WizardDialog") WizardDialog ("WizardDialog"),
    @SerializedName("QuickWizard") QuickWizard ("QuickWizard"),
    @SerializedName("ExtendedBolusDialog") ExtendedBolusDialog ("ExtendedBolusDialog"),
    @SerializedName("TTDialog") TTDialog ("TTDialog"),
    @SerializedName("ProfileSwitchDialog") ProfileSwitchDialog ("ProfileSwitchDialog"),
    @SerializedName("LoopDialog") LoopDialog ("LoopDialog"),
    @SerializedName("TempBasalDialog") TempBasalDialog ("TempBasalDialog"),
    @SerializedName("CalibrationDialog") CalibrationDialog ("CalibrationDialog"),
    @SerializedName("FillDialog") FillDialog ("FillDialog"),
    @SerializedName("BgCheck") BgCheck ("BgCheck"),
    @SerializedName("SensorInsert") SensorInsert ("SensorInsert"),
    @SerializedName("BatteryChange") BatteryChange ("BatteryChange"),
    @SerializedName("Note") Note ("Note"),
    @SerializedName("Exercise") Exercise ("Exercise"),
    @SerializedName("Question") Question ("Question"),
    @SerializedName("Announcement") Announcement ("Announcement"),
    @SerializedName("Actions") Actions ("Actions"),             //From Actions plugin
    @SerializedName("Automation") Automation ("Automation"),    //From Automation plugin
    @SerializedName("LocalProfile") LocalProfile ("LocalProfile"),  //From LocalProfile plugin
    @SerializedName("Loop") Loop ("Loop"),                      //From Loop plugin
    @SerializedName("Maintenance") Maintenance ("Maintenance"), //From Maintenance plugin
    @SerializedName("NSClient") NSClient ("NSClient"),          //From NSClient plugin
    @SerializedName("Pump") Pump ("Pump"),                      //From Pump plugin (for example from pump history)
    @SerializedName("SMS") SMS ("SMS"),                         //From SMS plugin
    @SerializedName("Treatments") Treatments ("Treatments"),    //From Treatments plugin
    @SerializedName("Wear") Wear ("Wear"),                      //From Wear plugin
    @SerializedName("Food") Food ("Food"),                      //From Food plugin
    @SerializedName("Unknown") Unknown ("Unknown")              //if necessary
    ;

    companion object {
        fun fromString(source: String?) = values().firstOrNull { it.name == source } ?: Unknown
        fun fromText(source: String?) = values().firstOrNull { it.text == source } ?: Unknown
    }
}