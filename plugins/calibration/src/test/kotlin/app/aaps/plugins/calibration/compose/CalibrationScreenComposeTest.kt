package app.aaps.plugins.calibration.compose

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.aaps.core.data.model.CAL
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.plugins.calibration.CalibrationFit
import app.aaps.plugins.calibration.FitMode
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Compose UI test for the Linear Calibration plugin screen, focused on the action buttons
 * ("Log sensor change" and "Add calibration") and the status/entry-count rendering.
 *
 * Renders the state-hoisted [CalibrationScreenContent] headlessly on the JVM via Robolectric,
 * mirroring the pattern established by the core:ui dialog tests.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class CalibrationScreenComposeTest {

    @get:Rule
    val compose = createComposeRule()

    private val now = 1_700_000_000_000L
    private val hour = 3_600_000L

    private val sampleEntries = listOf(
        CAL(id = 1, timestamp = now - 5 * hour, fingerstickMgdl = 120.0, sensorMgdlAtPairing = 110.0),
        CAL(id = 2, timestamp = now - 1 * hour, fingerstickMgdl = 150.0, sensorMgdlAtPairing = 145.0)
    )

    private fun appliedState() = CalibrationUiState(
        sessionStart = now - 6 * hour,
        warmUpEndsAt = now - 4 * hour,
        isInWarmUp = false,
        entries = sampleEntries,
        fit = CalibrationFit(slope = 1.05, offset = 2.0, mode = FitMode.Full),
        now = now,
        selectedEntryId = 2,
        glucoseUnit = GlucoseUnit.MGDL
    )

    private fun setContent(
        state: CalibrationUiState,
        onMarkSensorChange: () -> Unit = {},
        onAddCalibration: () -> Unit = {},
        onSelectEntry: (Long) -> Unit = {},
        onDeleteEntry: (Long) -> Unit = {}
    ) {
        compose.setContent {
            MaterialTheme {
                CalibrationScreenContent(
                    state = state,
                    formatDateTime = { "2024-01-01 12:00" },
                    formatTime = { "14:00" },
                    onMarkSensorChange = onMarkSensorChange,
                    onAddCalibration = onAddCalibration,
                    onSelectEntry = onSelectEntry,
                    onDeleteEntry = onDeleteEntry
                )
            }
        }
    }

    @Test
    fun showsBothActionButtons() {
        setContent(appliedState())
        compose.onNodeWithText("Log sensor change").assertIsDisplayed()
        compose.onNodeWithText("Add calibration").assertIsDisplayed()
    }

    @Test
    fun addCalibrationButton_invokesCallback() {
        var clicked = false
        setContent(appliedState(), onAddCalibration = { clicked = true })
        compose.onNodeWithText("Add calibration").performClick()
        assertThat(clicked).isTrue()
    }

    @Test
    fun markSensorChangeButton_invokesCallback() {
        var clicked = false
        setContent(appliedState(), onMarkSensorChange = { clicked = true })
        compose.onNodeWithText("Log sensor change").performClick()
        assertThat(clicked).isTrue()
    }

    @Test
    fun noSession_showsNoSessionStatusAndEmptyEntries() {
        setContent(CalibrationUiState())
        compose.onNodeWithText("Log a sensor change to enable calibration.").assertExists()
        compose.onNodeWithText("No fingerstick entries yet.").assertExists()
    }

    @Test
    fun appliedState_showsAppliedStatusAndEntryCount() {
        setContent(appliedState())
        compose.onNodeWithText("Calibration applied.").assertExists()
        compose.onNodeWithText("Fingerstick entries (2)").assertExists()
    }
}
