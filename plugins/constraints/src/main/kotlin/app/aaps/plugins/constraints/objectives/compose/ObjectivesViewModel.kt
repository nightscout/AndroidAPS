package app.aaps.plugins.constraints.objectives.compose

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aaps.core.data.time.T
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.data.ue.ValueWithUnit
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.receivers.ReceiverStatusStore
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventSWUpdate
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.BooleanNonKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.plugins.constraints.R
import app.aaps.plugins.constraints.objectives.ObjectivesPlugin
import app.aaps.plugins.constraints.objectives.SntpClient
import app.aaps.plugins.constraints.objectives.events.EventObjectivesUpdateGui
import app.aaps.plugins.constraints.objectives.objectives.Objective
import app.aaps.plugins.constraints.objectives.objectives.Objective.ExamTask
import app.aaps.plugins.constraints.objectives.objectives.Objective.UITask
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ObjectivesViewModel @Inject constructor(
    private val objectivesPlugin: ObjectivesPlugin,
    private val rxBus: RxBus,
    private val rh: ResourceHelper,
    private val dateUtil: DateUtil,
    private val sntpClient: SntpClient,
    private val receiverStatusStore: ReceiverStatusStore,
    private val aapsLogger: AAPSLogger,
    private val uel: UserEntryLogger,
    private val preferences: Preferences
) : ViewModel() {

    private val scope get() = viewModelScope

    private val _uiState = MutableStateFlow(ObjectivesUiState())
    val uiState: StateFlow<ObjectivesUiState> = _uiState.asStateFlow()

    /** Index to auto-scroll to after state update */
    private val _scrollToIndex = MutableStateFlow(-1)
    val scrollToIndex: StateFlow<Int> = _scrollToIndex.asStateFlow()

    /** Snackbar message to show */
    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    fun onSnackbarShown() {
        _snackbarMessage.value = null
    }

    init {
        rxBus.toFlow(EventObjectivesUpdateGui::class.java)
            .onEach { updateState() }
            .launchIn(scope)

        // Observe preference changes that affect task completion
        val objectivesPreferenceKeys = listOf(
            BooleanNonKey.ObjectivesBgIsAvailableInNs,
            BooleanNonKey.ObjectivesPumpStatusIsAvailableInNS,
            BooleanNonKey.ObjectivesProfileSwitchUsed,
            BooleanNonKey.ObjectivesDisconnectUsed,
            BooleanNonKey.ObjectivesReconnectUsed,
            BooleanNonKey.ObjectivesTempTargetUsed,
            BooleanNonKey.ObjectivesLoopUsed,
            BooleanNonKey.ObjectivesScaleUsed
        )
        objectivesPreferenceKeys
            .map { key -> preferences.observe(key).drop(1) } // drop initial value
            .merge()
            .onEach { updateState() }
            .launchIn(scope)

        updateState()
        startUpdateTimer()
    }

    private fun startUpdateTimer() {
        scope.launch {
            while (isActive) {
                delay(60_000)
                updateState()
            }
        }
    }

    fun updateState() {
        val objectives = objectivesPlugin.objectives.mapIndexed { index, objective ->
            val tasks = objective.tasks
                .filter { !it.shouldBeIgnored() }
                .mapIndexed { taskIndex, task ->
                    TaskUiItem(
                        index = taskIndex,
                        name = rh.gs(task.task),
                        isCompleted = task.isCompleted(),
                        progress = task.progress,
                        hints = task.hints.map { HintUiItem(rh.gs(it.hint)) },
                        learned = task.learned.map { rh.gs(it.learned) },
                        type = when (task) {
                            is ExamTask                      -> TaskType.EXAM
                            is UITask                        -> TaskType.UI_TASK
                            is Objective.MinimumDurationTask -> TaskType.DURATION
                            else                             -> TaskType.NORMAL
                        }
                    )
                }
            val completedCount = tasks.count { it.isCompleted }
            val totalCount = tasks.size
            val state = when {
                objective.isAccomplished                                   -> ObjectiveState.ACCOMPLISHED
                objective.isStarted                                        -> ObjectiveState.STARTED
                index == 0 || objectivesPlugin.allPriorAccomplished(index) -> ObjectiveState.NOT_STARTED
                else                                                       -> ObjectiveState.LOCKED
            }

            ObjectiveUiItem(
                index = index,
                number = index + 1,
                title = rh.gs(R.string.nth_objective, index + 1),
                description = if (objective.objective != 0) rh.gs(objective.objective) else null,
                gate = if (objective.gate != 0) rh.gs(objective.gate) else null,
                state = state,
                accomplishedOn = if (objective.isAccomplished) rh.gs(R.string.accomplished, dateUtil.dateAndTimeString(objective.accomplishedOn)) else null,
                tasks = tasks,
                completedTaskCount = completedCount,
                totalTaskCount = totalCount,
                progress = if (totalCount > 0) completedCount.toFloat() / totalCount else 0f,
                learned = tasks.flatMap { it.learned },
                canStart = state == ObjectiveState.NOT_STARTED
            )
        }

        _uiState.update { it.copy(objectives = objectives) }
    }

    fun onDismissNtpDialog() {
        _uiState.value = _uiState.value.copy(ntpVerification = null)
    }

    fun onFakeModeToggle(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(isFakeMode = enabled)
        updateState()
    }

    fun onReset() {
        objectivesPlugin.reset()
        updateState()
        scrollToCurrentObjective()
    }

    fun onStart(objectiveIndex: Int) {
        val objective = objectivesPlugin.objectives[objectiveIndex]
        receiverStatusStore.updateNetworkStatus()
        if (_uiState.value.isFakeMode) {
            objective.startedOn = dateUtil.now()
            updateState()
            scrollToCurrentObjective()
            rxBus.send(EventSWUpdate(false))
        } else {
            scope.launch {
                val result = ntpVerify()
                if (!result.networkConnected) {
                    showNtpError(rh.gs(R.string.notconnected))
                } else if (result.success) {
                    objective.startedOn = result.time
                    showNtpSuccess()
                    updateState()
                    scrollToCurrentObjective()
                    rxBus.send(EventSWUpdate(false))
                } else {
                    showNtpError(rh.gs(R.string.failedretrievetime))
                }
            }
        }
    }

    fun onVerify(objectiveIndex: Int) {
        val objective = objectivesPlugin.objectives[objectiveIndex]
        receiverStatusStore.updateNetworkStatus()
        if (_uiState.value.isFakeMode) {
            // -1000ms: Objective.isAccomplished uses strict `<` comparison with dateUtil.now(),
            // so the timestamp must be strictly in the past for the UI to update immediately.
            objective.accomplishedOn = dateUtil.now() - 1000
            updateState()
            scrollToCurrentObjective()
            rxBus.send(EventSWUpdate(false))
        } else {
            scope.launch {
                val result = ntpVerify()
                if (!result.networkConnected) {
                    showNtpError(rh.gs(R.string.notconnected))
                } else if (result.success) {
                    if (objective.isCompleted(result.time)) {
                        showNtpSuccess()
                        // -1000ms: see isFakeMode branch above for rationale
                        objective.accomplishedOn = dateUtil.now() - 1000
                        updateState()
                        scrollToCurrentObjective()
                        rxBus.send(EventSWUpdate(false))
                    } else {
                        showNtpError(rh.gs(R.string.requirementnotmet))
                    }
                } else {
                    showNtpError(rh.gs(R.string.failedretrievetime))
                }
            }
        }
    }

    private suspend fun ntpVerify(): SntpClient.NtpResult {
        _uiState.value = _uiState.value.copy(
            ntpVerification = NtpVerificationState(
                status = rh.gs(app.aaps.core.ui.R.string.timedetection),
                percent = 30
            )
        )
        return sntpClient.ntpTime(receiverStatusStore.isKnownNetworkStatus && receiverStatusStore.isConnected)
    }

    private suspend fun showNtpSuccess() {
        _uiState.value = _uiState.value.copy(
            ntpVerification = NtpVerificationState(
                status = rh.gs(app.aaps.core.ui.R.string.success),
                percent = 100
            )
        )
        delay(1000)
        _uiState.value = _uiState.value.copy(ntpVerification = null)
    }

    private fun showNtpError(message: String) {
        _uiState.value = _uiState.value.copy(ntpVerification = null)
        _snackbarMessage.value = message
    }

    fun onRequestUnstart(objectiveIndex: Int) {
        _uiState.value = _uiState.value.copy(confirmUnstartDialog = objectiveIndex)
    }

    fun onConfirmUnstart(objectiveIndex: Int) {
        val objective = objectivesPlugin.objectives[objectiveIndex]
        uel.log(
            action = Action.OBJECTIVE_UNSTARTED,
            source = Sources.Objectives,
            value = ValueWithUnit.SimpleInt(objectiveIndex + 1)
        )
        objective.startedOn = 0
        _uiState.value = _uiState.value.copy(confirmUnstartDialog = null)
        updateState()
        scrollToCurrentObjective()
        rxBus.send(EventSWUpdate(false))
    }

    fun onDismissUnstartDialog() {
        _uiState.value = _uiState.value.copy(confirmUnstartDialog = null)
    }

    fun onUnfinish(objectiveIndex: Int) {
        val objective = objectivesPlugin.objectives[objectiveIndex]
        objective.accomplishedOn = 0
        updateState()
        scrollToCurrentObjective()
        rxBus.send(EventSWUpdate(false))
    }

    fun onShowLearned(objectiveIndex: Int) {
        val state = _uiState.value
        val item = state.objectives.getOrNull(objectiveIndex) ?: return
        _uiState.value = state.copy(
            learnedSheet = LearnedSheetState(
                objectiveNumber = item.number,
                objectiveTitle = item.description ?: item.title,
                items = item.learned
            )
        )
    }

    fun onDismissLearnedSheet() {
        _uiState.value = _uiState.value.copy(learnedSheet = null)
    }

    // Exam sheet
    fun onOpenExam(objectiveIndex: Int, taskIndex: Int) {
        openExamTask(objectiveIndex, taskIndex)
    }

    private fun openExamTask(objectiveIndex: Int, taskIndex: Int) {
        val objective = objectivesPlugin.objectives[objectiveIndex]
        val visibleTasks = objective.tasks.filter { !it.shouldBeIgnored() }
        val task = visibleTasks[taskIndex] as? ExamTask ?: return
        _uiState.value = _uiState.value.copy(
            examSheet = ExamSheetState(
                objectiveIndex = objectiveIndex,
                currentTaskIndex = taskIndex,
                taskName = rh.gs(task.task),
                question = if (task.question != 0) rh.gs(task.question) else "",
                options = task.options.mapIndexed { i, option ->
                    ExamOptionUi(
                        index = i,
                        text = rh.gs(option.option),
                        isCorrect = option.isCorrect,
                        isChecked = if (task.answered) option.isCorrect else false
                    )
                },
                hints = task.hints.map { HintUiItem(rh.gs(it.hint)) },
                totalTasks = visibleTasks.size,
                isAnswered = task.answered,
                disabledUntil = if (!task.isEnabledAnswer()) rh.gs(R.string.answerdisabledto, dateUtil.timeString(task.disabledTo)) else null,
                canAnswer = !task.answered && task.isEnabledAnswer(),
                canGoBack = taskIndex > 0,
                canGoNext = taskIndex < visibleTasks.size - 1,
                allCompleted = objective.isCompleted
            )
        )
    }

    fun onExamOptionToggle(optionIndex: Int) {
        val examSheet = _uiState.value.examSheet ?: return
        if (examSheet.isAnswered) return
        val updatedOptions = examSheet.options.map { option ->
            if (option.index == optionIndex) option.copy(isChecked = !option.isChecked) else option
        }
        _uiState.value = _uiState.value.copy(examSheet = examSheet.copy(options = updatedOptions))
    }

    fun onExamVerify() {
        val examSheet = _uiState.value.examSheet ?: return
        val objective = objectivesPlugin.objectives[examSheet.objectiveIndex]
        val visibleTasks = objective.tasks.filter { !it.shouldBeIgnored() }
        val task = visibleTasks[examSheet.currentTaskIndex] as? ExamTask ?: return

        val currentOptions = examSheet.options
        var allCorrect = true
        for (option in currentOptions) {
            if (option.isChecked != option.isCorrect) {
                allCorrect = false
                break
            }
        }
        task.answered = allCorrect
        if (!allCorrect) {
            task.disabledTo = dateUtil.now() + T.hours(1).msecs()
            _snackbarMessage.value = rh.gs(R.string.wronganswer)
        } else {
            task.disabledTo = 0
        }
        openExamTask(examSheet.objectiveIndex, examSheet.currentTaskIndex)
        rxBus.send(EventObjectivesUpdateGui())
    }

    fun onExamReset() {
        val examSheet = _uiState.value.examSheet ?: return
        val objective = objectivesPlugin.objectives[examSheet.objectiveIndex]
        val visibleTasks = objective.tasks.filter { !it.shouldBeIgnored() }
        val task = visibleTasks[examSheet.currentTaskIndex] as? ExamTask ?: return
        task.answered = false
        openExamTask(examSheet.objectiveIndex, examSheet.currentTaskIndex)
        rxBus.send(EventObjectivesUpdateGui())
    }

    fun onExamNavigate(direction: Int) {
        val examSheet = _uiState.value.examSheet ?: return
        val newIndex = examSheet.currentTaskIndex + direction
        if (newIndex in 0 until examSheet.totalTasks) {
            openExamTask(examSheet.objectiveIndex, newIndex)
        }
    }

    fun onExamNextUnanswered() {
        val examSheet = _uiState.value.examSheet ?: return
        val objective = objectivesPlugin.objectives[examSheet.objectiveIndex]
        val visibleTasks = objective.tasks.filter { !it.shouldBeIgnored() }
        // Search from current+1 to end, then wrap from 0
        for (i in (examSheet.currentTaskIndex + 1) until visibleTasks.size) {
            if (!visibleTasks[i].isCompleted()) {
                openExamTask(examSheet.objectiveIndex, i)
                return
            }
        }
        for (i in 0..examSheet.currentTaskIndex) {
            if (!visibleTasks[i].isCompleted()) {
                openExamTask(examSheet.objectiveIndex, i)
                return
            }
        }
    }

    fun onDismissExamSheet() {
        _uiState.value = _uiState.value.copy(examSheet = null)
    }

    /** Invoke UITask code (e.g. password check) */
    fun onInvokeUITask(context: Context, objectiveIndex: Int, taskIndex: Int) {
        val objective = objectivesPlugin.objectives[objectiveIndex]
        val visibleTasks = objective.tasks.filter { !it.shouldBeIgnored() }
        val task = visibleTasks[taskIndex] as? UITask ?: return
        task.code.invoke(context, task, { updateState() }) { message ->
            _snackbarMessage.value = message
        }
    }

    fun scrollToCurrentObjective() {
        for (i in objectivesPlugin.objectives.indices) {
            val obj = objectivesPlugin.objectives[i]
            if (!obj.isAccomplished) {
                _scrollToIndex.value = i
                return
            }
        }
    }

    fun onScrollHandled() {
        _scrollToIndex.value = -1
    }
}
