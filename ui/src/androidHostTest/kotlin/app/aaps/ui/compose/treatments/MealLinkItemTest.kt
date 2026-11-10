package app.aaps.ui.compose.treatments

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import app.aaps.core.data.model.BS
import app.aaps.core.data.model.CA
import app.aaps.core.data.model.ICfg
import app.aaps.core.data.model.IDs
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.resources.TextRefIdRegistry
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.StringKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.compose.AapsTheme
import app.aaps.core.ui.compose.LocalDateUtil
import app.aaps.core.ui.compose.LocalPreferences
import app.aaps.ui.UiStringIds
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A row of Treatments → Carbs & boluses on a normal phone. A long text in the row (the insulin label of an
 * older bolus, the notes of carbs) used to take the width first, so the bolus type wrapped and the NS and
 * pump-history icons got no width at all and disappeared (#5481).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h640dp")
class MealLinkItemTest {

    @get:Rule
    val compose = createComposeRule()

    private val dateUtil: DateUtil = mock()
    private val preferences: Preferences = mock()
    private val profile: Profile = mock()

    private val now = 1_700_000_000_000L
    private val activeInsulin = ICfg("Lyumjev 45m 7.5h U100", insulinEndTime = T.mins(450).msecs(), insulinPeakTime = T.mins(45).msecs())

    // After an insulin change every older bolus shows its label, and the end of it is the concentration
    private val olderInsulin = ICfg("Humalog Junior 45m 7.5h U200", insulinEndTime = T.mins(450).msecs(), insulinPeakTime = T.mins(45).msecs(), concentration = 2.0)
    private val inNsAndPumpHistory = IDs(nightscoutId = "ns1", pumpSerial = "123456", pumpId = 7L)

    @Before
    fun setUp() {
        TextRefIdRegistry.register("ui") { name -> UiStringIds.idOf(name) }
        whenever(dateUtil.now()).thenReturn(now)
        whenever(dateUtil.timeString(any())).thenReturn("21:33")
        whenever(profile.iCfg).thenReturn(activeInsulin)
        // AapsTheme reads the dark mode preference
        whenever(preferences.observe(StringKey.GeneralDarkMode)).thenReturn(MutableStateFlow("light"))
    }

    @Test
    fun bolusWithOlderInsulinKeepsItsTypeAndIcons() {
        // Recent enough to still have IOB, so the amount shows the IOB part too: "3.60 U (… U)"
        val bolus = BS(timestamp = now - T.mins(30).msecs(), amount = 3.6, type = BS.Type.NORMAL, ids = inNsAndPumpHistory, iCfg = olderInsulin)

        show(MealLink(bolus = bolus))

        assertIconsKeepTheirWidth()
        assertThat(lineCount("Meal Bolus")).isEqualTo(1)
        // The whole label stays, with the concentration at its end
        compose.onNodeWithText(olderInsulin.insulinLabel, useUnmergedTree = true).assertIsDisplayed()
        assertThat(lineCount(olderInsulin.insulinLabel)).isEqualTo(1)
    }

    @Test
    fun carbsWithLongNotesKeepTheirIcons() {
        val notes = "Pasta with tomato sauce, bread and a dessert after it"
        val carbs = CA(timestamp = now - T.mins(30).msecs(), duration = 0L, amount = 80.0, notes = notes, ids = inNsAndPumpHistory)

        show(MealLink(carbs = carbs))

        assertIconsKeepTheirWidth()
        compose.onNodeWithText(notes, useUnmergedTree = true).assertIsDisplayed()
    }

    private fun show(mealLink: MealLink) {
        compose.setContent {
            CompositionLocalProvider(
                LocalPreferences provides preferences,
                LocalDateUtil provides dateUtil
            ) {
                AapsTheme {
                    MealLinkItem(
                        mealLink = mealLink,
                        isRemovingMode = false,
                        isSelected = false,
                        onClick = {},
                        onLongPress = {},
                        onCalculatorClick = {},
                        profile = profile,
                        showInvalidated = false
                    )
                }
            }
        }
    }

    // The card merges its children for TalkBack, so the icons are looked up in the unmerged tree.
    // An icon is 21 dp with 5 dp padding at the start, so the icon itself is 16 dp. Squeezed out, it was 0 dp.
    private fun assertIconsKeepTheirWidth() {
        compose.onNodeWithContentDescription("NS", useUnmergedTree = true).assertWidthIsEqualTo(16.dp)
        compose.onNodeWithContentDescription("Pump history", useUnmergedTree = true).assertWidthIsEqualTo(16.dp)
    }

    private fun lineCount(text: String): Int {
        val layouts = mutableListOf<TextLayoutResult>()
        val getLayout = compose.onNodeWithText(text, useUnmergedTree = true).fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult]
        getLayout.action?.invoke(layouts)
        return layouts.single().lineCount
    }
}
