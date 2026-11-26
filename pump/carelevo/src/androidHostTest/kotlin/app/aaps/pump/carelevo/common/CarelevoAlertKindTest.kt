package app.aaps.pump.carelevo.common

import app.aaps.core.interfaces.alerts.AlertGroup
import app.aaps.core.interfaces.alerts.AlertOrigin
import app.aaps.core.interfaces.alerts.DosingEffect
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.pump.carelevo.domain.type.AlarmCause
import app.aaps.pump.carelevo.domain.type.AlarmType
import app.aaps.pump.carelevo.ext.stringResources
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class CarelevoAlertKindTest {

    @Test
    fun `every alarm cause has its kind`() {
        AlarmCause.entries.forEach { cause ->
            assertThat(CarelevoAlertKind.of(cause).causes).contains(cause)
        }
        assertThat(CarelevoAlertKind.all.flatMap { it.causes }).containsExactlyElementsIn(AlarmCause.entries)
    }

    /** The ids are stored with the user's choice, so they come from the title's string name, never from a fallback. */
    @Test
    fun `the ids are unique and come from the title`() {
        val ids = CarelevoAlertKind.all.map { it.id }
        assertThat(ids).containsNoDuplicates()
        CarelevoAlertKind.all.forEach { kind ->
            assertThat(kind.title).isInstanceOf(TextRef.Named::class.java)
            assertThat(kind.id).isEqualTo("carelevo." + (kind.title as TextRef.Named).name)
            assertThat(kind.source).isEqualTo(CarelevoAlarmNotifier.SOURCE)
            assertThat(kind.origin).isEqualTo(AlertOrigin.PUMP)
        }
    }

    /** Causes with the same title are one kind: two rows of the same name, one silenced, would only confuse. */
    @Test
    fun `causes that share a title are one kind`() {
        (CarelevoAlertKind.all - CarelevoAlertKind.NOT_STOPPED).forEach { kind ->
            assertThat(kind.causes.map { it.stringResources().titleRes }.toSet()).containsExactly(kind.title)
        }
        assertThat(CarelevoAlertKind.of(AlarmCause.ALARM_WARNING_PATCH_EXPIRED_PHASE_1))
            .isSameInstanceAs(CarelevoAlertKind.of(AlarmCause.ALARM_WARNING_PATCH_EXPIRED))
    }

    /** Decided 2026-11-24 for every driver: a kind with an event that affects dosing cannot be set Silent or Off. */
    @Test
    fun `a kind is protected when one of its causes affects dosing`() {
        (CarelevoAlertKind.all - CarelevoAlertKind.NOT_STOPPED).forEach { kind ->
            assertThat(kind.isProtected).isEqualTo(kind.causes.any { it.effect.affectsDosing })
        }
        assertThat(CarelevoAlertKind.of(AlarmCause.ALARM_WARNING_PUMP_CLOGGED).isProtected).isTrue()
        // A reservoir nearly empty: the insulin stops unless the user acts.
        assertThat(CarelevoAlertKind.of(AlarmCause.ALARM_NOTICE_LOW_INSULIN).isProtected).isTrue()
        // Reminders the user set.
        assertThat(CarelevoAlertKind.of(AlarmCause.ALARM_NOTICE_BG_CHECK).isProtected).isFalse()
        assertThat(CarelevoAlertKind.of(AlarmCause.ALARM_NOTICE_PATCH_EXPIRED).isProtected).isFalse()
        assertThat(CarelevoAlertKind.of(AlarmCause.ALARM_NOTICE_LGS_START).isProtected).isFalse()
        // The patch may still give insulin that AAPS does not count.
        assertThat(CarelevoAlertKind.NOT_STOPPED.isProtected).isTrue()
    }

    /** In the settings a kind follows the pump's group of what it does to dosing (decided 2026-11-26). */
    @Test
    fun `a kind is in the pump group of its effects`() {
        CarelevoAlertKind.all.forEach { kind ->
            assertThat(kind.group).isEqualTo(AlertGroup.of(AlertOrigin.PUMP, kind.effects))
            assertThat(kind.isProtected).isEqualTo(kind.group.isProtected)
        }
        assertThat(CarelevoAlertKind.of(AlarmCause.ALARM_WARNING_PUMP_CLOGGED).group).isEqualTo(AlertGroup.PUMP_CRITICAL)
        assertThat(CarelevoAlertKind.of(AlarmCause.ALARM_NOTICE_BG_CHECK).group).isEqualTo(AlertGroup.PUMP_ACTION)
        assertThat(CarelevoAlertKind.of(AlarmCause.ALARM_NOTICE_LGS_START).group).isEqualTo(AlertGroup.PUMP_INFO)
    }

    /** A pump has no glucose alarm: every cause has a severity, so none is dropped. */
    @Test
    fun `every cause has a severity`() {
        AlarmCause.entries.forEach { cause ->
            assertThat(cause.effect).isNotEqualTo(DosingEffect.GLUCOSE)
        }
    }

    /** Every WARNING of the patch says the insulin has stopped, so the patch's tier alone is enough there. */
    @Test
    fun `every WARNING of the patch affects dosing now`() {
        AlarmCause.entries.filter { it.alarmType == AlarmType.WARNING }.forEach { cause ->
            assertThat(cause.effect).isEqualTo(DosingEffect.NOW)
        }
    }

    /** The user's choice is stored under the id: a change loses it. */
    @Test
    fun `the id of the patch that was not stopped is pinned`() {
        assertThat(CarelevoAlertKind.NOT_STOPPED.id).isEqualTo("carelevo.carelevo_alert_not_stopped")
        assertThat(CarelevoAlertKind.all).contains(CarelevoAlertKind.NOT_STOPPED)
    }
}
