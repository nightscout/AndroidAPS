package app.aaps.plugins.source.compose

import androidx.compose.runtime.Composable
import app.aaps.core.ui.compose.stringResource
import app.aaps.plugins.dexcomoneplus.OnePlusWarmupState
import app.aaps.plugins.source.SourceStrings

/**
 * Localized labels for ONE+ Status / Warm-up screens.
 * Driver [OnePlusWarmupState.message] codes stay English for logcat; UI maps them here.
 */
object DexcomOnePlusUiLabels {

    @Composable
    fun phaseLabel(phase: OnePlusWarmupState.Phase): String = when (phase) {
        OnePlusWarmupState.Phase.IDLE -> stringResource(SourceStrings.dexcom_oneplus_phase_idle)
        OnePlusWarmupState.Phase.PAIRING -> stringResource(SourceStrings.dexcom_oneplus_phase_pairing)
        OnePlusWarmupState.Phase.CONNECTING -> stringResource(SourceStrings.dexcom_oneplus_phase_connecting)
        OnePlusWarmupState.Phase.RECONNECTING -> stringResource(SourceStrings.dexcom_oneplus_phase_reconnecting)
        OnePlusWarmupState.Phase.WARMING -> stringResource(SourceStrings.dexcom_oneplus_phase_warming)
        OnePlusWarmupState.Phase.READY -> stringResource(SourceStrings.dexcom_oneplus_phase_ready)
        OnePlusWarmupState.Phase.FAILED -> stringResource(SourceStrings.dexcom_oneplus_phase_failed)
    }

    /**
     * Maps driver/log message codes to user-facing copy. Unknown technical codes are not shown raw.
     */
    @Composable
    fun userMessage(raw: String?): String {
        if (raw.isNullOrBlank()) {
            return stringResource(SourceStrings.dexcom_oneplus_status_message_none)
        }
        return when {
            raw == "pairing" || raw.startsWith("pairing_attempt_") ->
                stringResource(SourceStrings.dexcom_oneplus_msg_pairing)
            raw == "auth_ok" ->
                stringResource(SourceStrings.dexcom_oneplus_msg_auth_ok)
            raw == "tx_time_session" ->
                stringResource(SourceStrings.dexcom_oneplus_msg_tx_session)
            raw == "egv_loop_exit" || raw == "disconnect" || raw == "shutdown" || raw == "cancelled" ->
                stringResource(SourceStrings.dexcom_oneplus_msg_session_down)
            raw.startsWith("ONEPLUS_AUTH") || raw.contains("bond", ignoreCase = true) ->
                stringResource(SourceStrings.dexcom_oneplus_error_auth_bond)
            raw.startsWith("ONEPLUS_GATT") || raw.startsWith("ONEPLUS_EGV") ||
                raw.startsWith("ONEPLUS_CONTROL") || raw.startsWith("ONEPLUS_SESSION") ||
                raw.startsWith("ONEPLUS_RECONNECT") || raw.startsWith("ONEPLUS_BACKFILL") ||
                raw.startsWith("ONEPLUS_TIME") || raw.startsWith("ONEPLUS_PAIR") ->
                stringResource(SourceStrings.dexcom_oneplus_error_connection)
            else -> stringResource(SourceStrings.dexcom_oneplus_status_message_generic)
        }
    }
}
