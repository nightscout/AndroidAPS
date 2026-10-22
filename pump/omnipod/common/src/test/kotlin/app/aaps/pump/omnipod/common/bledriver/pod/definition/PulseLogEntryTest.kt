package app.aaps.pump.omnipod.common.bledriver.pod.definition

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * [PulseLogEntry.decode] against the openaps/openomni wiki's documented bit layout - see
 * [PulseLogEntry]'s class doc for the source and the manual bit-by-bit verification this
 * golden value was checked against.
 */
class PulseLogEntryTest {

    @Test
    fun `decodes the openomni wiki's own worked example`() {
        val entry = PulseLogEntry.decode(0x51213E00.toInt())

        assertThat(entry.encoder).isEqualTo(20)
        assertThat(entry.load2Active).isTrue()
        assertThat(entry.pulseMode).isEqualTo(PulseLogEntryType.BASAL)
        assertThat(entry.lowReservoir).isFalse()
        assertThat(entry.immediateBolusTick).isEqualTo(0)
        assertThat(entry.loadCountVal).isEqualTo(125)
        assertThat(entry.lastComparatorRead).isFalse()
        assertThat(entry.faultFlag).isFalse()
        assertThat(entry.lastEncoderValue).isEqualTo(0)
    }

    @Test
    fun `decodes a synthetic entry exercising every field`() {
        val entry = PulseLogEntry.decode(0x147C96FB.toInt())

        assertThat(entry.encoder).isEqualTo(5)
        assertThat(entry.load2Active).isFalse()
        assertThat(entry.pulseMode).isEqualTo(PulseLogEntryType.BOLUS)
        assertThat(entry.lowReservoir).isTrue()
        assertThat(entry.immediateBolusTick).isEqualTo(6)
        assertThat(entry.loadCountVal).isEqualTo(300)
        assertThat(entry.lastComparatorRead).isTrue()
        assertThat(entry.faultFlag).isTrue()
        assertThat(entry.lastEncoderValue).isEqualTo(-5)
    }

    @Test
    fun `pulseMode covers every documented mode`() {
        assertThat(PulseLogEntry.decode(0x00000000.toInt()).pulseMode).isEqualTo(PulseLogEntryType.NONE)
        assertThat(PulseLogEntry.decode(0x00200000.toInt()).pulseMode).isEqualTo(PulseLogEntryType.BASAL)
        assertThat(PulseLogEntry.decode(0x00400000.toInt()).pulseMode).isEqualTo(PulseLogEntryType.TEMP_BASAL)
        assertThat(PulseLogEntry.decode(0x00600000.toInt()).pulseMode).isEqualTo(PulseLogEntryType.BOLUS)
        assertThat(PulseLogEntry.decode(0x00800000.toInt()).pulseMode).isEqualTo(PulseLogEntryType.EXTENDED_BOLUS)
    }

    @Test
    fun `pulseMode falls back to UNKNOWN for undefined raw values`() {
        assertThat(PulseLogEntry.decode(0x00E00000.toInt()).pulseMode).isEqualTo(PulseLogEntryType.UNKNOWN)
    }

    @Test
    fun `lastEncoderValue sign-extends the 6-bit field correctly`() {
        assertThat(PulseLogEntry.decode(0x00000000.toInt()).lastEncoderValue).isEqualTo(0)
        assertThat(PulseLogEntry.decode(0x0000001F.toInt()).lastEncoderValue).isEqualTo(31)
        assertThat(PulseLogEntry.decode(0x00000020.toInt()).lastEncoderValue).isEqualTo(-32)
        assertThat(PulseLogEntry.decode(0x0000003F.toInt()).lastEncoderValue).isEqualTo(-1)
    }

    @Test
    fun `decodeAll maps a list of dwords in order`() {
        val entries = PulseLogEntry.decodeAll(listOf(0x00000000.toInt(), 0x00600000.toInt()))

        assertThat(entries).hasSize(2)
        assertThat(entries[0].pulseMode).isEqualTo(PulseLogEntryType.NONE)
        assertThat(entries[1].pulseMode).isEqualTo(PulseLogEntryType.BOLUS)
    }
}
