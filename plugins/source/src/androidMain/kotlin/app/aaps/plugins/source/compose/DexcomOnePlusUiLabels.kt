package app.aaps.plugins.source.compose

import androidx.compose.runtime.Composable
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.keys.interfaces.TextRef.Companion.withArgs
import app.aaps.core.ui.compose.stringResource
import app.aaps.plugins.dexcomoneplus.OnePlusWarmupState
import app.aaps.plugins.dexcomoneplus.session.OnePlusAuthRefusalPolicy
import app.aaps.plugins.source.SourceStrings

/**
 * Localized labels for ONE+ Status / Warm-up screens.
 * Driver [OnePlusWarmupState.message] codes stay English for logcat; UI maps them here.
 *
 * The [TextRef] forms are for code outside Compose (the status state builder), the Composable
 * forms for the screens. Both give the same text.
 */
object DexcomOnePlusUiLabels {

    fun phaseLabelRef(phase: OnePlusWarmupState.Phase): TextRef = when (phase) {
        OnePlusWarmupState.Phase.IDLE -> SourceStrings.dexcom_oneplus_phase_idle
        OnePlusWarmupState.Phase.PAIRING -> SourceStrings.dexcom_oneplus_phase_pairing
        OnePlusWarmupState.Phase.CONNECTING -> SourceStrings.dexcom_oneplus_phase_connecting
        OnePlusWarmupState.Phase.RECONNECTING -> SourceStrings.dexcom_oneplus_phase_reconnecting
        OnePlusWarmupState.Phase.WARMING -> SourceStrings.dexcom_oneplus_phase_warming
        OnePlusWarmupState.Phase.READY -> SourceStrings.dexcom_oneplus_phase_ready
        OnePlusWarmupState.Phase.FAILED -> SourceStrings.dexcom_oneplus_phase_failed
    }

    @Composable
    fun phaseLabel(phase: OnePlusWarmupState.Phase): String = stringResource(phaseLabelRef(phase))

    /**
     * Maps driver/log message codes to user-facing copy. Unknown technical codes are not shown raw.
     */
    fun userMessageRef(raw: String?): TextRef {
        if (raw.isNullOrBlank()) return SourceStrings.dexcom_oneplus_status_message_none
        return when {
            raw == "pairing" || raw.startsWith("pairing_attempt_") ->
                SourceStrings.dexcom_oneplus_msg_pairing
            raw == "auth_ok" ->
                SourceStrings.dexcom_oneplus_msg_auth_ok
            raw == "tx_time_session" ->
                SourceStrings.dexcom_oneplus_msg_tx_session
            raw == "egv_loop_exit" || raw == "disconnect" || raw == "shutdown" || raw == "cancelled" ->
                SourceStrings.dexcom_oneplus_msg_session_down
            // Before the generic auth line: these two say what the user has to do.
            raw.startsWith(OnePlusAuthRefusalPolicy.MESSAGE_LOCKED) ->
                SourceStrings.dexcom_oneplus_error_refused_stopped.withArgs(OnePlusAuthRefusalPolicy.MAX_REFUSALS_IN_A_ROW)
            raw.startsWith(OnePlusAuthRefusalPolicy.MESSAGE_PAUSED) ->
                SourceStrings.dexcom_oneplus_msg_refused_pause
            raw.startsWith("ONEPLUS_AUTH") || raw.contains("bond", ignoreCase = true) ->
                SourceStrings.dexcom_oneplus_error_auth_bond
            raw.startsWith("ONEPLUS_GATT") || raw.startsWith("ONEPLUS_EGV") ||
                raw.startsWith("ONEPLUS_CONTROL") || raw.startsWith("ONEPLUS_SESSION") ||
                raw.startsWith("ONEPLUS_RECONNECT") || raw.startsWith("ONEPLUS_BACKFILL") ||
                raw.startsWith("ONEPLUS_TIME") || raw.startsWith("ONEPLUS_PAIR") ->
                SourceStrings.dexcom_oneplus_error_connection
            else -> SourceStrings.dexcom_oneplus_status_message_generic
        }
    }

    @Composable
    fun userMessage(raw: String?): String = stringResource(userMessageRef(raw))
}
