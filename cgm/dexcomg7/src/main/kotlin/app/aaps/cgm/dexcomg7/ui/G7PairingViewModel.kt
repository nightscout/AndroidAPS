package app.aaps.cgm.dexcomg7.ui

import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aaps.cgm.dexcomg7.R
import app.aaps.cgm.dexcomg7.data.G7StateStore
import app.aaps.cgm.dexcomg7.protocol.G7SensorPackage
import app.aaps.cgm.dexcomg7.session.G7PairingService
import app.aaps.cgm.dexcomg7.session.G7PairingState
import app.aaps.core.interfaces.resources.ResourceHelper
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class G7PairingStep { INTRO, CODE, SCANNER, PAIRING, DONE, FAILED }

data class G7PairingUiState(
    val step: G7PairingStep = G7PairingStep.INTRO,
    /** Apps on this phone that would compete for the sensor. */
    val conflictingApps: List<String> = emptyList(),
    val code: String = "",
    /** Serial from the barcode; lets pairing skip other people's sensors. */
    val serial: String? = null,
    /** Result of the last barcode scan, to show under the code field. */
    val scanMessage: String? = null,
    val pairing: G7PairingState = G7PairingState.Idle,
    val failureText: String? = null
) {

    val codeIsValid: Boolean get() = G7PairingService.isValidPairingCode(code)
}

/**
 * The pairing wizard: check for competing apps, explain how to apply the sensor, get the code by
 * barcode or typing, then run the pairing and show its progress. Follows Trio's onboarding.
 */
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
@ViewModelKey
@Stable
@Inject
class G7PairingViewModel(
    private val context: Context,
    private val rh: ResourceHelper,
    private val pairing: G7PairingService,
    private val store: G7StateStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(G7PairingUiState())
    val uiState: StateFlow<G7PairingUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            pairing.state.collect { state ->
                _uiState.update { ui ->
                    when (state) {
                        is G7PairingState.Succeeded -> ui.copy(pairing = state, step = G7PairingStep.DONE)
                        is G7PairingState.Failed    -> ui.copy(pairing = state, step = G7PairingStep.FAILED, failureText = failureText(state))
                        else                        -> ui.copy(pairing = state)
                    }
                }
            }
        }
    }

    /** Starts the wizard from the beginning. */
    fun reset() {
        pairing.reset()
        _uiState.value = G7PairingUiState(conflictingApps = installedConflictingApps())
    }

    fun continueFromIntro() = _uiState.update { it.copy(step = G7PairingStep.CODE, conflictingApps = installedConflictingApps()) }

    fun updateCode(code: String) = _uiState.update { it.copy(code = code.filter(Char::isDigit).take(4), serial = null, scanMessage = null) }

    fun openScanner() = _uiState.update { it.copy(step = G7PairingStep.SCANNER) }

    fun closeScanner() = _uiState.update { it.copy(step = G7PairingStep.CODE) }

    /** A Data Matrix was read. Returns true when it held a pairing code, so the scanner can stop. */
    fun onBarcode(payload: String): Boolean {
        val box = G7SensorPackage.parse(payload)
        return when {
            box?.pairingCode != null -> {
                _uiState.update {
                    it.copy(
                        step = G7PairingStep.CODE,
                        code = box.pairingCode!!,
                        serial = box.serial,
                        scanMessage = rh.gs(R.string.dexcom_g7_scan_found, box.pairingCode!!, box.serial ?: "?") +
                            if (!box.isDexcom) "\n" + rh.gs(R.string.dexcom_g7_scan_not_dexcom) else ""
                    )
                }
                true
            }

            box != null              -> {
                _uiState.update { it.copy(scanMessage = rh.gs(R.string.dexcom_g7_scan_no_code)) }
                false
            }

            else                     -> false
        }
    }

    fun startPairing() {
        val ui = _uiState.value
        if (!ui.codeIsValid) return
        _uiState.update { it.copy(step = G7PairingStep.PAIRING, failureText = null) }
        pairing.start(ui.code, ui.serial)
    }

    fun cancel() {
        pairing.cancel()
    }

    fun retry() = _uiState.update { it.copy(step = G7PairingStep.CODE, failureText = null) }

    val isReplacingSensor: Boolean get() = store.value.isPaired

    private fun failureText(failed: G7PairingState.Failed): String {
        val main = rh.gs(
            when (failed.reason) {
                G7PairingState.Reason.INVALID_CODE          -> R.string.dexcom_g7_pairing_failed_invalid_code
                G7PairingState.Reason.BLUETOOTH_UNAVAILABLE -> R.string.dexcom_g7_pairing_failed_bluetooth
                G7PairingState.Reason.NO_SENSOR_FOUND       -> R.string.dexcom_g7_pairing_failed_no_sensor
                G7PairingState.Reason.ALL_CANDIDATES_FAILED -> R.string.dexcom_g7_pairing_failed_all
            }
        )
        return if (failed.details.isEmpty()) main else main + "\n\n" + failed.details.joinToString("\n")
    }

    private fun installedConflictingApps(): List<String> = CONFLICTING_PACKAGES.mapNotNull { (packageName, label) ->
        try {
            context.packageManager.getPackageInfo(packageName, 0)
            label
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }
    }

    companion object {

        /** Apps that connect to the sensor themselves. A sensor accepts one phone app at a time. */
        val CONFLICTING_PACKAGES = listOf(
            "com.dexcom.g7" to "Dexcom G7",
            "com.dexcom.dexcomone" to "Dexcom ONE",
            "com.dexcom.one" to "Dexcom ONE+",
            "com.dexcom.stelo" to "Dexcom Stelo",
            "com.eveningoutpost.dexdrip" to "xDrip+",
            "tk.glucodata" to "Juggluco"
        )
    }
}
