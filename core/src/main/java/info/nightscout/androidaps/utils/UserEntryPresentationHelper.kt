package info.nightscout.androidaps.utils

import android.text.Spanned
import dagger.Reusable
import info.nightscout.androidaps.Constants
import info.nightscout.androidaps.core.R
import info.nightscout.androidaps.database.entities.UserEntry
import info.nightscout.androidaps.database.entities.UserEntry.*
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
        Sources.LocalProfile        -> R.drawable.ic_local_profile
        Sources.Loop                -> R.drawable.ic_loop_closed_white
        Sources.Maintenance         -> R.drawable.ic_maintenance
        Sources.NSClient            -> R.drawable.ic_nightscout_syncs
        Sources.Pump                -> R.drawable.ic_generic_icon
        Sources.SMS                 -> R.drawable.ic_sms
        Sources.Treatments          -> R.drawable.ic_treatments
        Sources.Wear                -> R.drawable.ic_watch
        Sources.Food                -> R.drawable.ic_food
        Sources.Unknown             -> R.drawable.ic_generic_icon
    }

    fun actionToColoredString(action: Action): Spanned = when (action) {
            Action.TREATMENT -> HtmlHelper.fromHtml(coloredAction(Action.BOLUS) + " + " + coloredAction(Action.CARBS))
            else             -> HtmlHelper.fromHtml(coloredAction(action))
    }

    private fun coloredAction(action: Action): String = "<font color='${resourceHelper.gc(colorId(action.colorGroup))}'>${translator.translate(action)}</font>"

    fun listToPresentationString(list: List<XXXValueWithUnit>) =
        list.joinToString(separator = " ", transform = this::toPresentationString)

    private fun toPresentationString(valueWithUnit: XXXValueWithUnit): String = when (valueWithUnit) {
        is XXXValueWithUnit.Gram -> "${valueWithUnit.value} ${translator.translate(Units.G)}"
        is XXXValueWithUnit.Hour -> "${valueWithUnit.value} ${translator.translate(Units.H)}"
        is XXXValueWithUnit.Minute -> "${valueWithUnit.value} ${translator.translate(Units.G)}"
        is XXXValueWithUnit.Percent -> "${valueWithUnit.value} ${translator.translate(Units.Percent)}"
        is XXXValueWithUnit.Insulin -> DecimalFormatter.to2Decimal(valueWithUnit.value) + translator.translate(UserEntry.Units.U)
        is XXXValueWithUnit.UnitPerHour -> DecimalFormatter.to2Decimal(valueWithUnit.value) + translator.translate(UserEntry.Units.U_H)
        is XXXValueWithUnit.SimpleInt -> valueWithUnit.value.toString()
        is XXXValueWithUnit.SimpleString -> valueWithUnit.value
        is XXXValueWithUnit.StringResource -> resourceHelper.gs(valueWithUnit.value, valueWithUnit.params.map(this::toPresentationString))
        is XXXValueWithUnit.TherapyEventMeterType -> translator.translate(valueWithUnit.value)
        is XXXValueWithUnit.TherapyEventTTReason -> translator.translate(valueWithUnit.value)
        is XXXValueWithUnit.TherapyEventType -> translator.translate(valueWithUnit.value)
        is XXXValueWithUnit.Timestamp -> dateUtil.dateAndTimeAndSecondsString(valueWithUnit.value)

        is XXXValueWithUnit.Mgdl -> {
            if (profileFunction.getUnits() == Constants.MGDL) DecimalFormatter.to0Decimal(valueWithUnit.value) + translator.translate(Units.Mg_Dl)
            else DecimalFormatter.to1Decimal(valueWithUnit.value / Constants.MMOLL_TO_MGDL) + translator.translate(Units.Mmol_L)
        }

        is XXXValueWithUnit.Mmoll -> {
            if (profileFunction.getUnits() == Constants.MGDL) DecimalFormatter.to0Decimal(valueWithUnit.value) + translator.translate(Units.Mmol_L)
            else DecimalFormatter.to1Decimal(valueWithUnit.value * Constants.MMOLL_TO_MGDL) + translator.translate(Units.Mg_Dl)
        }

        XXXValueWithUnit.UNKNOWN -> ""
    }
}