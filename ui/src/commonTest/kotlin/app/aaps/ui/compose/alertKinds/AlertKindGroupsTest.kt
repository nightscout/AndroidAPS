package app.aaps.ui.compose.alertKinds

import app.aaps.core.interfaces.alerts.AlertChoice
import app.aaps.core.interfaces.alerts.AlertKind
import app.aaps.core.interfaces.alerts.AlertKindCatalogue
import app.aaps.core.interfaces.alerts.AlertOrigin
import app.aaps.core.keys.interfaces.TextRef
import kotlin.test.Test
import kotlin.test.assertEquals

class AlertKindGroupsTest {

    private class Kind(override val source: String, name: String, override val origin: AlertOrigin, override val isProtected: Boolean = false) : AlertKind {

        override val id: String = "$source.$name"
        override val title: TextRef = TextRef.Literal(name)
    }

    private class Catalogue(name: String, override val kinds: List<AlertKind>, private val used: Boolean = true) : AlertKindCatalogue {

        override val title: TextRef = TextRef.Literal(name)
        override fun inUse(): Boolean = used
    }

    private val commandFailed = Kind("aaps", "command_failed", AlertOrigin.APP)
    private val insulinChange = Kind("aaps", "insulin_change_failed", AlertOrigin.APP, isProtected = true)
    private val occlusion = Kind("patch", "occlusion", AlertOrigin.PUMP, isProtected = true)
    private val bgCheck = Kind("patch", "bg_check", AlertOrigin.PUMP)

    private val app = Catalogue("AAPS", listOf(commandFailed, insulinChange))
    private val patch = Catalogue("Patch", listOf(occlusion, bgCheck))

    @Test
    fun theAppsKindsComeFirst() {
        val groups = alertKindGroups(listOf(patch, app), emptyMap())

        assertEquals(listOf(app.title, patch.title), groups.map { it.title })
    }

    /** A driver that is not the pump now has nothing to raise, so its kinds would only fill the list. */
    @Test
    fun aGroupNotInUseIsLeftOut() {
        val groups = alertKindGroups(listOf(app, Catalogue("Patch", listOf(occlusion, bgCheck), used = false)), emptyMap())

        assertEquals(listOf(app.title), groups.map { it.title })
    }

    @Test
    fun eachKindShowsWhatThisPhoneChoseAndRingsByDefault() {
        val rows = alertKindGroups(listOf(app, patch), mapOf(bgCheck.id to AlertChoice.OFF)).flatMap { it.rows }

        assertEquals(AlertChoice.RING, rows.single { it.kind == commandFailed }.choice)
        assertEquals(AlertChoice.OFF, rows.single { it.kind == bgCheck }.choice)
    }

    /** A protected kind always rings, so a choice stored for it is not shown as if it were used. */
    @Test
    fun aProtectedKindShowsRingWhateverIsStored() {
        val rows = alertKindGroups(listOf(app, patch), mapOf(occlusion.id to AlertChoice.OFF)).flatMap { it.rows }

        assertEquals(AlertChoice.RING, rows.single { it.kind == occlusion }.choice)
    }
}
