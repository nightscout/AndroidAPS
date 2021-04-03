package info.nightscout.androidaps.utils

import android.text.Spanned
import dagger.Reusable
import info.nightscout.androidaps.Constants
import info.nightscout.androidaps.core.R
import info.nightscout.androidaps.database.entities.UserEntry
import info.nightscout.androidaps.database.entities.UserEntry.Action
import info.nightscout.androidaps.database.entities.UserEntry.Sources
import info.nightscout.androidaps.database.entities.UserEntry.ColorGroup
import info.nightscout.androidaps.database.entities.XXXValueWithUnit
import info.nightscout.androidaps.interfaces.ProfileFunction
import info.nightscout.androidaps.utils.resources.ResourceHelper
import javax.inject.Inject

@Reusable
class UserEntryPresentationHelper @Inject constructor(
    private val translator: Translator,
    private val profileFunction: ProfileFunction,
    private val resourceHelper: ResourceHelper,
    private val dateUtil: DateUtil
) {

    fun colorId(colorGroup: ColorGroup): Int = when (colorGroup) {
        ColorGroup.InsulinTreatment -> R.color.basal
        ColorGroup.CarbTreatment    -> R.color.carbs
        ColorGroup.TT               -> R.color.tempTargetConfirmation
        ColorGroup.Profile          -> R.color.white
        ColorGroup.Loop             -> R.color.loopClosed
        ColorGroup.Careportal       -> R.color.high
        ColorGroup.Pump             -> R.color.iob
        ColorGroup.Aaps             -> R.color.defaulttext
        else                        -> R.color.defaulttext
    }

    fun iconId(source: Sources): Int = when (source) {
        Sources.TreatmentDialog     -> R.drawable.icon_insulin_carbs
        Sources.InsulinDialog       -> R.drawable.ic_bolus
        Sources.CarbDialog          -> R.drawable.ic_cp_bolus_carbs
        Sources.WizardDialog        -> R.drawable.ic_calculator
        Sources.QuickWizard         -> R.drawable.ic_quick_wizard
        Sources.ExtendedBolusDialog -> R.drawable.ic_actions_startextbolus
        Sources.TTDialog            -> R.drawable.ic_temptarget_high
        Sources.ProfileSwitchDialog -> R.drawable.ic_actions_profileswitch
        Sources.LoopDialog          -> R.drawable.ic_loop_closed
        Sources.TempBasalDialog     -> R.drawable.ic_actions_starttempbasal
        Sources.CalibrationDialog   -> R.drawable.ic_calibration
        Sources.FillDialog          -> R.drawable.ic_cp_pump_canula
        Sources.BgCheck             -> R.drawable.ic_cp_bgcheck
        Sources.SensorInsert        -> R.drawable.ic_cp_cgm_insert
        Sources.BatteryChange       -> R.drawable.ic_cp_pump_battery
        Sources.Note                -> R.drawable.ic_cp_note
        Sources.Exercise            -> R.drawable.ic_cp_exercise
        Sources.Question            -> R.drawable.ic_cp_question
        Sources.Announcement        -> R.drawable.ic_cp_announcement
        Sources.Actions             -> R.drawable.ic_action
        Sources.Automation          -> R.drawable.ic_automation
        Sources.BG                  -> R.drawable.ic_generic_cgm
        Sources.LocalProfile        -> R.drawable.ic_local_profile
        Sources.Loop                -> R.drawable.ic_loop_closed_white
        Sources.Maintenance         -> R.drawable.ic_maintenance
        Sources.NSClient            -> R.drawable.ic_nightscout_syncs
        Sources.NSProfile           -> R.drawable.ic_nightscout_profile
        Sources.Objectives          -> R.drawable.ic_graduation
        Sources.Pump                -> R.drawable.ic_generic_icon
        Sources.SMS                 -> R.drawable.ic_sms
        Sources.Treatments          -> R.drawable.ic_treatments
        Sources.Wear                -> R.drawable.ic_watch
        Sources.Food                -> R.drawable.ic_food
        Sources.Stats               -> R.drawable.ic_cp_stats
        Sources.ConfigBuilder       -> R.drawable.ic_generic_icon
        Sources.Overview            -> R.drawable.ic_generic_icon
        Sources.Unknown             -> R.drawable.ic_generic_icon
    }

    fun actionToColoredString(action: Action): Spanned = when (action) {
            Action.TREATMENT -> HtmlHelper.fromHtml(coloredAction(Action.BOLUS) + " + " + coloredAction(Action.CARBS))
            else             -> HtmlHelper.fromHtml(coloredAction(action))
    }

    private fun coloredAction(action: Action): String = "<font color='${resourceHelper.gc(colorId(action.colorGroup))}'>${translator.translate(action)}</font>"

    fun listToPresentationString(list: List<XXXValueWithUnit?>) =
        list.joinToString(separator = "  ", transform = this::toPresentationString)

    private fun toPresentationString(valueWithUnit: XXXValueWithUnit?): String = when (valueWithUnit) {
        is XXXValueWithUnit.Gram                  -> "${valueWithUnit.value}${translator.translate(valueWithUnit)}"
        is XXXValueWithUnit.Hour                  -> "${valueWithUnit.value}${translator.translate(valueWithUnit)}"
        is XXXValueWithUnit.Minute                -> "${valueWithUnit.value}${translator.translate(valueWithUnit)}"
        is XXXValueWithUnit.Percent               -> "${valueWithUnit.value}${translator.translate(valueWithUnit)}"
        is XXXValueWithUnit.Insulin               -> DecimalFormatter.to2Decimal(valueWithUnit.value) + translator.translate(valueWithUnit)
        is XXXValueWithUnit.UnitPerHour           -> DecimalFormatter.to2Decimal(valueWithUnit.value) + translator.translate(valueWithUnit)
        is XXXValueWithUnit.SimpleInt             -> valueWithUnit.value.toString()
        is XXXValueWithUnit.SimpleString          -> valueWithUnit.value
//        is XXXValueWithUnit.StringResource        -> resourceHelper.gs(valueWithUnit.value, valueWithUnit.params.map{ it.value() }.toTypedArray())      //Todo Fix StringResource with Param
        is XXXValueWithUnit.StringResource        -> if (valueWithUnit.params.size > 0 ) valueWithUnit.params.joinToString(separator = "  ") { value -> toPresentationString(value) }  else resourceHelper.gs(valueWithUnit.value)    //To help debugging
        is XXXValueWithUnit.TherapyEventMeterType -> translator.translate(valueWithUnit.value)
        is XXXValueWithUnit.TherapyEventTTReason  -> translator.translate(valueWithUnit.value)
        is XXXValueWithUnit.TherapyEventType      -> translator.translate(valueWithUnit.value)
        is XXXValueWithUnit.Timestamp             -> dateUtil.dateAndTimeAndSecondsString(valueWithUnit.value)

        is XXXValueWithUnit.Mgdl                  -> {
            if (profileFunction.getUnits() == Constants.MGDL) DecimalFormatter.to0Decimal(valueWithUnit.value) + translator.translate(valueWithUnit)
            else DecimalFormatter.to1Decimal(valueWithUnit.value / Constants.MMOLL_TO_MGDL) + translator.translate(valueWithUnit)
        }

        is XXXValueWithUnit.Mmoll                 -> {
            if (profileFunction.getUnits() == Constants.MGDL) DecimalFormatter.to0Decimal(valueWithUnit.value) + translator.translate(valueWithUnit)
            else DecimalFormatter.to1Decimal(valueWithUnit.value * Constants.MMOLL_TO_MGDL) + translator.translate(valueWithUnit)
        }

        XXXValueWithUnit.UNKNOWN                  -> ""
        null                                      -> ""
    }

    fun UserEntriesToCsv(userEntries: List<UserEntry>): String {
        return getCsvHeader() + userEntries.joinToString("\n") { entry -> getCsvEntry(entry) }
    }

    private fun getCsvHeader() = resourceHelper.gs(R.string.ue_csv_header,
        csvString(R.string.ue_timestamp),
        csvString(R.string.date),
        csvString(R.string.ue_utc_offset),
        csvString(R.string.ue_action),
        csvString(R.string.eventtype),
        csvString(R.string.ue_source),
        csvString(R.string.careportal_note),
        csvString(R.string.ue_formated_string),
        csvString(R.string.event_time_label),
        csvString(if (profileFunction.getUnits() == Constants.MGDL) R.string.mgdl else R.string.mmol ),
        csvString(R.string.shortgram),
        csvString(R.string.insulin_unit_shortname),
        csvString(R.string.profile_ins_units_per_hour),
        csvString(R.string.shortpercent),
        csvString(R.string.shorthour),
        csvString(R.string.shortminute),
        csvString(R.string.ue_none)
    ) + "\n"

    private fun getCsvEntry(entry: UserEntry): String {
        val fullvalueWithUnitList = ArrayList<XXXValueWithUnit?>(entry.values)
        var timestampRec = "" + entry.timestamp
        var dateTimestampRev = dateUtil.dateAndTimeAndSecondsString(entry.timestamp)
        var utcOffset = dateUtil.timeString(entry.utcOffset)
        var action = csvString(entry.action)
        var therapyEvent = ""
        var source = translator.translate(entry.source)
        var note = csvString(entry.note)
        var stringResource = ""
        var timestamp = ""
        var bg = ""
        var gram = ""
        var insulin = ""
        var unitPerHour = ""
        var percent = ""
        var hour = ""
        var minute = ""
        var other = ""
        for (valueWithUnit in entry.values) {
            if (valueWithUnit is XXXValueWithUnit.StringResource) fullvalueWithUnitList.addAll(valueWithUnit.params)
        }

        for (valueWithUnit in fullvalueWithUnitList.filter { it != null }) {
            when (valueWithUnit) {
                is XXXValueWithUnit.Gram                  -> gram = valueWithUnit.value.toString()
                is XXXValueWithUnit.Hour                  -> hour = valueWithUnit.value.toString()
                is XXXValueWithUnit.Minute                -> minute = valueWithUnit.value.toString()
                is XXXValueWithUnit.Percent               -> percent = valueWithUnit.value.toString()
                is XXXValueWithUnit.Insulin               -> insulin = DecimalFormatter.to2Decimal(valueWithUnit.value)
                is XXXValueWithUnit.UnitPerHour           -> unitPerHour = DecimalFormatter.to2Decimal(valueWithUnit.value)
                is XXXValueWithUnit.SimpleInt             -> other = if (other == "") valueWithUnit.value.toString() else other + " / " + valueWithUnit.value.toString()
                is XXXValueWithUnit.SimpleString          -> other = if (other == "") valueWithUnit.value else other + " / " + valueWithUnit.value
//                is XXXValueWithUnit.StringResource        -> stringResource = if (stringResource == "") resourceHelper.gs(valueWithUnit.value, valueWithUnit.params.map { it.value() }.toTypedArray()) else stringResource + " / " + resourceHelper.gs(valueWithUnit.value, valueWithUnit.params.map { it.value() }.toTypedArray())
                is XXXValueWithUnit.StringResource        -> if (valueWithUnit.params.size == 0) { stringResource = if (stringResource == "") resourceHelper.gs(valueWithUnit.value) else stringResource + " / " + resourceHelper.gs(valueWithUnit.value)}
                is XXXValueWithUnit.TherapyEventMeterType -> therapyEvent = if (therapyEvent == "") translator.translate(valueWithUnit.value) else therapyEvent + " / " + translator.translate(valueWithUnit.value)
                is XXXValueWithUnit.TherapyEventTTReason  -> therapyEvent = if (therapyEvent == "") translator.translate(valueWithUnit.value) else therapyEvent + " / " + translator.translate(valueWithUnit.value)
                is XXXValueWithUnit.TherapyEventType      -> therapyEvent = if (therapyEvent == "") translator.translate(valueWithUnit.value) else therapyEvent + " / " + translator.translate(valueWithUnit.value)
                is XXXValueWithUnit.Timestamp             -> timestamp = dateUtil.dateAndTimeAndSecondsString(valueWithUnit.value)

                is XXXValueWithUnit.Mgdl                  -> {
                    bg = if (profileFunction.getUnits() == Constants.MGDL) DecimalFormatter.to0Decimal(valueWithUnit.value)
                        else DecimalFormatter.to1Decimal(valueWithUnit.value / Constants.MMOLL_TO_MGDL)
                }
                is XXXValueWithUnit.Mmoll                 -> {
                    bg = if (profileFunction.getUnits() == Constants.MGDL) DecimalFormatter.to0Decimal(valueWithUnit.value)
                        else DecimalFormatter.to1Decimal(valueWithUnit.value * Constants.MMOLL_TO_MGDL)
                }

            }
        }

        therapyEvent = csvString(therapyEvent)
        stringResource = csvString(stringResource)
        other = csvString(other)
        return timestampRec + ";" + dateTimestampRev + ";" + utcOffset + ";" + action + ";" + therapyEvent + ";" + source + ";" + note + ";" + stringResource + ";" + timestamp + ";" + bg + ";" + gram + ";" + insulin + ";" + unitPerHour + ";" + percent + ";" + hour + ";" + minute + ";" + other
    }

    private fun saveString(id: Int): String = if (id != 0) resourceHelper.gs(id) else ""
    private fun csvString(action: Action): String = "\"" + translator.translate(action).replace("\"", "\"\"") + "\""
    private fun csvString(id: Int): String = if (id != 0) "\"" + resourceHelper.gs(id).replace("\"", "\"\"") + "\"" else ""
    private fun csvString(s: String): String = if (s != "") "\"" + s.replace("\"", "\"\"") + "\"" else ""
}