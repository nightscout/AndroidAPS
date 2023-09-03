package info.nightscout.core.extensions

import info.nightscout.database.entities.data.Block
import info.nightscout.database.entities.data.TargetBlock
import info.nightscout.database.entities.data.checkSanity
import info.nightscout.shared.utils.T
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class BlockExtensionKtTest {

    @Test
    fun shiftBlock() {
        val b = arrayListOf<Block>()
        b.add(Block(T.hours(1).msecs(), 1.0))
        b.add(Block(T.hours(1).msecs(), 2.0))
        b.add(Block(T.hours(10).msecs(), 3.0))
        b.add(Block(T.hours(12).msecs(), 4.0))

        Assertions.assertTrue(b.checkSanity())

        Assertions.assertEquals(1.0, b.blockValueBySeconds(T.hours(0).secs().toInt(), 1.0, 0), 0.01)
        Assertions.assertEquals(2.0, b.blockValueBySeconds(T.hours(1).secs().toInt(), 1.0, 0), 0.01)
        Assertions.assertEquals(3.0, b.blockValueBySeconds(T.hours(2).secs().toInt(), 1.0, 0), 0.01)
        Assertions.assertEquals(3.0, b.blockValueBySeconds(T.hours(3).secs().toInt(), 1.0, 0), 0.01)
        Assertions.assertEquals(4.0, b.blockValueBySeconds(T.hours(12).secs().toInt(), 1.0, 0), 0.01)
        Assertions.assertEquals(4.0, b.blockValueBySeconds(T.hours(13).secs().toInt(), 1.0, 0), 0.01)

        val s1 = b.shiftBlock(1.0, -1)

        Assertions.assertTrue(s1.checkSanity())

        Assertions.assertEquals(1.0, s1.blockValueBySeconds(T.hours(23).secs().toInt(), 1.0, 0), 0.01)
        Assertions.assertEquals(2.0, s1.blockValueBySeconds(T.hours(0).secs().toInt(), 1.0, 0), 0.01)
        Assertions.assertEquals(3.0, s1.blockValueBySeconds(T.hours(1).secs().toInt(), 1.0, 0), 0.01)
        Assertions.assertEquals(3.0, s1.blockValueBySeconds(T.hours(2).secs().toInt(), 1.0, 0), 0.01)
        Assertions.assertEquals(4.0, s1.blockValueBySeconds(T.hours(11).secs().toInt(), 1.0, 0), 0.01)
        Assertions.assertEquals(4.0, s1.blockValueBySeconds(T.hours(12).secs().toInt(), 1.0, 0), 0.01)

        val s2 = b.shiftBlock(2.0, 1)

        Assertions.assertTrue(s2.checkSanity())

        Assertions.assertEquals(2.0, s2.blockValueBySeconds(T.hours(1).secs().toInt(), 1.0, 0), 0.01)
        Assertions.assertEquals(4.0, s2.blockValueBySeconds(T.hours(2).secs().toInt(), 1.0, 0), 0.01)
        Assertions.assertEquals(6.0, s2.blockValueBySeconds(T.hours(3).secs().toInt(), 1.0, 0), 0.01)
        Assertions.assertEquals(6.0, s2.blockValueBySeconds(T.hours(4).secs().toInt(), 1.0, 0), 0.01)
        Assertions.assertEquals(8.0, s2.blockValueBySeconds(T.hours(13).secs().toInt(), 1.0, 0), 0.01)
        Assertions.assertEquals(8.0, s2.blockValueBySeconds(T.hours(14).secs().toInt(), 1.0, 0), 0.01)
    }

    @Test
    fun shiftTargetBlock() {
        val b = arrayListOf<TargetBlock>()
        b.add(TargetBlock(T.hours(1).msecs(), 1.0, 2.0))
        b.add(TargetBlock(T.hours(1).msecs(), 2.0, 3.0))
        b.add(TargetBlock(T.hours(10).msecs(), 3.0, 4.0))
        b.add(TargetBlock(T.hours(12).msecs(), 4.0, 5.0))

        Assertions.assertTrue(b.checkSanity())

        Assertions.assertEquals(1.5, b.targetBlockValueBySeconds(T.hours(0).secs().toInt(), 0), 0.01)
        Assertions.assertEquals(2.5, b.targetBlockValueBySeconds(T.hours(1).secs().toInt(), 0), 0.01)
        Assertions.assertEquals(3.5, b.targetBlockValueBySeconds(T.hours(2).secs().toInt(), 0), 0.01)
        Assertions.assertEquals(3.5, b.targetBlockValueBySeconds(T.hours(3).secs().toInt(), 0), 0.01)
        Assertions.assertEquals(4.5, b.targetBlockValueBySeconds(T.hours(12).secs().toInt(), 0), 0.01)
        Assertions.assertEquals(4.5, b.targetBlockValueBySeconds(T.hours(13).secs().toInt(), 0), 0.01)

        val s1 = b.shiftTargetBlock(-1)

        Assertions.assertTrue(s1.checkSanity())

        Assertions.assertEquals(1.5, s1.targetBlockValueBySeconds(T.hours(23).secs().toInt(), 0), 0.01)
        Assertions.assertEquals(2.5, s1.targetBlockValueBySeconds(T.hours(0).secs().toInt(), 0), 0.01)
        Assertions.assertEquals(3.5, s1.targetBlockValueBySeconds(T.hours(1).secs().toInt(), 0), 0.01)
        Assertions.assertEquals(3.5, s1.targetBlockValueBySeconds(T.hours(2).secs().toInt(), 0), 0.01)
        Assertions.assertEquals(4.5, s1.targetBlockValueBySeconds(T.hours(11).secs().toInt(), 0), 0.01)
        Assertions.assertEquals(4.5, s1.targetBlockValueBySeconds(T.hours(12).secs().toInt(), 0), 0.01)

        val s2 = b.shiftTargetBlock(1)

        Assertions.assertTrue(s2.checkSanity())

        Assertions.assertEquals(1.5, s2.targetBlockValueBySeconds(T.hours(1).secs().toInt(), 0), 0.01)
        Assertions.assertEquals(2.5, s2.targetBlockValueBySeconds(T.hours(2).secs().toInt(), 0), 0.01)
        Assertions.assertEquals(3.5, s2.targetBlockValueBySeconds(T.hours(3).secs().toInt(), 0), 0.01)
        Assertions.assertEquals(3.5, s2.targetBlockValueBySeconds(T.hours(4).secs().toInt(), 0), 0.01)
        Assertions.assertEquals(4.5, s2.targetBlockValueBySeconds(T.hours(13).secs().toInt(), 0), 0.01)
        Assertions.assertEquals(4.5, s2.targetBlockValueBySeconds(T.hours(14).secs().toInt(), 0), 0.01)
    }

    @Test
    fun lowTargetBlockValueBySeconds() {
        val b = arrayListOf<TargetBlock>()
        b.add(TargetBlock(T.hours(1).msecs(), 1.0, 2.0))
        b.add(TargetBlock(T.hours(1).msecs(), 2.0, 3.0))
        b.add(TargetBlock(T.hours(10).msecs(), 3.0, 4.0))
        b.add(TargetBlock(T.hours(12).msecs(), 4.0, 5.0))

        Assertions.assertTrue(b.checkSanity())

        Assertions.assertEquals(1.0, b.lowTargetBlockValueBySeconds(T.hours(0).secs().toInt(), 0), 0.01)
        Assertions.assertEquals(2.0, b.lowTargetBlockValueBySeconds(T.hours(1).secs().toInt(), 0), 0.01)
        Assertions.assertEquals(3.0, b.lowTargetBlockValueBySeconds(T.hours(2).secs().toInt(), 0), 0.01)
        Assertions.assertEquals(3.0, b.lowTargetBlockValueBySeconds(T.hours(3).secs().toInt(), 0), 0.01)
        Assertions.assertEquals(4.0, b.lowTargetBlockValueBySeconds(T.hours(12).secs().toInt(), 0), 0.01)
        Assertions.assertEquals(4.0, b.lowTargetBlockValueBySeconds(T.hours(13).secs().toInt(), 0), 0.01)
    }

    @Test
    fun highTargetBlockValueBySeconds() {
        val b = arrayListOf<TargetBlock>()
        b.add(TargetBlock(T.hours(1).msecs(), 1.0, 2.0))
        b.add(TargetBlock(T.hours(1).msecs(), 2.0, 3.0))
        b.add(TargetBlock(T.hours(10).msecs(), 3.0, 4.0))
        b.add(TargetBlock(T.hours(12).msecs(), 4.0, 5.0))

        Assertions.assertTrue(b.checkSanity())

        Assertions.assertEquals(2.0, b.highTargetBlockValueBySeconds(T.hours(0).secs().toInt(), 0), 0.01)
        Assertions.assertEquals(3.0, b.highTargetBlockValueBySeconds(T.hours(1).secs().toInt(), 0), 0.01)
        Assertions.assertEquals(4.0, b.highTargetBlockValueBySeconds(T.hours(2).secs().toInt(), 0), 0.01)
        Assertions.assertEquals(4.0, b.highTargetBlockValueBySeconds(T.hours(3).secs().toInt(), 0), 0.01)
        Assertions.assertEquals(5.0, b.highTargetBlockValueBySeconds(T.hours(12).secs().toInt(), 0), 0.01)
        Assertions.assertEquals(5.0, b.highTargetBlockValueBySeconds(T.hours(13).secs().toInt(), 0), 0.01)
    }
}