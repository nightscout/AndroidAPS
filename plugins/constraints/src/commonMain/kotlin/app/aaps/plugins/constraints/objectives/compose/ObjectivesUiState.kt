package app.aaps.plugins.constraints.objectives.compose

import androidx.compose.runtime.Immutable

@Immutable
data class ObjectivesUiState(
    val objectives: List<ObjectiveUiItem> = emptyList(),
    val isFakeMode: Boolean = false,
    val showDebugControls: Boolean = false,
    val ntpVerification: NtpVerificationState? = null,
    val examSheet: ExamSheetState? = null,
    val learnedSheet: LearnedSheetState? = null,
    val confirmUnstartDialog: Int? = null
)

@Immutable
data class ObjectiveUiItem(
    val index: Int,
    val number: Int,
    val title: String,
    val description: String?,
    val gate: String?,
    val state: ObjectiveState,
    val accomplishedOn: String?,
    val tasks: List<TaskUiItem>,
    val completedTaskCount: Int,
    val totalTaskCount: Int,
    val progress: Float,
    val learned: List<String>,
    val canStart: Boolean
)

enum class ObjectiveState {
    LOCKED,
    NOT_STARTED,
    STARTED,
    ACCOMPLISHED
}

@Immutable
data class TaskUiItem(
    val index: Int,
    val name: String,
    val isCompleted: Boolean,
    val progress: String,
    val hints: List<HintUiItem>,
    val learned: List<String>,
    val type: TaskType
)

enum class TaskType {
    NORMAL,
    EXAM,
    UI_TASK,
    DURATION
}

@Immutable
data class HintUiItem(
    val text: String
)

@Immutable
data class NtpVerificationState(
    val status: String,
    val percent: Int
)

@Immutable
data class ExamSheetState(
    val objectiveIndex: Int,
    val currentTaskIndex: Int,
    val taskName: String,
    val question: String,
    val options: List<ExamOptionUi>,
    val hints: List<HintUiItem>,
    val totalTasks: Int,
    val isAnswered: Boolean,
    val disabledUntil: String?,
    val canAnswer: Boolean,
    val canGoBack: Boolean,
    val canGoNext: Boolean,
    val allCompleted: Boolean
)

@Immutable
data class ExamOptionUi(
    val index: Int,
    val text: String,
    val isCorrect: Boolean,
    val isChecked: Boolean
)

@Immutable
data class LearnedSheetState(
    val objectiveNumber: Int,
    val objectiveTitle: String,
    val items: List<String>
)
