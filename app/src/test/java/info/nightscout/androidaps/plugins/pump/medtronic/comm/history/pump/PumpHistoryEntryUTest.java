package info.nightscout.androidaps.plugins.pump.medtronic.comm.history.pump;

import org.junit.Assert;
import org.junit.Test;

/**
 * Created by andy on 4/9/19.
 */
public class PumpHistoryEntryUTest {

    @Test
    public void checkIsAfter() {

        long dateObject = 20191010000000L;
        long queryObject = 20191009000000L;

        PumpHistoryEntry phe = new PumpHistoryEntry();
        phe.atechDateTime = dateObject;

        Assert.assertTrue(phe.isAfter(queryObject));
    }

}
