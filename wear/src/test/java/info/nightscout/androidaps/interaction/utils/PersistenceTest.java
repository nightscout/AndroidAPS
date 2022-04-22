package info.nightscout.androidaps.interaction.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Set;

import info.nightscout.androidaps.TestBase;

@SuppressWarnings("SpellCheckingInspection")
public class PersistenceTest extends TestBase {

    @Test
    public void putStringTest() {
        // WHEN
        final String emptyGot = persistence.getString("test-key", "default-value");
        persistence.putString("test-key", "newValue");
        final String updatedGot = persistence.getString("test-key", "another-default-value");

        // THEN
        assertEquals(emptyGot, "default-value");
        assertEquals(updatedGot, "newValue");
    }

    @Test
    public void putBooleanTest() {
        // WHEN
        final boolean emptyGot = persistence.getBoolean("test-key", false);
        persistence.putBoolean("test-key", true);
        final boolean updatedGot = persistence.getBoolean("test-key", false);

        // THEN
        assertFalse(emptyGot);
        assertTrue(updatedGot);
    }

    @Test
    public void setsTest() {
        // WHEN
        Set<String> emptySet = persistence.getSetOf("some fake id");

        persistence.addToSet("test-set", "element1");
        persistence.addToSet("test-set", "second-elem");
        persistence.addToSet("test-set", "3rd");
        persistence.addToSet("test-set", "czwarty");
        persistence.addToSet("test-set", "V");
        persistence.addToSet("test-set", "6");

        Set<String> initialSet = persistence.getSetOf("test-set");
        Set<String> sameInitialSet = persistence.getSetOf("test-set");

        persistence.addToSet("test-set", "second-elem");
        persistence.addToSet("test-set", "new-one");

        Set<String> extendedSet = persistence.getSetOf("test-set");

        persistence.removeFromSet("test-set", "czwarty");
        persistence.removeFromSet("test-set", "6");
        persistence.removeFromSet("test-set", "3rd");

        Set<String> reducedSet = persistence.getSetOf("test-set");

        // THEN
        assertEquals(emptySet.size(), 0);

        assertEquals(initialSet.size(), 6);
        assertTrue(initialSet.contains("element1"));
        assertTrue(initialSet.contains("second-elem"));
        assertTrue(initialSet.contains("3rd"));
        assertTrue(initialSet.contains("czwarty"));
        assertTrue(initialSet.contains("V"));
        assertTrue(initialSet.contains("6"));

        assertEquals(initialSet, sameInitialSet);

        assertEquals(extendedSet.size(), 7);
        assertTrue(extendedSet.contains("new-one"));

        assertEquals(reducedSet.size(), 4);
        assertTrue(reducedSet.contains("element1"));
        assertTrue(reducedSet.contains("second-elem"));
        assertFalse(reducedSet.contains("3rd"));
        assertFalse(reducedSet.contains("czwarty"));
        assertTrue(reducedSet.contains("V"));
        assertFalse(reducedSet.contains("6"));
        assertTrue(reducedSet.contains("new-one"));
    }

}
