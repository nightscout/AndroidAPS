package app.aaps.pump.danar.di

import app.aaps.pump.danar.comm.MessageBase
import app.aaps.shared.tests.missingMemberInjectorEntries
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * Every [MessageBase] subclass must have an entry in [DanaRMemberInjectors].
 *
 * See `missingMemberInjectorEntries` for why a hand written map needs this and why no other test catches
 * a missing entry.
 */
class DanaRMemberInjectorsTest {

    @Test
    fun everyPacketHasAMemberInjectorEntry() {
        val missing = missingMemberInjectorEntries(
            container = DanaRMemberInjectors::class.java,
            base = MessageBase::class.java
        )

        assertThat(missing).isEmpty()
    }
}
