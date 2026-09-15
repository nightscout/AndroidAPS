package app.aaps.core.ui.clientcontrol

import app.aaps.core.interfaces.clientcontrol.FailureReason
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.ui.CoreUiStrings

/**
 * The single localized-string mapping for a client-control [FailureReason], shared by the phone pending dialog
 * (`ClientControlPendingDialog`) and the wear relay error path (`DataHandlerMobile`). Resolve the returned id on
 * the SHOWING device (`stringResource` / `ResourceHelper.gs`) so the text is in that device's locale.
 */

fun FailureReason.failText(): TextRef = when (this) {
    FailureReason.NotPaired          -> CoreUiStrings.clientcontrol_fail_not_paired
    FailureReason.NotReachable       -> CoreUiStrings.clientcontrol_fail_not_reachable
    FailureReason.NoReply            -> CoreUiStrings.clientcontrol_fail_no_reply
    FailureReason.Expired            -> CoreUiStrings.clientcontrol_fail_expired
    FailureReason.Busy               -> CoreUiStrings.clientcontrol_fail_busy
    FailureReason.SendFailed         -> CoreUiStrings.clientcontrol_fail_send_failed
    FailureReason.NoActiveProfile    -> CoreUiStrings.clientcontrol_fail_no_active_profile
    FailureReason.SceneNotFound      -> CoreUiStrings.clientcontrol_fail_scene_not_found
    FailureReason.SceneDisabled      -> CoreUiStrings.clientcontrol_fail_scene_disabled
    FailureReason.PartialFailure     -> CoreUiStrings.clientcontrol_fail_partial
    FailureReason.ExecutionFailed    -> CoreUiStrings.clientcontrol_fail_execution
    FailureReason.ControlDisabled    -> CoreUiStrings.clientcontrol_fail_control_disabled
    FailureReason.NoAction           -> CoreUiStrings.no_action_selected
    FailureReason.NoPendingBolus     -> CoreUiStrings.clientcontrol_fail_no_pending_bolus
    FailureReason.BolusComputeFailed -> CoreUiStrings.clientcontrol_fail_bolus_compute
    FailureReason.Internal           -> CoreUiStrings.clientcontrol_fail_internal
    FailureReason.Unknown            -> CoreUiStrings.clientcontrol_fail_unknown
}
