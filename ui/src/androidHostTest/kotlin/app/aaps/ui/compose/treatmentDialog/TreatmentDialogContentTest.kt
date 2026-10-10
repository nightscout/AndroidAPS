package app.aaps.ui.compose.treatmentDialog

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import app.aaps.core.data.format.NumberFormat
import app.aaps.ui.compose.overview.chips.CobUiState
import app.aaps.ui.compose.overview.chips.IobUiState
import app.aaps.ui.compose.overview.graphs.BgInfoUiState
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

/** Robolectric composable test for [TreatmentDialogContent]: renders the insulin/carbs treatment
 *  dialog and fires navigate-back via the Close icon — headless JVM, no emulator. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class TreatmentDialogContentTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var closeLabel: String

    @Before
    fun setUp() {
        val ctx: Context = RuntimeEnvironment.getApplication()
        closeLabel = ctx.getString(CoreUiR.string.close)
    }

    @Test
    fun rendersAndFiresNavigateBack() {
        var back = false
        compose.setContent {
            MaterialTheme {
                TreatmentDialogContent(
                    uiState = TreatmentDialogUiState(),
                    bgInfo = BgInfoUiState(bgInfo = null, timeAgoText = ""),
                    iob = IobUiState(),
                    cob = CobUiState(),
                    bolusFormat = NumberFormat.DECIMAL_1,
                    onInsulinChange = {},
                    onCarbsChange = {},
                    onNavigateBack = { back = true },
                    onConfirmClick = {}
                )
            }
        }

        compose.onNodeWithContentDescription(closeLabel).assertIsDisplayed()
        compose.onNodeWithContentDescription(closeLabel).performClick()
        assertThat(back).isTrue()
    }
}
