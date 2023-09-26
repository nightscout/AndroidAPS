package app.aaps.wear.interaction.utils

import com.google.common.truth.Truth.assertThat
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
        assertThat(right).isNotEqualTo(left)
        assertThat(another).isEqualTo(left)
        assertThat(samePos1).isNotEqualTo(left)
        assertThat(samePos2).isNotEqualTo(left)
        assertThat(no2).isNotEqualTo(no1)
        assertThat(no3).isNotEqualTo(no1)
        assertThat(no4).isEqualTo(no1)
        assertThat(left.toString()).isNotEqualTo("aa bbb")
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
