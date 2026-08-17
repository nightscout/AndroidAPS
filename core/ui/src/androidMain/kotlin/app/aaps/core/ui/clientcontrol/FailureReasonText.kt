package app.aaps.core.ui.clientcontrol

import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.ui.UiStrings
import androidx.annotation.StringRes
import app.aaps.core.interfaces.clientcontrol.FailureReason
import app.aaps.core.ui.R

/**
 * The single localized-string mapping for a client-control [FailureReason], shared by the phone pending dialog
 * (`ClientControlPendingDialog`) and the wear relay error path (`DataHandlerMobile`). Resolve the returned id on
 * the SHOWING device (`stringResource` / `ResourceHelper.gs`) so the text is in that device's locale.
 */

fun FailureReason.failText(): TextRef = when (this) {
    FailureReason.NotPaired          -> UiStrings.clientcontrol_fail_not_paired
    FailureReason.NotReachable       -> UiStrings.clientcontrol_fail_not_reachable
    FailureReason.NoReply            -> UiStrings.clientcontrol_fail_no_reply
    FailureReason.Expired            -> UiStrings.clientcontrol_fail_expired
    FailureReason.Busy               -> UiStrings.clientcontrol_fail_busy
    FailureReason.SendFailed         -> UiStrings.clientcontrol_fail_send_failed
    FailureReason.NoActiveProfile    -> UiStrings.clientcontrol_fail_no_active_profile
    FailureReason.SceneNotFound      -> UiStrings.clientcontrol_fail_scene_not_found
    FailureReason.SceneDisabled      -> UiStrings.clientcontrol_fail_scene_disabled
    FailureReason.PartialFailure     -> UiStrings.clientcontrol_fail_partial
    FailureReason.ExecutionFailed    -> UiStrings.clientcontrol_fail_execution
    FailureReason.ControlDisabled    -> UiStrings.clientcontrol_fail_control_disabled
    FailureReason.NoAction           -> UiStrings.no_action_selected
    FailureReason.NoPendingBolus     -> UiStrings.clientcontrol_fail_no_pending_bolus
    FailureReason.BolusComputeFailed -> UiStrings.clientcontrol_fail_bolus_compute
    FailureReason.Internal           -> UiStrings.clientcontrol_fail_internal
    FailureReason.Unknown            -> UiStrings.clientcontrol_fail_unknown
}
