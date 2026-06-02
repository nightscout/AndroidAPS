package app.aaps.ui.compose.afrezzaDialog

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aaps.core.data.model.BS
import app.aaps.core.data.model.ICfg
import app.aaps.core.data.model.IDs
import app.aaps.core.data.model.TE
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.data.ue.ValueWithUnit
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.insulin.InsulinManager
import app.aaps.core.interfaces.insulin.InsulinType
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.ui.R
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
@Stable
class AfrezzaDialogViewModel @Inject constructor(
    private val insulinManager: InsulinManager,
    private val persistenceLayer: PersistenceLayer,
    private val uel: UserEntryLogger,
    private val dateUtil: DateUtil,
    private val rh: ResourceHelper,
    private val aapsLogger: AAPSLogger
) : ViewModel() {

    private val _uiState = MutableStateFlow(AfrezzaDialogUiState())
    val uiState: StateFlow<AfrezzaDialogUiState> = _uiState.asStateFlow()

    sealed class SideEffect {
        data class ShowMessage(val message: String) : SideEffect()
        data object DoseLogged : SideEffect()
    }

    private val _sideEffect = MutableSharedFlow<SideEffect>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val sideEffect: SharedFlow<SideEffect> = _sideEffect.asSharedFlow()

    init {
        val afrezzaIcfg = findAfrezzaIcfg()
        _uiState.update {
            AfrezzaDialogUiState(
                afrezzaIcfg = afrezzaIcfg,
                isConfigured = afrezzaIcfg != null
            )
        }
    }

    /**
     * Find the Afrezza ICfg from InsulinManager's configured insulins.
     * Matches by peak time against the OREF_INHALED_AFREZZA template.
     */
    private fun findAfrezzaIcfg(): ICfg? {
        val afrezzaPeak = InsulinType.OREF_INHALED_AFREZZA.insulinPeakTime
        return insulinManager.insulins.firstOrNull { it.insulinPeakTime == afrezzaPeak }
            ?: insulinManager.insulins.firstOrNull {
                // Fallback: match any insulin with DIA <= 4h (likely inhaled)
                val template = InsulinType.fromPeak(it.insulinPeakTime)
                template.isInhaled
            }
    }

    fun selectCartridge(units: Int) {
        _uiState.update { it.copy(selectedCartridge = units, showConfirmation = true) }
    }

    fun dismissConfirmation() {
        _uiState.update { it.copy(showConfirmation = false, selectedCartridge = null) }
    }

    fun confirmAndLog() {
        val state = _uiState.value
        val units = state.selectedCartridge ?: return
        val iCfg = state.afrezzaIcfg ?: return

        _uiState.update { it.copy(isLogging = true) }

        viewModelScope.launch {
            try {
                val now = dateUtil.now()
                val bolus = BS(
                    timestamp = now,
                    amount = units.toDouble(),
                    type = BS.Type.NORMAL,
                    notes = rh.gs(R.string.afrezza_inhaled),
                    iCfg = iCfg,
                    ids = IDs(pumpId = now)
                )

                persistenceLayer.insertOrUpdateBolus(
                    bolus = bolus,
                    action = Action.BOLUS,
                    source = Sources.AfrezzaDialog,
                    note = rh.gs(R.string.afrezza_inhaled)
                )

                uel.log(
                    Action.BOLUS,
                    Sources.InsulinDialog,
                    rh.gs(R.string.afrezza_inhaled),
                    ValueWithUnit.Insulin(units.toDouble())
                )

                aapsLogger.info(LTag.UI, "Afrezza ${units}U logged with ICfg: ${iCfg.insulinLabel}")

                _sideEffect.tryEmit(SideEffect.ShowMessage(rh.gs(R.string.afrezza_logged, units)))
                _sideEffect.tryEmit(SideEffect.DoseLogged)
            } catch (e: Exception) {
                aapsLogger.error(LTag.UI, "Failed to log Afrezza dose", e)
            } finally {
                _uiState.update { it.copy(isLogging = false, showConfirmation = false, selectedCartridge = null) }
            }
        }
    }
}
