package app.aaps.pump.eopatch.compose

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.aaps.core.ui.compose.LocalSnackbarHostState
import app.aaps.core.ui.compose.pump.PumpAction
import app.aaps.core.ui.compose.pump.PumpOverviewUiState
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import app.aaps.core.ui.R as CoreUiR

/**
 * Covers [EopatchOverviewScreen], and in particular the guard it puts in front of suspending and
 * resuming insulin.
 *
 * The screen takes the actions the view model offers and REPLACES the click handler on the suspend
 * and resume ones, so that pressing them opens a confirmation rather than doing the thing. Both
 * start or stop insulin going into someone, so a single stray tap must not be enough. That rewiring
 * is done by matching the action's label against a string resource - quiet to write, quiet to break,
 * and nothing else would notice if it stopped matching.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class EopatchOverviewScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val viewModel: EopatchOverviewViewModel = mock()
    private val context: Context get() = RuntimeEnvironment.getApplication()

    private val suspendLabel: String get() = context.getString(CoreUiR.string.pump_suspend)
    private val resumeLabel: String get() = context.getString(CoreUiR.string.pump_resume)
    private val confirmLabel: String get() = context.getString(CoreUiR.string.confirm)
    private val cancelLabel: String get() = context.getString(CoreUiR.string.cancel)

    /** Counts the clicks that would have reached the view model's own handler. */
    private var rawSuspendClicks = 0
    private var rawResumeClicks = 0
    private var otherClicks = 0

    private fun show(vararg actions: PumpAction) {
        whenever(viewModel.uiState).thenReturn(MutableStateFlow(PumpOverviewUiState(primaryActions = actions.toList())))
        whenever(viewModel.events).thenReturn(MutableSharedFlow())
        whenever(viewModel.getSuspendDialogText()).thenReturn("Suspend delivery?")
        compose.setContent {
            CompositionLocalProvider(LocalSnackbarHostState provides SnackbarHostState()) {
                MaterialTheme { EopatchOverviewScreen(viewModel = viewModel) }
            }
        }
    }

    private fun suspendAction() = PumpAction(label = suspendLabel, onClick = { rawSuspendClicks++ })
    private fun resumeAction() = PumpAction(label = resumeLabel, onClick = { rawResumeClicks++ })

    // ---- suspending ----

    /** Pressing suspend must ask, not suspend. */
    @Test
    fun pressingSuspendOpensAConfirmationInsteadOfSuspending() {
        show(suspendAction())

        compose.onNodeWithText(suspendLabel).performClick()

        compose.onNodeWithText("Suspend delivery?").assertIsDisplayed()
        assertThat(rawSuspendClicks).isEqualTo(0)
    }

    @Test
    fun dismissingTheSuspendConfirmationSuspendsNothing() {
        show(suspendAction())
        compose.onNodeWithText(suspendLabel).performClick()

        compose.onNodeWithText(cancelLabel).performClick()

        compose.onNodeWithText("Suspend delivery?").assertDoesNotExist()
        assertThat(rawSuspendClicks).isEqualTo(0)
    }

    /** Confirming moves on to choosing how long, still without suspending anything yet. */
    @Test
    fun confirmingTheSuspendLeadsToChoosingTheDuration() {
        show(suspendAction())
        compose.onNodeWithText(suspendLabel).performClick()

        compose.onNodeWithText(confirmLabel).performClick()

        compose.onNodeWithText("Suspend delivery?").assertDoesNotExist()
        assertThat(rawSuspendClicks).isEqualTo(0)
    }

    // ---- resuming ----

    @Test
    fun pressingResumeOpensAConfirmationInsteadOfResuming() {
        show(resumeAction())

        compose.onNodeWithText(resumeLabel).performClick()

        verify(viewModel, never()).resumeBasal()
        assertThat(rawResumeClicks).isEqualTo(0)
    }

    /** Only after the confirmation does insulin actually start again. */
    @Test
    fun confirmingTheResumeDialogResumesDelivery() {
        show(resumeAction())
        compose.onNodeWithText(resumeLabel).performClick()

        compose.onNodeWithText(confirmLabel).performClick()

        verify(viewModel).resumeBasal()
    }

    @Test
    fun dismissingTheResumeDialogResumesNothing() {
        show(resumeAction())
        compose.onNodeWithText(resumeLabel).performClick()

        compose.onNodeWithText(cancelLabel).performClick()

        verify(viewModel, never()).resumeBasal()
    }

    // ---- everything else is left alone ----

    /** Only suspend and resume are rewired; any other action keeps the handler it came with. */
    @Test
    fun anUnrelatedActionKeepsItsOwnBehaviour() {
        show(PumpAction(label = "Refresh", onClick = { otherClicks++ }))

        compose.onNodeWithText("Refresh").performClick()

        assertThat(otherClicks).isEqualTo(1)
    }
}
