package info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.definition;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

public class AlertSetTest {
    @Test
    public void testEquality() {
        AlertSet set1 = new AlertSet(Arrays.asList(AlertSlot.SLOT0, AlertSlot.SLOT1));
        AlertSet set2 = new AlertSet(Arrays.asList(AlertSlot.SLOT0, AlertSlot.SLOT1));
        AlertSet set3 = new AlertSet(Collections.singletonList(AlertSlot.SLOT1));

        assertEquals(set1, set2);
        assertNotEquals(set1, set3);
        assertNotEquals(set2, set3);
    }
}