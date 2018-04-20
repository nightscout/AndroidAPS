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
    public void pureNameTest() throws Exception {
        assertEquals("Fiasp", PercentageSplitter.pureName("Fiasp(101%)"));
    }
}
