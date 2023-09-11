package info.nightscout.androidaps.interaction.utils

import com.google.common.truth.Truth.assertThat
import org.junit.Assert
import org.junit.jupiter.api.Test

class PairTest {

    @Test fun pairEqualsTest() {
        // GIVEN
        val left: Pair<*, *> = Pair.create("aa", "bbb")
        val right: Pair<*, *> = Pair.create("ccc", "dd")
        val another: Pair<*, *> = Pair.create("aa", "bbb")
        val samePos1: Pair<*, *> = Pair.create("aa", "d")
        val samePos2: Pair<*, *> = Pair.create("zzzzz", "bbb")
        val no1: Pair<*, *> = Pair.create(12, 345L)
        val no2: Pair<*, *> = Pair.create(-943, 42)
        val no3: Pair<*, *> = Pair.create(12L, 345)
        val no4: Pair<*, *> = Pair.create(12, 345L)

        // THEN
        Assert.assertNotEquals(left, right)
        Assert.assertEquals(left, another)
        Assert.assertNotEquals(left, samePos1)
        Assert.assertNotEquals(left, samePos2)
        Assert.assertNotEquals(no1, no2)
        Assert.assertNotEquals(no1, no3)
        Assert.assertEquals(no1, no4)
        Assert.assertNotEquals("aa bbb", left.toString())
    }

    @Test fun pairHashTest() {
        // GIVEN
        val inserted: Pair<*, *> = Pair.create("aa", "bbb")
        val set: MutableSet<Pair<*, *>> = HashSet()

        // THEN
        assertThat(set).doesNotContain(inserted)
        set.add(inserted)
        assertThat(set).contains(inserted)
    }

    @Test fun toStringTest() {
        // GIVEN
        val pair: Pair<*, *> = Pair.create("the-first", "2nd")
        assertThat(pair.toString()).contains("the-first")
        assertThat(pair.toString()).contains("2nd")
    }
}