package app.aaps.wear.tile.source

import android.content.Context
import android.content.res.Resources
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.wear.AAPSLoggerTest
import app.aaps.wear.R
import app.aaps.wear.interaction.actions.BackgroundActionActivity
import app.aaps.wear.interaction.actions.BolusActivity
import app.aaps.wear.interaction.actions.CarbActivity
import app.aaps.wear.interaction.actions.ECarbActivity
import app.aaps.wear.interaction.actions.TempTargetActivity
import app.aaps.wear.interaction.actions.TreatmentActivity
import app.aaps.wear.interaction.actions.WizardActivity
import app.aaps.wear.tile.source.StaticTileSource.StaticAction
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * Tests for [ActionSource] which also exercises the shared [StaticTileSource] base logic
 * (setDefaultSettings / getActionFromPreference / empty fallback) through this concrete
 * subclass, since [StaticTileSource] is abstract.
 */
internal class ActionSourceTest {

    private val aapsLogger = AAPSLoggerTest()
    private val context: Context = mock()
    private val resources: Resources = mock()
    private val sp: SP = mock()

    // In-memory backing store so the source's getString/putString/contains behave like real prefs.
    private val store: MutableMap<String, String> = mutableMapOf()

    private lateinit var actionSource: ActionSource

    @BeforeEach
    fun setup() {
        // context.resources is used by getSelectedActions() / getActionFromPreference()
        whenever(context.resources).thenReturn(resources)

        // Distinct string per R.string.* used inside getActions() so results are unambiguous.
        whenever(resources.getString(R.string.menu_wizard_short)).thenReturn("Wizard")
        whenever(resources.getString(R.string.menu_wizard)).thenReturn("Wizard label")
        whenever(resources.getString(R.string.menu_treatment_short)).thenReturn("Treat")
        whenever(resources.getString(R.string.menu_treatment)).thenReturn("Treatment label")
        whenever(resources.getString(R.string.action_insulin)).thenReturn("Insulin")
        whenever(resources.getString(R.string.action_carbs)).thenReturn("Carbs")
        whenever(resources.getString(R.string.action_ecarbs)).thenReturn("eCarbs")
        whenever(resources.getString(R.string.menu_tempt)).thenReturn("TT")
        whenever(resources.getString(R.string.menu_tempt_long)).thenReturn("Temp target label")
        whenever(resources.getString(R.string.status_profile_switch)).thenReturn("Profile switch")

        // sp backed by the in-memory map
        doAnswer { invocation ->
            val key = invocation.getArgument<String>(0)
            store.containsKey(key)
        }.whenever(sp).contains(any<String>())

        doAnswer { invocation ->
            val key = invocation.getArgument<String>(0)
            val default = invocation.getArgument<String>(1)
            store[key] ?: default
        }.whenever(sp).getString(any<String>(), any<String>())

        doAnswer { invocation ->
            val key = invocation.getArgument<String>(0)
            val value = invocation.getArgument<String>(1)
            store[key] = value
            Unit
        }.whenever(sp).putString(any<String>(), any<String>())

        actionSource = ActionSource(context, sp, aapsLogger)
    }

    @Test
    fun getActionsReturnsSevenActionsWithExpectedSettingNames() {
        val actions = actionSource.getActions(resources)

        assertThat(actions).hasSize(7)
        assertThat(actions.map { it.settingName })
            .containsExactly("wizard", "treatment", "bolus", "carbs", "ecarbs", "temp_target", "profile_switch")
            .inOrder()
    }

    @Test
    fun getActionsMapsIconAndActivityClassPerSettingName() {
        val byName = actionSource.getActions(resources).associateBy { it.settingName }

        assertThat(byName.getValue("wizard").iconRes).isEqualTo(R.drawable.ic_calculator)
        assertThat(byName.getValue("wizard").activityClass).isEqualTo(WizardActivity::class.java.name)

        assertThat(byName.getValue("treatment").iconRes).isEqualTo(R.drawable.ic_bolus_carbs)
        assertThat(byName.getValue("treatment").activityClass).isEqualTo(TreatmentActivity::class.java.name)

        assertThat(byName.getValue("bolus").iconRes).isEqualTo(R.drawable.ic_bolus)
        assertThat(byName.getValue("bolus").activityClass).isEqualTo(BolusActivity::class.java.name)

        assertThat(byName.getValue("carbs").iconRes).isEqualTo(R.drawable.ic_carbs_orange)
        assertThat(byName.getValue("carbs").activityClass).isEqualTo(CarbActivity::class.java.name)

        assertThat(byName.getValue("ecarbs").iconRes).isEqualTo(R.drawable.ic_carbs_orange)
        assertThat(byName.getValue("ecarbs").activityClass).isEqualTo(ECarbActivity::class.java.name)

        assertThat(byName.getValue("temp_target").iconRes).isEqualTo(R.drawable.ic_temptarget_flat)
        assertThat(byName.getValue("temp_target").activityClass).isEqualTo(TempTargetActivity::class.java.name)

        assertThat(byName.getValue("profile_switch").iconRes).isEqualTo(R.drawable.ic_profile_switch)
        assertThat(byName.getValue("profile_switch").activityClass).isEqualTo(BackgroundActionActivity::class.java.name)
    }

