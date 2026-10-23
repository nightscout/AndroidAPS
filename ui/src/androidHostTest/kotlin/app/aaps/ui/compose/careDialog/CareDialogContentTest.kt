package app.aaps.ui.compose.careDialog

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.aaps.core.interfaces.resources.TextRefIdRegistry
import app.aaps.ui.R
import app.aaps.ui.UiStringIds
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import app.aaps.core.ui.R as CoreUiR

/** Robolectric composable test for [CareDialogContent] — headless on the JVM, no emulator. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class CareDialogContentTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var meterLabel: String
    private lateinit var sensorLabel: String
    private lateinit var bgExpandButton: String
    private lateinit var durationExpandButton: String

    /** The meter type as the folded BG row shows it, next to the value. */
    private lateinit var meterInParentheses: String

    @Before
    fun setUp() {
        // MainApp does this in production; a Robolectric test has no MainApp, so a TextRef.Named
        // would have no id to resolve to and the screen would render blank text.
        TextRefIdRegistry.register("ui") { name -> UiStringIds.idOf(name) }
        val ctx: Context = RuntimeEnvironment.getApplication()
        meterLabel = ctx.getString(R.string.bg_meter)
        sensorLabel = ctx.getString(R.string.bg_sensor)
        bgExpandButton = ctx.getString(CoreUiR.string.a11y_expand_button_description, ctx.getString(CoreUiR.string.bg_label))
        durationExpandButton = ctx.getString(CoreUiR.string.a11y_expand_button_description, ctx.getString(CoreUiR.string.duration_label))
        meterInParentheses = ctx.getString(CoreUiR.string.value_in_parentheses, meterLabel)
    }

    /** A BG check is about the BG: the meter options and the field are open, nothing to unfold. */
    @Test
    fun rendersBgCheckMeterOptionsAndFiresMeterTypeChange() {
        var meterChanged = false
        setCareDialog(CareportalEventType.BGCHECK, onMeterTypeChange = { meterChanged = true })

        compose.onNodeWithText(meterLabel).assertIsDisplayed()
        compose.onNodeWithText(sensorLabel).assertIsDisplayed()
        compose.onNodeWithContentDescription(bgExpandButton).assertDoesNotExist()

        compose.onNodeWithText(sensorLabel).performClick()
        assertThat(meterChanged).isTrue()
    }

    /** With a question the BG only comes along: folded, but the meter type is still shown next to the value. */
    @Test
    fun bgOfAQuestionIsFoldedAndShowsTheMeterType() {
        setCareDialog(CareportalEventType.QUESTION)

        compose.onNodeWithText(meterInParentheses).assertIsDisplayed()
        compose.onNodeWithText(sensorLabel).assertDoesNotExist()

        compose.onNodeWithContentDescription(bgExpandButton).performClick()
        compose.onNodeWithText(sensorLabel).assertIsDisplayed()
    }

    @Test
    fun durationOfANoteIsFolded() {
        setCareDialog(CareportalEventType.NOTE)
        compose.onNodeWithContentDescription(durationExpandButton).assertExists()
    }

    private fun setCareDialog(eventType: CareportalEventType, onMeterTypeChange: () -> Unit = {}) {
        compose.setContent {
            MaterialTheme {
                CareDialogContent(
                    uiState = CareDialogUiState(eventType = eventType, bgValue = 120.0),
                    eventType = eventType,
                    dateString = "2024-01-01",
                    timeString = "12:00",
                    onMeterTypeChange = { onMeterTypeChange() },
                    onBgValueChange = {},
                    onDurationChange = {},
                    onNotesChange = {},
                    onTimeOffsetChange = {},
                    onNavigateBack = {},
                    onConfirmClick = {},
                    onDateClick = {},
                    onTimeClick = {}
                )
            }
        }
    }
}
