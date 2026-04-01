package app.aaps.ui.compose.treatmentDialog

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aaps.core.data.model.TE
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.data.ue.ValueWithUnit
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.insulin.Insulin
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.pump.DetailedBolusInfo
import app.aaps.core.interfaces.pump.defs.determineCorrectBolusStepSize
import app.aaps.core.interfaces.queue.Callback
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.interfaces.utils.HardLimits
import app.aaps.core.objects.constraints.ConstraintObject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.abs

@HiltViewModel
@Stable
class TreatmentDialogViewModel @Inject constructor(
    private val constraintChecker: ConstraintsChecker,
    private val activePlugin: ActivePlugin,
    private val activeInsulin: Insulin,
    private val commandQueue: CommandQueue,
    config: Config,
    private val uel: UserEntryLogger,
    private val persistenceLayer: PersistenceLayer,
    val decimalFormatter: DecimalFormatter,
    private val rh: ResourceHelper,
    private val aapsLogger: AAPSLogger,
    private val profileFunction: ProfileFunction,
    private val hardLimits: HardLimits
) : ViewModel() {

    val uiState: StateFlow<TreatmentDialogUiState>
        field = MutableStateFlow(TreatmentDialogUiState())

    sealed class SideEffect {
        data class ShowDeliveryError(val comment: String) : SideEffect()
        data object ShowNoActionDialog : SideEffect()
    }

    val sideEffect: SharedFlow<SideEffect>
        field = MutableSharedFlow(
            replay = 0,
            extraBufferCapacity = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST
        )

    init {
        val pump = activePlugin.activePump
        val constrainedMax = constraintChecker.getMaxBolusAllowed().value()
        val maxInsulin = if (constrainedMax > 0.0) constrainedMax else hardLimits.maxBolus()
        val maxCarbs = constraintChecker.getMaxCarbsAllowed().value()
        val bolusStep = pump.pumpDescription.bolusStep
        val isAapsClient = config.AAPSCLIENT

        uiState.update {
            TreatmentDialogUiState(
                insulin = 0.0,
                carbs = 0,
                maxInsulin = maxInsulin,
                maxCarbs = maxCarbs,
                bolusStep = bolusStep,
                isAapsClient = isAapsClient
            )
        }
    }

    fun updateInsulin(value: Double) {
        val clamped = value.coerceIn(0.0, uiState.value.maxInsulin)
        uiState.update { it.copy(insulin = clamped) }
    }

    fun updateCarbs(value: Int) {
        val clamped = value.coerceIn(0, uiState.value.maxCarbs)
        uiState.update { it.copy(carbs = clamped) }
    }

    fun hasAction(): Boolean {
        val state = uiState.value
        val insulinAfterConstraints = constraintChecker.applyBolusConstraints(
            ConstraintObject(state.insulin, aapsLogger)
        ).value()
        val carbsAfterConstraints = constraintChecker.applyCarbsConstraints(
            ConstraintObject(state.carbs, aapsLogger)
        ).value()
        return insulinAfterConstraints > 0 || carbsAfterConstraints > 0
    }

    private var confirmedState: TreatmentDialogUiState? = null

    fun buildConfirmationSummary(): List<String> {
        val state = uiState.value
        confirmedState = state
        val lines = mutableListOf<String>()
        val pumpDescription = activePlugin.activePump.pumpDescription

        val insulin = state.insulin
        val insulinAfterConstraints = constraintChecker.applyBolusConstraints(
            ConstraintObject(insulin, aapsLogger)
        ).value()
        val carbs = state.carbs
        val carbsAfterConstraints = constraintChecker.applyCarbsConstraints(
            ConstraintObject(carbs, aapsLogger)
        ).value()

        if (insulinAfterConstraints > 0) {
            lines.add(
                rh.gs(app.aaps.core.ui.R.string.bolus) + ": " +
                    decimalFormatter.toPumpSupportedBolus(insulinAfterConstraints, pumpDescription.bolusStep)
            )
            if (state.isAapsClient) {
                lines.add(rh.gs(app.aaps.core.ui.R.string.bolus_recorded_only))
            }
            if (abs(insulinAfterConstraints - insulin) > pumpDescription.pumpType.determineCorrectBolusStepSize(insulinAfterConstraints)) {
                lines.add(rh.gs(app.aaps.core.ui.R.string.bolus_constraint_applied_warn, insulin, insulinAfterConstraints))
            }
        }

        if (carbsAfterConstraints > 0) {
            lines.add(
                rh.gs(app.aaps.core.ui.R.string.carbs) + ": " +
                    rh.gs(app.aaps.core.objects.R.string.format_carbs, carbsAfterConstraints)
            )
            if (carbsAfterConstraints != carbs) {
                lines.add(rh.gs(app.aaps.ui.R.string.carbs_constraint_applied))
            }
        }

        return lines
    }

    fun confirmAndSave() {
        viewModelScope.launch { confirmAndSaveSuspend() }
    }

    private suspend fun confirmAndSaveSuspend() {
        val state = confirmedState ?: return
        val insulin = state.insulin
        val insulinAfterConstraints = constraintChecker.applyBolusConstraints(
            ConstraintObject(insulin, aapsLogger)
        ).value()
        val carbs = state.carbs
        val carbsAfterConstraints = constraintChecker.applyCarbsConstraints(
            ConstraintObject(carbs, aapsLogger)
        ).value()
        val recordOnlyChecked = state.isAapsClient
        val iCfg = profileFunction.getProfile()?.iCfg ?: activeInsulin.iCfg

        val action = when {
            insulinAfterConstraints == 0.0 -> Action.CARBS
            carbsAfterConstraints == 0     -> Action.BOLUS
            else                           -> Action.TREATMENT
        }

        val detailedBolusInfo = DetailedBolusInfo().also {
            it.eventType = when {
                insulinAfterConstraints == 0.0 -> TE.Type.CARBS_CORRECTION
                carbsAfterConstraints == 0     -> TE.Type.CORRECTION_BOLUS
                else                           -> TE.Type.MEAL_BOLUS
            }
            it.insulin = insulinAfterConstraints
            it.carbs = carbsAfterConstraints.toDouble()
        }

        if (recordOnlyChecked) {
            if (detailedBolusInfo.insulin > 0) {
                viewModelScope.launch {
                    persistenceLayer.insertOrUpdateBolus(
                        bolus = detailedBolusInfo.createBolus(iCfg),
                        action = action,
                        source = Sources.TreatmentDialog,
                        note = if (insulinAfterConstraints != 0.0) rh.gs(app.aaps.core.ui.R.string.record) else ""
                    )
                }
            }
            if (detailedBolusInfo.carbs > 0) {
                viewModelScope.launch {
                    persistenceLayer.insertOrUpdateCarbs(
                        carbs = detailedBolusInfo.createCarbs(),
                        action = action,
                        source = Sources.TreatmentDialog,
                        note = if (carbsAfterConstraints != 0) rh.gs(app.aaps.core.ui.R.string.record) else ""
                    )
                }
            }
        } else {
            if (detailedBolusInfo.insulin > 0) {
                uel.log(
                    action = action,
                    source = Sources.TreatmentDialog,
                    listValues = listOfNotNull(
                        ValueWithUnit.Insulin(insulinAfterConstraints),
                        ValueWithUnit.Gram(carbsAfterConstraints).takeIf { carbsAfterConstraints != 0 }
                    )
                )
                commandQueue.bolus(detailedBolusInfo, object : Callback() {
                    override fun run() {
                        if (!result.success) {
                            sideEffect.tryEmit(SideEffect.ShowDeliveryError(result.comment))
                        }
                    }
                })
            } else {
                if (detailedBolusInfo.carbs > 0) {
                    viewModelScope.launch {
                        persistenceLayer.insertOrUpdateCarbs(
                            carbs = detailedBolusInfo.createCarbs(),
                            action = action,
                            source = Sources.TreatmentDialog
                        )
                    }
                }
            }
        }
    }
}
