package app.aaps.pump.medtrum.di

import app.aaps.pump.medtrum.comm.packets.MedtrumPacket
import app.aaps.shared.tests.missingMemberInjectorEntries
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * Every [MedtrumPacket] subclass must have an entry in [MedtrumMemberInjectors].
 *
 * See `missingMemberInjectorEntries` for why a hand written map needs this and why no other test catches
 * a missing entry.
 */
class MedtrumMemberInjectorsTest {

    @Test
    fun everyPacketHasAMemberInjectorEntry() {
        val missing = missingMemberInjectorEntries(
            container = MedtrumMemberInjectors::class.java,
            base = MedtrumPacket::class.java
        )

        assertThat(missing).isEmpty()
    }
}
