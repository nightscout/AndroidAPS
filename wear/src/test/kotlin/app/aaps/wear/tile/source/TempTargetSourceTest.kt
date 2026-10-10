package app.aaps.wear.tile.source

import android.content.Context
import android.content.res.Resources
import app.aaps.core.interfaces.rx.weardata.EventData
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.wear.AAPSLoggerTest
import app.aaps.wear.R
import app.aaps.wear.interaction.actions.BackgroundActionActivity
import app.aaps.wear.interaction.actions.TempTargetActivity
import app.aaps.wear.tile.source.StaticTileSource.StaticAction
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * Tests for [TempTargetSource] — the Temp-Target tile action source.
 *
 * Focuses on TempTargetSource-specific outputs (the five [StaticAction]s and their
 * [EventData.ActionTempTargetPreCheck] payloads, the manual action carrying a null action,
 * the default-config keys, and getValidFor()==null) while also exercising the shared
 * [StaticTileSource] flow (default seeding + preference lookup + fallback) end-to-end.
 */
class TempTargetSourceTest {

    private val aapsLogger = AAPSLoggerTest()
    private val context: Context = mock()
    private val resources: Resources = mock()
    private val sp: SP = mock()

    // in-memory backing store for the SP string preferences the code touches
    private val store = mutableMapOf<String, String>()

    private lateinit var source: TempTargetSource

    // Distinct realistic stubs so we can assert the exact strings ended up on the right actions
    private val confirmation = "Set temp target?"
    private val activityText = "Activity"
    private val eatingText = "Eating Soon"
    private val hypoText = "Hypo"
    private val manualText = "Manual"
    private val cancelText = "Cancel"

    @BeforeEach
    fun setup() {
        // Resource strings used by getActions(resources)
        whenever(resources.getString(R.string.action_tempt_confirmation)).thenReturn(confirmation)
        whenever(resources.getString(R.string.temp_target_activity)).thenReturn(activityText)
        whenever(resources.getString(R.string.temp_target_eating_soon)).thenReturn(eatingText)
        whenever(resources.getString(R.string.temp_target_hypo)).thenReturn(hypoText)
        whenever(resources.getString(R.string.temp_target_manual)).thenReturn(manualText)
        whenever(resources.getString(R.string.temp_target_cancel)).thenReturn(cancelText)

        // getSelectedActions() reaches through context.resources
        whenever(context.resources).thenReturn(resources)

        // SP backed by an in-memory map for contains/getString/putString
        doAnswer { store.containsKey(it.getArgument<String>(0)) }
            .whenever(sp).contains(any<String>())
        doAnswer { store[it.getArgument<String>(0)] ?: it.getArgument<String>(1) }
            .whenever(sp).getString(any<String>(), any())
        doAnswer {
            store[it.getArgument<String>(0)] = it.getArgument<String>(1)
            null
        }.whenever(sp).putString(any<String>(), any())

        source = TempTargetSource(context, sp, aapsLogger)
    }

    private fun ttCommand(action: EventData?): EventData.ActionTempTargetPreCheck.TempTargetCommand? =
        (action as? EventData.ActionTempTargetPreCheck)?.command

    @Test
    fun getActionsExposesFiveActionsWithExpectedSettingNamesInOrder() {
        val actions = source.getActions(resources)
        assertThat(actions.map { it.settingName })
            .containsExactly("activity", "eating_soon", "hypo", "manual", "cancel")
            .inOrder()
    }

    @Test
    fun getActionsMapsPresetActionsToTempTargetCommands() {
        val actions = source.getActions(resources).associateBy { it.settingName }

        assertThat(ttCommand(actions.getValue("activity").action))
            .isEqualTo(EventData.ActionTempTargetPreCheck.TempTargetCommand.PRESET_ACTIVITY)
        assertThat(ttCommand(actions.getValue("eating_soon").action))
            .isEqualTo(EventData.ActionTempTargetPreCheck.TempTargetCommand.PRESET_EATING)
        assertThat(ttCommand(actions.getValue("hypo").action))
            .isEqualTo(EventData.ActionTempTargetPreCheck.TempTargetCommand.PRESET_HYPO)
        assertThat(ttCommand(actions.getValue("cancel").action))
            .isEqualTo(EventData.ActionTempTargetPreCheck.TempTargetCommand.CANCEL)
    }

    @Test
    fun manualActionHasNullActionAndTargetsTempTargetActivity() {
        val manual = source.getActions(resources).single { it.settingName == "manual" }

        assertThat(manual.action).isNull()
        assertThat(manual.activityClass).isEqualTo(TempTargetActivity::class.java.name)
        assertThat(manual.message).isNull()
        assertThat(manual.buttonText).isEqualTo(manualText)
    }

