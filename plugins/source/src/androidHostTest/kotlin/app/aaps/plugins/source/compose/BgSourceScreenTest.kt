package app.aaps.plugins.source.compose

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import app.aaps.core.data.model.GV
import app.aaps.core.data.model.IDs
import app.aaps.core.data.model.SourceSensor
import app.aaps.core.data.model.TrendArrow
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.StringKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.ui.compose.LocalConfig
import app.aaps.core.ui.compose.LocalDateUtil
import app.aaps.core.ui.compose.LocalPreferences
import app.aaps.core.ui.compose.LocalProfileUtil
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.Config as RobolectricConfig

/**
 * Compose UI test (Robolectric) for [BgSourceScreen]. Uses a mocked [BgSourceViewModel] so each UI
 * state (loading / empty / data with badges / removing mode) can be rendered deterministically.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RobolectricConfig(sdk = [35])
class BgSourceScreenTest {

    @get:Rule val compose = createComposeRule()

    private val rh: ResourceHelper = mock()
    private val dateUtil: DateUtil = mock()
    private val prefs: Preferences = mock()
    private val config: Config = mock()
    private val profileUtil: ProfileUtil = mock()
    private val viewModel: BgSourceViewModel = mock()

    private fun gv(id: Long, valid: Boolean = true, nsId: String? = null, ageMs: Long = 0L) = GV(
        id = id,
        timestamp = 1_000_000L + id,
        dateCreated = 1_000_000L + id + ageMs,
        isValid = valid,
        ids = IDs(nightscoutId = nsId),
        raw = null,
        value = 100.0 + id,
        trendArrow = TrendArrow.FLAT,
        noise = null,
        sourceSensor = SourceSensor.DEXCOM_NATIVE_UNKNOWN
    )

    private fun render(state: BgSourceUiState) {
        whenever(viewModel.uiState).thenReturn(MutableStateFlow(state))
        whenever(viewModel.rh).thenReturn(rh)
        whenever(viewModel.dateUtil).thenReturn(dateUtil)
        whenever(viewModel.formatGlucoseValue(any())).thenReturn("100 mg/dl")
        // removing-mode toolbar builds its title via rh.gs(count_selected, n) and rh.gs(close).
        // SelectableListToolbar names its strings with TextRef now, so stub that overload.
        whenever(rh.gs(any<TextRef>())).thenReturn("label")
        whenever(rh.gs(any<TextRef>(), any())).thenReturn("selected")
        whenever(prefs.observe(StringKey.GeneralDarkMode)).thenReturn(MutableStateFlow("light"))
        whenever(dateUtil.dateString(any())).thenReturn("2024-01-01")
        whenever(dateUtil.dateStringRelative(any(), any())).thenReturn("Today")
        whenever(dateUtil.timeStringWithSeconds(any())).thenReturn("12:00:00")
        whenever(dateUtil.minOrSec(any(), any())).thenReturn("1m")
        compose.setContent {
            CompositionLocalProvider(
                LocalPreferences provides prefs,
                LocalConfig provides config,
                LocalProfileUtil provides profileUtil,
                LocalDateUtil provides dateUtil
            ) {
                BgSourceScreen(viewModel = viewModel, title = "BG Source", setToolbarConfig = {})
            }
        }
    }

    @Test
    fun rendersLoadingState() {
        render(BgSourceUiState(isLoading = true, glucoseValues = emptyList()))
    }

    @Test
    fun rendersEmptyState() {
        render(BgSourceUiState(isLoading = false, glucoseValues = emptyList()))
    }

    @Test
    fun rendersDataWithBadges() {
        val items = listOf(
            gv(id = 1L, valid = true, nsId = "ns-1", ageMs = 60_000L), // NS badge + age
            gv(id = 2L, valid = false)                                 // invalid badge + duplicate
        )
        render(BgSourceUiState(isLoading = false, glucoseValues = items, duplicateIds = setOf(2L)))
        compose.onNodeWithText("Today").assertIsDisplayed()
    }

    @Test
    fun rendersRemovingModeWithCheckbox() {
        val item = gv(id = 1L, valid = true)
        render(BgSourceUiState(isLoading = false, glucoseValues = listOf(item), isRemovingMode = true, selectedItems = setOf(item)))
        compose.onNodeWithText("12:00:00").assertIsDisplayed()
    }
}
