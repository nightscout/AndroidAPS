package app.aaps.pump.insight.app_layer.parameter_blocks

import app.aaps.pump.insight.utils.ByteBuf
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * Covers [BRProfileBlock], the wire format of a basal rate profile - shared by all five profile
 * blocks the pump exposes.
 *
 * The layout is fixed at 24 slots: twenty four little endian durations in minutes, then twenty four
 * rates. `parse` reads that and drops the slots whose duration is zero; `data` writes the slots back
 * and pads the rest with zeros. This is the basal rate the pump will actually deliver, so a slot
 * read into the wrong position, or a rate that does not survive the round trip, is a wrong basal
 * rate rather than a display problem.
 */
class BRProfileBlockTest {

    private class TestProfileBlock : BRProfileBlock()

    /** The pump's layout: 24 durations, then 24 rates. Unused slots are zero. */
    private fun bytesFor(slots: List<Pair<Int, Double>>): ByteBuf {
        val buf = ByteBuf(96)
        for (i in 0..23) buf.putUInt16LE(slots.getOrNull(i)?.first ?: 0)
        for (i in 0..23) buf.putUInt16Decimal(slots.getOrNull(i)?.second ?: 0.0)
        return buf
    }

    @Test
    fun aProfileIsReadBackWithItsDurationsAndRates() {
        val sut = TestProfileBlock()

        sut.parse(bytesFor(listOf(180 to 0.8, 300 to 1.25, 960 to 0.55)))

        val blocks = sut.getProfileBlocks()
        assertThat(blocks).hasSize(3)
        assertThat(blocks.map { it.duration }).containsExactly(180, 300, 960).inOrder()
        assertThat(blocks.map { it.basalAmount }).containsExactly(0.8, 1.25, 0.55).inOrder()
    }

    /** Empty slots are padding, not a basal rate of zero for the rest of the day. */
    @Test
    fun theUnusedSlotsAreDropped() {
        val sut = TestProfileBlock()

        sut.parse(bytesFor(listOf(1440 to 0.9)))

        assertThat(sut.getProfileBlocks()).hasSize(1)
        assertThat(sut.getProfileBlocks()[0].duration).isEqualTo(1440)
    }

    @Test
    fun aFullDayOfSlotsIsKept() {
        val sut = TestProfileBlock()
        val full = (0..23).map { 60 to (it + 1) * 0.05 }

        sut.parse(bytesFor(full))

        assertThat(sut.getProfileBlocks()).hasSize(24)
        // The wire format carries a fixed point decimal, so the value comes back rounded to the
        // step the pump actually uses - 1.2, not the 1.2000000000000002 that 24 * 0.05 gives here.
        assertThat(sut.getProfileBlocks().last().basalAmount).isWithin(1e-9).of(1.2)
    }

    /** What is read from the pump must be what is written back to it. */
    @Test
    fun aProfileSurvivesTheRoundTripBackToBytes() {
        val original = bytesFor(listOf(360 to 0.75, 480 to 1.1, 600 to 0.65)).bytes
        val sut = TestProfileBlock()

        sut.parse(ByteBuf.from(original))

        assertThat(sut.data.bytes).isEqualTo(original)
    }

    @Test
    fun anEmptyProfileWritesAllSlotsAsZero() {
        val sut = TestProfileBlock()
        sut.parse(bytesFor(emptyList()))

        val written = sut.data.bytes

        assertThat(sut.getProfileBlocks()).isEmpty()
        assertThat(written.size).isEqualTo(96)
        assertThat(written.all { it == 0.toByte() }).isTrue()
    }
}
