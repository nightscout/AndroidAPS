package app.aaps.pump.carelevo.common.keys

import app.aaps.core.keys.interfaces.BooleanPreferenceKey
import app.aaps.pump.carelevo.R
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

internal class CarelevoBooleanPreferenceKeyTest {

    @Test
    fun `has exactly the two declared keys`() {
        assertThat(CarelevoBooleanPreferenceKey.entries).containsExactly(
            CarelevoBooleanPreferenceKey.CARELEVO_BUZZER_REMINDER,
            CarelevoBooleanPreferenceKey.CARELEVO_CAGE_DEFAULT_APPLIED
        ).inOrder()
    }

    @Test
    fun `implements the BooleanPreferenceKey contract`() {
        CarelevoBooleanPreferenceKey.entries.forEach {
            assertThat(it).isInstanceOf(BooleanPreferenceKey::class.java)
        }
    }

    @Test
    fun `buzzer reminder metadata`() {
        val k = CarelevoBooleanPreferenceKey.CARELEVO_BUZZER_REMINDER
        assertThat(k.key).isEqualTo("CARELEVO_BUZZER_REMINDER")
        assertThat(k.defaultValue).isFalse()
        assertThat(k.titleResId).isEqualTo(R.string.carelevo_patch_buzzer_alarm_title)
    }

    @Test
    fun `cage default applied metadata`() {
        val k = CarelevoBooleanPreferenceKey.CARELEVO_CAGE_DEFAULT_APPLIED
        assertThat(k.key).isEqualTo("carelevo_cage_default_applied")
        assertThat(k.defaultValue).isFalse()
        assertThat(k.titleResId).isEqualTo(0)
    }

    @Test
    fun `keys are unique and non-blank`() {
        val keys = CarelevoBooleanPreferenceKey.entries.map { it.key }
        assertThat(keys.toSet()).hasSize(keys.size)
        assertThat(keys).containsNoDuplicates()
        keys.forEach { assertThat(it).isNotEmpty() }
    }

    @Test
    fun `default flags match the enum defaults`() {
        CarelevoBooleanPreferenceKey.entries.forEach { k ->
            assertThat(k.calculatedDefaultValue).isFalse()
            assertThat(k.engineeringModeOnly).isFalse()
            assertThat(k.defaultedBySM).isFalse()
            assertThat(k.showInApsMode).isTrue()
            assertThat(k.showInNsClientMode).isTrue()
            assertThat(k.showInPumpControlMode).isTrue()
            assertThat(k.dependency).isNull()
            assertThat(k.negativeDependency).isNull()
            assertThat(k.hideParentScreenIfHidden).isFalse()
            assertThat(k.exportable).isTrue()
        }
    }
}
