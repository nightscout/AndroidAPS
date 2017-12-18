package info.nightscout.utils;


import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Created by mike on 20.11.2017.
 */

public class DateUtilTest {

    public DateUtilTest() {
        super();
    }

    @Test
    public void fromISODateStringTest() throws Exception {
        assertEquals( 1511124634417L, DateUtil.fromISODateString("2017-11-19T22:50:34.417+0200").getTime());
        assertEquals( 1511124634000L, DateUtil.fromISODateString("2017-11-19T22:50:34+0200").getTime());
        assertEquals( 1512317365000L, DateUtil.fromISODateString("2017-12-03T16:09:25.000Z").getTime());
    }


}
