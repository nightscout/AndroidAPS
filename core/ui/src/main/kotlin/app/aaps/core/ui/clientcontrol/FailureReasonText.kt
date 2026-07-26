package app.aaps.core.ui.clientcontrol

import androidx.annotation.StringRes
import app.aaps.core.interfaces.clientcontrol.FailureReason
import app.aaps.core.ui.R

/**
 * The single localized-string mapping for a client-control [FailureReason], shared by the phone pending dialog
 * (`ClientControlPendingDialog`) and the wear relay error path (`DataHandlerMobile`). Resolve the returned id on
 * the SHOWING device (`stringResource` / `ResourceHelper.gs`) so the text is in that device's locale.
 */
@StringRes
fun FailureReason.failTextResId(): Int = when (this) {
    FailureReason.NotPaired          -> R.string.clientcontrol_fail_not_paired
    FailureReason.NotReachable       -> R.string.clientcontrol_fail_not_reachable
    FailureReason.NoReply            -> R.string.clientcontrol_fail_no_reply
    FailureReason.Expired            -> R.string.clientcontrol_fail_expired
    FailureReason.Busy               -> R.string.clientcontrol_fail_busy
    FailureReason.SendFailed         -> R.string.clientcontrol_fail_send_failed
    FailureReason.NoActiveProfile    -> R.string.clientcontrol_fail_no_active_profile
    FailureReason.SceneNotFound      -> R.string.clientcontrol_fail_scene_not_found
    FailureReason.SceneDisabled      -> R.string.clientcontrol_fail_scene_disabled
    FailureReason.PartialFailure     -> R.string.clientcontrol_fail_partial
    FailureReason.ExecutionFailed    -> R.string.clientcontrol_fail_execution
    FailureReason.ControlDisabled    -> R.string.clientcontrol_fail_control_disabled
    FailureReason.NoAction           -> R.string.no_action_selected
    FailureReason.NoPendingBolus     -> R.string.clientcontrol_fail_no_pending_bolus
    FailureReason.BolusComputeFailed -> R.string.clientcontrol_fail_bolus_compute
    FailureReason.Internal           -> R.string.clientcontrol_fail_internal
    FailureReason.Unknown            -> R.string.clientcontrol_fail_unknown
}
