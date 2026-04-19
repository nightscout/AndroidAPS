package app.aaps.implementation.userEntry

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Watch
import androidx.compose.ui.graphics.vector.ImageVector
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
import app.aaps.core.ui.compose.icons.IcAaps
import app.aaps.core.ui.compose.icons.IcAction
import app.aaps.core.ui.compose.icons.IcActivity
import app.aaps.core.ui.compose.icons.IcAnnouncement
import app.aaps.core.ui.compose.icons.IcAutomation
import app.aaps.core.ui.compose.icons.IcBgCheck
import app.aaps.core.ui.compose.icons.IcBolus
import app.aaps.core.ui.compose.icons.IcByoda
import app.aaps.core.ui.compose.icons.IcCalculator
import app.aaps.core.ui.compose.icons.IcCalibration
import app.aaps.core.ui.compose.icons.IcCannulaChange
import app.aaps.core.ui.compose.icons.IcCarbs
import app.aaps.core.ui.compose.icons.IcCgmInsert
import app.aaps.core.ui.compose.icons.IcClinicalNotes
import app.aaps.core.ui.compose.icons.IcExtendedBolus
import app.aaps.core.ui.compose.icons.IcGenericCgm
import app.aaps.core.ui.compose.icons.IcGenericIcon
import app.aaps.core.ui.compose.icons.IcLoopClosed
import app.aaps.core.ui.compose.icons.IcMdi
import app.aaps.core.ui.compose.icons.IcNote
import app.aaps.core.ui.compose.icons.IcPatchPump
import app.aaps.core.ui.compose.icons.IcPluginAutotune
import app.aaps.core.ui.compose.icons.IcPluginCombo
import app.aaps.core.ui.compose.icons.IcPluginConfigBuilder
import app.aaps.core.ui.compose.icons.IcPluginDanaI
import app.aaps.core.ui.compose.icons.IcPluginDiaconn
import app.aaps.core.ui.compose.icons.IcPluginEopatch
import app.aaps.core.ui.compose.icons.IcPluginEquil
import app.aaps.core.ui.compose.icons.IcPluginEversense
import app.aaps.core.ui.compose.icons.IcPluginFood
import app.aaps.core.ui.compose.icons.IcPluginGarmin
import app.aaps.core.ui.compose.icons.IcPluginGlimp
import app.aaps.core.ui.compose.icons.IcPluginGlunovo
import app.aaps.core.ui.compose.icons.IcPluginInsight
import app.aaps.core.ui.compose.icons.IcPluginInsulin
import app.aaps.core.ui.compose.icons.IcPluginIntelligo
import app.aaps.core.ui.compose.icons.IcPluginMM640G
import app.aaps.core.ui.compose.icons.IcPluginMaintenance
import app.aaps.core.ui.compose.icons.IcPluginMedtronic
import app.aaps.core.ui.compose.icons.IcPluginMedtrum
import app.aaps.core.ui.compose.icons.IcPluginNsClient
import app.aaps.core.ui.compose.icons.IcPluginNsClientBg
import app.aaps.core.ui.compose.icons.IcPluginObjectives
import app.aaps.core.ui.compose.icons.IcPluginPocTec
import app.aaps.core.ui.compose.icons.IcPluginRandomBg
import app.aaps.core.ui.compose.icons.IcPluginSms
import app.aaps.core.ui.compose.icons.IcPluginSyai
import app.aaps.core.ui.compose.icons.IcPluginTomato
import app.aaps.core.ui.compose.icons.IcPluginVirtualPump
import app.aaps.core.ui.compose.icons.IcProfile
import app.aaps.core.ui.compose.icons.IcPumpBattery
import app.aaps.core.ui.compose.icons.IcQuestion
import app.aaps.core.ui.compose.icons.IcQuickwizard
import app.aaps.core.ui.compose.icons.IcSiteRotation
import app.aaps.core.ui.compose.icons.IcStats
import app.aaps.core.ui.compose.icons.IcTbrHigh
import app.aaps.core.ui.compose.icons.IcTtHigh
import app.aaps.core.ui.compose.icons.IcXDrip
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

    override fun icon(source: Sources): ImageVector = when (source) {
        Sources.Aaps                -> IcAaps
        Sources.Actions             -> IcAction
        Sources.Aidex               -> IcXDrip
        Sources.Announcement        -> IcAnnouncement
        Sources.Automation          -> IcAutomation
        Sources.Autotune            -> IcPluginAutotune
        Sources.BG                  -> IcGenericCgm
        Sources.BatteryChange       -> IcPumpBattery
        Sources.BgCheck             -> IcBgCheck
        Sources.BgFragment          -> IcAaps
        Sources.CalibrationDialog   -> IcCalibration
        Sources.CarbDialog          -> IcCarbs
        Sources.Combo               -> IcPluginCombo
        Sources.ConcentrationDialog -> IcPluginInsulin
        Sources.ConfigBuilder       -> IcPluginConfigBuilder
        Sources.Dana                -> IcPluginDanaI
        Sources.DanaI               -> IcPluginDanaI
        Sources.DanaR               -> IcPluginDanaI
        Sources.DanaRC              -> IcPluginDanaI
        Sources.DanaRS              -> IcPluginDanaI
        Sources.DanaRv2             -> IcPluginDanaI
        Sources.Database            -> Icons.Default.Delete
        Sources.Dexcom              -> IcByoda
        Sources.DiaconnG8           -> IcPluginDiaconn
        Sources.EOPatch2            -> IcPluginEopatch
        Sources.Equil               -> IcPluginEquil
        Sources.Eversense           -> IcPluginEversense
        Sources.Exercise            -> IcActivity
        Sources.ExtendedBolusDialog -> IcExtendedBolus
        Sources.FillDialog          -> IcCannulaChange
        Sources.Food                -> IcPluginFood
        Sources.Garmin              -> IcPluginGarmin
        Sources.Glimp               -> IcPluginGlimp
        Sources.Glunovo             -> IcPluginGlunovo
        Sources.Insight             -> IcPluginInsight
        Sources.Insulin             -> IcPluginInsulin
        Sources.InsulinDialog       -> IcBolus
        Sources.Intelligo           -> IcPluginIntelligo
        Sources.LocalProfile        -> IcProfile
        Sources.Loop                -> IcLoopClosed
        Sources.LoopDialog          -> IcLoopClosed
        Sources.MDI                 -> IcMdi
        Sources.MM640g              -> IcPluginMM640G
        Sources.Maintenance         -> IcPluginMaintenance
        Sources.Medtronic           -> IcPluginMedtronic
        Sources.Medtrum             -> IcPluginMedtrum
        Sources.NSClient            -> IcPluginNsClient
        Sources.NSClientSource      -> IcPluginNsClientBg
        Sources.NSProfile           -> IcPluginNsClient
        Sources.Note                -> IcNote
        Sources.NotificationReader  -> IcGenericCgm
        Sources.Objectives          -> IcPluginObjectives
        Sources.Omnipod             -> IcPatchPump
        Sources.OmnipodDash         -> IcPatchPump
        Sources.OmnipodEros         -> IcPatchPump
        Sources.Ottai               -> IcPluginSyai
        Sources.Overview            -> Icons.Default.Home
        Sources.PocTech             -> IcPluginPocTec
        Sources.ProfileSwitchDialog -> IcProfile
        Sources.Pump                -> IcGenericIcon
        Sources.Question            -> IcQuestion
        Sources.QuickWizard         -> IcQuickwizard
        Sources.Random              -> IcPluginRandomBg
        Sources.SMS                 -> IcPluginSms
        Sources.SensorInsert        -> IcCgmInsert
        Sources.SettingsExport      -> IcAutomation
        Sources.SiBionic            -> IcGenericCgm
        Sources.Sino                -> IcGenericCgm
        Sources.SiteRotationDialog  -> IcSiteRotation
        Sources.Stats               -> IcStats
        Sources.SyaiTag             -> IcPluginSyai
        Sources.TTDialog            -> IcTtHigh
        Sources.TempBasalDialog     -> IcTbrHigh
        Sources.Tomato              -> IcPluginTomato
        Sources.TreatmentDialog     -> Icons.Default.Add
        Sources.Treatments          -> IcClinicalNotes
        Sources.Unknown             -> Icons.Default.Settings
        Sources.VirtualPump         -> IcPluginVirtualPump
        Sources.Wear                -> Icons.Default.Watch
        Sources.WizardDialog        -> IcCalculator
        Sources.Xdrip               -> IcXDrip
    }

    override fun listToPresentationString(list: List<ValueWithUnit>) =
        list.joinToString(separator = "  ", transform = this::toPresentationString)

    private fun toPresentationString(valueWithUnit: ValueWithUnit?): String = when (valueWithUnit) {
        is ValueWithUnit.Gram                 -> "${valueWithUnit.value}${translator.translate(valueWithUnit)}"
        is ValueWithUnit.Hour                 -> "${valueWithUnit.value}${translator.translate(valueWithUnit)}"
        is ValueWithUnit.Minute               -> "${valueWithUnit.value}${translator.translate(valueWithUnit)}"
        is ValueWithUnit.Percent              -> "${valueWithUnit.value}${translator.translate(valueWithUnit)}"
        is ValueWithUnit.Insulin              -> decimalFormatter.to2Decimal(valueWithUnit.value) + translator.translate(valueWithUnit)
        is ValueWithUnit.InsulinConcentration -> "${rh.gs(app.aaps.core.ui.R.string.ins_concentration_confirmed, valueWithUnit.value)}"
        is ValueWithUnit.UnitPerHour          -> decimalFormatter.to2Decimal(valueWithUnit.value) + translator.translate(valueWithUnit)
        is ValueWithUnit.SimpleInt            -> valueWithUnit.value.toString()
        is ValueWithUnit.SimpleString         -> valueWithUnit.value
        is ValueWithUnit.TEMeterType          -> translator.translate(valueWithUnit.value)
        is ValueWithUnit.TETTReason           -> translator.translate(valueWithUnit.value)
        is ValueWithUnit.RMMode               -> translator.translate(valueWithUnit.value)
        is ValueWithUnit.TEType               -> translator.translate(valueWithUnit.value)
        is ValueWithUnit.TELocation           -> translator.translate(valueWithUnit.value)
        is ValueWithUnit.TEArrow              -> translator.translate(valueWithUnit.value)
        is ValueWithUnit.Timestamp            -> dateUtil.dateAndTimeAndSecondsString(valueWithUnit.value)

        is ValueWithUnit.Mgdl                 -> {
            if (profileUtil.units == GlucoseUnit.MGDL) decimalFormatter.to0Decimal(valueWithUnit.value) + rh.gs(app.aaps.core.ui.R.string.mgdl)
            else decimalFormatter.to1Decimal(valueWithUnit.value * Constants.MGDL_TO_MMOLL) + rh.gs(app.aaps.core.ui.R.string.mmol)
        }

        is ValueWithUnit.Mmoll                -> {
            if (profileUtil.units == GlucoseUnit.MMOL) decimalFormatter.to1Decimal(valueWithUnit.value) + rh.gs(app.aaps.core.ui.R.string.mmol)
            else decimalFormatter.to0Decimal(valueWithUnit.value * Constants.MMOLL_TO_MGDL) + rh.gs(app.aaps.core.ui.R.string.mgdl)
        }

        ValueWithUnit.UNKNOWN                 -> ""
        null                                  -> ""
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
                is ValueWithUnit.Gram                 -> gram = valueWithUnit.value.toString()
                is ValueWithUnit.Hour                 -> hour = valueWithUnit.value.toString()
                is ValueWithUnit.Minute               -> minute = valueWithUnit.value.toString()
                is ValueWithUnit.Percent              -> percent = valueWithUnit.value.toString()
                is ValueWithUnit.Insulin              -> insulin = decimalFormatter.to2Decimal(valueWithUnit.value)
                is ValueWithUnit.InsulinConcentration -> simpleString = simpleString.addWithSeparator(rh.gs(app.aaps.core.ui.R.string.ins_concentration_confirmed, valueWithUnit.value))
                is ValueWithUnit.UnitPerHour          -> unitPerHour = decimalFormatter.to2Decimal(valueWithUnit.value)
                is ValueWithUnit.SimpleInt            -> noUnit = noUnit.addWithSeparator(valueWithUnit.value)
                is ValueWithUnit.SimpleString         -> simpleString = simpleString.addWithSeparator(valueWithUnit.value)
                is ValueWithUnit.TEMeterType          -> therapyEvent = therapyEvent.addWithSeparator(translator.translate(valueWithUnit.value))
                is ValueWithUnit.TETTReason           -> therapyEvent = therapyEvent.addWithSeparator(translator.translate(valueWithUnit.value))
                is ValueWithUnit.RMMode               -> therapyEvent = therapyEvent.addWithSeparator(translator.translate(valueWithUnit.value))
                is ValueWithUnit.TEType               -> therapyEvent = therapyEvent.addWithSeparator(translator.translate(valueWithUnit.value))
                is ValueWithUnit.TELocation           -> therapyEvent = therapyEvent.addWithSeparator(translator.translate(valueWithUnit.value))
                is ValueWithUnit.TEArrow              -> therapyEvent = therapyEvent.addWithSeparator(translator.translate(valueWithUnit.value))
                is ValueWithUnit.Timestamp            -> timestamp = dateUtil.dateAndTimeAndSecondsString(valueWithUnit.value)

                is ValueWithUnit.Mgdl                 ->
                    bg = profileUtil.fromMgdlToStringInUnits(valueWithUnit.value)

                is ValueWithUnit.Mmoll                ->
                    bg = profileUtil.fromMgdlToStringInUnits(valueWithUnit.value * Constants.MMOLL_TO_MGDL)

                ValueWithUnit.UNKNOWN                 -> Unit
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
