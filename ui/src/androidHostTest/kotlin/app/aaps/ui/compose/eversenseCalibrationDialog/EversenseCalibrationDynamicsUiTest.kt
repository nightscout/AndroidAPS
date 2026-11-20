package app.aaps.ui.compose.eversenseCalibrationDialog

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.aaps.core.interfaces.resources.TextRefIdRegistry
import app.aaps.ui.UiStringIds
import app.aaps.ui.R
import org.junit.Assert.assertTrue
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
class EversenseCalibrationDynamicsUiTest {
    @get:Rule val compose = createComposeRule()

    @Before fun setUp() {
        TextRefIdRegistry.register("ui") { name -> UiStringIds.idOf(name) }
    }

    @Test fun warningIsVisibleWithoutCoveringOrDisablingSubmission() {
        val rapid = mutableStateOf(true)
        var submitted = false
        compose.setContent {
            MaterialTheme {
                Column {
                    EversenseCalibrationDynamicsWarningContent(rapid.value)
                    Button(onClick = { submitted = true }) { Text("Submit") }
                }
            }
        }
        val context: Context = RuntimeEnvironment.getApplication()
        val message = context.getString(R.string.eversense_calibration_rapid_change_warning)
        compose.onNodeWithText(message).assertIsDisplayed()
        compose.onNodeWithText("Submit").performClick()
        compose.runOnIdle { assertTrue(submitted); rapid.value = false }
        compose.onNodeWithText(message).assertDoesNotExist()
    }
}
