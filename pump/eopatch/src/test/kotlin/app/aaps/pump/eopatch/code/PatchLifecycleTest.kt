package app.aaps.pump.eopatch.code

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class PatchLifecycleTest {

    @Test
    fun `isShutdown should return true only for SHUTDOWN`() {
        assertThat(PatchLifecycle.SHUTDOWN.isShutdown).isTrue()
        assertThat(PatchLifecycle.BONDED.isShutdown).isFalse()
        assertThat(PatchLifecycle.ACTIVATED.isShutdown).isFalse()
    }

    @Test
    fun `isActivated should return true only for ACTIVATED`() {
        assertThat(PatchLifecycle.ACTIVATED.isActivated).isTrue()
        assertThat(PatchLifecycle.SHUTDOWN.isActivated).isFalse()
        assertThat(PatchLifecycle.BONDED.isActivated).isFalse()
    }

    @Test
    fun `rawValue should be unique for each lifecycle`() {
        val values = PatchLifecycle.entries.map { it.rawValue }
        val uniqueValues = values.toSet()

        assertThat(uniqueValues.size).isEqualTo(values.size)
    }

    @Test
    fun `rawValue should match expected values`() {
        assertThat(PatchLifecycle.SHUTDOWN.rawValue).isEqualTo(1)
        assertThat(PatchLifecycle.BONDED.rawValue).isEqualTo(2)
        assertThat(PatchLifecycle.SAFETY_CHECK.rawValue).isEqualTo(3)
        assertThat(PatchLifecycle.REMOVE_NEEDLE_CAP.rawValue).isEqualTo(4)
        assertThat(PatchLifecycle.REMOVE_PROTECTION_TAPE.rawValue).isEqualTo(5)
        assertThat(PatchLifecycle.ROTATE_KNOB.rawValue).isEqualTo(6)
        assertThat(PatchLifecycle.BASAL_SETTING.rawValue).isEqualTo(7)
        assertThat(PatchLifecycle.ACTIVATED.rawValue).isEqualTo(8)
    }

    @Test
    fun `all lifecycle values should be accessible`() {
        assertThat(PatchLifecycle.entries).hasSize(8)
        assertThat(PatchLifecycle.entries).containsExactly(
            PatchLifecycle.SHUTDOWN,
            PatchLifecycle.BONDED,
            PatchLifecycle.SAFETY_CHECK,
            PatchLifecycle.REMOVE_NEEDLE_CAP,
            PatchLifecycle.REMOVE_PROTECTION_TAPE,
            PatchLifecycle.ROTATE_KNOB,
            PatchLifecycle.BASAL_SETTING,
            PatchLifecycle.ACTIVATED
        )
    }

    @Test
    fun `lifecycle progression should be in ascending order`() {
        val entries = PatchLifecycle.entries
        for (i in 0 until entries.size - 1) {
            assertThat(entries[i].rawValue).isLessThan(entries[i + 1].rawValue)
        }
    }

    @Test
    fun `activation lifecycle has highest rawValue`() {
        val maxRawValue = PatchLifecycle.entries.maxOf { it.rawValue }
        assertThat(PatchLifecycle.ACTIVATED.rawValue).isEqualTo(maxRawValue)
    }

    @Test
    fun `shutdown lifecycle has lowest rawValue`() {
        val minRawValue = PatchLifecycle.entries.minOf { it.rawValue }
        assertThat(PatchLifecycle.SHUTDOWN.rawValue).isEqualTo(minRawValue)
    }
}
