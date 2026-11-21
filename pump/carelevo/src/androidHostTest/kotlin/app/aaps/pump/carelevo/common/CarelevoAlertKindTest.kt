package app.aaps.pump.carelevo.common

import app.aaps.core.interfaces.alerts.AlertOrigin
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
        CarelevoAlertKind.all.forEach { kind ->
            assertThat(kind.causes.map { it.stringResources().titleRes }.toSet()).containsExactly(kind.title)
        }
        assertThat(CarelevoAlertKind.of(AlarmCause.ALARM_WARNING_PATCH_EXPIRED_PHASE_1))
            .isSameInstanceAs(CarelevoAlertKind.of(AlarmCause.ALARM_WARNING_PATCH_EXPIRED))
    }

    /** The driver treats WARNING and ALERT as "the insulin stops or the patch ends": those the user cannot silence. */
    @Test
    fun `a kind is protected when one of its causes is of a critical tier`() {
        CarelevoAlertKind.all.forEach { kind ->
            val critical = kind.causes.any { it.alarmType == AlarmType.WARNING || it.alarmType == AlarmType.ALERT }
            assertThat(kind.isProtected).isEqualTo(critical)
        }
        assertThat(CarelevoAlertKind.of(AlarmCause.ALARM_WARNING_PUMP_CLOGGED).isProtected).isTrue()
        assertThat(CarelevoAlertKind.of(AlarmCause.ALARM_NOTICE_BG_CHECK).isProtected).isFalse()
    }
}
