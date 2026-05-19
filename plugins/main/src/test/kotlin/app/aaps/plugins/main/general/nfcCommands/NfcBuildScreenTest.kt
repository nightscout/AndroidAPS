package app.aaps.plugins.main.general.nfcCommands

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class NfcBuildScreenTest {

    @Test
    fun `resolveWriteOutcome returns REASSIGNED when already assigned`() {
        assertThat(resolveWriteOutcome(alreadyAssigned = true, ndefWritten = false))
            .isEqualTo(WriteOutcome.REASSIGNED)
        assertThat(resolveWriteOutcome(alreadyAssigned = true, ndefWritten = true))
            .isEqualTo(WriteOutcome.REASSIGNED)
    }

    @Test
    fun `resolveWriteOutcome returns NDEF_WRITTEN on successful write of new tag`() {
        assertThat(resolveWriteOutcome(alreadyAssigned = false, ndefWritten = true))
            .isEqualTo(WriteOutcome.NDEF_WRITTEN)
    }

    @Test
    fun `resolveWriteOutcome returns GENERIC_ASSIGNED when NDEF write fails`() {
        assertThat(resolveWriteOutcome(alreadyAssigned = false, ndefWritten = false))
            .isEqualTo(WriteOutcome.GENERIC_ASSIGNED)
    }
}
