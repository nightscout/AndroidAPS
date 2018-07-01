package info.nightscout.utils;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Created by mike on 22.12.2017.
 */

public class PercentageSplitterTest {
    public PercentageSplitterTest() {
        super();
    }

    @Test
    public void pureNameTestPercentageOnly() {
        assertEquals("Fiasp", PercentageSplitter.pureName("Fiasp(101%)"));
    }

    @Test
    public void pureNameTestPercentageAndShift() {
        assertEquals("Fiasp", PercentageSplitter.pureName("Fiasp (101%,2h)"));
    }
}
