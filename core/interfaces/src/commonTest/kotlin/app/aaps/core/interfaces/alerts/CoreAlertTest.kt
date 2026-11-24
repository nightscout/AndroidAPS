package app.aaps.core.interfaces.alerts

import app.aaps.core.interfaces.ui.UiInteraction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CoreAlertTest {

    /** The ids are stored with the user's choice: a renamed id would quietly drop what the user chose for it. */
    @Test
    fun theIdsStayAsTheyAre() {
        assertEquals(
            listOf(
                "aaps.command_failed", "aaps.insulin_change_failed", "aaps.eat_reminder", "aaps.automation_alarm",
                "aaps.profile_update_failed", "aaps.basal_far_from_profile", "aaps.bolus_failed", "aaps.bolus_cancelled",
                "aaps.carbs_not_saved", "aaps.pump_unreachable", "aaps.pump_driver_not_responding", "aaps.zero_delivery_not_set",
                "aaps.hard_limit", "aaps.carbs_required", "aaps.pump_driver_alarm"
            ),
            CoreAlert.entries.map { it.id }
        )
    }

    /** One alert of a kind at a time: its key is the kind's, and the alert carries the kind's title. */
    @Test
    fun anAlertOfAKindHasTheKeyAndTitleOfTheKind() {
        val alert = CoreAlert.BOLUS_FAILED.alert(AlertSeverity.CRITICAL, AlertLifetime.EVENT, CoreAlert.BOLUS_FAILED.title, raisedAt = 1000L)
        assertEquals("bolus_failed", alert.key)
        assertEquals(CoreAlert.BOLUS_FAILED.title, alert.title)
        assertEquals(UiInteraction.SOURCE, alert.source)
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

    /** Protected: insulin delivery that is not safe, or insulin the user asked for that was not given. */
    @Test
    fun onlyTheSafetyKindsAreProtected() {
        assertEquals(
            setOf(CoreAlert.INSULIN_CHANGE_FAILED, CoreAlert.BOLUS_FAILED, CoreAlert.ZERO_DELIVERY_NOT_SET, CoreAlert.PUMP_DRIVER_ALARM),
            CoreAlert.entries.filter { it.isProtected }.toSet()
        )
    }
}
