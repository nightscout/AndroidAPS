package app.aaps.ui.compose.alertKinds

import app.aaps.core.interfaces.alerts.AlertChoice
import app.aaps.core.interfaces.alerts.AlertGroup
import app.aaps.core.interfaces.alerts.AlertKind
import app.aaps.core.interfaces.alerts.AlertKindCatalogue
import app.aaps.core.interfaces.alerts.AlertOrigin
import app.aaps.core.interfaces.alerts.AlertSection
import app.aaps.core.interfaces.alerts.BgAlert
import app.aaps.core.interfaces.alerts.CoreAlert
import app.aaps.core.interfaces.alerts.NightscoutAlert
import app.aaps.core.keys.interfaces.TextRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AlertSettingsSectionsTest {

    private class Kind(
        override val source: String,
        name: String,
        override val origin: AlertOrigin,
        override val group: AlertGroup?,
        override val isProtected: Boolean = group?.isProtected ?: false
    ) : AlertKind {

        override val id: String = "$source.$name"
        override val title: TextRef = TextRef.Literal(name)
    }

    private class Catalogue(name: String, override val kinds: List<AlertKind>, private val used: Boolean = true) : AlertKindCatalogue {

        override val title: TextRef = TextRef.Literal(name)
        override fun inUse(): Boolean = used
    }

    private val occlusion = Kind("patch", "occlusion", AlertOrigin.PUMP, AlertGroup.PUMP_CRITICAL)
    private val bgCheck = Kind("patch", "bg_check", AlertOrigin.PUMP, AlertGroup.PUMP_ACTION)
    private val lgs = Kind("patch", "lgs", AlertOrigin.PUMP, AlertGroup.PUMP_INFO)
    private val calibrate = Kind("cgm", "calibrate", AlertOrigin.CGM, AlertGroup.CGM_ACTION)

    private val app = Catalogue("AAPS", CoreAlert.entries)
    private val bg = Catalogue("BG alarms", BgAlert.entries)
    private val nightscout = Catalogue("Nightscout", NightscoutAlert.entries)
    private val patch = Catalogue("Patch", listOf(occlusion, bgCheck, lgs))
    private val cgm = Catalogue("Sensor", listOf(calibrate))

    private fun sections(choices: Map<String, AlertChoice> = emptyMap(), vararg catalogues: AlertKindCatalogue = arrayOf(nightscout, patch, cgm, bg, app)) =
        alertSettingsSections(catalogues.toList(), choices)

    private fun List<AlertSettingsSection>.of(section: AlertSection) = single { it.section == section }

    @Test
    fun theSectionsComeInTheirOrder() {
        assertEquals(listOf(AlertSection.PUMP, AlertSection.CGM, AlertSection.BG, AlertSection.APP), sections().map { it.section })
    }

    /** No CGM driver with alerts of its own in use: there is nothing to set for it. */
    @Test
    fun aSectionWithNoKindInUseIsLeftOut() {
        val sections = sections(catalogues = arrayOf(app, bg, Catalogue("Sensor", listOf(calibrate), used = false)))

        assertEquals(listOf(AlertSection.PUMP, AlertSection.BG, AlertSection.APP), sections.map { it.section })
    }

    /** The pump is set by level, not by alarm name: the app's pump alerts and the driver's share the rows. */
    @Test
    fun thePumpSectionHasItsLevels() {
        val pump = sections().of(AlertSection.PUMP)

        assertEquals(listOf(AlertGroup.PUMP_CRITICAL, AlertGroup.PUMP_ACTION, AlertGroup.PUMP_INFO), pump.rows.map { it.setting })
        assertEquals(listOf(TextRef.Literal("Patch")), pump.devices)
    }

    /** The app alone raises no info alert about the pump: an Info row would set nothing. */
    @Test
    fun aGroupWithNoKindInUseHasNoRow() {
        val pump = sections(catalogues = arrayOf(app, bg)).of(AlertSection.PUMP)

        assertEquals(listOf(AlertGroup.PUMP_CRITICAL, AlertGroup.PUMP_ACTION), pump.rows.map { it.setting })
        assertTrue(pump.devices.isEmpty())
    }

    /** Urgent low first, as it always rings; the lows and highs by their group; missed readings on its own. */
    @Test
    fun theBgSectionHasUrgentLowTheGroupsAndMissedReadings() {
        val rows = sections().of(AlertSection.BG).rows

        assertEquals(listOf(BgAlert.URGENT_LOW, AlertGroup.BG_LOWS, AlertGroup.BG_HIGHS, BgAlert.STALE), rows.map { it.setting })
    }

    @Test
    fun theAppSectionHasItsOwnKindsAndNightscoutLast() {
        val rows = sections().of(AlertSection.APP).rows

        assertEquals(
            listOf(CoreAlert.EAT_REMINDER, CoreAlert.AUTOMATION_ALARM, CoreAlert.CARBS_NOT_SAVED, CoreAlert.HARD_LIMIT, CoreAlert.CARBS_REQUIRED, NightscoutAlert.ANNOUNCEMENT),
            rows.map { it.setting }
        )
    }

    @Test
    fun eachRowShowsWhatThisPhoneChoseAndRingsByDefault() {
        val sections = sections(mapOf(AlertGroup.PUMP_ACTION.id to AlertChoice.SILENT, CoreAlert.CARBS_REQUIRED.id to AlertChoice.OFF))

        assertEquals(AlertChoice.SILENT, sections.of(AlertSection.PUMP).rows.single { it.setting == AlertGroup.PUMP_ACTION }.choice)
        assertEquals(AlertChoice.RING, sections.of(AlertSection.PUMP).rows.single { it.setting == AlertGroup.PUMP_INFO }.choice)
        assertEquals(AlertChoice.OFF, sections.of(AlertSection.APP).rows.single { it.setting == CoreAlert.CARBS_REQUIRED }.choice)
    }

    /** A protected setting always rings, so a choice stored for it is not shown as if it were used. */
    @Test
    fun aProtectedRowShowsRingWhateverIsStored() {
        val rows = sections(mapOf(AlertGroup.PUMP_CRITICAL.id to AlertChoice.OFF)).of(AlertSection.PUMP).rows

        assertEquals(AlertChoice.RING, rows.single { it.setting == AlertGroup.PUMP_CRITICAL }.choice)
    }

    /** An info card has no sound: Show or Off, and no sound to pick or test. */
    @Test
    fun anInfoGroupHasNoSilentAndNoSound() {
        val info = sections().of(AlertSection.PUMP).rows.single { it.setting == AlertGroup.PUMP_INFO }

        assertEquals(listOf(AlertChoice.RING, AlertChoice.OFF), info.choices)
        assertNull(info.testKind)
    }

    @Test
    fun aGroupIsTestedWithOneOfItsKinds() {
        val action = sections().of(AlertSection.PUMP).rows.single { it.setting == AlertGroup.PUMP_ACTION }

        assertEquals(AlertGroup.PUMP_ACTION, action.testKind?.group)
    }

    /** A kind's own choice, also Ring, is an exception: it is listed, and it is no longer offered to add. */
    @Test
    fun aKindWithItsOwnChoiceIsAnException() {
        val pump = sections(mapOf(AlertGroup.PUMP_ACTION.id to AlertChoice.SILENT, bgCheck.id to AlertChoice.RING)).of(AlertSection.PUMP)

        assertEquals(listOf(AlertExceptionRow(bgCheck, AlertChoice.RING, AlertGroup.PUMP_ACTION)), pump.exceptions)
        assertTrue(bgCheck !in pump.candidates)
        assertTrue(lgs in pump.candidates)
    }

    /** A protected kind cannot be set apart: it would only look silenced. */
    @Test
    fun aProtectedKindIsNeverAnException() {
        val pump = sections(mapOf(occlusion.id to AlertChoice.OFF)).of(AlertSection.PUMP)

        assertTrue(pump.exceptions.isEmpty())
        assertTrue(occlusion !in pump.candidates)
        assertTrue(CoreAlert.BOLUS_FAILED !in pump.candidates)
    }

    /** The app's kinds of their own are rows already: there is nothing to set apart in that section. */
    @Test
    fun theAppSectionHasNoExceptions() {
        val app = sections().of(AlertSection.APP)

        assertTrue(app.candidates.isEmpty())
    }

    @Test
    fun aNewExceptionStartsOnTheOtherSideOfItsGroup() {
        assertEquals(AlertChoice.SILENT, firstExceptionChoice(AlertGroup.PUMP_ACTION, AlertChoice.RING))
        assertEquals(AlertChoice.OFF, firstExceptionChoice(AlertGroup.PUMP_INFO, AlertChoice.RING))
        assertEquals(AlertChoice.RING, firstExceptionChoice(AlertGroup.PUMP_ACTION, AlertChoice.OFF))
    }

    /** The line that keeps a choice from being forgotten: an exception set to Ring silences nothing. */
    @Test
    fun silentOrOffCountsWhatIsNotRingAndThePauses() {
        val choices = mapOf(AlertGroup.PUMP_INFO.id to AlertChoice.OFF, bgCheck.id to AlertChoice.RING, CoreAlert.HARD_LIMIT.id to AlertChoice.SILENT)

        assertEquals(2, silentOrOff(choices, emptyMap()))
        assertEquals(3, silentOrOff(choices, mapOf(BgAlert.LOW.id to 1L)))
        // A kind both silent and paused is one
        assertEquals(2, silentOrOff(choices, mapOf(CoreAlert.HARD_LIMIT.id to 1L)))
    }
}
