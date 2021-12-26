package info.nightscout.androidaps.interaction.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static info.nightscout.androidaps.testing.mockers.WearUtilMocker.REF_NOW;

import com.google.android.gms.wearable.DataMap;

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
    public void whenDataUpdatedTest() {
        // GIVEN
        DataMap map = new DataMap();

        // WHEN
        final long whenNotUpdated = persistence.whenDataUpdated();

        persistence.storeDataMap("data-map", map);
        final long whenUpdatedFirst = persistence.whenDataUpdated();

        getWearUtilMocker().progressClock(60000);
        persistence.storeDataMap("data-map", map);
        final long whenUpdatedNext = persistence.whenDataUpdated();

        // THEN
        assertEquals(whenNotUpdated, 0L);
        assertEquals(whenUpdatedFirst, REF_NOW);
        assertEquals(whenUpdatedNext, REF_NOW + 60000);
    }

    @Test
    public void getDataMapTest() {
        // GIVEN
        DataMap map = new DataMap();
        map.putByteArray("test-key", new byte[]{9, 42, 127, -5});

        // WHEN
        DataMap notExisting = persistence.getDataMap("not-there");
        persistence.storeDataMap("data-map", map);
        DataMap restoredMap = persistence.getDataMap("data-map");
        assert restoredMap != null;
        byte[] restoredMapContents = restoredMap.getByteArray("test-key");

        // THEN
        assertNull(notExisting);
        assertNotNull(restoredMap);
        assertTrue(restoredMap.containsKey("test-key"));

        assertEquals(restoredMapContents.length, 4);
        assertEquals(restoredMapContents[0], (byte) 9);
        assertEquals(restoredMapContents[1], (byte) 42);
        assertEquals(restoredMapContents[2], (byte) 127);
        assertEquals(restoredMapContents[3], (byte) -5);
    }

    @Test
    public void brokenDataMapTest() {
        // WHEN
        persistence.putString("data-map", "ZmFrZSBkYXRh");
        DataMap restoredMap = persistence.getDataMap("data-map");

        // THEN
        assertNull(restoredMap);
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
