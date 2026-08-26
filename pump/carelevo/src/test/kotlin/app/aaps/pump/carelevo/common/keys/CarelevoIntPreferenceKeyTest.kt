package app.aaps.pump.carelevo.common.keys

import app.aaps.core.keys.PreferenceType
import app.aaps.core.keys.interfaces.IntPreferenceKey
import app.aaps.pump.carelevo.R
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

internal class CarelevoIntPreferenceKeyTest {

    @Test
    fun `has exactly the two declared keys`() {
        assertThat(CarelevoIntPreferenceKey.entries).containsExactly(
            CarelevoIntPreferenceKey.CARELEVO_PATCH_EXPIRATION_REMINDER_HOURS,
            CarelevoIntPreferenceKey.CARELEVO_LOW_INSULIN_EXPIRATION_REMINDER_HOURS
        ).inOrder()
    }

    @Test
    fun `implements the IntPreferenceKey contract`() {
        CarelevoIntPreferenceKey.entries.forEach {
            assertThat(it).isInstanceOf(IntPreferenceKey::class.java)
        }
    }

    @Test
    fun `patch expiration reminder metadata`() {
        val k = CarelevoIntPreferenceKey.CARELEVO_PATCH_EXPIRATION_REMINDER_HOURS
        assertThat(k.key).isEqualTo("CARELEVO_PATCH_EXPIRATION_REMINDER_HOURS")
        assertThat(k.defaultValue).isEqualTo(116)
        assertThat(k.titleResId).isEqualTo(R.string.carelevo_patch_expiration_reminders_title_value)
        assertThat(k.preferenceType).isEqualTo(PreferenceType.LIST)
    }

    @Test
    fun `low insulin reminder metadata`() {
        val k = CarelevoIntPreferenceKey.CARELEVO_LOW_INSULIN_EXPIRATION_REMINDER_HOURS
        assertThat(k.key).isEqualTo("CARELEVO_LOW_INSULIN_EXPIRATION_REMINDER_HOURS")
        assertThat(k.defaultValue).isEqualTo(30)
        assertThat(k.titleResId).isEqualTo(R.string.carelevo_low_reservoir_reminders_title_value)
        assertThat(k.preferenceType).isEqualTo(PreferenceType.LIST)
    }

    @Test
    fun `keys are unique and non-blank`() {
        val keys = CarelevoIntPreferenceKey.entries.map { it.key }
        assertThat(keys.toSet()).hasSize(keys.size)
        assertThat(keys).containsNoDuplicates()
        keys.forEach { assertThat(it).isNotEmpty() }
    }

    @Test
    fun `min and max fall back to the Int range defaults`() {
        CarelevoIntPreferenceKey.entries.forEach { k ->
            assertThat(k.min).isEqualTo(Int.MIN_VALUE)
            assertThat(k.max).isEqualTo(Int.MAX_VALUE)
        }
    }

    @Test
    fun `entries map defaults to empty`() {
        CarelevoIntPreferenceKey.entries.forEach { k ->
            assertThat(k.entries).isEmpty()
        }
    }

    @Test
    fun `default flags match the enum defaults`() {
        CarelevoIntPreferenceKey.entries.forEach { k ->
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
