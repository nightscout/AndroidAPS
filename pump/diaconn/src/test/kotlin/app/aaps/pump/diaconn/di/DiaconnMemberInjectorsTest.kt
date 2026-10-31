package app.aaps.pump.diaconn.di

import app.aaps.pump.diaconn.packet.DiaconnG8Packet
import app.aaps.shared.tests.missingMemberInjectorEntries
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * Every [DiaconnG8Packet] subclass must have an entry in [DiaconnMemberInjectors].
 *
 * See `missingMemberInjectorEntries` for why a hand written map needs this and why no other test catches
 * a missing entry.
 */
class DiaconnMemberInjectorsTest {

    @Test
    fun everyPacketHasAMemberInjectorEntry() {
        val missing = missingMemberInjectorEntries(
            container = DiaconnMemberInjectors::class.java,
            base = DiaconnG8Packet::class.java
        )

        assertThat(missing).isEmpty()
    }
}
