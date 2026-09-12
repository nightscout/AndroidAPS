package app.aaps.pump.carelevo.compose.patchflow

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import app.aaps.core.ui.R as CoreUiR
import app.aaps.pump.carelevo.R
import app.aaps.pump.carelevo.presentation.type.CarelevoPatchStep
import app.aaps.pump.carelevo.presentation.viewmodel.CarelevoPatchConnectionFlowViewModel
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Render + interaction tests for [CarelevoPatchFlowStep04Attach].
 *
 * The step is a pure instruction sheet: it renders three numbered sections plus a closing hint and
 * routes the single primary button to [CarelevoPatchConnectionFlowViewModel.setPage]. The VM is a
 * mock (Mockito's inline mock maker handles the final class) because the composable only writes to
 * it — it reads no VM state — so no stubbing is needed.
 *
 * Section texts live inside the scrollable content column of `WizardStepLayout`, so they are
 * scrolled into view before asserting visibility; the button row is pinned and always visible.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class CarelevoPatchFlowStep04AttachTest {

    @get:Rule
    val compose = createComposeRule()

    private val viewModel = mock<CarelevoPatchConnectionFlowViewModel>()

    private lateinit var app: Application

    private lateinit var stepLabel1: String
    private lateinit var stepLabel2: String
    private lateinit var stepLabel3: String
    private lateinit var title1: String
    private lateinit var title2: String
    private lateinit var title3: String
    private lateinit var desc1: String
    private lateinit var desc2: String
    private lateinit var desc3: String
    private lateinit var desc4: String
    private lateinit var nextLabel: String
    private lateinit var cancelLabel: String

    @Before
    fun setUp() {
        app = RuntimeEnvironment.getApplication()
        stepLabel1 = app.getString(R.string.carelevo_patch_step_1)
        stepLabel2 = app.getString(R.string.carelevo_patch_step_2)
        stepLabel3 = app.getString(R.string.carelevo_patch_step_3)
        title1 = app.getString(R.string.carelevo_patch_attach_step1_title)
        title2 = app.getString(R.string.carelevo_patch_attach_step2_title)
        title3 = app.getString(R.string.carelevo_patch_attach_step3_title)
        desc1 = app.getString(R.string.carelevo_patch_attach_step1_desc)
        desc2 = app.getString(R.string.carelevo_patch_attach_step2_desc)
        desc3 = app.getString(R.string.carelevo_patch_attach_step3_desc)
        desc4 = app.getString(R.string.carelevo_patch_attach_step4_desc)
        nextLabel = app.getString(CoreUiR.string.next)
        cancelLabel = app.getString(CoreUiR.string.cancel)
    }

    private fun render() {
        compose.setContent {
            MaterialTheme {
                CarelevoPatchFlowStep04Attach(viewModel = viewModel)
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun showsAllThreeStepLabels() {
        render()

        compose.onNodeWithText(stepLabel1).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(stepLabel2).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(stepLabel3).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun showsAllThreeSectionTitles() {
        render()

        compose.onNodeWithText(title1).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(title2).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(title3).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun showsAllThreeSectionDescriptions() {
        render()

        compose.onNodeWithText(desc1).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(desc2).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(desc3).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun showsClosingInstruction() {
        render()

        compose.onNodeWithText(desc4).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun primaryButton_isDisplayedAndEnabled() {
        render()

        compose.onNodeWithText(nextLabel).assertIsDisplayed().assertIsEnabled()
    }

    @Test
    fun hasNoSecondaryButton() {
        render()

        compose.onNodeWithText(cancelLabel).assertDoesNotExist()
    }

    @Test
    fun doesNotTouchViewModel_beforeAnyClick() {
        render()

        verify(viewModel, never()).setPage(any())
    }

    @Test
    fun primaryClick_advancesToNeedleInsertion() {
        render()

        compose.onNodeWithText(nextLabel).performClick()
        compose.waitForIdle()

        verify(viewModel).setPage(CarelevoPatchStep.NEEDLE_INSERTION)
    }
}
