package app.aaps.core.main.extensions

import app.aaps.core.interfaces.utils.T
import app.aaps.database.entities.data.Block
import app.aaps.database.entities.data.TargetBlock
import app.aaps.database.entities.data.checkSanity
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class BlockExtensionKtTest {

    @Test
    fun shiftBlock() {
        val b = arrayListOf<Block>()
        b.add(Block(T.hours(1).msecs(), 1.0))
        b.add(Block(T.hours(1).msecs(), 2.0))
        b.add(Block(T.hours(10).msecs(), 3.0))
        b.add(Block(T.hours(12).msecs(), 4.0))

        assertThat(b.checkSanity()).isTrue()

        assertThat(b.blockValueBySeconds(T.hours(0).secs().toInt(), 1.0, 0)).isWithin(0.01).of(1.0)
        assertThat(b.blockValueBySeconds(T.hours(1).secs().toInt(), 1.0, 0)).isWithin(0.01).of(2.0)
        assertThat(b.blockValueBySeconds(T.hours(2).secs().toInt(), 1.0, 0)).isWithin(0.01).of(3.0)
        assertThat(b.blockValueBySeconds(T.hours(3).secs().toInt(), 1.0, 0)).isWithin(0.01).of(3.0)
        assertThat(b.blockValueBySeconds(T.hours(12).secs().toInt(), 1.0, 0)).isWithin(0.01).of(4.0)
        assertThat(b.blockValueBySeconds(T.hours(13).secs().toInt(), 1.0, 0)).isWithin(0.01).of(4.0)

        val s1 = b.shiftBlock(1.0, -1)

        assertThat(s1.checkSanity()).isTrue()

        assertThat(s1.blockValueBySeconds(T.hours(23).secs().toInt(), 1.0, 0)).isWithin(0.01).of(1.0)
        assertThat(s1.blockValueBySeconds(T.hours(0).secs().toInt(), 1.0, 0)).isWithin(0.01).of(2.0)
        assertThat(s1.blockValueBySeconds(T.hours(1).secs().toInt(), 1.0, 0)).isWithin(0.01).of(3.0)
        assertThat(s1.blockValueBySeconds(T.hours(2).secs().toInt(), 1.0, 0)).isWithin(0.01).of(3.0)
        assertThat(s1.blockValueBySeconds(T.hours(11).secs().toInt(), 1.0, 0)).isWithin(0.01).of(4.0)
        assertThat(s1.blockValueBySeconds(T.hours(12).secs().toInt(), 1.0, 0)).isWithin(0.01).of(4.0)

        val s2 = b.shiftBlock(2.0, 1)

        assertThat(s2.checkSanity()).isTrue()

        assertThat(s2.blockValueBySeconds(T.hours(1).secs().toInt(), 1.0, 0)).isWithin(0.01).of(2.0)
        assertThat(s2.blockValueBySeconds(T.hours(2).secs().toInt(), 1.0, 0)).isWithin(0.01).of(4.0)
        assertThat(s2.blockValueBySeconds(T.hours(3).secs().toInt(), 1.0, 0)).isWithin(0.01).of(6.0)
        assertThat(s2.blockValueBySeconds(T.hours(4).secs().toInt(), 1.0, 0)).isWithin(0.01).of(6.0)
        assertThat(s2.blockValueBySeconds(T.hours(13).secs().toInt(), 1.0, 0)).isWithin(0.01).of(8.0)
        assertThat(s2.blockValueBySeconds(T.hours(14).secs().toInt(), 1.0, 0)).isWithin(0.01).of(8.0)
    }

    @Test
    fun shiftTargetBlock() {
        val b = arrayListOf<TargetBlock>()
        b.add(TargetBlock(T.hours(1).msecs(), 1.0, 2.0))
        b.add(TargetBlock(T.hours(1).msecs(), 2.0, 3.0))
        b.add(TargetBlock(T.hours(10).msecs(), 3.0, 4.0))
        b.add(TargetBlock(T.hours(12).msecs(), 4.0, 5.0))

        assertThat(b.checkSanity()).isTrue()

        assertThat(b.targetBlockValueBySeconds(T.hours(0).secs().toInt(), 0)).isWithin(0.01).of(1.5)
        assertThat(b.targetBlockValueBySeconds(T.hours(1).secs().toInt(), 0)).isWithin(0.01).of(2.5)
        assertThat(b.targetBlockValueBySeconds(T.hours(2).secs().toInt(), 0)).isWithin(0.01).of(3.5)
        assertThat(b.targetBlockValueBySeconds(T.hours(3).secs().toInt(), 0)).isWithin(0.01).of(3.5)
        assertThat(b.targetBlockValueBySeconds(T.hours(12).secs().toInt(), 0)).isWithin(0.01).of(4.5)
        assertThat(b.targetBlockValueBySeconds(T.hours(13).secs().toInt(), 0)).isWithin(0.01).of(4.5)

        val s1 = b.shiftTargetBlock(-1)

        assertThat(s1.checkSanity()).isTrue()

        assertThat(s1.targetBlockValueBySeconds(T.hours(23).secs().toInt(), 0)).isWithin(0.01).of(1.5)
        assertThat(s1.targetBlockValueBySeconds(T.hours(0).secs().toInt(), 0)).isWithin(0.01).of(2.5)
        assertThat(s1.targetBlockValueBySeconds(T.hours(1).secs().toInt(), 0)).isWithin(0.01).of(3.5)
        assertThat(s1.targetBlockValueBySeconds(T.hours(2).secs().toInt(), 0)).isWithin(0.01).of(3.5)
        assertThat(s1.targetBlockValueBySeconds(T.hours(11).secs().toInt(), 0)).isWithin(0.01).of(4.5)
        assertThat(s1.targetBlockValueBySeconds(T.hours(12).secs().toInt(), 0)).isWithin(0.01).of(4.5)

        val s2 = b.shiftTargetBlock(1)

        assertThat(s2.checkSanity()).isTrue()

        assertThat(s2.targetBlockValueBySeconds(T.hours(1).secs().toInt(), 0)).isWithin(0.01).of(1.5)
        assertThat(s2.targetBlockValueBySeconds(T.hours(2).secs().toInt(), 0)).isWithin(0.01).of(2.5)
        assertThat(s2.targetBlockValueBySeconds(T.hours(3).secs().toInt(), 0)).isWithin(0.01).of(3.5)
        assertThat(s2.targetBlockValueBySeconds(T.hours(4).secs().toInt(), 0)).isWithin(0.01).of(3.5)
        assertThat(s2.targetBlockValueBySeconds(T.hours(13).secs().toInt(), 0)).isWithin(0.01).of(4.5)
        assertThat(s2.targetBlockValueBySeconds(T.hours(14).secs().toInt(), 0)).isWithin(0.01).of(4.5)
    }

    @Test
    fun lowTargetBlockValueBySeconds() {
        val b = arrayListOf<TargetBlock>()
        b.add(TargetBlock(T.hours(1).msecs(), 1.0, 2.0))
        b.add(TargetBlock(T.hours(1).msecs(), 2.0, 3.0))
        b.add(TargetBlock(T.hours(10).msecs(), 3.0, 4.0))
        b.add(TargetBlock(T.hours(12).msecs(), 4.0, 5.0))

        assertThat(b.checkSanity()).isTrue()

        assertThat(b.lowTargetBlockValueBySeconds(T.hours(0).secs().toInt(), 0)).isWithin(0.01).of(1.0)
        assertThat(b.lowTargetBlockValueBySeconds(T.hours(1).secs().toInt(), 0)).isWithin(0.01).of(2.0)
        assertThat(b.lowTargetBlockValueBySeconds(T.hours(2).secs().toInt(), 0)).isWithin(0.01).of(3.0)
        assertThat(b.lowTargetBlockValueBySeconds(T.hours(3).secs().toInt(), 0)).isWithin(0.01).of(3.0)
        assertThat(b.lowTargetBlockValueBySeconds(T.hours(12).secs().toInt(), 0)).isWithin(0.01).of(4.0)
        assertThat(b.lowTargetBlockValueBySeconds(T.hours(13).secs().toInt(), 0)).isWithin(0.01).of(4.0)
    }

    @Test
    fun highTargetBlockValueBySeconds() {
        val b = arrayListOf<TargetBlock>()
        b.add(TargetBlock(T.hours(1).msecs(), 1.0, 2.0))
        b.add(TargetBlock(T.hours(1).msecs(), 2.0, 3.0))
        b.add(TargetBlock(T.hours(10).msecs(), 3.0, 4.0))
        b.add(TargetBlock(T.hours(12).msecs(), 4.0, 5.0))

        assertThat(b.checkSanity()).isTrue()

        assertThat(b.highTargetBlockValueBySeconds(T.hours(0).secs().toInt(), 0)).isWithin(0.01).of(2.0)
        assertThat(b.highTargetBlockValueBySeconds(T.hours(1).secs().toInt(), 0)).isWithin(0.01).of(3.0)
        assertThat(b.highTargetBlockValueBySeconds(T.hours(2).secs().toInt(), 0)).isWithin(0.01).of(4.0)
        assertThat(b.highTargetBlockValueBySeconds(T.hours(3).secs().toInt(), 0)).isWithin(0.01).of(4.0)
        assertThat(b.highTargetBlockValueBySeconds(T.hours(12).secs().toInt(), 0)).isWithin(0.01).of(5.0)
        assertThat(b.highTargetBlockValueBySeconds(T.hours(13).secs().toInt(), 0)).isWithin(0.01).of(5.0)
    }
}
