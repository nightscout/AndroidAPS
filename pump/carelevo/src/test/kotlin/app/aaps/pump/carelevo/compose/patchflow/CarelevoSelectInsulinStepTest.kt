package app.aaps.pump.carelevo.compose.patchflow

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import app.aaps.core.data.model.ICfg
import app.aaps.core.interfaces.R as CoreInterfacesR
import app.aaps.core.ui.R as CoreUiR
import app.aaps.pump.carelevo.presentation.viewmodel.CarelevoPatchConnectionFlowViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Before
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

/**
 * Render + interaction tests for [CarelevoSelectInsulinStep].
 *
 * The step is thin glue: it collects three VM StateFlows, feeds the shared
 * `app.aaps.core.ui.compose.insulin.SelectInsulin` picker (always `initialExpanded = true`), gates
 * the primary button on `selectedInsulin != null`, and routes the three interactions back to the VM
 * (`selectInsulin` / `advanceFromInsulin` / `exitWizard`).
 *
 * The VM is a Mockito mock (inline mock maker handles the final class) with real [MutableStateFlow]
 * backing each collected property, so every branch is driven by simply setting a flow value before
 * rendering, and every interaction is asserted with `verify`.
 *
 * Note the picker filters rows by the selected insulin's concentration: with [fiasp] selected only
 * the U100 insulins are listed, [lyumjev] (U200) is filtered out.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class CarelevoSelectInsulinStepTest {

    @get:Rule
    val compose = createComposeRule()

    private val fiasp = ICfg("Fiasp U100", peak = 55, dia = 5.0, concentration = 1.0)
    private val novoRapid = ICfg("NovoRapid U100", peak = 75, dia = 5.0, concentration = 1.0)
    private val lyumjev = ICfg("Lyumjev U200", peak = 45, dia = 5.0, concentration = 2.0)

    private val availableInsulins = MutableStateFlow<List<ICfg>>(emptyList())
    private val selectedInsulin = MutableStateFlow<ICfg?>(null)
    private val activeInsulinLabel = MutableStateFlow<String?>(null)

    private val viewModel = mock<CarelevoPatchConnectionFlowViewModel>()

    private lateinit var app: Application
    private lateinit var currentInsulinLabel: String
    private lateinit var changeLabel: String
    private lateinit var concentrationLabel: String
    private lateinit var u100Label: String
    private lateinit var nextLabel: String
    private lateinit var cancelLabel: String

    @Before
    fun setUp() {
        app = RuntimeEnvironment.getApplication()
        currentInsulinLabel = app.getString(CoreUiR.string.current_insulin)
        changeLabel = app.getString(CoreUiR.string.change_insulin)
        concentrationLabel = app.getString(CoreUiR.string.concentration_label)
        u100Label = app.getString(CoreInterfacesR.string.u100)
        nextLabel = app.getString(CoreUiR.string.next)
        cancelLabel = app.getString(CoreUiR.string.cancel)

        whenever(viewModel.availableInsulins).thenReturn(availableInsulins)
        whenever(viewModel.selectedInsulin).thenReturn(selectedInsulin)
        whenever(viewModel.activeInsulinLabel).thenReturn(activeInsulinLabel)
        whenever(viewModel.concentrationEnabled).thenReturn(false)
    }

    private fun render() {
        compose.setContent {
            MaterialTheme {
                CarelevoSelectInsulinStep(viewModel = viewModel)
            }
        }
        compose.waitForIdle()
    }

    /** Seeds the common "three insulins configured, Fiasp active and selected" case. */
    private fun seedThreeInsulins() {
        availableInsulins.value = listOf(fiasp, novoRapid, lyumjev)
        selectedInsulin.value = fiasp
        activeInsulinLabel.value = fiasp.insulinLabel
    }

    @Test
    fun showsSelectedInsulinInHeader_andExpandedList() {
        seedThreeInsulins()
        render()

        compose.onNodeWithText(changeLabel).performScrollTo().assertIsDisplayed()
        // "Fiasp U100" is both the header value and the (active, selected) list row.
        compose.onAllNodesWithText(fiasp.insulinLabel).assertCountEquals(2)
        // "Currently active" labels the header and marks the active row.
        compose.onAllNodesWithText(currentInsulinLabel).assertCountEquals(2)
    }

    @Test
    fun listsInsulinsOfSelectedConcentration_andFiltersOthersOut() {
        seedThreeInsulins()
        render()

        compose.onNodeWithText(novoRapid.insulinLabel).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(lyumjev.insulinLabel).assertDoesNotExist()
    }

    @Test
    fun selectingInsulin_delegatesToViewModel() {
        seedThreeInsulins()
        render()

        compose.onNodeWithText(novoRapid.insulinLabel).performScrollTo().performClick()
        compose.waitForIdle()

        verify(viewModel).selectInsulin(novoRapid)
    }

    @Test
    fun primaryButton_isEnabled_whenInsulinSelected() {
        seedThreeInsulins()
        render()

        compose.onNodeWithText(nextLabel).assertIsDisplayed().assertIsEnabled()
    }

    @Test
    fun primaryClick_advancesFromInsulin() {
        seedThreeInsulins()
        render()

        compose.onNodeWithText(nextLabel).performClick()
        compose.waitForIdle()

        verify(viewModel).advanceFromInsulin()
    }

    @Test
    fun primaryButton_isDisabled_whenNothingSelected() {
        availableInsulins.value = listOf(fiasp, novoRapid)
        selectedInsulin.value = null
        activeInsulinLabel.value = null
        render()

        compose.onNodeWithText(nextLabel).assertIsDisplayed().assertIsNotEnabled()

        compose.onNodeWithText(nextLabel).performClick()
        compose.waitForIdle()

        verify(viewModel, never()).advanceFromInsulin()
    }

    @Test
    fun secondaryButton_isDisplayed_andExitsWizard() {
        seedThreeInsulins()
        render()

        compose.onNodeWithText(cancelLabel).assertIsDisplayed().assertIsEnabled()

        compose.onNodeWithText(cancelLabel).performClick()
        compose.waitForIdle()

        verify(viewModel).exitWizard()
    }

    @Test
    fun emptyInsulinList_fallsBackToActiveLabel_andDisablesPrimary() {
        availableInsulins.value = emptyList()
        selectedInsulin.value = null
        activeInsulinLabel.value = fiasp.insulinLabel
        render()

        // With no rows to render, the active label is shown once — in the header only.
        compose.onNodeWithText(fiasp.insulinLabel).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(changeLabel).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(nextLabel).assertIsNotEnabled()
    }

    @Test
    fun concentrationDropdown_isShown_whenEnabledAndMultipleConcentrations() {
        whenever(viewModel.concentrationEnabled).thenReturn(true)
        seedThreeInsulins()
        render()

        compose.onNodeWithText(concentrationLabel).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(u100Label).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun concentrationDropdown_isHidden_whenConcentrationDisabled() {
        whenever(viewModel.concentrationEnabled).thenReturn(false)
        seedThreeInsulins()
        render()

        compose.onNodeWithText(concentrationLabel).assertDoesNotExist()
    }

    @Test
    fun concentrationDropdown_isHidden_whenOnlyOneConcentrationAvailable() {
        whenever(viewModel.concentrationEnabled).thenReturn(true)
        availableInsulins.value = listOf(fiasp, novoRapid)
        selectedInsulin.value = fiasp
        activeInsulinLabel.value = fiasp.insulinLabel
        render()

        compose.onNodeWithText(concentrationLabel).assertDoesNotExist()
        // The picker itself still renders both same-concentration insulins.
        compose.onNodeWithText(novoRapid.insulinLabel).performScrollTo().assertIsDisplayed()
    }
}
