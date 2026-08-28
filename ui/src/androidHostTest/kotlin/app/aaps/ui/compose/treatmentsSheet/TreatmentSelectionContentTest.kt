package app.aaps.ui.compose.treatmentsSheet

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.aaps.core.ui.R as CoreUiR
import app.aaps.core.ui.compose.LocalConfig
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import app.aaps.core.interfaces.configuration.Config as AppConfig

/** Robolectric composable test for [TreatmentSelectionContent]: header renders (all action buttons enabled) + settings fires. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class TreatmentSelectionContentTest {

    @get:Rule
    val compose = createComposeRule()

    private val config: AppConfig = mock()

    private lateinit var treatmentsLabel: String
    private lateinit var settingsLabel: String

    @Before
    fun setUp() {
        val ctx: Context = RuntimeEnvironment.getApplication()
        treatmentsLabel = ctx.getString(CoreUiR.string.treatments)
        settingsLabel = ctx.getString(CoreUiR.string.settings)
    }

    @Test
    fun rendersHeaderAndFiresSettings() {
        var settingsClicked = false
        compose.setContent {
            CompositionLocalProvider(LocalConfig provides config) {
                MaterialTheme {
                TreatmentSelectionContent(
                    onDismiss = {},
                    onNavigate = {},
                    quickWizardItems = emptyList(),
                    showCgm = true,
                    showCalibration = true,
                    showTreatment = true,
                    showInsulin = true,
                    showCarbs = true,
                    showCalculator = true,
                    isDexcomSource = false,
                    showSettingsIcon = true,
                    onSettingsClick = { settingsClicked = true }
                )
                }
            }
        }
        compose.onNodeWithText(treatmentsLabel).assertIsDisplayed()
        compose.onNodeWithContentDescription(settingsLabel).performClick()
        assertThat(settingsClicked).isTrue()
    }
}
