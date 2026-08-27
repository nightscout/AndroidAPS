package app.aaps.ui.compose.siteRotationDialog

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import app.aaps.ui.compose.siteRotationDialog.viewModels.SiteRotationUiState
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

/** Robolectric composable test for [SiteRotationManagementContent]: renders the pump/CGM filter
 *  segments and fires the pump-sites toggle — headless on the JVM, no emulator. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class SiteRotationManagementContentTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var pumpSitesDesc: String
    private lateinit var cgmSitesDesc: String

    @Before
    fun setUp() {
        val ctx: Context = RuntimeEnvironment.getApplication()
        pumpSitesDesc = ctx.getString(CoreUiR.string.careportal_pump_site_management)
        cgmSitesDesc = ctx.getString(CoreUiR.string.careportal_cgm_site_management)
    }

    @Test
    fun rendersFilterSegmentsAndFiresPumpSitesToggle() {
        var pumpToggled: Boolean? = null
        compose.setContent {
            MaterialTheme {
                SiteRotationManagementContent(
                    uiState = SiteRotationUiState(),
                    displayEntries = emptyList(),
                    onClose = {},
                    onPreferenceClick = {},
                    onShowPumpSites = { pumpToggled = it },
                    onShowCgmSites = {},
                    onZoneClick = {},
                    onEntryClick = {},
                    onEditEntry = {},
                    onCancelEdit = {},
                    onConfirmEdit = {},
                    onArrowClick = {},
                    onNoteChange = {},
                    editedTeDate = "",
                    editedTeLocation = ""
                )
            }
        }

        compose.onNodeWithContentDescription(pumpSitesDesc).assertIsDisplayed()
        compose.onNodeWithContentDescription(cgmSitesDesc).assertIsDisplayed()

        compose.onNodeWithContentDescription(pumpSitesDesc).performClick()
        assertThat(pumpToggled).isNotNull()
    }
}
