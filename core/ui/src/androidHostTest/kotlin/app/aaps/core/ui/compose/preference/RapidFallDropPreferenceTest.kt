package app.aaps.core.ui.compose.preference

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.IntKey
import app.aaps.core.keys.UnitType
import app.aaps.core.keys.interfaces.AppPlatform
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.compose.LocalConfig
import app.aaps.core.ui.compose.LocalPreferences
import app.aaps.core.ui.compose.LocalProfileUtil
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import app.aaps.core.interfaces.configuration.Config as AppConfig

/**
 * The rapid fall drop is a glucose *difference*, so it is an int key in mg/dL and not a unit double
 * key (that type guesses the unit from the number, and 30 mg/dL would be shown as 540). These tests
 * set 20 mg/dL through the screen state on an mg/dL phone and on an mmol/L phone, and check that 20
 * is stored and shown as "20 mg/dL" in both cases.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class RapidFallDropPreferenceTest {

    @get:Rule
    val compose = createComposeRule()

    // A mocked platform would be null, which no key lists, so the row would not be drawn at all.
    private val config: AppConfig = mock { on { platform } doReturn AppPlatform.Android }

    /** A preferences mock that really keeps the drop value, so a write can be read back. */
    private fun preferencesWithStore(initial: Int): Preferences {
        var stored = initial
        val prefs: Preferences = mock()
        whenever(prefs.get(IntKey.AlertRapidFallDrop)).doAnswer { stored }
        whenever(prefs.put(eq(IntKey.AlertRapidFallDrop), any<Int>())).doAnswer { stored = it.getArgument(1) }
        whenever(prefs.get(BooleanKey.AlertRapidFall)).thenReturn(true)
        return prefs
    }

    private fun profileUtilIn(units: GlucoseUnit): ProfileUtil {
        val profileUtil: ProfileUtil = mock()
        whenever(profileUtil.units).thenReturn(units)
        return profileUtil
    }

    private fun roundTrip(units: GlucoseUnit) {
        val prefs = preferencesWithStore(initial = IntKey.AlertRapidFallDrop.defaultValue)
        val state = PreferenceIntState(prefs, IntKey.AlertRapidFallDrop, mutableStateMapOf())

        state.value = 20

        // Stored as 20 mg/dL, not converted by the unit setting.
        assertThat(prefs.get(IntKey.AlertRapidFallDrop)).isEqualTo(20)
        // A new screen reads 20 back.
        assertThat(PreferenceIntState(prefs, IntKey.AlertRapidFallDrop, mutableStateMapOf()).value).isEqualTo(20)

        // And the row shows it with its mg/dL unit, whatever the user's display units are.
        compose.setContent {
            CompositionLocalProvider(
                LocalPreferences provides prefs,
                LocalConfig provides config,
                LocalProfileUtil provides profileUtilIn(units)
            ) {
                // Not PreviewTheme: it provides its own fake Preferences over these.
                MaterialTheme { ProvidePreferenceTheme { AdaptiveIntPreferenceItem(intKey = IntKey.AlertRapidFallDrop) } }
            }
        }
        compose.onNodeWithText("20 mg/dL", substring = true).assertIsDisplayed()
    }

    @Test
    fun `20 mg per dL round trips on an mg per dL phone`() = roundTrip(GlucoseUnit.MGDL)

    @Test
    fun `20 mg per dL round trips on an mmol phone`() = roundTrip(GlucoseUnit.MMOL)

    @Test
    fun `the drop key is in mg per dL inside the allowed range`() {
        assertThat(IntKey.AlertRapidFallDrop.unitType).isEqualTo(UnitType.MGDL)
        assertThat(IntKey.AlertRapidFallDrop.defaultValue).isEqualTo(30)
        assertThat(IntKey.AlertRapidFallDrop.min).isEqualTo(15)
        assertThat(IntKey.AlertRapidFallDrop.max).isEqualTo(60)
    }
}
