package app.aaps.pump.eopatch.code

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class BasalStatusTest {

    @Test
    fun `isStarted should return true only for STARTED`() {
        assertThat(BasalStatus.STARTED.isStarted).isTrue()
        assertThat(BasalStatus.STOPPED.isStarted).isFalse()
        assertThat(BasalStatus.PAUSED.isStarted).isFalse()
        assertThat(BasalStatus.SUSPENDED.isStarted).isFalse()
        assertThat(BasalStatus.SELECTED.isStarted).isFalse()
    }

    @Test
    fun `isSuspended should return true only for SUSPENDED`() {
        assertThat(BasalStatus.SUSPENDED.isSuspended).isTrue()
        assertThat(BasalStatus.STARTED.isSuspended).isFalse()
        assertThat(BasalStatus.STOPPED.isSuspended).isFalse()
        assertThat(BasalStatus.PAUSED.isSuspended).isFalse()
        assertThat(BasalStatus.SELECTED.isSuspended).isFalse()
    }

    @Test
    fun `isStopped should return true only for STOPPED`() {
        assertThat(BasalStatus.STOPPED.isStopped).isTrue()
        assertThat(BasalStatus.STARTED.isStopped).isFalse()
        assertThat(BasalStatus.PAUSED.isStopped).isFalse()
        assertThat(BasalStatus.SUSPENDED.isStopped).isFalse()
        assertThat(BasalStatus.SELECTED.isStopped).isFalse()
    }

    @Test
    fun `rawValue should be unique for each status`() {
        val values = BasalStatus.entries.map { it.rawValue }
        val uniqueValues = values.toSet()

        assertThat(uniqueValues.size).isEqualTo(values.size)
    }

    @Test
    fun `rawValue should match expected values`() {
        assertThat(BasalStatus.STOPPED.rawValue).isEqualTo(0)
        assertThat(BasalStatus.PAUSED.rawValue).isEqualTo(1)
        assertThat(BasalStatus.SUSPENDED.rawValue).isEqualTo(2)
        assertThat(BasalStatus.STARTED.rawValue).isEqualTo(3)
        assertThat(BasalStatus.SELECTED.rawValue).isEqualTo(4)
    }

    @Test
    fun `all status values should be accessible`() {
        assertThat(BasalStatus.entries).hasSize(5)
        assertThat(BasalStatus.entries).containsExactly(
            BasalStatus.STOPPED,
            BasalStatus.PAUSED,
            BasalStatus.SUSPENDED,
            BasalStatus.STARTED,
            BasalStatus.SELECTED
        )
    }
}
