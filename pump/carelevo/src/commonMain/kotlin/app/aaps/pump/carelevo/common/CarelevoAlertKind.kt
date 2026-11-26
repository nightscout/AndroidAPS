package app.aaps.pump.carelevo.common

import app.aaps.core.interfaces.alerts.AlertGroup
import app.aaps.core.interfaces.alerts.AlertKind
import app.aaps.core.interfaces.alerts.AlertOrigin
import app.aaps.core.interfaces.alerts.DosingEffect
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.pump.carelevo.CarelevoStrings
import app.aaps.pump.carelevo.domain.type.AlarmCause
import app.aaps.pump.carelevo.ext.stringResources

/**
 * The kinds of the patch's alerts, as the user sees them in the alert settings (`_docs/ALARMS.md`, section 6).
 *
 * One kind per title the user sees, not per alarm cause: several causes share a title (the two phases of "patch
 * expired"), and two rows with the same name, of which only one is silenced, would only confuse. The id is the
 * title's string name, which stays as it is because a renamed string loses its translations.
 *
 * Protected when one of its causes affects dosing ([DosingEffect.affectsDosing], decided 2026-11-24, as for
 * Eversense): those the user cannot set to Silent or Off. In the settings it is a member of the pump's group of its
 * effects ([AlertGroup.of]), not a row of its own.
 */
class CarelevoAlertKind private constructor(
    override val title: TextRef,
    /** The alarm causes shown under this title. Empty for an alert that is not one of the patch's alarms. */
    val causes: List<AlarmCause>,
    /** What the events shown under this title do to dosing. */
    val effects: List<DosingEffect>
) : AlertKind {

    override val id: String = "${CarelevoAlarmNotifier.SOURCE}.${(title as TextRef.Named).name}"
    override val source: String get() = CarelevoAlarmNotifier.SOURCE
    override val origin: AlertOrigin get() = AlertOrigin.PUMP
    override val isProtected: Boolean = effects.any { it.affectsDosing }
    override val group: AlertGroup = AlertGroup.of(origin, effects)

    companion object {

        private val alarmKinds: List<CarelevoAlertKind> =
            AlarmCause.entries.groupBy { it.stringResources().titleRes }.map { (title, causes) -> CarelevoAlertKind(title, causes, causes.map { it.effect }) }

        private val byCause: Map<AlarmCause, CarelevoAlertKind> = alarmKinds.flatMap { kind -> kind.causes.map { it to kind } }.toMap()

        /**
         * The patch was removed from AAPS without being reached. It was not told to stop, so it may still give insulin
         * that AAPS does not count: dosing is affected now. AAPS raises it, the patch does not.
         */
        val NOT_STOPPED = CarelevoAlertKind(CarelevoStrings.carelevo_alert_not_stopped, emptyList(), listOf(DosingEffect.NOW))

        /** Every kind, in the order of the causes, and the patch that was not stopped last. */
        val all: List<CarelevoAlertKind> = alarmKinds + NOT_STOPPED

        /** The kind an alarm of [cause] is shown as. */
        fun of(cause: AlarmCause): CarelevoAlertKind = byCause.getValue(cause)
    }
}

/**
 * What an alarm of the patch does to dosing, read from the text the patch shows for it (`PUMP_DESIGN.md`, section 4).
 *
 * The patch's own tier is an input, not the answer: a WARNING says the insulin has stopped, but an ALERT is mostly the
 * step before it ("prepare to change the patch"), and some NOTICEs are reminders the user set and wants to hear.
 */
val AlarmCause.effect: DosingEffect
    get() = when (this) {
        // "Insulin delivery stopped", or the patch must be changed now: no insulin.
        AlarmCause.ALARM_WARNING_LOW_INSULIN,
        AlarmCause.ALARM_WARNING_PATCH_EXPIRED_PHASE_1,
        AlarmCause.ALARM_WARNING_LOW_BATTERY,
        AlarmCause.ALARM_WARNING_INVALID_TEMPERATURE,
        AlarmCause.ALARM_WARNING_NOT_USED_APP_AUTO_OFF,
        AlarmCause.ALARM_WARNING_BLE_NOT_CONNECTED,
        AlarmCause.ALARM_WARNING_INCOMPLETE_PATCH_SETTING,
        AlarmCause.ALARM_WARNING_SELF_DIAGNOSIS_FAILED,
        AlarmCause.ALARM_WARNING_PATCH_EXPIRED,
        AlarmCause.ALARM_WARNING_PATCH_ERROR,
        AlarmCause.ALARM_WARNING_PUMP_CLOGGED,
        AlarmCause.ALARM_WARNING_NEEDLE_INSERTION_ERROR,
        // Delivery is paused, not started yet, or waits for the user to start it again.
        AlarmCause.ALARM_ALERT_INVALID_TEMPERATURE,
        AlarmCause.ALARM_ALERT_PATCH_APPLICATION_INCOMPLETE,
        AlarmCause.ALARM_ALERT_RESUME_INSULIN_DELIVERY_TIMEOUT,
        // A code AAPS does not know: it may have stopped the insulin.
        AlarmCause.ALARM_UNKNOWN                                       -> DosingEffect.NOW

        // The insulin still flows, but stops unless the user acts: the reservoir or battery is nearly empty, the patch
        // ends, the patch stops if the app is not used, or AAPS cannot reach it to change the basal.
        AlarmCause.ALARM_ALERT_OUT_OF_INSULIN,
        AlarmCause.ALARM_ALERT_PATCH_EXPIRED_PHASE_1,
        AlarmCause.ALARM_ALERT_PATCH_EXPIRED_PHASE_2,
        AlarmCause.ALARM_ALERT_LOW_BATTERY,
        AlarmCause.ALARM_ALERT_APP_NO_USE,
        AlarmCause.ALARM_ALERT_BLE_NOT_CONNECTED,
        AlarmCause.ALARM_ALERT_BLUETOOTH_OFF,
        AlarmCause.ALARM_NOTICE_LOW_INSULIN                            -> DosingEffect.SOON

        // Reminders the user set: the patch's age, check the patch, check the BG after a bolus.
        AlarmCause.ALARM_NOTICE_PATCH_EXPIRED,
        AlarmCause.ALARM_NOTICE_ATTACH_PATCH_CHECK,
        AlarmCause.ALARM_NOTICE_BG_CHECK                               -> DosingEffect.ACTION

        // AAPS never turns the patch's LGS on, and a stop at a low glucose is the safe side: the BG alarms ring for the low.
        AlarmCause.ALARM_NOTICE_TIME_ZONE_CHANGED,
        AlarmCause.ALARM_NOTICE_LGS_START,
        AlarmCause.ALARM_NOTICE_LGS_FINISHED_DISCONNECTED_PATCH_OR_CGM,
        AlarmCause.ALARM_NOTICE_LGS_FINISHED_PAUSE_LGS,
        AlarmCause.ALARM_NOTICE_LGS_FINISHED_TIME_OVER,
        AlarmCause.ALARM_NOTICE_LGS_FINISHED_OFF_LGS,
        AlarmCause.ALARM_NOTICE_LGS_FINISHED_HIGH_BG,
        AlarmCause.ALARM_NOTICE_LGS_FINISHED_UNKNOWN,
        AlarmCause.ALARM_NOTICE_LGS_NOT_WORKING                        -> DosingEffect.INFO
    }
