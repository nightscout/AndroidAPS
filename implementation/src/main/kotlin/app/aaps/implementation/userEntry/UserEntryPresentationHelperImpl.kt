package app.aaps.implementation.userEntry

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Watch
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
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
import app.aaps.core.ui.R
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
import app.aaps.core.ui.compose.icons.IcPluginAutomation
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
import app.aaps.core.ui.compose.navigation.ElementType
import app.aaps.core.ui.compose.navigation.color
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
        Sources.Automation          -> IcPluginAutomation
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
        Sources.Database            -> Icons.Default.Storage
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
        Sources.Instara             -> IcGenericCgm
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
        Sources.Scene               -> IcAutomation
        Sources.SensorInsert        -> IcCgmInsert
        Sources.SettingsExport      -> Icons.Default.FileUpload
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

    @Composable
    override fun iconColor(source: Sources): Color = when (source) {
        Sources.Aaps                -> ElementType.AAPS.color()
        Sources.Actions             -> ElementType.TEMP_BASAL.color()
        Sources.Aidex               -> ElementType.CGM_DEX.color()
        Sources.Announcement        -> ElementType.ANNOUNCEMENT.color()
        Sources.Automation          -> ElementType.AUTOMATION.color()
        Sources.Autotune            -> ElementType.AAPS.color()
        Sources.BG                  -> ElementType.CGM_DEX.color()
        Sources.BatteryChange       -> ElementType.BATTERY_CHANGE.color()
        Sources.BgCheck             -> ElementType.BG_CHECK.color()
        Sources.BgFragment          -> ElementType.AAPS.color()
        Sources.CalibrationDialog   -> ElementType.CALIBRATION.color()
        Sources.CarbDialog          -> ElementType.CARBS.color()
        Sources.Combo               -> ElementType.PUMP.color()
        Sources.ConcentrationDialog -> ElementType.INSULIN_MANAGEMENT.color()
        Sources.ConfigBuilder       -> ElementType.CONFIGURATION.color()
        Sources.Dana                -> ElementType.PUMP.color()
        Sources.DanaI               -> ElementType.PUMP.color()
        Sources.DanaR               -> ElementType.PUMP.color()
        Sources.DanaRC              -> ElementType.PUMP.color()
        Sources.DanaRS              -> ElementType.PUMP.color()
        Sources.DanaRv2             -> ElementType.PUMP.color()
        Sources.Database            -> ElementType.AAPS.color()
        Sources.Dexcom              -> ElementType.CGM_DEX.color()
        Sources.DiaconnG8           -> ElementType.PUMP.color()
        Sources.EOPatch2            -> ElementType.PUMP.color()
        Sources.Equil               -> ElementType.PUMP.color()
        Sources.Eversense           -> ElementType.CGM_DEX.color()
        Sources.Exercise            -> ElementType.EXERCISE.color()
        Sources.ExtendedBolusDialog -> ElementType.EXTENDED_BOLUS.color()
        Sources.FillDialog          -> ElementType.FILL.color()
        Sources.Food                -> ElementType.FOOD_MANAGEMENT.color()
        Sources.Garmin              -> ElementType.AAPS.color()
        Sources.Glimp               -> ElementType.CGM_DEX.color()
        Sources.Glunovo             -> ElementType.CGM_DEX.color()
        Sources.Insight             -> ElementType.PUMP.color()
        Sources.Instara             -> ElementType.CGM_DEX.color()
        Sources.Insulin             -> ElementType.INSULIN_MANAGEMENT.color()
        Sources.InsulinDialog       -> ElementType.INSULIN.color()
        Sources.Intelligo           -> ElementType.CGM_DEX.color()
        Sources.LocalProfile        -> ElementType.PROFILE_MANAGEMENT.color()
        Sources.Loop                -> ElementType.LOOP.color()
        Sources.LoopDialog          -> ElementType.LOOP.color()
        Sources.MDI                 -> ElementType.CGM_DEX.color()
        Sources.MM640g              -> ElementType.CGM_DEX.color()
        Sources.Maintenance         -> ElementType.AAPS.color()
        Sources.Medtronic           -> ElementType.PUMP.color()
        Sources.Medtrum             -> ElementType.PUMP.color()
        Sources.NSClient            -> ElementType.AAPS.color()
        Sources.NSClientSource      -> ElementType.AAPS.color()
        Sources.NSProfile           -> ElementType.PROFILE_MANAGEMENT.color()
        Sources.Note                -> ElementType.NOTE.color()
        Sources.NotificationReader  -> ElementType.CGM_DEX.color()
        Sources.Objectives          -> ElementType.AAPS.color()
        Sources.Omnipod             -> ElementType.PUMP.color()
        Sources.OmnipodDash         -> ElementType.PUMP.color()
        Sources.OmnipodEros         -> ElementType.PUMP.color()
        Sources.Ottai               -> ElementType.CGM_DEX.color()
        Sources.Overview            -> ElementType.AAPS.color()
        Sources.PocTech             -> ElementType.CGM_DEX.color()
        Sources.ProfileSwitchDialog -> ElementType.PROFILE_MANAGEMENT.color()
        Sources.Pump                -> ElementType.PUMP.color()
        Sources.Question            -> ElementType.QUESTION.color()
        Sources.QuickWizard         -> ElementType.QUICK_WIZARD.color()
        Sources.Random              -> ElementType.CGM_DEX.color()
        Sources.SMS                 -> ElementType.AAPS.color()
        Sources.Scene               -> ElementType.SCENE_MANAGEMENT.color()
        Sources.SensorInsert        -> ElementType.SENSOR_INSERT.color()
        Sources.SettingsExport      -> ElementType.AAPS.color()
        Sources.SiBionic            -> ElementType.CGM_DEX.color()
        Sources.Sino                -> ElementType.CGM_DEX.color()
        Sources.SiteRotationDialog  -> ElementType.SITE_ROTATION.color()
        Sources.Stats               -> ElementType.STATISTICS.color()
        Sources.SyaiTag             -> ElementType.CGM_DEX.color()
        Sources.TTDialog            -> ElementType.TEMP_TARGET_MANAGEMENT.color()
        Sources.TempBasalDialog     -> ElementType.TEMP_TARGET_MANAGEMENT.color()
        Sources.Tomato              -> ElementType.CGM_DEX.color()
        Sources.TreatmentDialog     -> ElementType.TREATMENT.color()
        Sources.Treatments          -> ElementType.TREATMENTS.color()
        Sources.Unknown             -> ElementType.AAPS.color()
        Sources.VirtualPump         -> ElementType.PUMP.color()
        Sources.Wear                -> ElementType.AAPS.color()
        Sources.WizardDialog        -> ElementType.BOLUS_WIZARD.color()
        Sources.Xdrip               -> ElementType.CGM_XDRIP.color()
    }

    override fun listToPresentationString(list: List<ValueWithUnit>) =
        list.joinToString(separator = "  ", transform = this::toPresentationString)

    private fun toPresentationString(valueWithUnit: ValueWithUnit?): String = when (valueWithUnit) {
        is ValueWithUnit.Gram                 -> "${valueWithUnit.value}${translator.translate(valueWithUnit)}"
        is ValueWithUnit.Hour                 -> "${valueWithUnit.value}${translator.translate(valueWithUnit)}"
        is ValueWithUnit.Minute               -> "${valueWithUnit.value}${translator.translate(valueWithUnit)}"
        is ValueWithUnit.Percent              -> "${valueWithUnit.value}${translator.translate(valueWithUnit)}"
        is ValueWithUnit.Insulin              -> decimalFormatter.to2Decimal(valueWithUnit.value) + translator.translate(valueWithUnit)
        is ValueWithUnit.InsulinConcentration -> "${rh.gs(R.string.ins_concentration_confirmed, valueWithUnit.value)}"
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
            if (profileUtil.units == GlucoseUnit.MGDL) decimalFormatter.to0Decimal(valueWithUnit.value) + rh.gs(R.string.mgdl)
            else decimalFormatter.to1Decimal(valueWithUnit.value * Constants.MGDL_TO_MMOLL) + rh.gs(R.string.mmol)
        }

        is ValueWithUnit.Mmoll                -> {
            if (profileUtil.units == GlucoseUnit.MMOL) decimalFormatter.to1Decimal(valueWithUnit.value) + rh.gs(R.string.mmol)
            else decimalFormatter.to0Decimal(valueWithUnit.value * Constants.MMOLL_TO_MGDL) + rh.gs(R.string.mgdl)
        }

        ValueWithUnit.UNKNOWN                 -> ""
        null                                  -> ""
    }

    override fun userEntriesToCsv(userEntries: List<UE>): String {
        return getCsvHeader() + userEntries.joinToString("\n") { entry -> getCsvEntry(entry) }
    }

    private fun getCsvHeader() = rh.gs(
        R.string.ue_csv_header,
        csvString(R.string.ue_timestamp),
        csvString(R.string.date),
        csvString(R.string.ue_utc_offset),
        csvString(R.string.ue_action),
        csvString(R.string.event_type),
        csvString(R.string.ue_source),
        csvString(R.string.careportal_note),
        csvString(R.string.ue_string),
        csvString(R.string.event_time_label),
        csvString(if (profileUtil.units == GlucoseUnit.MGDL) R.string.mgdl else R.string.mmol),
        csvString(R.string.shortgram),
        csvString(R.string.insulin_unit_shortname),
        csvString(R.string.profile_ins_units_per_hour),
        csvString(R.string.shortpercent),
        csvString(app.aaps.core.interfaces.R.string.shorthour),
        csvString(app.aaps.core.interfaces.R.string.shortminute),
        csvString(R.string.ue_none)
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
                is ValueWithUnit.InsulinConcentration -> simpleString = simpleString.addWithSeparator(rh.gs(R.string.ins_concentration_confirmed, valueWithUnit.value))
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
