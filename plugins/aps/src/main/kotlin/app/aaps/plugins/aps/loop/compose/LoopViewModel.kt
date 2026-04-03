package app.aaps.plugins.aps.loop.compose

import androidx.compose.runtime.Immutable
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventLoopUpdateGui
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.keys.BooleanNonKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.objects.constraints.ConstraintObject
import app.aaps.plugins.aps.R
import app.aaps.plugins.aps.loop.events.EventLoopSetLastRunGui
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

@Immutable
data class LoopUiState(
    val lastRun: String = "",
    val source: String = "",
    val request: String = "",
    val constraintsProcessed: String = "",
    val constraints: String = "",
    val tbrRequestTime: String = "",
    val tbrExecutionTime: String = "",
    val tbrSetByPump: String = "",
    val smbRequestTime: String = "",
    val smbExecutionTime: String = "",
    val smbSetByPump: String = "",
    val statusMessage: String = "",
    val isRefreshing: Boolean = false
)

class LoopViewModel(
    private val loop: Loop,
    private val rxBus: RxBus,
    private val rh: ResourceHelper,
    private val dateUtil: DateUtil,
    private val decimalFormatter: DecimalFormatter,
    private val aapsLogger: AAPSLogger,
    private val preferences: Preferences,
    private val scope: CoroutineScope
) {

    private val _uiState = MutableStateFlow(LoopUiState())
    val uiState: StateFlow<LoopUiState> = _uiState.asStateFlow()

    init {
        rxBus.toFlow(EventLoopUpdateGui::class.java)
            .onEach { updateState() }
            .launchIn(scope)

        rxBus.toFlow(EventLoopSetLastRunGui::class.java)
            .onEach { event ->
                _uiState.value = LoopUiState(statusMessage = event.text)
            }
            .launchIn(scope)

        updateState()
        preferences.put(BooleanNonKey.ObjectivesLoopUsed, true)
    }

    fun onRefresh() {
        _uiState.value = _uiState.value.copy(isRefreshing = true)
        scope.launch {
            loop.invoke("Loop pull-to-refresh", true)
        }
    }

    private fun updateState() {
        val lastRun = loop.lastRun
        if (lastRun == null) {
            _uiState.value = LoopUiState(
                statusMessage = rh.gs(app.aaps.core.ui.R.string.not_available_full)
            )
            return
        }

        var constraints =
            lastRun.constraintsProcessed?.let { constraintsProcessed ->
                val allConstraints = ConstraintObject(0.0, aapsLogger)
                constraintsProcessed.rateConstraint?.let { allConstraints.copyReasons(it) }
                constraintsProcessed.smbConstraint?.let { allConstraints.copyReasons(it) }
                allConstraints.getMostLimitedReasons()
            } ?: ""
        constraints += loop.closedLoopEnabled?.getReasons() ?: ""

        _uiState.value = LoopUiState(
            lastRun = dateUtil.dateAndTimeString(lastRun.lastAPSRun),
            source = lastRun.source ?: "",
            request = lastRun.request?.resultAsString() ?: "",
            constraintsProcessed = lastRun.constraintsProcessed?.resultAsString() ?: "",
            constraints = constraints,
            tbrRequestTime = dateUtil.dateAndTimeAndSecondsString(lastRun.lastTBRRequest),
            tbrExecutionTime = dateUtil.dateAndTimeAndSecondsString(lastRun.lastTBREnact),
            tbrSetByPump = lastRun.tbrSetByPump?.toPlainText() ?: "",
            smbRequestTime = dateUtil.dateAndTimeAndSecondsString(lastRun.lastSMBRequest),
            smbExecutionTime = dateUtil.dateAndTimeAndSecondsString(lastRun.lastSMBEnact),
            smbSetByPump = lastRun.smbSetByPump?.toPlainText() ?: "",
            isRefreshing = false
        )
    }

    private fun PumpEnactResult.toPlainText(): String {
        var ret = rh.gs(app.aaps.core.ui.R.string.success) + ": " + success
        if (queued) {
            ret = rh.gs(app.aaps.core.ui.R.string.waitingforpumpresult)
        } else if (enacted) {
            when {
                bolusDelivered > 0         -> {
                    ret += "\n" + rh.gs(app.aaps.core.ui.R.string.enacted) + ": " + enacted
                    if (comment.isNotEmpty()) ret += "\n" + rh.gs(app.aaps.core.ui.R.string.comment) + ": " + comment
                    ret += "\n" + rh.gs(app.aaps.core.ui.R.string.smb_shortname) + ": " + bolusDelivered + " " + rh.gs(app.aaps.core.ui.R.string.insulin_unit_shortname)
                }

                isTempCancel               -> {
                    ret += "\n" + rh.gs(app.aaps.core.ui.R.string.enacted) + ": " + enacted
                    ret += "\n" + rh.gs(app.aaps.core.ui.R.string.comment) + ": " + comment +
                        "\n" + rh.gs(app.aaps.core.ui.R.string.cancel_temp)
                }

                isPercent && percent != -1 -> {
                    ret += "\n" + rh.gs(app.aaps.core.ui.R.string.enacted) + ": " + enacted
                    if (comment.isNotEmpty()) ret += "\n" + rh.gs(app.aaps.core.ui.R.string.comment) + ": " + comment
                    ret += "\n" + rh.gs(app.aaps.core.ui.R.string.duration) + ": " + duration + " min"
                    ret += "\n" + rh.gs(app.aaps.core.ui.R.string.percent) + ": " + percent + "%"
                }

                absolute != -1.0           -> {
                    ret += "\n" + rh.gs(app.aaps.core.ui.R.string.enacted) + ": " + enacted
                    if (comment.isNotEmpty()) ret += "\n" + rh.gs(app.aaps.core.ui.R.string.comment) + ": " + comment
                    ret += "\n" + rh.gs(app.aaps.core.ui.R.string.duration) + ": " + duration + " min"
                    ret += "\n" + rh.gs(app.aaps.core.ui.R.string.absolute) + ": " + decimalFormatter.to2Decimal(absolute) + " U/h"
                }
            }
        } else {
            if (comment.isNotEmpty()) ret += "\n" + rh.gs(app.aaps.core.ui.R.string.comment) + ": " + comment
        }
        return ret
    }
}
