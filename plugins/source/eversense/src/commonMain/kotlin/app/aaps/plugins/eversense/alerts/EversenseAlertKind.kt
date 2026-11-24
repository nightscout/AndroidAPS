package app.aaps.plugins.eversense.alerts

import app.aaps.core.interfaces.alerts.AlertKind
import app.aaps.core.interfaces.alerts.AlertOrigin
import app.aaps.core.interfaces.alerts.DosingEffect
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.plugins.eversense.EversenseStrings
import app.aaps.plugins.eversense.enums.EversenseAlarm

/**
 * The kinds of the transmitter's alerts, as the user sees them in the alert settings (`_docs/ALARMS.md`, section 6).
 *
 * One kind per title the user sees, as Carelevo has: several alarm codes share a title ("Sensor replacement" is the
 * sensor at its end and the sensor ending soon), and two rows with the same name, of which only one is silenced, would
 * only confuse. The id is the title's string name, which stays as it is because a renamed string loses its
 * translations.
 *
 * Protected when one of its events affects dosing ([DosingEffect.affectsDosing], decided 2026-11-24). The glucose
 * alarms of the transmitter have no kind: AAPS's own BG alarms raise those.
 */
class EversenseAlertKind private constructor(
    override val title: TextRef,
    /** What the events shown under this title do to dosing. */
    val effects: List<DosingEffect>
) : AlertKind {

    override val id: String = "$SOURCE.${(title as TextRef.Named).name}"
    override val source: String get() = SOURCE
    override val origin: AlertOrigin get() = AlertOrigin.CGM
    override val isProtected: Boolean = effects.any { it.affectsDosing }

    companion object {

        /** The source of every Eversense alert. */
        const val SOURCE = "eversense"

        private val byAlarm: Map<EversenseAlarm, EversenseAlertKind> =
            EversenseAlarm.entries
                .filter { it.effect != DosingEffect.GLUCOSE }
                .groupBy { it.title }
                .flatMap { (title, alarms) -> EversenseAlertKind(title, alarms.map { it.effect }).let { kind -> alarms.map { it to kind } } }
                .toMap()

        /** The transmitter is not over the sensor: AAPS finds it from the signal, the transmitter does not say it. */
        val PLACEMENT = EversenseAlertKind(EversenseStrings.eversense_placement_title, listOf(DosingEffect.NOW))

        /** Every kind, in the order of the alarm codes, and the placement last. */
        val all: List<EversenseAlertKind> = byAlarm.values.distinct() + PLACEMENT

        /** The kind an alarm of [alarm] is shown as, or null for a glucose alarm, which AAPS's BG alarms raise. */
        fun of(alarm: EversenseAlarm): EversenseAlertKind? = byAlarm[alarm]
    }
}