    @Test
    fun defaultConfigMapsSlotsOneToFour() {
        assertThat(actionSource.getDefaultConfig())
            .containsExactlyEntriesIn(
                mapOf(
                    "tile_action_1" to "wizard",
                    "tile_action_2" to "treatment",
                    "tile_action_3" to "ecarbs",
                    "tile_action_4" to "temp_target"
                )
            )
    }

    @Test
    fun getSelectedActionsWritesDefaultsWhenFirstKeyAbsentAndReturnsConfiguredOrder() {
        // firstKey ('tile_action_1') absent -> setDefaultSettings should populate all 4 defaults
        assertThat(store).doesNotContainKey("tile_action_1")

        val selected = actionSource.getSelectedActions()

        // All 4 defaults persisted
        assertThat(store).containsEntry("tile_action_1", "wizard")
        assertThat(store).containsEntry("tile_action_2", "treatment")
        assertThat(store).containsEntry("tile_action_3", "ecarbs")
        assertThat(store).containsEntry("tile_action_4", "temp_target")

        // Returned actions follow the configured slot order
        assertThat(selected).hasSize(4)
        assertThat((selected as List<StaticAction>).map { it.settingName })
            .containsExactly("wizard", "treatment", "ecarbs", "temp_target")
            .inOrder()
    }

    @Test
    fun getSelectedActionsFiltersOutNoneAndUnknownSlots() {
        // firstKey present so defaults are NOT applied; we control every slot explicitly
        store["tile_action_1"] = "bolus"
        store["tile_action_2"] = "none"
        store["tile_action_3"] = "does_not_exist"
        store["tile_action_4"] = "carbs"

        val selected = actionSource.getSelectedActions()

        // 'none' and unknown slots filtered out, valid ones kept in order
        assertThat((selected as List<StaticAction>).map { it.settingName })
            .containsExactly("bolus", "carbs")
            .inOrder()
        // Defaults must not overwrite existing config
        assertThat(store).containsEntry("tile_action_1", "bolus")
        assertThat(store).containsEntry("tile_action_2", "none")
    }

    @Test
    fun getSelectedActionsFallsBackToFirstFourWhenNothingSelected() {
        // firstKey present (so no defaults written) but every slot resolves to nothing valid
        store["tile_action_1"] = "none"
        store["tile_action_2"] = "none"
        store["tile_action_3"] = "unknown"
        store["tile_action_4"] = "none"

        val selected = actionSource.getSelectedActions()

        // Empty result path -> fall back to getActions().take(4)
        assertThat(selected).hasSize(4)
        assertThat((selected as List<StaticAction>).map { it.settingName })
            .containsExactly("wizard", "treatment", "bolus", "carbs")
            .inOrder()
    }

    @Test
    fun getResourceReferencesMapsIconResOfAllActions() {
        val refs = actionSource.getResourceReferences(resources)

        assertThat(refs)
            .containsExactly(
                R.drawable.ic_calculator,
                R.drawable.ic_bolus_carbs,
                R.drawable.ic_bolus,
                R.drawable.ic_carbs_orange,
                R.drawable.ic_carbs_orange,
                R.drawable.ic_temptarget_flat,
                R.drawable.ic_profile_switch
            )
            .inOrder()
    }

    @Test
    fun getValidForFromBaseIsNull() {
        assertThat(actionSource.getValidFor()).isNull()
    }

    @Test
    fun preferencePrefixIsTileAction() {
        assertThat(actionSource.preferencePrefix).isEqualTo("tile_action_")
    }
}
