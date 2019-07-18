package info.nightscout.androidaps.plugins.pump.medtronic.comm.history.pump;

import org.junit.Assert;
import org.junit.Test;

import java.util.Date;

/**
 * Created by andy on 4/9/19.
 */
public class PumpHistoryEntryUTest {

    //@Test
    public void checkIsAfter() {

        long dateObject = 20191010000000L;
        long queryObject = 20191009000000L;

        PumpHistoryEntry phe = new PumpHistoryEntry();
        phe.atechDateTime = dateObject;

        Assert.assertTrue(phe.isAfter(queryObject));
    }

    //@Test
    public void testDatesTDD() {
        long[]  data = { 1557010799726L,
        1557010799651L,
        1557010799393L,
        1557010799289L,
        1557010799109L,
        1556924400709L,
        1556924400521L,
        1556924400353L,
        1556924399948L,
        1556924399910L };

        for (long datum : data) {
            Date d = new Date();
            d.setTime(datum);

            System.out.println("Date: " + d);
        }




    }

}
