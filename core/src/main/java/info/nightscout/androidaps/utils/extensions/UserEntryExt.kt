package info.nightscout.androidaps.utils.extensions

import info.nightscout.androidaps.core.R
import info.nightscout.androidaps.database.entities.UserEntry.*

fun ColorGroup.colorId(): Int {
    return when (this) {
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
}

fun Sources.iconId(): Int {
    return when (this) {
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
        Sources.Loop                -> R.drawable.ic_loop_closed_white
        Sources.NSClient            -> R.drawable.ic_nightscout_syncs
        Sources.Wear                -> R.drawable.ic_watch
        else                        -> -1
    }
}