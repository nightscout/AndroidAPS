package app.aaps.pump.omnipod.eros.driver.definition;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

class AlertSetTest {
    @Test
    void testEquality() {
        AlertSet set1 = new AlertSet(Arrays.asList(AlertSlot.SLOT0, AlertSlot.SLOT1));
        AlertSet set2 = new AlertSet(Arrays.asList(AlertSlot.SLOT0, AlertSlot.SLOT1));
        AlertSet set3 = new AlertSet(Collections.singletonList(AlertSlot.SLOT1));

        Assertions.assertEquals(set1, set2);
        Assertions.assertNotEquals(set1, set3);
        Assertions.assertNotEquals(set2, set3);
    }
}