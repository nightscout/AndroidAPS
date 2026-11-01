package app.aaps.plugins.libre3.session

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * The rule that protects a sensor: a phone that already holds a pairing key must use the short
 * reconnect, and must never send the first pairing command again.
 *
 * Source: LibreLoop `LibreLoopPairingService.reconnect`, the live `if let phase5RawKey` branch.
 * Its own comment says that an active sensor refuses the first pairing command late in the
 * handshake, so a fall back to it can never work and only wastes a connection.
 */
class Libre3SessionStartPolicyTest {

    private fun decide(
        hasStoredSensor: Boolean = true,
        pinWriteFinished: Boolean = true,
        hasPairingKey: Boolean = false,
        firstPairAvailable: Boolean = true,
    ) = Libre3SessionStartPolicy.decide(hasStoredSensor, pinWriteFinished, hasPairingKey, firstPairAvailable)

    @Test
    fun `a stored pairing key always means the short reconnect`() {
        val decision = decide(hasPairingKey = true)

        assertThat(decision.start).isEqualTo(Libre3SessionStart.CACHED_RECONNECT)
    }

    @Test
    fun `a stored pairing key never leads to a first pairing, whatever else is true`() {
        // Every mix that still has a key must stay on the short path.
        for (firstPairAvailable in listOf(true, false)) {
            val decision = decide(hasPairingKey = true, firstPairAvailable = firstPairAvailable)

            assertThat(decision.start).isNotEqualTo(Libre3SessionStart.FIRST_PAIR)
            assertThat(decision.start).isEqualTo(Libre3SessionStart.CACHED_RECONNECT)
        }
    }

    @Test
    fun `a sensor without a pairing key is paired for the first time`() {
        val decision = decide(hasPairingKey = false)

        assertThat(decision.start).isEqualTo(Libre3SessionStart.FIRST_PAIR)
    }

    @Test
    fun `nothing is sent while the PIN write has not finished`() {
        val decision = decide(pinWriteFinished = false)

        assertThat(decision.start).isEqualTo(Libre3SessionStart.BLOCKED)
        assertThat(decision.refusal).isEqualTo(Libre3StartRefusal.PIN_NOT_WRITTEN_YET)
    }

    @Test
    fun `an unfinished PIN write blocks even a sensor that has a pairing key`() {
        val decision = decide(pinWriteFinished = false, hasPairingKey = true)

        assertThat(decision.start).isEqualTo(Libre3SessionStart.BLOCKED)
    }

    @Test
    fun `nothing is sent when no sensor was ever scanned`() {
        val decision = decide(hasStoredSensor = false)

        assertThat(decision.start).isEqualTo(Libre3SessionStart.BLOCKED)
        assertThat(decision.refusal).isEqualTo(Libre3StartRefusal.NO_SENSOR_STORED)
    }

    @Test
    fun `a build that cannot pair a new sensor says so instead of trying`() {
        val decision = decide(hasPairingKey = false, firstPairAvailable = false)

        assertThat(decision.start).isEqualTo(Libre3SessionStart.BLOCKED)
        assertThat(decision.refusal).isEqualTo(Libre3StartRefusal.FIRST_PAIR_NOT_AVAILABLE)
    }

    @Test
    fun `a stored key that does not work is a dead end until the sensor is forgotten`() {
        // This pins the reason the Status screen has a "Forget this sensor" action.
        //
        // A first pairing stores its key before the last two steps, on purpose. If that key turns
        // out not to work, every later attempt takes the short reconnect path, because a stored key
        // is the only thing this decision looks at. Hard ban 4 forbids falling back to the first
        // pairing command, and rightly so: an active sensor refuses it. A fresh NFC scan of the
        // same sensor does not help either, because the store only drops the key when the serial
        // changes. So the key has to be thrown away on purpose, and then a first pairing is
        // possible again.
        val stuck = Libre3SessionStartPolicy.decide(
            hasStoredSensor = true,
            pinWriteFinished = true,
            hasPairingKey = true,
            firstPairAvailable = true,
        )
        assertThat(stuck.start).isEqualTo(Libre3SessionStart.CACHED_RECONNECT)

        // What the store looks like after the sensor is forgotten: nothing at all.
        val afterForgetting = Libre3SessionStartPolicy.decide(
            hasStoredSensor = false,
            pinWriteFinished = false,
            hasPairingKey = false,
            firstPairAvailable = true,
        )
        assertThat(afterForgetting.start).isEqualTo(Libre3SessionStart.BLOCKED)
        assertThat(afterForgetting.refusal).isEqualTo(Libre3StartRefusal.NO_SENSOR_STORED)

        // And once the sensor is scanned again, a first pairing may run.
        val afterRescan = Libre3SessionStartPolicy.decide(
            hasStoredSensor = true,
            pinWriteFinished = true,
            hasPairingKey = false,
            firstPairAvailable = true,
        )
        assertThat(afterRescan.start).isEqualTo(Libre3SessionStart.FIRST_PAIR)
    }
}
