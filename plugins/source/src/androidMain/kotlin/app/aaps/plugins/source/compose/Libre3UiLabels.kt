package app.aaps.plugins.source.compose

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.aaps.plugins.libre3.Libre3WarmupState
import app.aaps.plugins.libre3.nfc.Libre3NfcFailure
import app.aaps.plugins.source.R

/**
 * Turns what the driver reports into words the user can read.
 *
 * The driver module has no strings of its own on purpose, so nothing it says can reach a screen
 * untranslated. Every value it reports is turned into a string resource here.
 */
object Libre3UiLabels {

    @Composable
    fun phaseLabel(phase: Libre3WarmupState.Phase): String = stringResource(
        when (phase) {
            Libre3WarmupState.Phase.IDLE         -> R.string.libre3_phase_idle
            Libre3WarmupState.Phase.PAIRING      -> R.string.libre3_phase_pairing
            Libre3WarmupState.Phase.CONNECTING   -> R.string.libre3_phase_connecting
            Libre3WarmupState.Phase.RECONNECTING -> R.string.libre3_phase_reconnecting
            Libre3WarmupState.Phase.WARMING      -> R.string.libre3_phase_warming
            Libre3WarmupState.Phase.READY        -> R.string.libre3_phase_ready
            Libre3WarmupState.Phase.FAILED       -> R.string.libre3_phase_failed
        }
    )

    @Composable
    fun nfcFailureLabel(failure: Libre3NfcFailure): String = stringResource(
        when (failure) {
            Libre3NfcFailure.NOT_A_LIBRE3_SENSOR    -> R.string.libre3_nfc_not_a_sensor
            Libre3NfcFailure.SENSOR_REFUSED         -> R.string.libre3_nfc_sensor_refused
            Libre3NfcFailure.LINK_LOST              -> R.string.libre3_nfc_link_lost
            Libre3NfcFailure.PHONE_STORAGE_FAILED   -> R.string.libre3_nfc_storage_failed
            Libre3NfcFailure.SAME_SENSOR_OTHER_SLOT -> R.string.libre3_nfc_same_sensor_other_slot
        }
    )

    /** "Libre 3" or "Libre 3 Plus", from what the sensor said over NFC. */
    @Composable
    fun generationLabel(generation: Int): String =
        stringResource(if (generation >= 1) R.string.libre3_family_plus else R.string.libre3_family_three)
}
