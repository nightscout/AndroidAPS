package app.aaps.implementation.userEntry

import android.text.Spanned
import app.aaps.core.data.configuration.Constants
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.UE
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.data.ue.ValueWithUnit
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.userEntry.UserEntryPresentationHelper
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.interfaces.utils.Translator
import app.aaps.core.objects.R
import app.aaps.core.utils.HtmlHelper
import dagger.Reusable
import javax.inject.Inject

@Reusable
class UserEntryPresentationHelperImpl @Inject constructor(
    private val translator: Translator,
    private val profileUtil: ProfileUtil,
    private val rh: ResourceHelper,
    private val dateUtil: DateUtil,
    private val decimalFormatter: DecimalFormatter
) : UserEntryPresentationHelper {

    override fun colorId(colorGroup: Action.ColorGroup): Int = when (colorGroup) {
        Action.ColorGroup.InsulinTreatment -> app.aaps.core.ui.R.color.iob
        Action.ColorGroup.BasalTreatment   -> app.aaps.core.ui.R.color.basal
        Action.ColorGroup.CarbTreatment    -> app.aaps.core.ui.R.color.carbs
        Action.ColorGroup.TT               -> app.aaps.core.ui.R.color.tempTargetConfirmation
        Action.ColorGroup.Profile          -> app.aaps.core.ui.R.color.white
        Action.ColorGroup.Loop             -> app.aaps.core.ui.R.color.loopClosed
        Action.ColorGroup.Careportal       -> app.aaps.core.ui.R.color.high
        Action.ColorGroup.Pump             -> app.aaps.core.ui.R.color.loopDisconnected
        Action.ColorGroup.Aaps             -> app.aaps.core.ui.R.color.defaultText
        Action.ColorGroup.RunningMode      -> app.aaps.core.ui.R.color.white
        // else                               -> app.aaps.core.ui.R.color.defaultText
    }

    override fun iconId(source: Sources): Int = when (source) {
        Sources.TreatmentDialog     -> R.drawable.icon_insulin_carbs
        Sources.InsulinDialog       -> R.drawable.ic_bolus
        Sources.CarbDialog          -> R.drawable.ic_cp_bolus_carbs
        Sources.WizardDialog        -> R.drawable.ic_calculator
        Sources.QuickWizard         -> R.drawable.ic_quick_wizard
        Sources.ExtendedBolusDialog -> R.drawable.ic_actions_start_extended_bolus
        Sources.TTDialog            -> R.drawable.ic_temptarget_high
        Sources.ProfileSwitchDialog -> app.aaps.core.ui.R.drawable.ic_actions_profileswitch
        Sources.LoopDialog          -> R.drawable.ic_loop_closed
        Sources.TempBasalDialog     -> R.drawable.ic_actions_start_temp_basal
        Sources.CalibrationDialog   -> R.drawable.ic_calibration
        Sources.FillDialog          -> R.drawable.ic_cp_pump_cannula
        Sources.SiteRotationDialog  -> app.aaps.core.ui.R.drawable.ic_site_rotation
        Sources.BgCheck             -> R.drawable.ic_cp_bgcheck
        Sources.SensorInsert        -> R.drawable.ic_cp_cgm_insert
        Sources.BatteryChange       -> R.drawable.ic_cp_pump_battery
        Sources.Note                -> R.drawable.ic_cp_note
        Sources.Exercise            -> R.drawable.ic_cp_exercise
        Sources.Question            -> R.drawable.ic_cp_question
        Sources.Announcement        -> R.drawable.ic_cp_announcement
        Sources.SettingsExport      -> R.drawable.ic_automation
        Sources.Actions             -> R.drawable.ic_action
        Sources.Automation          -> R.drawable.ic_automation
        Sources.Autotune            -> R.drawable.ic_autotune
        Sources.BG                  -> R.drawable.ic_generic_cgm
        Sources.Aidex               -> R.drawable.ic_blooddrop_48
        Sources.Dexcom              -> R.drawable.ic_dexcom_g6
        Sources.Eversense           -> R.drawable.ic_eversense
        Sources.Glimp               -> R.drawable.ic_glimp
        Sources.MM640g              -> R.drawable.ic_generic_cgm
        Sources.NSClientSource      -> R.drawable.ic_nsclient_bg
        Sources.PocTech             -> R.drawable.ic_poctech
        Sources.Tomato              -> R.drawable.ic_sensor
        Sources.Glunovo             -> R.drawable.ic_glunovo
        Sources.Intelligo           -> app.aaps.core.ui.R.drawable.ic_intelligo
        Sources.Xdrip               -> R.drawable.ic_blooddrop_48
        Sources.Ottai    -> R.drawable.ic_syai_tag
        Sources.SyaiTag  -> R.drawable.ic_syai_tag
        Sources.SiBionic -> R.drawable.ic_generic_cgm
        Sources.Sino     -> R.drawable.ic_generic_cgm
        Sources.LocalProfile        -> R.drawable.ic_local_profile
        Sources.Loop                -> R.drawable.ic_loop_closed_white
        Sources.Maintenance         -> app.aaps.core.ui.R.drawable.ic_maintenance
        Sources.NSClient            -> app.aaps.core.ui.R.drawable.ic_nightscout_syncs
        Sources.NSProfile           -> R.drawable.ic_nightscout_profile
        Sources.Objectives          -> app.aaps.core.ui.R.drawable.ic_graduation
        Sources.Pump                -> app.aaps.core.ui.R.drawable.ic_generic_icon
        Sources.Dana                -> app.aaps.core.ui.R.drawable.ic_danars_128
        Sources.DanaR               -> app.aaps.core.ui.R.drawable.ic_danars_128
        Sources.DanaRC              -> app.aaps.core.ui.R.drawable.ic_danars_128
        Sources.DanaRv2             -> app.aaps.core.ui.R.drawable.ic_danars_128
        Sources.DanaRS              -> app.aaps.core.ui.R.drawable.ic_danars_128
        Sources.DanaI               -> app.aaps.core.ui.R.drawable.ic_danai_128
        Sources.DiaconnG8           -> app.aaps.core.ui.R.drawable.ic_diaconn_g8
        Sources.Insight             -> app.aaps.core.ui.R.drawable.ic_insight_128
        Sources.Combo               -> app.aaps.core.ui.R.drawable.ic_combo_128
        Sources.Medtronic           -> app.aaps.core.ui.R.drawable.ic_veo_128
        Sources.Omnipod             -> R.drawable.ic_patch_pump_outline
        Sources.OmnipodEros         -> R.drawable.ic_patch_pump_outline
        Sources.OmnipodDash         -> R.drawable.ic_patch_pump_outline
        Sources.EOPatch2            -> app.aaps.core.ui.R.drawable.ic_eopatch2_128
        Sources.Equil               -> app.aaps.core.ui.R.drawable.ic_equil_128
        Sources.Medtrum             -> app.aaps.core.ui.R.drawable.ic_medtrum_128
        Sources.MDI                 -> R.drawable.ic_ict
        Sources.VirtualPump         -> R.drawable.ic_virtual_pump
        Sources.SMS                 -> R.drawable.ic_sms
        Sources.Treatments          -> R.drawable.ic_treatments
        Sources.Wear                -> R.drawable.ic_watch
        Sources.Food                -> R.drawable.ic_food
        Sources.Stats               -> R.drawable.ic_cp_stats
        Sources.ConfigBuilder       -> app.aaps.core.ui.R.drawable.ic_cogs
        Sources.Overview            -> app.aaps.core.ui.R.drawable.ic_home
        Sources.Aaps                -> R.drawable.ic_aaps
        Sources.Garmin              -> app.aaps.core.ui.R.drawable.ic_generic_icon
        Sources.Database            -> app.aaps.core.ui.R.drawable.ic_database_cleanup
        Sources.Unknown             -> app.aaps.core.ui.R.drawable.ic_generic_icon
        Sources.Random              -> R.drawable.ic_aaps
        Sources.BgFragment          -> R.drawable.ic_aaps
    }

    override fun actionToColoredString(action: Action): Spanned = when (action) {
        Action.TREATMENT -> HtmlHelper.fromHtml(coloredAction(Action.BOLUS) + " + " + coloredAction(Action.CARBS))
        else             -> HtmlHelper.fromHtml(coloredAction(action))
    }

    private fun coloredAction(action: Action): String = "<font color='${rh.gc(colorId(action.colorGroup))}'>${translator.translate(action)}</font>"

    override fun listToPresentationString(list: List<ValueWithUnit>) =
        list.joinToString(separator = "  ", transform = this::toPresentationString)

    private fun toPresentationString(valueWithUnit: ValueWithUnit?): String = when (valueWithUnit) {
        is ValueWithUnit.Gram         -> "${valueWithUnit.value}${translator.translate(valueWithUnit)}"
        is ValueWithUnit.Hour         -> "${valueWithUnit.value}${translator.translate(valueWithUnit)}"
        is ValueWithUnit.Minute       -> "${valueWithUnit.value}${translator.translate(valueWithUnit)}"
        is ValueWithUnit.Percent      -> "${valueWithUnit.value}${translator.translate(valueWithUnit)}"
        is ValueWithUnit.Insulin      -> decimalFormatter.to2Decimal(valueWithUnit.value) + translator.translate(valueWithUnit)
        is ValueWithUnit.UnitPerHour  -> decimalFormatter.to2Decimal(valueWithUnit.value) + translator.translate(valueWithUnit)
        is ValueWithUnit.SimpleInt    -> valueWithUnit.value.toString()
        is ValueWithUnit.SimpleString -> valueWithUnit.value
        is ValueWithUnit.TEMeterType  -> translator.translate(valueWithUnit.value)
        is ValueWithUnit.TETTReason   -> translator.translate(valueWithUnit.value)
        is ValueWithUnit.RMMode       -> translator.translate(valueWithUnit.value)
        is ValueWithUnit.TEType       -> translator.translate(valueWithUnit.value)
        is ValueWithUnit.TELocation   -> translator.translate(valueWithUnit.value)
        is ValueWithUnit.TEArrow      -> translator.translate(valueWithUnit.value)
        is ValueWithUnit.Timestamp    -> dateUtil.dateAndTimeAndSecondsString(valueWithUnit.value)

        is ValueWithUnit.Mgdl         -> {
            if (profileUtil.units == GlucoseUnit.MGDL) decimalFormatter.to0Decimal(valueWithUnit.value) + rh.gs(app.aaps.core.ui.R.string.mgdl)
            else decimalFormatter.to1Decimal(valueWithUnit.value * Constants.MGDL_TO_MMOLL) + rh.gs(app.aaps.core.ui.R.string.mmol)
        }

        is ValueWithUnit.Mmoll        -> {
            if (profileUtil.units == GlucoseUnit.MMOL) decimalFormatter.to1Decimal(valueWithUnit.value) + rh.gs(app.aaps.core.ui.R.string.mmol)
            else decimalFormatter.to0Decimal(valueWithUnit.value * Constants.MMOLL_TO_MGDL) + rh.gs(app.aaps.core.ui.R.string.mgdl)
        }

        ValueWithUnit.UNKNOWN         -> ""
        null                          -> ""
    }

    override fun userEntriesToCsv(userEntries: List<UE>): String {
        return getCsvHeader() + userEntries.joinToString("\n") { entry -> getCsvEntry(entry) }
    }

    private fun getCsvHeader() = rh.gs(
        app.aaps.core.ui.R.string.ue_csv_header,
        csvString(app.aaps.core.ui.R.string.ue_timestamp),
        csvString(app.aaps.core.ui.R.string.date),
        csvString(app.aaps.core.ui.R.string.ue_utc_offset),
        csvString(app.aaps.core.ui.R.string.ue_action),
        csvString(app.aaps.core.ui.R.string.event_type),
        csvString(app.aaps.core.ui.R.string.ue_source),
        csvString(app.aaps.core.ui.R.string.careportal_note),
        csvString(app.aaps.core.ui.R.string.ue_string),
        csvString(app.aaps.core.ui.R.string.event_time_label),
        csvString(if (profileUtil.units == GlucoseUnit.MGDL) app.aaps.core.ui.R.string.mgdl else app.aaps.core.ui.R.string.mmol),
        csvString(app.aaps.core.ui.R.string.shortgram),
        csvString(app.aaps.core.ui.R.string.insulin_unit_shortname),
        csvString(app.aaps.core.ui.R.string.profile_ins_units_per_hour),
        csvString(app.aaps.core.ui.R.string.shortpercent),
        csvString(app.aaps.core.interfaces.R.string.shorthour),
        csvString(app.aaps.core.interfaces.R.string.shortminute),
        csvString(app.aaps.core.ui.R.string.ue_none)
    ) + "\n"

    private fun getCsvEntry(entry: UE): String {
        val fullValueWithUnitList = ArrayList(entry.values)
        val timestampRec = entry.timestamp.toString()
        val dateTimestampRev = dateUtil.dateAndTimeAndSecondsString(entry.timestamp)
        val utcOffset = dateUtil.timeStringFromSeconds((entry.utcOffset / 1000).toInt())
        val action = csvString(entry.action)
        var therapyEvent = ""
        val source = translator.translate(entry.source)
        val note = csvString(entry.note)
        var simpleString = ""
        var timestamp = ""
        var bg = ""
        var gram = ""
        var insulin = ""
        var unitPerHour = ""
        var percent = ""
        var hour = ""
        var minute = ""
        var noUnit = ""

        for (valueWithUnit in fullValueWithUnitList.filterNotNull()) {
            when (valueWithUnit) {
                is ValueWithUnit.Gram         -> gram = valueWithUnit.value.toString()
                is ValueWithUnit.Hour         -> hour = valueWithUnit.value.toString()
                is ValueWithUnit.Minute       -> minute = valueWithUnit.value.toString()
                is ValueWithUnit.Percent      -> percent = valueWithUnit.value.toString()
                is ValueWithUnit.Insulin      -> insulin = decimalFormatter.to2Decimal(valueWithUnit.value)
                is ValueWithUnit.UnitPerHour  -> unitPerHour = decimalFormatter.to2Decimal(valueWithUnit.value)
                is ValueWithUnit.SimpleInt    -> noUnit = noUnit.addWithSeparator(valueWithUnit.value)
                is ValueWithUnit.SimpleString -> simpleString = simpleString.addWithSeparator(valueWithUnit.value)
                is ValueWithUnit.TEMeterType  -> therapyEvent = therapyEvent.addWithSeparator(translator.translate(valueWithUnit.value))
                is ValueWithUnit.TETTReason   -> therapyEvent = therapyEvent.addWithSeparator(translator.translate(valueWithUnit.value))
                is ValueWithUnit.RMMode       -> therapyEvent = therapyEvent.addWithSeparator(translator.translate(valueWithUnit.value))
                is ValueWithUnit.TEType       -> therapyEvent = therapyEvent.addWithSeparator(translator.translate(valueWithUnit.value))
                is ValueWithUnit.TELocation   -> therapyEvent = therapyEvent.addWithSeparator(translator.translate(valueWithUnit.value))
                is ValueWithUnit.TEArrow      -> therapyEvent = therapyEvent.addWithSeparator(translator.translate(valueWithUnit.value))
                is ValueWithUnit.Timestamp    -> timestamp = dateUtil.dateAndTimeAndSecondsString(valueWithUnit.value)

                is ValueWithUnit.Mgdl         ->
                    bg = profileUtil.fromMgdlToStringInUnits(valueWithUnit.value)

                is ValueWithUnit.Mmoll        ->
                    bg = profileUtil.fromMgdlToStringInUnits(valueWithUnit.value * Constants.MMOLL_TO_MGDL)

                ValueWithUnit.UNKNOWN         -> Unit
            }
        }

        therapyEvent = csvString(therapyEvent)
        simpleString = csvString(simpleString)
        noUnit = csvString(noUnit)
        return "$timestampRec;$dateTimestampRev;$utcOffset;$action;$therapyEvent;$source;$note;$simpleString;$timestamp;$bg;$gram;$insulin;$unitPerHour;$percent;$hour;$minute;$noUnit"
    }

    private fun csvString(action: Action): String = "\"" + translator.translate(action).replace("\"", "\"\"").replace("\n", " / ") + "\""
    private fun csvString(id: Int): String = if (id != 0) "\"" + rh.gs(id).replace("\"", "\"\"").replace("\n", " / ") + "\"" else ""
    private fun csvString(s: String): String = if (s != "") "\"" + s.replace("\"", "\"\"").replace("\n", " / ") + "\"" else ""

    private fun String.addWithSeparator(add: Any) =
        this + (if (this.isBlank()) "" else " / ") + add.toString()
}
