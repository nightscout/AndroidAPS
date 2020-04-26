package info.nightscout.androidaps.data;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.modules.junit4.PowerMockRunner;

/**
 * Created by mike on 26.03.2018.
 */

@RunWith(PowerMockRunner.class)
public class DetailedBolusInfoTest {

    @Test
    public void toStringShouldBeOverloaded() {
        DetailedBolusInfo detailedBolusInfo = new DetailedBolusInfo();
        Assert.assertEquals(true, detailedBolusInfo.toString().contains("insulin"));
    }

    @Test
    public void copyShouldCopyAllProperties() {
        DetailedBolusInfo d1 = new DetailedBolusInfo();
        d1.deliverAt = 123;
        DetailedBolusInfo d2 = d1.copy();
        Assert.assertEquals(true, EqualsBuilder.reflectionEquals(d2, d1));
    }
}
