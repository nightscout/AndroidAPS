package app.aaps.wear.tile.source

import android.content.Context
import android.content.res.Resources
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.wear.AAPSLoggerTest
import app.aaps.wear.R
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Tests for the shared logic in [StaticTileSource]. As advised by the survey, the abstract base is
 * exercised through the concrete [ActionSource] (a real production subclass) instead of a synthetic
 * hand-rolled fixture, so the wiring of [ActionSource.getActions], [ActionSource.preferencePrefix]
 * and [ActionSource.getDefaultConfig] is covered together with the base behaviour:
 *
 *  - [StaticTileSource.getSelectedActions] default seeding (only when the first default key is absent),
 *    preference->action mapping by settingName, filtering of "none"/unknown slots, empty fallback to
 *    the first four actions, and 1..4 ordering.
 *  - [StaticTileSource.getValidFor] always null.
 */
class StaticTileSourceTest {

    private val aapsLogger = AAPSLoggerTest()
    private val context: Context = mock()
    private val resources: Resources = mock()
    private val sp: SP = mock()

    private lateinit var source: ActionSource

    // The settingNames produced by ActionSource.getActions(), in order.
    private val orderedSettingNames = listOf(
        "wizard", "treatment", "bolus", "carbs", "ecarbs", "temp_target", "profile_switch"
    )

    @BeforeEach
    fun setup() {
        whenever(context.resources).thenReturn(resources)
        // ActionSource.getActions() reads every button/label string from resources; stub each with a
        // deterministic value so the non-null String returns never NPE under lenient mocking.
        whenever(resources.getString(R.string.menu_wizard_short)).thenReturn("wizard")
        whenever(resources.getString(R.string.menu_wizard)).thenReturn("Wizard")
        whenever(resources.getString(R.string.menu_treatment_short)).thenReturn("treat")
        whenever(resources.getString(R.string.menu_treatment)).thenReturn("Treatment")
        whenever(resources.getString(R.string.action_insulin)).thenReturn("Insulin")
        whenever(resources.getString(R.string.action_carbs)).thenReturn("Carbs")
        whenever(resources.getString(R.string.action_ecarbs)).thenReturn("eCarbs")
        whenever(resources.getString(R.string.menu_tempt)).thenReturn("TT")
        whenever(resources.getString(R.string.menu_tempt_long)).thenReturn("Temp target")
        whenever(resources.getString(R.string.status_profile_switch)).thenReturn("Profile")

        source = ActionSource(context, sp, aapsLogger)
    }

    /** All four configured slots resolve to distinct actions, kept in slot order 1..4. */
    @Test
    fun getSelectedActionsMapsEachSlotBySettingName() {
        whenever(sp.contains("tile_action_1")).thenReturn(true) // defaults already present
        whenever(sp.getString(eq("tile_action_1"), any())).thenReturn("wizard")
        whenever(sp.getString(eq("tile_action_2"), any())).thenReturn("treatment")
        whenever(sp.getString(eq("tile_action_3"), any())).thenReturn("ecarbs")
        whenever(sp.getString(eq("tile_action_4"), any())).thenReturn("temp_target")

        val actions = source.getSelectedActions()

        assertThat(actions).hasSize(4)
        assertThat(actions.map { it.buttonText }).containsExactly("wizard", "treat", "eCarbs", "TT").inOrder()
    }

    /** Slots resolving to "none" or an unknown value are dropped; the survivors keep their order. */
    @Test
    fun getSelectedActionsFiltersNoneAndUnknownSlots() {
        whenever(sp.contains("tile_action_1")).thenReturn(true)
        whenever(sp.getString(eq("tile_action_1"), any())).thenReturn("bolus")
        whenever(sp.getString(eq("tile_action_2"), any())).thenReturn("none")
        whenever(sp.getString(eq("tile_action_3"), any())).thenReturn("does_not_exist")
        whenever(sp.getString(eq("tile_action_4"), any())).thenReturn("carbs")

        val actions = source.getSelectedActions()

        assertThat(actions).hasSize(2)
        assertThat(actions.map { it.buttonText }).containsExactly("Insulin", "Carbs").inOrder()
    }

    /** When no slot resolves to a known action, fall back to the first four available actions. */
    @Test
    fun getSelectedActionsFallsBackToFirstFourWhenAllSlotsEmpty() {
        whenever(sp.contains("tile_action_1")).thenReturn(true)
        whenever(sp.getString(any<String>(), any())).thenReturn("none")

        val actions = source.getSelectedActions()

        assertThat(actions).hasSize(4)
        // getActions().take(4) => first four settingNames in declaration order.
        assertThat(actions.map { it.buttonText }).containsExactly("wizard", "treat", "Insulin", "Carbs").inOrder()
    }

    /** The default config is written exactly once, only when the first default key is absent. */
    @Test
    fun getSelectedActionsSeedsDefaultsWhenFirstKeyAbsent() {
        whenever(sp.contains("tile_action_1")).thenReturn(false) // not seeded yet
        whenever(sp.getString(any<String>(), any())).thenReturn("none")

        source.getSelectedActions()

        verify(sp).putString("tile_action_1", "wizard")
        verify(sp).putString("tile_action_2", "treatment")
        verify(sp).putString("tile_action_3", "ecarbs")
        verify(sp).putString("tile_action_4", "temp_target")
    }

    /** Defaults must not be re-written once the first default key already exists. */
    @Test
    fun getSelectedActionsDoesNotSeedWhenFirstKeyPresent() {
        whenever(sp.contains("tile_action_1")).thenReturn(true)
        whenever(sp.getString(any<String>(), any())).thenReturn("none")

        source.getSelectedActions()

        verify(sp, never()).putString(any<String>(), any())
    }

    /** getActionFromPreference queries slots 1..4 using preferencePrefix + index and default "none". */
    @Test
    fun getSelectedActionsQueriesEachSlotWithPrefixedKeyAndNoneDefault() {
        whenever(sp.contains("tile_action_1")).thenReturn(true)
        whenever(sp.getString(any<String>(), any())).thenReturn("none")

        source.getSelectedActions()

        verify(sp).getString("tile_action_1", "none")
        verify(sp).getString("tile_action_2", "none")
        verify(sp).getString("tile_action_3", "none")
        verify(sp).getString("tile_action_4", "none")
    }

    /** getValidFor is always null for static tile sources. */
    @Test
    fun getValidForIsNull() {
        assertThat(source.getValidFor()).isNull()
    }
}
