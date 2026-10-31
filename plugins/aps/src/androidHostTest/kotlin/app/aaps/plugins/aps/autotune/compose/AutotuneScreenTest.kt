package app.aaps.plugins.aps.autotune.compose

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.plugins.aps.R
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Stateless render test for [AutotuneScreen]. The VM ([AutotuneViewModel]) is intentionally not
 * tested here: it depends on the concrete [app.aaps.plugins.aps.autotune.AutotunePlugin] and runs
 * heavy work in init, which is impractical to stub. The screen is fully stateless, so it is
 * rendered directly with a default and a seeded [AutotuneUiState]. With the default
 * [DialogState.None] the rh/dateUtil/profileFunction/profileUtil mocks are never invoked.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class AutotuneScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val rh = mock<ResourceHelper>()
    private val dateUtil = mock<DateUtil>()
    private val profileFunction = mock<ProfileFunction>()
    private val profileUtil = mock<ProfileUtil>()

    private lateinit var lastRunLabel: String

    @Before
    fun setUp() {
        lastRunLabel = RuntimeEnvironment.getApplication().getString(R.string.autotune_last_run)
    }

    private fun render(state: AutotuneUiState) {
        compose.setContent {
            MaterialTheme {
                AutotuneScreen(
                    state = state,
                    rh = rh,
                    dateUtil = dateUtil,
                    profileFunction = profileFunction,
                    profileUtil = profileUtil,
                    onProfileSelected = {},
                    onDaysChanged = {},
                    onDayToggle = { _, _ -> },
                    onToggleWeekDays = {},
                    onRunAutotune = {},
                    onLoadLastRun = {},
                    onCopyLocal = {},
                    onUpdateProfile = {},
                    onRevertProfile = {},
                    onProfileSwitch = {},
                    onCheckInputProfile = {},
                    onCompareProfiles = {},
                    onDialogConfirm = {},
                    onDialogDismiss = {},
                    onCopyLocalConfirm = {}
                )
            }
        }
    }

    @Test
    fun rendersLastRunLabel_withDefaultState() {
        render(AutotuneUiState())

        compose.onNodeWithText(lastRunLabel).assertIsDisplayed()
    }

    @Test
    fun showsSeededLastRunAndWarning() {
        render(
            AutotuneUiState(
                lastRunText = "2026-04-08 10:00",
                warningText = "Check the results carefully!"
            )
        )

        compose.onNodeWithText(lastRunLabel).assertIsDisplayed()
        compose.onNodeWithText("2026-04-08 10:00").assertIsDisplayed()
        compose.onNodeWithText("Check the results carefully!").assertIsDisplayed()
    }
}
