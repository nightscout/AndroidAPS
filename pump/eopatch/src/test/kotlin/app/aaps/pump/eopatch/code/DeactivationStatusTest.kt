package app.aaps.pump.eopatch.code

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class DeactivationStatusTest {

    @Test
    fun `should have exactly three statuses`() {
        assertThat(DeactivationStatus.entries).hasSize(3)
    }

    @Test
    fun `should contain all expected statuses`() {
        assertThat(DeactivationStatus.entries).containsExactly(
            DeactivationStatus.DEACTIVATION_FAILED,
            DeactivationStatus.NORMAL_DEACTIVATED,
            DeactivationStatus.FORCE_DEACTIVATED
        )
    }

    @Test
    fun `DEACTIVATION_FAILED should be first status`() {
        assertThat(DeactivationStatus.entries[0]).isEqualTo(DeactivationStatus.DEACTIVATION_FAILED)
    }

    @Test
    fun `NORMAL_DEACTIVATED should be second status`() {
        assertThat(DeactivationStatus.entries[1]).isEqualTo(DeactivationStatus.NORMAL_DEACTIVATED)
    }

    @Test
    fun `FORCE_DEACTIVATED should be third status`() {
        assertThat(DeactivationStatus.entries[2]).isEqualTo(DeactivationStatus.FORCE_DEACTIVATED)
    }

    @Test
    fun `all statuses should be distinct`() {
        assertThat(DeactivationStatus.DEACTIVATION_FAILED).isNotEqualTo(DeactivationStatus.NORMAL_DEACTIVATED)
        assertThat(DeactivationStatus.NORMAL_DEACTIVATED).isNotEqualTo(DeactivationStatus.FORCE_DEACTIVATED)
        assertThat(DeactivationStatus.DEACTIVATION_FAILED).isNotEqualTo(DeactivationStatus.FORCE_DEACTIVATED)
    }

    @Test
    fun `should support valueOf`() {
        assertThat(DeactivationStatus.valueOf("DEACTIVATION_FAILED")).isEqualTo(DeactivationStatus.DEACTIVATION_FAILED)
        assertThat(DeactivationStatus.valueOf("NORMAL_DEACTIVATED")).isEqualTo(DeactivationStatus.NORMAL_DEACTIVATED)
        assertThat(DeactivationStatus.valueOf("FORCE_DEACTIVATED")).isEqualTo(DeactivationStatus.FORCE_DEACTIVATED)
    }

    @Test
    fun `ordinal values should be sequential`() {
        assertThat(DeactivationStatus.DEACTIVATION_FAILED.ordinal).isEqualTo(0)
        assertThat(DeactivationStatus.NORMAL_DEACTIVATED.ordinal).isEqualTo(1)
        assertThat(DeactivationStatus.FORCE_DEACTIVATED.ordinal).isEqualTo(2)
    }

    @Test
    fun `DEACTIVATION_FAILED should not be deactivated`() {
        assertThat(DeactivationStatus.DEACTIVATION_FAILED.isDeactivated).isFalse()
    }

    @Test
    fun `NORMAL_DEACTIVATED should be deactivated`() {
        assertThat(DeactivationStatus.NORMAL_DEACTIVATED.isDeactivated).isTrue()
    }

    @Test
    fun `FORCE_DEACTIVATED should be deactivated`() {
        assertThat(DeactivationStatus.FORCE_DEACTIVATED.isDeactivated).isTrue()
    }

    @Test
    fun `of should return NORMAL_DEACTIVATED when success is true`() {
        assertThat(DeactivationStatus.of(isSuccess = true, forced = false)).isEqualTo(DeactivationStatus.NORMAL_DEACTIVATED)
        assertThat(DeactivationStatus.of(isSuccess = true, forced = true)).isEqualTo(DeactivationStatus.NORMAL_DEACTIVATED)
    }

    @Test
    fun `of should return FORCE_DEACTIVATED when success is false and forced is true`() {
        assertThat(DeactivationStatus.of(isSuccess = false, forced = true)).isEqualTo(DeactivationStatus.FORCE_DEACTIVATED)
    }

    @Test
    fun `of should return DEACTIVATION_FAILED when success is false and forced is false`() {
        assertThat(DeactivationStatus.of(isSuccess = false, forced = false)).isEqualTo(DeactivationStatus.DEACTIVATION_FAILED)
    }

    @Test
    fun `of should handle all combinations correctly`() {
        // Success = true, forced = true -> NORMAL_DEACTIVATED (success takes precedence)
        assertThat(DeactivationStatus.of(true, true)).isEqualTo(DeactivationStatus.NORMAL_DEACTIVATED)

        // Success = true, forced = false -> NORMAL_DEACTIVATED
        assertThat(DeactivationStatus.of(true, false)).isEqualTo(DeactivationStatus.NORMAL_DEACTIVATED)

        // Success = false, forced = true -> FORCE_DEACTIVATED
        assertThat(DeactivationStatus.of(false, true)).isEqualTo(DeactivationStatus.FORCE_DEACTIVATED)

        // Success = false, forced = false -> DEACTIVATION_FAILED
        assertThat(DeactivationStatus.of(false, false)).isEqualTo(DeactivationStatus.DEACTIVATION_FAILED)
    }
}
