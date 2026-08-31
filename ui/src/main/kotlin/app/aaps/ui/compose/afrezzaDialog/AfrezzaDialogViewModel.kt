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
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.keys.DoubleKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.interfaces.queue.CommandQueue
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
    private val aapsLogger: AAPSLogger,
    private val commandQueue: CommandQueue,
    private val profileFunction: ProfileFunction,
    private val preferences: Preferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(AfrezzaDialogUiState())
    val uiState: StateFlow<AfrezzaDialogUiState> = _uiState.asStateFlow()

    sealed class SideEffect {
        data class ShowMessage(val message: String) : SideEffect()
        data object DoseLogged : SideEffect()
        data object OpenWizard : SideEffect()
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

    private fun findAfrezzaIcfg(): ICfg? {
        val afrezzaPeak = InsulinType.OREF_INHALED_AFREZZA.insulinPeakTime
        return insulinManager.insulins.firstOrNull { it.insulinPeakTime == afrezzaPeak }
            ?: insulinManager.insulins.firstOrNull { it.isInhaled }
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
                // Afrezza inhaled cartridges are stored as U100-equivalent IU.
                // cartridge units / 2.0  ->  4U->2.0, 8U->4.0, 12U->6.0.
                // Keep in sync with DataHandlerMobile.doAfrezzaBolus (the watch path).
                val effectiveAmount = units.toDouble() / 2.0
                val logNote = rh.gs(R.string.afrezza_inhaled) + " (${units}U)"
                val bolus = BS(
                    timestamp = now,
                    amount = effectiveAmount,
                    type = BS.Type.NORMAL,
                    notes = logNote,
                    iCfg = iCfg,
                    ids = IDs(pumpId = now)
                )

                persistenceLayer.insertOrUpdateBolus(
                    bolus = bolus,
                    action = Action.BOLUS,
                    source = Sources.AfrezzaDialog,
                    note = logNote
                )

                uel.log(
                    Action.BOLUS,
                    Sources.InsulinDialog,
                    logNote,
                    ValueWithUnit.Insulin(effectiveAmount)
                )

                aapsLogger.info(LTag.UI, "Afrezza cartridge ${units}U logged as ${effectiveAmount}U with ICfg: ${iCfg.insulinLabel}")
                _sideEffect.tryEmit(SideEffect.ShowMessage(rh.gs(R.string.afrezza_logged, units)))
                _uiState.update { it.copy(isLogging = false, showConfirmation = false, showCarbPrompt = true) }
            } catch (e: Exception) {
                aapsLogger.error(LTag.UI, "Failed to log Afrezza dose", e)
                _uiState.update { it.copy(isLogging = false, showConfirmation = false, selectedCartridge = null) }
            }
        }
    }



    fun openWizard() {
        _uiState.update { it.copy(showCarbPrompt = false, selectedCartridge = null) }
        _sideEffect.tryEmit(SideEffect.OpenWizard)
    }

    fun dismissCarbPrompt() {
        _uiState.update { it.copy(showCarbPrompt = false, selectedCartridge = null) }
        _sideEffect.tryEmit(SideEffect.DoseLogged)
    }
}


