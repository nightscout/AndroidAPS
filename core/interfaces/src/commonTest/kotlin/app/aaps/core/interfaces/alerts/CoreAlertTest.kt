package app.aaps.core.interfaces.alerts

import app.aaps.core.interfaces.ui.UiInteraction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CoreAlertTest {

    /** The ids are stored with the user's choice: a renamed id would quietly drop what the user chose for it. */
    @Test
    fun theIdsStayAsTheyAre() {
        assertEquals(
            listOf("aaps.command_failed", "aaps.insulin_change_failed", "aaps.eat_reminder", "aaps.automation_alarm", "aaps.pump_driver_alarm"),
            CoreAlert.entries.map { it.id }
        )
    }

    @Test
    fun everyKindIsTheAppsOwn() {
        CoreAlert.entries.forEach {
            assertEquals(UiInteraction.SOURCE, it.source)
            assertEquals(AlertOrigin.APP, it.origin)
        }
    }

    @Test
    fun eachKeyOfTheAppHasItsKind() {
        assertEquals(CoreAlert.COMMAND_FAILED, CoreAlert.ofRunAlarmKey(UiInteraction.KEY_COMMAND_FAILED))
        assertEquals(CoreAlert.INSULIN_CHANGE_FAILED, CoreAlert.ofRunAlarmKey(UiInteraction.KEY_INSULIN_CHANGE_FAILED))
        assertEquals(CoreAlert.EAT_REMINDER, CoreAlert.ofRunAlarmKey(UiInteraction.KEY_EAT_REMINDER))
        assertEquals(CoreAlert.AUTOMATION_ALARM, CoreAlert.ofRunAlarmKey(UiInteraction.KEY_AUTOMATION_ALARM))
    }

    /** A driver's own key: the app cannot tell whether that alarm is safe to silence. */
    @Test
    fun aKeyOfADriverIsAProtectedDriverAlarm() {
        assertEquals(CoreAlert.PUMP_DRIVER_ALARM, CoreAlert.ofRunAlarmKey("danars-time-diff"))
        assertTrue(CoreAlert.PUMP_DRIVER_ALARM.isProtected)
    }

    @Test
    fun theWrongBasalAfterAnInsulinChangeIsProtectedAndTheOthersAreNot() {
        assertTrue(CoreAlert.INSULIN_CHANGE_FAILED.isProtected)
        assertFalse(CoreAlert.COMMAND_FAILED.isProtected)
        assertFalse(CoreAlert.EAT_REMINDER.isProtected)
        assertFalse(CoreAlert.AUTOMATION_ALARM.isProtected)
    }
}
