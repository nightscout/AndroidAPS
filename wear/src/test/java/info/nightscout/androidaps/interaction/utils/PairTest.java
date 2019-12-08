package info.nightscout.androidaps.interaction.utils;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.modules.junit4.PowerMockRunner;

import java.util.HashSet;
import java.util.Set;

import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

@RunWith(PowerMockRunner.class)
public class PairTest {

    @Test
    public void pairEqualsTest() {
        // GIVEN
        Pair left = Pair.create("aa", "bbb");
        Pair right = Pair.create("ccc", "dd");
        Pair another = Pair.create("aa", "bbb");
        Pair samePos1 = Pair.create("aa", "d");
        Pair samePos2 = Pair.create("zzzzz", "bbb");
        Pair no1 = Pair.create(12, 345L);
        Pair no2 = Pair.create(-943, 42);
        Pair no3 = Pair.create(12L, 345);
        Pair no4 = Pair.create(12, 345L);

        // THEN
        assertNotEquals(left, right);
        assertEquals(left, another);
        assertNotEquals(left, samePos1);
        assertNotEquals(left, samePos2);
        assertNotEquals(no1, no2);
        assertNotEquals(no1, no3);
        assertEquals(no1, no4);

        assertFalse(left.equals("aa bbb"));
    }

    @Test
    public void pairHashTest() {
        // GIVEN
        Pair inserted = Pair.create("aa", "bbb");
        Set<Pair> set = new HashSet<>();

        // THEN
        assertFalse(set.contains(inserted));
        set.add(inserted);
        assertTrue(set.contains(inserted));
    }

    @Test
    public void toStringTest() {
        // GIVEN
        Pair pair = Pair.create("the-first", "2nd");

        assertThat(pair.toString(), containsString("the-first"));
        assertThat(pair+"", containsString("2nd"));
    }



}