    @Test
    fun presetAndCancelActionsShareConfirmationMessageAndUseBackgroundActivity() {
        val actions = source.getActions(resources).associateBy { it.settingName }

        for (name in listOf("activity", "eating_soon", "hypo", "cancel")) {
            val action = actions.getValue(name)
            assertThat(action.message).isEqualTo(confirmation)
            assertThat(action.activityClass).isEqualTo(BackgroundActionActivity::class.java.name)
        }
        // button texts are wired through to the right actions
        assertThat(actions.getValue("activity").buttonText).isEqualTo(activityText)
        assertThat(actions.getValue("eating_soon").buttonText).isEqualTo(eatingText)
        assertThat(actions.getValue("hypo").buttonText).isEqualTo(hypoText)
        assertThat(actions.getValue("cancel").buttonText).isEqualTo(cancelText)
    }

    @Test
    fun getDefaultConfigMapsSlotsOneToFourToPresets() {
        assertThat(source.getDefaultConfig())
            .containsExactlyEntriesIn(
                mapOf(
                    "tile_tempt_1" to "activity",
                    "tile_tempt_2" to "eating_soon",
                    "tile_tempt_3" to "hypo",
                    "tile_tempt_4" to "manual"
                )
            )
    }

    @Test
    fun getValidForIsNull() {
        assertThat(source.getValidFor()).isNull()
    }

    @Test
    fun getSelectedActionsSeedsDefaultsWhenPreferencesEmptyThenReturnsThem() {
        // store starts empty -> setDefaultSettings() should seed tile_tempt_1..4
        val selected = source.getSelectedActions()

        // defaults were persisted
        assertThat(store).containsEntry("tile_tempt_1", "activity")
        assertThat(store).containsEntry("tile_tempt_2", "eating_soon")
        assertThat(store).containsEntry("tile_tempt_3", "hypo")
        assertThat(store).containsEntry("tile_tempt_4", "manual")

        // and the four seeded actions are returned in slot order (manual maps to null action)
        assertThat(selected.map { (it.action as? EventData.ActionTempTargetPreCheck)?.command })
            .containsExactly(
                EventData.ActionTempTargetPreCheck.TempTargetCommand.PRESET_ACTIVITY,
                EventData.ActionTempTargetPreCheck.TempTargetCommand.PRESET_EATING,
                EventData.ActionTempTargetPreCheck.TempTargetCommand.PRESET_HYPO,
                null
            ).inOrder()
    }

    @Test
    fun getSelectedActionsHonoursExistingPreferencesWithoutReseeding() {
        // first key already present -> defaults are NOT applied, values are read as-is
        store["tile_tempt_1"] = "cancel"
        store["tile_tempt_2"] = "hypo"
        store["tile_tempt_3"] = "none"   // unknown -> filtered out
        store["tile_tempt_4"] = "manual"

        val selected = source.getSelectedActions()

        // tile_tempt_2/tile_tempt_3 defaults must not have been written over
        assertThat(store["tile_tempt_2"]).isEqualTo("hypo")
        assertThat(store["tile_tempt_3"]).isEqualTo("none")

        // "none" (slot 3) is filtered out -> 3 actions in slot order
        assertThat(selected.map { (it.action as? EventData.ActionTempTargetPreCheck)?.command })
            .containsExactly(
                EventData.ActionTempTargetPreCheck.TempTargetCommand.CANCEL,
                EventData.ActionTempTargetPreCheck.TempTargetCommand.PRESET_HYPO,
                null // manual
            ).inOrder()
    }

    @Test
    fun getSelectedActionsFallsBackToFirstFourActionsWhenNoPreferenceMatches() {
        // first key present so seeding is skipped, but every slot points at an unknown name
        store["tile_tempt_1"] = "does_not_exist"
        store["tile_tempt_2"] = "does_not_exist"
        store["tile_tempt_3"] = "does_not_exist"
        store["tile_tempt_4"] = "does_not_exist"

        val selected: List<StaticAction> = source.getSelectedActions().filterIsInstance<StaticAction>()

        // fallback returns getActions().take(4) -> first four of the five actions
        assertThat(selected).hasSize(4)
        assertThat(selected.map { it.settingName })
            .containsExactly("activity", "eating_soon", "hypo", "manual")
            .inOrder()
    }

    @Test
    fun getResourceReferencesReturnsIconForEachAction() {
        val refs = source.getResourceReferences(resources)
        assertThat(refs).hasSize(5)
        assertThat(refs).containsExactlyElementsIn(source.getActions(resources).map { it.iconRes })
    }
}
