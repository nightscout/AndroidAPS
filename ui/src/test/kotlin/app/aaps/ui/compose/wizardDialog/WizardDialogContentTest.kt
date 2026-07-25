package app.aaps.ui.compose.wizardDialog

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import app.aaps.core.ui.R as CoreUiR

/** Robolectric composable test for [WizardDialogContent]: renders + fires navigate-back (Close). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class WizardDialogContentTest {

    @get:Rule
    val compose = createComposeRule()

    private val decimalFormatter: DecimalFormatter = mock()
    private val profileUtil: ProfileUtil = mock()

    private lateinit var closeLabel: String

    @Before
    fun setUp() {
        val ctx: Context = RuntimeEnvironment.getApplication()
        closeLabel = ctx.getString(CoreUiR.string.close)
        whenever(profileUtil.units).thenReturn(GlucoseUnit.MGDL)
        whenever(profileUtil.fromMgdlToUnits(any(), any())).thenReturn(5.0)
    }

    @Test
    fun rendersAndFiresNavigateBack() {
        var back = false
        compose.setContent {
            MaterialTheme {
                WizardDialogContent(
                    uiState = WizardDialogUiState(),
                    decimalFormatter = decimalFormatter,
                    profileUtil = profileUtil,
                    unitsLabel = "mg/dl",
                    onBgChange = {},
                    onCarbsChange = {},
                    onAddCarbs = {},
                    onCarbsTypeChange = {},
                    onPercentageChange = {},
                    onDirectCorrectionChange = {},
                    onCarbTimeChange = {},
                    onNotesChange = {},
                    onProfileSelect = {},
                    onBgToggle = {},
                    onTTToggle = {},
                    onTrendToggle = {},
                    onIOBToggle = {},
                    onCOBToggle = {},
                    onAlarmToggle = {},
                    onAdvancedExpandToggle = {},
                    onCalculationExpandToggle = {},
                    onNavigateBack = { back = true },
                    onConfirmClick = {},
                    onSettingsClick = {}
                )
            }
        }
        compose.onNodeWithContentDescription(closeLabel).assertIsDisplayed()
        compose.onNodeWithContentDescription(closeLabel).performClick()
        assertThat(back).isTrue()
    }
}
