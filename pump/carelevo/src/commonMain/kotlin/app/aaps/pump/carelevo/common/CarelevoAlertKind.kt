package app.aaps.pump.carelevo.common

import app.aaps.core.interfaces.alerts.AlertKind
import app.aaps.core.interfaces.alerts.AlertOrigin
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.pump.carelevo.domain.type.AlarmCause
import app.aaps.pump.carelevo.domain.type.AlarmType.Companion.isCritical
import app.aaps.pump.carelevo.ext.stringResources

/**
 * The kinds of the patch's alerts, as the user sees them in the alert settings (`_docs/ALARMS.md`, section 6).
 *
 * One kind per title the user sees, not per alarm cause: several causes share a title (the two phases of "patch
 * expired"), and two rows with the same name, of which only one is silenced, would only confuse. The id is the
 * title's string name, which stays as it is because a renamed string loses its translations.
 *
 * Protected when one of its causes is of a critical tier: the driver treats WARNING and ALERT as "the insulin stops
 * or the patch ends" (`AlarmType.isCritical`). Only the NOTICE kinds can be made silent or turned off.
 */
class CarelevoAlertKind private constructor(
    override val title: TextRef,
    /** The alarm causes shown under this title. */
    val causes: List<AlarmCause>
) : AlertKind {

    override val id: String = "${CarelevoAlarmNotifier.SOURCE}.${(title as? TextRef.Named)?.name ?: causes.first().name.lowercase()}"
    override val source: String get() = CarelevoAlarmNotifier.SOURCE
    override val origin: AlertOrigin get() = AlertOrigin.PUMP
    override val isProtected: Boolean = causes.any { it.alarmType.isCritical() }

    companion object {

        /** Every kind, in the order of the causes. */
        val all: List<CarelevoAlertKind> =
            AlarmCause.entries.groupBy { it.stringResources().titleRes }.map { (title, causes) -> CarelevoAlertKind(title, causes) }

        private val byCause: Map<AlarmCause, CarelevoAlertKind> = all.flatMap { kind -> kind.causes.map { it to kind } }.toMap()

        /** The kind an alarm of [cause] is shown as. */
        fun of(cause: AlarmCause): CarelevoAlertKind = byCause.getValue(cause)
    }
}
