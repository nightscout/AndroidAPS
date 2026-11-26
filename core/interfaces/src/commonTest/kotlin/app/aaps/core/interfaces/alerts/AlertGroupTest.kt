package app.aaps.core.interfaces.alerts

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull

class AlertGroupTest {

    /** The ids are stored with the user's choice and sound: a renamed id would quietly drop them. */
    @Test
    fun theIdsStayAsTheyAre() {
        assertEquals(
            listOf("pump_critical", "pump_action", "pump_info", "cgm_critical", "cgm_action", "cgm_info", "bg_lows", "bg_highs").map { "group.$it" },
            AlertGroup.entries.map { it.id }
        )
    }

    /** Decision 14: what affects dosing always rings; an action the user has to take; the rest is info. */
    @Test
    fun aDevicesKindIsInTheGroupOfWhatItDoesToDosing() {
        assertEquals(AlertGroup.PUMP_CRITICAL, AlertGroup.of(AlertOrigin.PUMP, listOf(DosingEffect.INFO, DosingEffect.SOON)))
        assertEquals(AlertGroup.PUMP_CRITICAL, AlertGroup.of(AlertOrigin.PUMP, listOf(DosingEffect.NOW)))
        assertEquals(AlertGroup.PUMP_ACTION, AlertGroup.of(AlertOrigin.PUMP, listOf(DosingEffect.INFO, DosingEffect.ACTION)))
        assertEquals(AlertGroup.PUMP_INFO, AlertGroup.of(AlertOrigin.PUMP, listOf(DosingEffect.INFO)))
        assertEquals(AlertGroup.CGM_CRITICAL, AlertGroup.of(AlertOrigin.CGM, listOf(DosingEffect.NOW)))
        assertEquals(AlertGroup.CGM_ACTION, AlertGroup.of(AlertOrigin.CGM, listOf(DosingEffect.ACTION)))
        assertEquals(AlertGroup.CGM_INFO, AlertGroup.of(AlertOrigin.CGM, listOf(DosingEffect.INFO)))
    }

    @Test
    fun onlyAPumpOrACgmHasDeviceGroups() {
        assertFailsWith<IllegalArgumentException> { AlertGroup.of(AlertOrigin.APP, listOf(DosingEffect.INFO)) }
    }

    /** Only the critical groups always ring, and only the info groups have no sound. */
    @Test
    fun theCriticalGroupsAreProtectedAndTheInfoGroupsAreQuiet() {
        assertEquals(setOf(AlertGroup.PUMP_CRITICAL, AlertGroup.CGM_CRITICAL), AlertGroup.entries.filter { it.isProtected }.toSet())
        assertEquals(setOf(AlertGroup.PUMP_INFO, AlertGroup.CGM_INFO), AlertGroup.entries.filterNot { it.makesSound }.toSet())
        assertEquals(listOf(AlertChoice.RING, AlertChoice.OFF), AlertGroup.PUMP_INFO.choices)
        assertEquals(AlertChoice.entries, AlertGroup.PUMP_ACTION.choices)
    }

    /** A kind in a group that rings whatever is chosen would show a choice that is not used, or the other way round. */
    @Test
    fun aKindIsProtectedExactlyWhenItsGroupIs() {
        (CoreAlert.entries + BgAlert.entries).forEach { kind ->
            kind.group?.let { assertEquals(it.isProtected, kind.isProtected, kind.id) }
        }
    }

    /** For the user they are the pump's alerts, whoever raises them. */
    @Test
    fun theAppsAlertsAboutThePumpAreInThePumpGroups() {
        assertEquals(
            setOf(CoreAlert.INSULIN_CHANGE_FAILED, CoreAlert.BOLUS_FAILED, CoreAlert.ZERO_DELIVERY_NOT_SET, CoreAlert.PUMP_DRIVER_ALARM),
            CoreAlert.entries.filter { it.group == AlertGroup.PUMP_CRITICAL }.toSet()
        )
        assertEquals(
            setOf(
                CoreAlert.COMMAND_FAILED, CoreAlert.PROFILE_UPDATE_FAILED, CoreAlert.BASAL_FAR_FROM_PROFILE, CoreAlert.BOLUS_CANCELLED,
                CoreAlert.PUMP_UNREACHABLE, CoreAlert.PUMP_DRIVER_NOT_RESPONDING
            ),
            CoreAlert.entries.filter { it.group == AlertGroup.PUMP_ACTION }.toSet()
        )
        // The others the user made, or they ask for something only the user knows: a row each
        listOf(CoreAlert.EAT_REMINDER, CoreAlert.AUTOMATION_ALARM, CoreAlert.CARBS_NOT_SAVED, CoreAlert.CARBS_REQUIRED).forEach {
            assertNull(it.group, it.id)
            assertEquals(AlertSection.APP, it.section, it.id)
        }
    }

    /** The lows and highs are what their Pause buttons pause; urgent low and missed readings are rows of their own. */
    @Test
    fun theBgGroupsAreWhatThePauseButtonsPause() {
        assertEquals(BgAlert.LOWS.toSet(), BgAlert.entries.filter { it.group == AlertGroup.BG_LOWS }.toSet())
        assertEquals(BgAlert.HIGHS.toSet(), BgAlert.entries.filter { it.group == AlertGroup.BG_HIGHS }.toSet())
        assertNull(BgAlert.URGENT_LOW.group)
        assertNull(BgAlert.STALE.group)
        BgAlert.entries.forEach { assertEquals(AlertSection.BG, it.section, it.id) }
    }

    @Test
    fun aNightscoutAnnouncementIsARowOfTheApp() {
        assertNull(NightscoutAlert.ANNOUNCEMENT.group)
        assertEquals(AlertSection.APP, NightscoutAlert.ANNOUNCEMENT.section)
        assertFalse(NightscoutAlert.ANNOUNCEMENT.isProtected)
    }
}
