package app.aaps.ui.search

import app.aaps.core.interfaces.alerts.CgmAlarmType
import app.aaps.core.ui.compose.preference.PreferenceSubScreenDef
import app.aaps.core.ui.search.SearchableItem
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

/**
 * The settings screen draws two levels: a card, and the sections inside it. A section inside a
 * section is drawn as nothing (`AdaptivePreferenceList`), so every setting in it can be found by the
 * search but never opened. That hid every CGM alarm setting: the alarms were a third level inside
 * the local alerts card, and no alarm could be switched on.
 */
class SettingsScreenDepthTest {

    private val searchables = BuiltInSearchables(mock(), mock(), mock(), mock(), mock(), mock())

    /** The cards [app.aaps.ui.compose.preferences.AllPreferencesScreen] shows from [BuiltInSearchables]. */
    private val cards = with(searchables) {
        listOf(general, appearance, protection, pump, alerts, cgmAlarms, maintenance, automation)
    }

    private fun depth(screen: PreferenceSubScreenDef): Int =
        1 + (screen.items.filterIsInstance<PreferenceSubScreenDef>().maxOfOrNull { depth(it) } ?: 0)

    @Test
    fun everyBuiltInCardFitsTheTwoLevelsTheScreenDraws() {
        cards.forEach { card ->
            assertTrue(depth(card) <= 2, "${card.key} has ${depth(card)} levels, the screen draws 2")
        }
    }

    @Test
    fun everyCgmAlarmHasItsOwnSectionInTheCgmAlarmsCard() {
        val sections = searchables.cgmAlarms.items.filterIsInstance<PreferenceSubScreenDef>().map { it.key }
        // As sets: the menu groups the alarms by theme, the enum orders them by priority.
        assertEquals(CgmAlarmType.entries.map { "cgm_alarm_${it.id}" }.toSet(), sections.toSet())
        assertEquals(CgmAlarmType.entries.size, sections.size)
    }

    @Test
    fun theCgmAlarmsAreNotInsideTheLocalAlertsCard() {
        assertFalse(searchables.alerts.items.any { it is PreferenceSubScreenDef && it.key == "cgm_alarms" })
    }

    @Test
    fun theCgmAlarmsCardCanBeFoundBySearch() {
        val categories = searchables.getSearchableItems().filterIsInstance<SearchableItem.Category>()
        assertTrue(categories.any { it.screenDef.key == "cgm_alarms" })
    }
}
