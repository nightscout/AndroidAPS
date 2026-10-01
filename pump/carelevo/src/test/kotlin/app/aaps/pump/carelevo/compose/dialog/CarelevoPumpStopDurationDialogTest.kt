package app.aaps.pump.carelevo.compose.dialog

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.aaps.pump.carelevo.R
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Compose UI test for [CarelevoPumpStopDurationDialog].
 *
 * The dialog is state-hoisted apart from the internally remembered `selectedIndex`, so no ViewModel
 * is involved. It reads only Material defaults, hence the plain `MaterialTheme` wrapper rather than
 * `AapsTheme` (which additionally requires the `LocalPreferences` CompositionLocal).
 *
 * Labels/buttons are resolved from resources rather than hardcoded English so the test survives
 * label and locale changes.
 *
 * Covered branches:
 *  - `labels.forEachIndexed` -> a radio row per label, and the empty-list case
 *  - `selectedIndex == index` -> selected / unselected radio state, seeded from `initialIndex`
 *  - `onClick { selectedIndex = index }` -> selection moves, including re-clicking the current one
 *  - confirm maps the selected index back through `options` (before and after a selection change)
 *  - cancel routes to `onDismissRequest`
 *  - `remember(initialIndex)` -> selection resets when the `initialIndex` key changes
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class CarelevoPumpStopDurationDialogTest {

    @get:Rule
    val compose = createComposeRule()

    private val options = listOf(30, 60, 120)
    private val labels = listOf("30 minutes", "1 hour", "2 hours")

    private lateinit var titleLabel: String
    private lateinit var confirmLabel: String
    private lateinit var cancelLabel: String

    @Before
    fun setUp() {
        val context: Context = RuntimeEnvironment.getApplication()
        titleLabel = context.getString(R.string.carelevo_pump_stop_duration_title)
        confirmLabel = context.getString(R.string.carelevo_btn_confirm)
        cancelLabel = context.getString(R.string.carelevo_btn_cancel)
    }

    private fun show(
        initialIndex: Int = 0,
        onDismissRequest: () -> Unit = {},
        onConfirm: (Int) -> Unit = {}
    ) {
        compose.setContent {
            MaterialTheme {
                CarelevoPumpStopDurationDialog(
                    options = options,
                    labels = labels,
                    initialIndex = initialIndex,
                    onDismissRequest = onDismissRequest,
                    onConfirm = onConfirm
                )
            }
        }
    }

    @Test
    fun rendersTitleAllLabelsAndBothButtons() {
        show()

        compose.onNodeWithText(titleLabel).assertIsDisplayed()
        labels.forEach { compose.onNodeWithText(it).assertIsDisplayed() }
        compose.onNodeWithText(confirmLabel).assertIsDisplayed()
        compose.onNodeWithText(cancelLabel).assertIsDisplayed()
    }

    @Test
    fun rendersOneRadioButtonPerLabel() {
        show()

        compose.onAllNodes(isSelectable()).assertCountEquals(labels.size)
    }

    @Test
    fun initialIndex_preselectsMatchingRadioButton() {
        show(initialIndex = 1)

        val radios = compose.onAllNodes(isSelectable())
        radios[0].assertIsNotSelected()
        radios[1].assertIsSelected()
        radios[2].assertIsNotSelected()
    }

    @Test
    fun firstOption_isPreselected_whenInitialIndexIsZero() {
        show(initialIndex = 0)

        val radios = compose.onAllNodes(isSelectable())
        radios[0].assertIsSelected()
        radios[1].assertIsNotSelected()
        radios[2].assertIsNotSelected()
    }

    @Test
    fun lastOption_isPreselected_whenInitialIndexIsLast() {
        show(initialIndex = 2)

        val radios = compose.onAllNodes(isSelectable())
        radios[0].assertIsNotSelected()
        radios[1].assertIsNotSelected()
        radios[2].assertIsSelected()
    }

    @Test
    fun confirm_withoutChangingSelection_returnsInitialOption() {
        var confirmed: Int? = null
        show(initialIndex = 1, onConfirm = { confirmed = it })

        compose.onNodeWithText(confirmLabel).performClick()

        assertThat(confirmed).isEqualTo(60)
    }

    @Test
    fun selectingAnotherOption_movesSelection() {
        show(initialIndex = 0)

        compose.onAllNodes(isSelectable())[2].performClick()

        val radios = compose.onAllNodes(isSelectable())
        radios[0].assertIsNotSelected()
        radios[1].assertIsNotSelected()
        radios[2].assertIsSelected()
    }

    @Test
    fun confirm_afterSelectionChange_returnsSelectedOption() {
        var confirmed: Int? = null
        show(initialIndex = 0, onConfirm = { confirmed = it })

        compose.onAllNodes(isSelectable())[2].performClick()
        compose.onNodeWithText(confirmLabel).performClick()

        assertThat(confirmed).isEqualTo(120)
    }

    @Test
    fun selectionCanMoveMultipleTimes_lastSelectionWins() {
        var confirmed: Int? = null
        show(initialIndex = 0, onConfirm = { confirmed = it })

        compose.onAllNodes(isSelectable())[2].performClick()
        compose.onAllNodes(isSelectable())[1].performClick()
        compose.onNodeWithText(confirmLabel).performClick()

        assertThat(confirmed).isEqualTo(60)
    }

    @Test
    fun reclickingSelectedOption_keepsSelectionAndConfirmsSameOption() {
        var confirmed: Int? = null
        show(initialIndex = 1, onConfirm = { confirmed = it })

        compose.onAllNodes(isSelectable())[1].performClick()

        compose.onAllNodes(isSelectable())[1].assertIsSelected()
        compose.onNodeWithText(confirmLabel).performClick()
        assertThat(confirmed).isEqualTo(60)
    }

    @Test
    fun confirm_doesNotFireDismiss() {
        var dismissals = 0
        var confirmations = 0
        show(initialIndex = 0, onDismissRequest = { dismissals++ }, onConfirm = { confirmations++ })

        compose.onNodeWithText(confirmLabel).performClick()

        assertThat(confirmations).isEqualTo(1)
        assertThat(dismissals).isEqualTo(0)
    }

    @Test
    fun cancel_firesOnDismissRequestOnly() {
        var dismissals = 0
        var confirmed: Int? = null
        show(initialIndex = 0, onDismissRequest = { dismissals++ }, onConfirm = { confirmed = it })

        compose.onNodeWithText(cancelLabel).performClick()

        assertThat(dismissals).isEqualTo(1)
        assertThat(confirmed).isNull()
    }

    @Test
    fun changingInitialIndex_resetsSelection() {
        // `remember(initialIndex)` is keyed, so a new initialIndex must discard the user's pick.
        var initialIndex by mutableIntStateOf(0)
        var confirmed: Int? = null
        compose.setContent {
            MaterialTheme {
                CarelevoPumpStopDurationDialog(
                    options = options,
                    labels = labels,
                    initialIndex = initialIndex,
                    onDismissRequest = {},
                    onConfirm = { confirmed = it }
                )
            }
        }

        compose.onAllNodes(isSelectable())[2].performClick()
        compose.onAllNodes(isSelectable())[2].assertIsSelected()

        compose.runOnIdle { initialIndex = 1 }

        val radios = compose.onAllNodes(isSelectable())
        radios[1].assertIsSelected()
        radios[2].assertIsNotSelected()

        compose.onNodeWithText(confirmLabel).performClick()
        assertThat(confirmed).isEqualTo(60)
    }

    @Test
    fun emptyLabels_rendersTitleAndButtonsWithoutRadioButtons() {
        compose.setContent {
            MaterialTheme {
                CarelevoPumpStopDurationDialog(
                    options = emptyList(),
                    labels = emptyList(),
                    initialIndex = 0,
                    onDismissRequest = {},
                    onConfirm = {}
                )
            }
        }

        compose.onNodeWithText(titleLabel).assertIsDisplayed()
        compose.onNodeWithText(confirmLabel).assertIsDisplayed()
        compose.onNodeWithText(cancelLabel).assertIsDisplayed()
        compose.onAllNodes(isSelectable()).assertCountEquals(0)
    }

    @Test
    fun singleOption_rendersAndConfirmsThatOption() {
        var confirmed: Int? = null
        compose.setContent {
            MaterialTheme {
                CarelevoPumpStopDurationDialog(
                    options = listOf(45),
                    labels = listOf("45 minutes"),
                    initialIndex = 0,
                    onDismissRequest = {},
                    onConfirm = { confirmed = it }
                )
            }
        }

        compose.onNodeWithText("45 minutes").assertIsDisplayed()
        compose.onAllNodes(isSelectable()).assertCountEquals(1)
        compose.onAllNodes(isSelectable())[0].assertIsSelected()

        compose.onNodeWithText(confirmLabel).performClick()
        assertThat(confirmed).isEqualTo(45)
    }
}
