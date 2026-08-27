package app.aaps.plugins.constraints.objectives.compose

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import app.aaps.plugins.constraints.R
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

    @Before
    fun setUp() {
        val ctx = RuntimeEnvironment.getApplication()
        startLabel = ctx.getString(R.string.objectives_button_start)
        verifyLabel = ctx.getString(R.string.objectives_button_verify)
        learnedLabel = ctx.getString(R.string.what_i_ve_learned)
        expandLabel = ctx.getString(R.string.objectives_expand)
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

    private fun setContent(state: ObjectivesUiState) {
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
                    onOpenExam = { _, _ -> },
                    onInvokeUITask = { _, _, _ -> },
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
}
