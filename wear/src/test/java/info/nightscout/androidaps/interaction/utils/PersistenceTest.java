package info.nightscout.androidaps.interaction.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.google.android.gms.wearable.DataMap;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.util.Set;

import info.nightscout.androidaps.aaps;
import info.nightscout.androidaps.testing.mockers.AAPSMocker;
import info.nightscout.androidaps.testing.mockers.AndroidMocker;
import info.nightscout.androidaps.testing.mockers.LogMocker;
import info.nightscout.androidaps.testing.mockers.WearUtilMocker;

import static info.nightscout.androidaps.testing.mockers.WearUtilMocker.REF_NOW;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

@RunWith(PowerMockRunner.class)
@PrepareForTest( { WearUtil.class, Log.class, SharedPreferences.class, Context.class, aaps.class, android.util.Base64.class} )
public class PersistenceTest {

    @Before
    public void mock() throws Exception {
        WearUtilMocker.prepareMock();
        LogMocker.prepareMock();
        AAPSMocker.prepareMock();
        AAPSMocker.resetMockedSharedPrefs();
        AndroidMocker.mockBase64();
    }

    @Test
    public void putStringTest() {
        // GIVEN
        Persistence persistence = new Persistence();

        // WHEN
        final String emptyGot = persistence.getString("test-key", "default-value");
        persistence.putString("test-key", "newValue");
        final String updatedGot = persistence.getString("test-key", "another-default-value");

        // THEN
        assertThat(emptyGot, is("default-value"));
        assertThat(updatedGot, is("newValue"));
    }

    @Test
    public void putBooleanTest() {
        // GIVEN
        Persistence persistence = new Persistence();

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
        Persistence persistence = new Persistence();
        DataMap map = new DataMap();

        // WHEN
        final long whenNotUpdated = persistence.whenDataUpdated();

        Persistence.storeDataMap("data-map", map);
        final long whenUpdatedFirst = persistence.whenDataUpdated();

        WearUtilMocker.progressClock(60000);
        Persistence.storeDataMap("data-map", map);
        final long whenUpdatedNext = persistence.whenDataUpdated();

        // THEN
        assertThat(whenNotUpdated, is(0L));
        assertThat(whenUpdatedFirst, is(REF_NOW));
        assertThat(whenUpdatedNext, is(REF_NOW + 60000));
    }

    @Test
    public void getDataMapTest() {
        // GIVEN
        Persistence persistence = new Persistence();
        DataMap map = new DataMap();
        map.putByteArray("test-key", new byte[]{9, 42, 127, -5});

        // WHEN
        DataMap notExisting = persistence.getDataMap("not-there");
        Persistence.storeDataMap("data-map", map);
        DataMap restoredMap = persistence.getDataMap("data-map");
        byte[] restoredMapContents = restoredMap.getByteArray("test-key");

        // THEN
        assertNull(notExisting);
        assertNotNull(restoredMap);
        assertTrue(restoredMap.containsKey("test-key"));

        assertThat(restoredMapContents.length, is(4));
        assertThat(restoredMapContents[0], is((byte)9));
        assertThat(restoredMapContents[1], is((byte)42));
        assertThat(restoredMapContents[2], is((byte)127));
        assertThat(restoredMapContents[3], is((byte)-5));
    }

    @Test
    public void brokenDataMapTest() {
        // GIVEN
        Persistence persistence = new Persistence();

        // WHEN
        persistence.putString("data-map", "ZmFrZSBkYXRh");
        DataMap restoredMap = persistence.getDataMap("data-map");

        // THEN
        assertNull(restoredMap);
    }

    @Test
    public void setsTest() {
        // GIVEN
        Persistence persistence = new Persistence();

        // WHEN
        Set<String> emptySet = persistence.getSetOf("some fake id");

        persistence.addToSet("test-set", "element1");
        persistence.addToSet("test-set", "second-elem");
        persistence.addToSet("test-set", "3rd");
        persistence.addToSet("test-set", "czwarty");
        persistence.addToSet("test-set", "V");
        persistence.addToSet("test-set", "6");

        Set<String> initialSet = persistence.getSetOf("test-set");
        Set<String> sameInitialSet = Persistence.setOf("test-set");

        persistence.addToSet("test-set", "second-elem");
        persistence.addToSet("test-set", "new-one");

        Set<String> extendedSet = persistence.getSetOf("test-set");

        persistence.removeFromSet("test-set", "czwarty");
        persistence.removeFromSet("test-set", "6");
        persistence.removeFromSet("test-set", "3rd");

        Set<String> reducedSet = persistence.getSetOf("test-set");

        // THEN
        assertThat(emptySet.size(), is(0));

        assertThat(initialSet.size(), is(6));
        assertTrue(initialSet.contains("element1"));
        assertTrue(initialSet.contains("second-elem"));
        assertTrue(initialSet.contains("3rd"));
        assertTrue(initialSet.contains("czwarty"));
        assertTrue(initialSet.contains("V"));
        assertTrue(initialSet.contains("6"));

        assertThat(initialSet, is(sameInitialSet));

        assertThat(extendedSet.size(), is(7));
        assertTrue(extendedSet.contains("new-one"));

        assertThat(reducedSet.size(), is(4));
        assertTrue(reducedSet.contains("element1"));
        assertTrue(reducedSet.contains("second-elem"));
        assertFalse(reducedSet.contains("3rd"));
        assertFalse(reducedSet.contains("czwarty"));
        assertTrue(reducedSet.contains("V"));
        assertFalse(reducedSet.contains("6"));
        assertTrue(reducedSet.contains("new-one"));
    }

}
