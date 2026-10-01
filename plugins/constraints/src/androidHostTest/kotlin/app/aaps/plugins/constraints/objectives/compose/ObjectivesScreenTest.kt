package app.aaps.plugins.constraints.objectives.compose

import app.aaps.core.interfaces.resources.TextRefIdRegistry
import app.aaps.plugins.constraints.ConstraintsStringIds
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class ObjectivesScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var startLabel: String
    private lateinit var verifyLabel: String
    private lateinit var learnedLabel: String
    private lateinit var expandLabel: String
    private lateinit var unstartLabel: String
    private lateinit var unfinishLabel: String
    private lateinit var hintLabel: String

    @Before
    fun setUp() {
        // What MainApp does at startup: a TextRef.Named is resolved through this registry, so
        // without it every label renders as its raw name.
        TextRefIdRegistry.register("constraints") { name -> ConstraintsStringIds.idOf(name) }
        val ctx = RuntimeEnvironment.getApplication()
        startLabel = ctx.getString(ConstraintsStringIds.idOf("objectives_button_start")!!)
        verifyLabel = ctx.getString(ConstraintsStringIds.idOf("objectives_button_verify")!!)
        learnedLabel = ctx.getString(ConstraintsStringIds.idOf("what_i_ve_learned")!!)
        expandLabel = ctx.getString(ConstraintsStringIds.idOf("objectives_expand")!!)
        unstartLabel = ctx.getString(ConstraintsStringIds.idOf("objectives_button_unstart")!!)
        unfinishLabel = ctx.getString(ConstraintsStringIds.idOf("objectives_button_unfinish")!!)
        hintLabel = ctx.getString(ConstraintsStringIds.idOf("objectives_show_hint")!!)
    }

    private fun objective(
        index: Int,
        state: ObjectiveState,
        title: String,
        description: String? = null,
        gate: String? = null,
        accomplishedOn: String? = null,
        tasks: List<TaskUiItem> = emptyList(),
        completedTaskCount: Int = 0,
        totalTaskCount: Int = 0,
        progress: Float = 0f,
        learned: List<String> = emptyList()
    ) = ObjectiveUiItem(
        index = index,
        number = index + 1,
        title = title,
        description = description,
        gate = gate,
        state = state,
        accomplishedOn = accomplishedOn,
        tasks = tasks,
        completedTaskCount = completedTaskCount,
        totalTaskCount = totalTaskCount,
        progress = progress,
        learned = learned,
        canStart = state == ObjectiveState.NOT_STARTED
    )

    private fun task(
        index: Int,
        name: String,
        isCompleted: Boolean = false,
        progress: String = "",
        hints: List<HintUiItem> = emptyList(),
        type: TaskType = TaskType.NORMAL
    ) = TaskUiItem(
        index = index,
        name = name,
        isCompleted = isCompleted,
        progress = progress,
        hints = hints,
        learned = emptyList(),
        type = type
    )

    private fun setContent(
        state: ObjectivesUiState,
        onOpenExam: (Int, Int) -> Unit = { _, _ -> },
        onInvokeUITask: (Int, Int) -> Unit = { _, _ -> }
    ) {
        compose.setContent {
            MaterialTheme {
                ObjectivesScreen(
                    state = state,
                    onFakeModeToggle = {},
                    onReset = {},
                    onStart = {},
                    onVerify = {},
                    onRequestUnstart = {},
                    onUnfinish = {},
                    onShowLearned = {},
                    onOpenExam = onOpenExam,
                    onInvokeUITask = onInvokeUITask,
                    scrollToIndex = -1,
                    onScrollHandled = {}
                )
            }
        }
    }

    @Test
    fun notStartedObjective_showsTitleDescriptionGateAndStartButton() {
        setContent(
            ObjectivesUiState(
                objectives = listOf(
                    objective(
                        index = 0,
                        state = ObjectiveState.NOT_STARTED,
                        title = "Objective 1",
                        description = "Set up visualization",
                        gate = "Verify your settings"
                    )
                )
            )
        )

        compose.onNodeWithText("Objective 1").assertIsDisplayed()
        compose.onNodeWithText("Set up visualization").assertIsDisplayed()
        compose.onNodeWithText("Verify your settings").assertIsDisplayed()
        compose.onNodeWithText(startLabel).assertIsDisplayed()
    }

    @Test
    fun startedObjective_showsTaskProgressAndVerifyButton() {
        setContent(
            ObjectivesUiState(
                objectives = listOf(
                    objective(
                        index = 1,
                        state = ObjectiveState.STARTED,
                        title = "Objective 2",
                        tasks = listOf(
                            TaskUiItem(
                                index = 0,
                                name = "Enter password",
                                isCompleted = false,
                                progress = "Not done",
                                hints = emptyList(),
                                learned = emptyList(),
                                type = TaskType.NORMAL
                            )
                        ),
                        completedTaskCount = 1,
                        totalTaskCount = 2,
                        progress = 0.5f
                    )
                )
            )
        )

        compose.onNodeWithText("Objective 2").assertIsDisplayed()
        compose.onNodeWithText("Enter password").assertIsDisplayed()
        compose.onNodeWithText("1/2").assertIsDisplayed()
        compose.onNodeWithText(verifyLabel).assertIsDisplayed()
    }

    @Test
    fun accomplishedObjective_withLearned_showsLearnedButtonAndExpandToggle() {
        setContent(
            ObjectivesUiState(
                objectives = listOf(
                    objective(
                        index = 0,
                        state = ObjectiveState.ACCOMPLISHED,
                        title = "Objective 1",
                        accomplishedOn = "Accomplished on 1.1.2026",
                        learned = listOf("I learned looping")
                    )
                )
            )
        )

        compose.onNodeWithText("Objective 1").assertIsDisplayed()
        compose.onNodeWithText("Accomplished on 1.1.2026").assertIsDisplayed()
        compose.onNodeWithText(learnedLabel).assertIsDisplayed()
        compose.onNodeWithText(expandLabel).assertIsDisplayed()
    }

    @Test
    fun lockedObjective_showsTitleButHidesStartButton() {
        setContent(
            ObjectivesUiState(
                objectives = listOf(
                    objective(
                        index = 2,
                        state = ObjectiveState.LOCKED,
                        title = "Objective 3",
                        description = "Locked objective"
                    )
                )
            )
        )

        compose.onNodeWithText("Objective 3").assertIsDisplayed()
        compose.onNodeWithText(startLabel).assertDoesNotExist()
    }

    // The gate that stops an objective being marked done before its tasks are: Verify stays
    // disabled while progress < 1. Losing this would let a user advance the loop early.

    @Test
    fun startedObjective_withIncompleteTasks_disablesVerify() {
        setContent(
            ObjectivesUiState(
                objectives = listOf(
                    objective(
                        index = 0,
                        state = ObjectiveState.STARTED,
                        title = "Objective 1",
                        tasks = listOf(task(index = 0, name = "Task A")),
                        completedTaskCount = 1,
                        totalTaskCount = 2,
                        progress = 0.5f
                    )
                )
            )
        )

        compose.onNodeWithText(verifyLabel).assertIsNotEnabled()
    }

    @Test
    fun startedObjective_withAllTasksComplete_enablesVerify() {
        setContent(
            ObjectivesUiState(
                objectives = listOf(
                    objective(
                        index = 0,
                        state = ObjectiveState.STARTED,
                        title = "Objective 1",
                        tasks = listOf(task(index = 0, name = "Task A", isCompleted = true)),
                        completedTaskCount = 2,
                        totalTaskCount = 2,
                        progress = 1f
                    )
                )
            )
        )

        compose.onNodeWithText(verifyLabel).assertIsEnabled()
    }

    @Test
    fun startedObjective_inFakeMode_enablesVerifyEvenWhenIncomplete() {
        setContent(
            ObjectivesUiState(
                isFakeMode = true,
                objectives = listOf(
                    objective(
                        index = 0,
                        state = ObjectiveState.STARTED,
                        title = "Objective 1",
                        tasks = listOf(task(index = 0, name = "Task A")),
                        completedTaskCount = 0,
                        totalTaskCount = 2,
                        progress = 0f
                    )
                )
            )
        )

        compose.onNodeWithText(verifyLabel).assertIsEnabled()
    }

    @Test
    fun startedObjective_showsUnstartButton() {
        setContent(
            ObjectivesUiState(
                objectives = listOf(
                    objective(
                        index = 0,
                        state = ObjectiveState.STARTED,
                        title = "Objective 1",
                        tasks = listOf(task(index = 0, name = "Task A"))
                    )
                )
            )
        )

        compose.onNodeWithText(unstartLabel).assertIsDisplayed()
    }

    // Click routing. An exam row must open the exam sheet and a UI task row must run its code,
    // both carrying the objective index and the task index the row was built from.

    @Test
    fun examTaskRow_click_opensExamWithObjectiveAndTaskIndex() {
        var opened: Pair<Int, Int>? = null
        setContent(
            state = ObjectivesUiState(
                objectives = listOf(
                    objective(
                        index = 3,
                        state = ObjectiveState.STARTED,
                        title = "Objective 4",
                        tasks = listOf(
                            task(index = 0, name = "First task"),
                            task(index = 1, name = "Exam task", type = TaskType.EXAM)
                        )
                    )
                )
            ),
            onOpenExam = { objectiveIndex, taskIndex -> opened = objectiveIndex to taskIndex }
        )

        compose.onNodeWithText("Exam task").performClick()

        assertThat(opened).isEqualTo(3 to 1)
    }

    @Test
    fun uiTaskRow_click_invokesUiTaskWithObjectiveAndTaskIndex() {
        var invoked: Pair<Int, Int>? = null
        setContent(
            state = ObjectivesUiState(
                objectives = listOf(
                    objective(
                        index = 2,
                        state = ObjectiveState.STARTED,
                        title = "Objective 3",
                        tasks = listOf(
                            task(index = 0, name = "First task"),
                            task(index = 1, name = "Password task", type = TaskType.UI_TASK)
                        )
                    )
                )
            ),
            onInvokeUITask = { objectiveIndex, taskIndex -> invoked = objectiveIndex to taskIndex }
        )

        compose.onNodeWithText("Password task").performClick()

        assertThat(invoked).isEqualTo(2 to 1)
    }

    @Test
    fun completedExamTaskRow_click_doesNothing() {
        var opened: Pair<Int, Int>? = null
        setContent(
            state = ObjectivesUiState(
                objectives = listOf(
                    objective(
                        index = 0,
                        state = ObjectiveState.STARTED,
                        title = "Objective 1",
                        tasks = listOf(
                            task(index = 0, name = "Answered exam", isCompleted = true, type = TaskType.EXAM)
                        )
                    )
                )
            ),
            onOpenExam = { objectiveIndex, taskIndex -> opened = objectiveIndex to taskIndex }
        )

        compose.onNodeWithText("Answered exam").performClick()

        assertThat(opened).isNull()
    }

    @Test
    fun normalTaskRow_click_doesNothing() {
        var invoked: Pair<Int, Int>? = null
        var opened: Pair<Int, Int>? = null
        setContent(
            state = ObjectivesUiState(
                objectives = listOf(
                    objective(
                        index = 0,
                        state = ObjectiveState.STARTED,
                        title = "Objective 1",
                        tasks = listOf(task(index = 0, name = "Plain task"))
                    )
                )
            ),
            onOpenExam = { objectiveIndex, taskIndex -> opened = objectiveIndex to taskIndex },
            onInvokeUITask = { objectiveIndex, taskIndex -> invoked = objectiveIndex to taskIndex }
        )

        compose.onNodeWithText("Plain task").performClick()

        assertThat(opened).isNull()
        assertThat(invoked).isNull()
    }

    // Hints are hidden behind a toggle and only offered while a task is not done.

    @Test
    fun incompleteTaskWithHints_showsHintToggle_andRevealsHintOnClick() {
        setContent(
            ObjectivesUiState(
                objectives = listOf(
                    objective(
                        index = 0,
                        state = ObjectiveState.STARTED,
                        title = "Objective 1",
                        tasks = listOf(
                            task(index = 0, name = "Task A", hints = listOf(HintUiItem("Read the docs")))
                        )
                    )
                )
            )
        )

        compose.onNodeWithText("Read the docs").assertDoesNotExist()
        compose.onNodeWithText(hintLabel).performClick()
        compose.onNodeWithText("Read the docs").assertIsDisplayed()
    }

    @Test
    fun completedTaskWithHints_hidesHintToggle() {
        setContent(
            ObjectivesUiState(
                objectives = listOf(
                    objective(
                        index = 0,
                        state = ObjectiveState.STARTED,
                        title = "Objective 1",
                        tasks = listOf(
                            task(index = 0, name = "Task A", isCompleted = true, hints = listOf(HintUiItem("Read the docs")))
                        )
                    )
                )
            )
        )

        compose.onNodeWithText(hintLabel).assertDoesNotExist()
    }

    // The fake time and progress switch must never reach a normal build.

    @Test
    fun debugControls_hiddenByDefault() {
        setContent(
            ObjectivesUiState(
                objectives = listOf(objective(index = 0, state = ObjectiveState.NOT_STARTED, title = "Objective 1"))
            )
        )

        compose.onNodeWithText("Enable fake time and progress").assertDoesNotExist()
        compose.onNodeWithText("Reset").assertDoesNotExist()
    }

    @Test
    fun debugControls_shownWhenEnabled() {
        setContent(
            ObjectivesUiState(
                showDebugControls = true,
                objectives = listOf(objective(index = 0, state = ObjectiveState.NOT_STARTED, title = "Objective 1"))
            )
        )

        compose.onNodeWithText("Enable fake time and progress").assertIsDisplayed()
        compose.onNodeWithText("Reset").assertIsDisplayed()
    }

    // An accomplished objective keeps its description and the unfinish button behind the toggle.

    @Test
    fun accomplishedObjective_hidesDescriptionAndUnfinishUntilExpanded() {
        setContent(
            ObjectivesUiState(
                objectives = listOf(
                    objective(
                        index = 0,
                        state = ObjectiveState.ACCOMPLISHED,
                        title = "Objective 1",
                        description = "Set up visualization"
                    )
                )
            )
        )

        compose.onNodeWithText("Set up visualization").assertDoesNotExist()
        compose.onNodeWithText(unfinishLabel).assertDoesNotExist()

        compose.onNodeWithText(expandLabel).performClick()

        compose.onNodeWithText("Set up visualization").assertIsDisplayed()
        compose.onNodeWithText(unfinishLabel).assertIsDisplayed()
    }

    @Test
    fun accomplishedObjective_withoutLearned_hidesLearnedButton() {
        setContent(
            ObjectivesUiState(
                objectives = listOf(
                    objective(
                        index = 0,
                        state = ObjectiveState.ACCOMPLISHED,
                        title = "Objective 1",
                        learned = emptyList()
                    )
                )
            )
        )

        compose.onNodeWithText(learnedLabel).assertDoesNotExist()
    }
}
